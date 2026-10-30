from __future__ import annotations

import argparse
import base64
import binascii
import json
import sys
import tempfile
from pathlib import Path

from .core import Context, Session
from .providers import LocalProvider, download_models, record


def rpc(session: Session, transcriber=None) -> None:
    """One JSON request/response per line. Use SSH as the authenticated transport."""
    while True:
        line = sys.stdin.buffer.readline(2 * 1024 * 1024 + 1)
        if not line:
            return
        request_id = None
        try:
            if len(line) > 2 * 1024 * 1024:
                # Do not interpret the remainder of an oversized line as a new request.
                while line and not line.endswith(b"\n"):
                    line = sys.stdin.buffer.readline(2 * 1024 * 1024 + 1)
                raise ValueError("Request exceeds 2 MiB")
            request = json.loads(line)
            if not isinstance(request, dict):
                raise ValueError("Request must be an object")
            request_id = request.get("id")
            method = request.get("method")
            params = request.get("params", {})
            if not isinstance(params, dict):
                raise ValueError("params must be an object")
            if method == "context":
                result = session.context.__dict__
            elif method == "transcribe":
                if transcriber is None:
                    raise ValueError("Transcription is unavailable")
                encoded = params.get("audio")
                if not isinstance(encoded, str) or len(encoded) > 1400000:
                    raise ValueError("Audio must be base64 WAV, at most 1 MiB")
                try:
                    audio = base64.b64decode(encoded, validate=True)
                except (ValueError, binascii.Error):
                    raise ValueError("Invalid base64 audio") from None
                if len(audio) > 1024 * 1024 or not audio.startswith(b"RIFF") or audio[8:12] != b"WAVE":
                    raise ValueError("Audio must be WAV, at most 1 MiB")
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "request.wav"
                    path.write_bytes(audio)
                    result = {"text": transcriber.transcribe(path)}
            elif method == "plan":
                result = session.propose(params.get("text")).to_dict()
            elif method == "execute":
                if not isinstance(params.get("proposal_id"), str):
                    raise ValueError("proposal_id must be a string")
                with tempfile.TemporaryFile() as output:
                    result = session.execute(params["proposal_id"], params.get("approved"), output)
                    output.seek(0)
                    data = output.read(1024 * 1024 + 1)
                result.update(output=data[:1024 * 1024].decode("utf-8", errors="replace"),
                              truncated=len(data) > 1024 * 1024)
            else:
                raise ValueError("Unknown method; use context, transcribe, plan, or execute")
            response = {"id": request_id, "result": result}
        except Exception as error:
            response = {"id": request_id, "error": str(error)}
        print(json.dumps(response), flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Speak or type intent; review and run shell commands")
    parser.add_argument("text", nargs="*", help="Natural-language request")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--download-models", action="store_true", help="Download local Whisper weights (one-time online setup)")
    source.add_argument("--listen", action="store_true", help="Record the local microphone")
    source.add_argument("--audio", type=Path, help="Transcribe an existing audio file")
    source.add_argument("--rpc", action="store_true", help="JSON-lines protocol over stdin/stdout")
    parser.add_argument("--seconds", type=float, default=5, help="Recording length (default: 5)")
    parser.add_argument("--cwd", help="Execution directory")
    parser.add_argument("--timeout", type=int, default=60, help="Execution timeout in seconds")
    parser.add_argument("--dry-run", action="store_true", help="Print proposal without execution")
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    if args.text and (args.rpc or args.listen or args.audio or args.download_models):
        parser.error("Choose a text request or one input mode")
    try:
        if args.download_models:
            download_models()
            print("Whisper model downloaded. Command model setup: ollama pull qwen2.5:3b")
            return 0
        provider = LocalProvider()
        session = Session(provider, Context.local(args.cwd), args.timeout)
        if args.rpc:
            if args.dry_run:
                parser.error("--dry-run cannot be combined with --rpc")
            rpc(session, provider)
            return 0
        if args.listen:
            print(f"Recording for {args.seconds:g} seconds…", file=sys.stderr)
            with tempfile.TemporaryDirectory() as directory:
                audio = Path(directory) / "recording.wav"
                record(audio, args.seconds)
                text = provider.transcribe(audio)
        elif args.audio:
            text = provider.transcribe(args.audio)
        else:
            text = " ".join(args.text) if args.text else input("What would you like to do? ")
        proposal = session.propose(text)
        print(f"Request: {text}\nHost: {proposal.context.system}\nDirectory: {proposal.context.cwd}")
        # JSON escaping prevents model-generated terminal control sequences from executing.
        print("Command: " + json.dumps(proposal.command, ensure_ascii=True))
        print("Explanation: " + json.dumps(proposal.explanation, ensure_ascii=True))
        if args.dry_run:
            return 0
        if not sys.stdin.isatty():
            print("Execution requires an interactive terminal; use ssh -t or the RPC protocol.", file=sys.stderr)
            return 2
        if input("Type 'run' to execute: ").strip() != "run":
            print("Cancelled.")
            return 0
        result = session.execute(proposal.id, True)
        if result["timed_out"]:
            print("Command timed out.", file=sys.stderr)
        return result["exit_code"]
    except KeyboardInterrupt:
        print("\nCancelled.", file=sys.stderr)
        return 130
    except Exception as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
