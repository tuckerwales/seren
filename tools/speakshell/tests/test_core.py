import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from speakshell.core import Context, Session
from speakshell.cli import rpc
from speakshell.providers import LocalProvider


class FakePlanner:
    def __init__(self, command="printf hello"):
        self.command = command

    def plan(self, request, context):
        return self.command, "Test command"


class CoreTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.context = Context.local(self.directory.name)

    def test_proposal_never_executes_and_needs_approval(self):
        target = Path(self.directory.name) / "created"
        session = Session(FakePlanner("touch created"), self.context)
        proposal = session.propose("create a file")
        self.assertFalse(target.exists())
        with self.assertRaises(ValueError):
            session.execute(proposal.id, False)
        self.assertFalse(target.exists())
        self.assertEqual(session.execute(proposal.id, True)["exit_code"], 0)
        self.assertTrue(target.exists())
        with self.assertRaises(ValueError):
            session.execute(proposal.id, True)

    def test_superseded_and_foreign_proposals_rejected(self):
        session = Session(FakePlanner(), self.context)
        old = session.propose("first")
        session.propose("second")
        for token in (old.id, "foreign"):
            with self.assertRaises(ValueError):
                session.execute(token, True)

    def test_output_exit_code_and_cwd(self):
        session = Session(FakePlanner("pwd; printf error >&2; exit 7"), self.context)
        proposal = session.propose("test")
        with tempfile.TemporaryFile() as output:
            result = session.execute(proposal.id, True, output)
            output.seek(0)
            text = output.read().decode()
        self.assertEqual(result["exit_code"], 7)
        self.assertIn(self.context.cwd, text)
        self.assertIn("error", text)

    def test_timeout(self):
        session = Session(FakePlanner("sleep 10"), self.context, timeout=0.05)
        proposal = session.propose("wait")
        self.assertEqual(session.execute(proposal.id, True), {"exit_code": 124, "timed_out": True})

    def test_invalid_plans(self):
        for command in ("", "\x00", 12):
            with self.assertRaises(ValueError):
                Session(FakePlanner(command), self.context).propose("hello")

    def test_rpc_error_recovery_and_plan(self):
        source = io.TextIOWrapper(io.BytesIO(
            b'not json\n{"id":1,"method":"plan","params":{"text":"list files"}}\n'
        ))
        output = io.StringIO()
        with patch("sys.stdin", source), patch("sys.stdout", output):
            rpc(Session(FakePlanner(), self.context))
        responses = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertIn("error", responses[0])
        self.assertEqual(responses[1]["result"]["command"], "printf hello")

    def test_provider_payload_and_parsing(self):
        provider = LocalProvider()
        with patch.object(provider, "_post", side_effect=[{}, {"message": {
            "content": '{"command":"ls -la","explanation":"List files"}'}}]) as post:
            self.assertEqual(provider.plan("list files", self.context)[0], "ls -la")
            payload = post.call_args.args[1]
            self.assertIn(self.context.system, payload["messages"][0]["content"])
            self.assertFalse(payload["stream"])



if __name__ == "__main__":
    unittest.main()
