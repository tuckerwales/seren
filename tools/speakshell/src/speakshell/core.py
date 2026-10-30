from __future__ import annotations

import os
import platform
import secrets
import signal
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Protocol


@dataclass(frozen=True)
class Context:
    cwd: str
    system: str
    shell: str

    @classmethod
    def local(cls, cwd: str | None = None) -> Context:
        directory = Path(cwd or os.getcwd()).expanduser().resolve(strict=True)
        if not directory.is_dir():
            raise ValueError("Working directory must be a directory")
        # A predictable POSIX shell, independent of the user's interactive setup.
        return cls(str(directory), platform.system(), "/bin/sh")


@dataclass(frozen=True)
class Proposal:
    id: str
    request: str
    command: str
    explanation: str
    context: Context

    def to_dict(self) -> dict:
        return asdict(self)


class Planner(Protocol):
    def plan(self, request: str, context: Context) -> tuple[str, str]: ...


class Transcriber(Protocol):
    def transcribe(self, path: Path) -> str: ...


class Session:
    """Transport-independent proposals. Approval refers to the stored command only."""

    def __init__(self, planner: Planner, context: Context, timeout: int = 60):
        self.planner = planner
        self.context = context
        self.timeout = timeout
        self.pending: dict[str, Proposal] = {}

    def propose(self, request: str) -> Proposal:
        if not isinstance(request, str) or not request.strip() or len(request) > 16000:
            raise ValueError("Request must contain 1–16000 characters")
        command, explanation = self.planner.plan(request, self.context)
        if not isinstance(command, str) or not command.strip() or '\x00' in command or len(command) > 32000:
            raise ValueError("Planner returned an invalid command")
        if not isinstance(explanation, str):
            raise ValueError("Planner returned an invalid explanation")
        proposal = Proposal(secrets.token_urlsafe(24), request, command, explanation, self.context)
        # Only the latest proposal can be approved.
        self.pending = {proposal.id: proposal}
        return proposal

    def execute(self, proposal_id: str, approved: bool, output=None) -> dict:
        if approved is not True:
            raise ValueError("Explicit approval is required")
        proposal = self.pending.pop(proposal_id, None)
        if proposal is None:
            raise ValueError("Unknown, superseded, or already executed proposal")
        with subprocess.Popen(
            [proposal.context.shell, "-c", proposal.command],
            cwd=proposal.context.cwd, stdin=subprocess.DEVNULL,
            stdout=output, stderr=subprocess.STDOUT if output is not None else None,
            # Stream to the terminal; RPC captures output separately at the CLI layer.
            start_new_session=True,
        ) as process:
            try:
                return {"exit_code": process.wait(timeout=self.timeout), "timed_out": False}
            except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                if isinstance(error, KeyboardInterrupt):
                    raise
                return {"exit_code": 124, "timed_out": True}
