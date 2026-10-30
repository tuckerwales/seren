# SpeakShell host companion for Seren SSH

Runs speech recognition and natural-language command translation **on your own macOS/Linux SSH host**. Android records PCM audio and sends it through an authenticated SSH exec channel. faster-whisper transcribes it locally; a local Ollama model generates a proposal. The phone displays the transcript, command, host OS, and working directory. Only explicit user approval executes the stored proposal, and output returns to the dialog.

No API keys, subscriptions, cloud inference, Android system speech service, or public HTTP server. Software and model downloads need internet once. Afterwards, inference works offline. SSH naturally still needs connectivity to the host, and commands you choose to run may use the internet. The models run on the SSH host, not on the phone.

## Host setup

Requires Python 3.11+ and Ollama (https://ollama.com). From this repository on the SSH host:

```sh
cd tools/speakshell
python3 -m venv .venv
. .venv/bin/activate
pip install '.[speech]'
speakshell --download-models
```

Install and start Ollama locally. If its desktop application/service isn't already running, run this in a separate terminal:

```sh
ollama serve
```

Download the command model once, then test translation:

```sh
ollama pull qwen2.5:3b
speakshell --dry-run 'show files including hidden files'
```

Keep Ollama bound to its default loopback interface. SpeakShell connects only to `http://127.0.0.1:11434`, ignores environment HTTP proxies, rejects redirects, and rejects cloud/remote Ollama models. It never forwards the model service to Android.

The default command model is `qwen2.5:3b`, with a few GB of weights plus runtime/context memory. A larger local model, such as `qwen2.5:7b`, can produce better proposals if the host has enough RAM. Ollama uses supported GPUs/Apple Silicon where available. Whisper defaults to `base`, using CPU int8 for portability. Inference speed varies by host; the client allows four minutes per request and local command execution has a 60-second timeout.

Optional environment variables on the **host backend process**:

- `SPEAKSHELL_MODEL`: a locally downloaded Ollama model name.
- `SPEAKSHELL_WHISPER_MODEL`: a faster-whisper model name or a local CTranslate2 Whisper directory. Set this before downloading and at runtime.

If you need custom environment settings, create an executable wrapper that sets them and then `exec /absolute/path/to/.venv/bin/speakshell "$@"`. Configure the wrapper path in the app. SSH noninteractive shells may not load your interactive shell's environment.

## Use in Seren SSH

1. Connect to the configured host normally. Existing host-key verification, authentication, and jump hosts apply.
2. Open the terminal's **More → Voice commands** menu.
3. Enter the absolute host path to `tools/speakshell/.venv/bin/speakshell` (or your wrapper). `speakshell` also works if installed on the noninteractive SSH PATH. `~` is not expanded in the executable path; use an absolute path.
4. Enter a host working directory or leave it blank for the login directory. Directory paths support `~` on the host.
5. Tap **Record voice**, allow microphone access, and speak. Tap **Stop recording** or wait eight seconds. The app records locally in memory and sends a WAV clip through SSH. The host removes its temporary audio after transcription.
6. Review/edit the transcript, then tap **Translate request**. Typed requests also work without microphone permission or Whisper installed.
7. Review the exact proposed command, explanation, OS, and directory. Tap **Run approved command** to execute it. Exit status and output appear in the dialog.

Executable/directory settings are saved per user/host/port after translation. Transcripts, proposals, and output are not saved. Recording stops when the app leaves the foreground; closing/backgrounding the dialog closes its private SSH channel.

The assistant uses a **fresh noninteractive POSIX `/bin/sh`**, separately from the interactive terminal. It does not inherit the terminal's current directory, `cd`, exported variables, terminal programs, or shell history. Choose the directory explicitly. Generated commands are fallible and run with your SSH account's privileges, without sandboxing. Review them carefully.

If connectivity is lost after approval, command completion may be unknown. Do not retry blindly; reconnect and inspect the host. Closing a channel is not a guarantee that a started command was cancelled. The host execution timeout kills the process group; deliberately detached processes can survive. RPC output is capped at 1 MiB and buffered on disk while running, so unbounded output can consume disk space. Interactive programs and persistent shell state are unsupported.

## Standalone use

```sh
speakshell 'show current git changes'
speakshell --audio request.wav
# Local microphone on macOS/Linux (optional PortAudio system library required):
pip install '.[voice]'
speakshell --listen --seconds 6
```

The CLI displays a proposal and requires typing `run`. Use `--dry-run` to inspect without executing, `--cwd` to select a directory, and `--timeout` to change the execution limit.

## Protocol, version 1

Open `speakshell --rpc [--cwd DIRECTORY]` in a persistent SSH exec channel, without a PTY. One UTF-8 JSON request/response per line. No stdout banners/logs. Requests are limited to 2 MiB; Android's eight-second WAV recording is below 256 KiB plus its WAV header. Audio input is base64 WAV up to 1 MiB decoded. Responses have the same request `id`, and either `result` or an `error` string.

```json
{"id":1,"method":"context"}
{"id":2,"method":"transcribe","params":{"audio":"BASE64_WAV_BYTES"}}
{"id":3,"method":"plan","params":{"text":"list files"}}
{"id":4,"method":"execute","params":{"proposal_id":"ID_FROM_PLAN_RESULT","approved":true}}
```

`context` returns `cwd`, `system`, and `shell`. `transcribe` returns `text`. `plan` returns `id` (proposal token), `request`, `command`, `explanation`, and `context`. `execute` returns `exit_code`, `timed_out`, `output` (merged stdout/stderr), and `truncated`. The latest proposal supersedes the previous one; execution consumes a token once, including failures. Approval uses the stored command, never a client-supplied replacement. Tokens are valid only in that backend session. Clients must obtain user approval; the protocol does not defend against a malicious authenticated SSH user.

## Modules and tests

- `core.py`: host context, planner/transcriber interfaces, proposal lifecycle, shell execution.
- `providers.py`: local model adapters and optional microphone input.
- `cli.py`: CLI plus transport-independent JSON-lines protocol.
- Android `voice/`: PCM recorder, SSH protocol client, Compose review/approval dialog.

```sh
PYTHONPATH=src python3 -m unittest discover -s tests -v
```

Tests exercise speech-adapter boundaries, local-only model requests, approval, rejection of replayed proposals, cleanup, and a real subprocess protocol exchange from audio through execution. Model responses are faked; real transcription quality, local model quality, microphone behavior, and device-to-sshd integration require a configured host and Android device. Android unit tests cover WAV framing, path quoting, protocol approval/output and failures; an optional existing `SSH_TEST_HOST` integration test covers the separate exec channel.
