"""Eval pipeline of the chat. Run from the chat folder: python -m evals.runner ... (see evals/README.md)."""
import sys
from pathlib import Path

# the production code (package "app") lives next to this folder; make it importable whatever the working directory is
_chat = str(Path(__file__).resolve().parent.parent)
if _chat not in sys.path:
    sys.path.insert(0, _chat)
