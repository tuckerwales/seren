from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
import wave
from pathlib import Path

from .core import Context


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("Local model service attempted a redirect")


class LocalProvider:
    """Local Ollama translation and in-process faster-whisper transcription."""

    def __init__(self):
        self.model = os.environ.get("SPEAKSHELL_MODEL", "qwen2.5:3b")
        if "cloud" in self.model.lower():
            raise ValueError("Choose a local Ollama model; cloud models are disabled")
        # Fixed loopback address, no environment proxies or HTTP redirects.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self._whisper = None

    def _post(self, endpoint: str, payload: dict) -> dict:
        request = urllib.request.Request(
            "http://127.0.0.1:11434" + endpoint,
            data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json"},
        )
        try:
            with self.opener.open(request, timeout=180) as response:
                result = json.load(response)
            if not isinstance(result, dict):
                raise ValueError("Local model service returned an invalid response")
            return result
        except urllib.error.HTTPError as error:
            if error.code == 404:
                raise RuntimeError(f"Local model not found. Download it first: ollama pull {self.model}") from None
            raise RuntimeError(f"Local Ollama returned HTTP {error.code}") from None
        except urllib.error.URLError:
            raise RuntimeError("Cannot reach local Ollama. Start it with: ollama serve") from None

    def plan(self, request: str, context: Context) -> tuple[str, str]:
        info = self._post("/api/show", {"model": self.model})
        if info.get("remote_model") or info.get("remote_host"):
            raise ValueError("Remote Ollama models are disabled; choose a locally downloaded model")
        payload = {
            "model": self.model,
            "stream": False,
            "format": "json",
            "options": {"temperature": 0},
            "messages": [
                {"role": "system", "content": (
                    "Translate the user's intent into a POSIX shell command for the supplied host. "
                    "Return JSON with exactly command and explanation strings. Do not run anything. "
                    "Use commands compatible with the host OS. Quote paths correctly. "
                    "Do not invent paths or credentials. If the request is ambiguous, return an empty "
                    "command and explain what needs clarification. Describe destructive effects. "
                    "Never add sudo unless explicitly requested. Do not use interactive programs. "
                    "Each command runs in a fresh shell; cd and environment changes do not persist. "
                    "Host context: " + json.dumps(context.__dict__)
                )},
                {"role": "user", "content": request},
            ],
        }
        result = self._post("/api/chat", payload)
        try:
            plan = json.loads(result["message"]["content"])
            if not isinstance(plan, dict):
                raise TypeError()
            if not plan.get("command"):
                raise ValueError("Clarification needed: " + str(plan.get("explanation", "Rephrase the request")))
            if not isinstance(plan["command"], str) or not isinstance(plan["explanation"], str):
                raise TypeError()
            return plan["command"], plan["explanation"]
        except (KeyError, IndexError, TypeError, json.JSONDecodeError):
            raise ValueError("Local model returned a malformed plan; try rephrasing or a larger model") from None

    def transcribe(self, path: Path) -> str:
        if not path.is_file():
            raise ValueError("Audio file does not exist")
        try:
            from faster_whisper import WhisperModel
        except ImportError:
            raise ValueError("Local transcription requires: pip install 'speakshell[speech]'") from None
        if self._whisper is None:
            model = os.environ.get("SPEAKSHELL_WHISPER_MODEL", "base")
            try:
                self._whisper = WhisperModel(
                    model, device="cpu", compute_type="int8", local_files_only=True,
                )
            except Exception:
                raise RuntimeError(
                    "Whisper model is not available locally. Run speakshell --download-models "
                    "once while online, or set SPEAKSHELL_WHISPER_MODEL to a local model directory."
                ) from None
        segments, _ = self._whisper.transcribe(str(path), beam_size=5, vad_filter=True)
        text = " ".join(segment.text.strip() for segment in segments).strip()
        if not text:
            raise ValueError("No speech detected")
        return text


def download_models() -> None:
    """Explicit online setup only. Normal transcription never downloads models."""
    try:
        from faster_whisper.utils import download_model
    except ImportError:
        raise ValueError("Install speech support first: pip install 'speakshell[speech]'") from None
    download_model(os.environ.get("SPEAKSHELL_WHISPER_MODEL", "base"))

def record(path: Path, seconds: float) -> None:
    if not 0 < seconds <= 120:
        raise ValueError("Recording duration must be between 0 and 120 seconds")
    try:
        import sounddevice as sd
    except ImportError:
        raise ValueError("Microphone support requires: pip install 'speakshell[voice]'") from None
    rate = 16000
    audio = sd.rec(int(seconds * rate), samplerate=rate, channels=1, dtype="int16")
    sd.wait()
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(audio.tobytes())
