import os
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from speakshell.core import Context
from speakshell.providers import LocalProvider


class LocalTests(unittest.TestCase):
    def test_no_key_required(self):
        with patch.dict(os.environ, {}, clear=True):
            provider = LocalProvider()
            self.assertEqual(provider.model, "qwen2.5:3b")

    def test_cloud_model_rejected(self):
        with patch.dict(os.environ, {"SPEAKSHELL_MODEL": "model:cloud"}):
            with self.assertRaises(ValueError):
                LocalProvider()

    def test_remote_model_rejected_before_chat(self):
        provider = LocalProvider()
        with patch.object(provider, "_post", return_value={"remote_host": "https://example.com"}) as post:
            with self.assertRaises(ValueError):
                provider.plan("hello", Context.local())
            self.assertEqual(post.call_count, 1)

    def test_loopback_only(self):
        provider = LocalProvider()
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b'{}'
        with patch.object(provider.opener, "open", return_value=response) as opened:
            provider._post("/api/show", {"model": "test"})
            self.assertEqual(opened.call_args.args[0].full_url, "http://127.0.0.1:11434/api/show")

    def test_transcription_offline_and_cached(self):
        model = Mock()
        model.transcribe.side_effect = [
            (iter([types.SimpleNamespace(text=" list files ")]), None),
            (iter([types.SimpleNamespace(text=" show changes ")]), None),
        ]
        constructor = Mock(return_value=model)
        module = types.SimpleNamespace(WhisperModel=constructor)
        with tempfile.NamedTemporaryFile(suffix=".wav") as audio:
            with patch.dict("sys.modules", {"faster_whisper": module}):
                provider = LocalProvider()
                self.assertEqual(provider.transcribe(Path(audio.name)), "list files")
                self.assertEqual(provider.transcribe(Path(audio.name)), "show changes")
        constructor.assert_called_once()
        self.assertTrue(constructor.call_args.kwargs["local_files_only"])
        self.assertEqual(constructor.call_args.kwargs["device"], "cpu")

    def test_malformed_plan(self):
        provider = LocalProvider()
        for content in ("oops", "[]", '{"command":12,"explanation":"bad"}'):
            with patch.object(provider, "_post", side_effect=[{}, {"message": {"content": content}}]):
                with self.assertRaises(ValueError):
                    provider.plan("hello", Context.local())
