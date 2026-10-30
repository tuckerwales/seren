import base64
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import patch

from speakshell.cli import rpc
from speakshell.core import Context, Session


class FakeModels:
    def plan(self, request, context):
        return "printf hello; touch approved", "Write a marker"

    def transcribe(self, path):
        self.path = path
        with wave.open(str(path)) as audio:
            assert audio.getframerate() == 16000
        return "create a marker"


def audio():
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(b"\x00\x00" * 800)
    return base64.b64encode(output.getvalue()).decode()


class RpcTests(unittest.TestCase):
    def call(self, requests):
        models = FakeModels()
        incoming = io.TextIOWrapper(io.BytesIO(("\n".join(json.dumps(r) for r in requests) + "\n").encode()))
        output = io.StringIO()
        with patch("sys.stdin", incoming), patch("sys.stdout", output):
            rpc(Session(models, Context.local()), models)
        return [json.loads(line) for line in output.getvalue().splitlines()], models

    def test_audio_transcription_and_cleanup(self):
        responses, models = self.call([{"id": 1, "method": "transcribe", "params": {"audio": audio()}}])
        self.assertEqual(responses[0]["result"]["text"], "create a marker")
        self.assertFalse(models.path.exists())

    def test_rejects_invalid_audio_and_recovers(self):
        for invalid in (None, "not base64", base64.b64encode(b"not wav").decode(), 123):
            results, _ = self.call([
                {"id": 1, "method": "transcribe", "params": {"audio": invalid}},
                {"id": 2, "method": "context"},
            ])
            self.assertIn("error", results[0])
            self.assertIn("cwd", results[1]["result"])

    def test_requires_actual_boolean_approval(self):
        results, _ = self.call([{"id": 1, "method": "execute", "params": {"proposal_id": "anything", "approved": "true"}}])
        self.assertIn("Explicit approval", results[0]["error"])

    def test_separate_process_voice_plan_approve_output_replay(self):
        # Same persistent stdin/stdout exchange used by Android's SSH exec channel.
        runner = '''
import os
from speakshell.cli import rpc
from speakshell.core import Context, Session
class Models:
    def transcribe(self, path): return "create a marker"
    def plan(self, request, context): return "printf hello; touch approved", "Write a marker"
models = Models()
rpc(Session(models, Context.local()), models)
'''
        with tempfile.TemporaryDirectory() as directory:
            environment = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parents[1] / "src"))
            process = subprocess.Popen([sys.executable, "-c", runner], cwd=directory,
                                       env=environment, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE, text=True)
            def cleanup():
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
                for stream in (process.stdin, process.stdout, process.stderr):
                    stream.close()
            self.addCleanup(cleanup)
            def send(request):
                process.stdin.write(json.dumps(request) + "\n")
                process.stdin.flush()
                return json.loads(process.stdout.readline())
            transcript = send({"id": 1, "method": "transcribe", "params": {"audio": audio()}})
            plan = send({"id": 2, "method": "plan", "params": {"text": transcript["result"]["text"]}})["result"]
            self.assertFalse((Path(directory) / "approved").exists())
            self.assertEqual(plan["context"]["cwd"], str(Path(directory).resolve()))
            execute = {"id": 3, "method": "execute", "params": {"proposal_id": plan["id"], "approved": True}}
            result = send(execute)["result"]
            self.assertEqual(result["output"], "hello")
            self.assertEqual(result["exit_code"], 0)
            self.assertTrue((Path(directory) / "approved").exists())
            self.assertIn("error", send(execute))
            process.stdin.close()
            self.assertEqual(process.wait(timeout=5), 0)
            process.stdout.close()
            process.stderr.close()
