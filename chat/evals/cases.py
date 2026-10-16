"""The eval cases: schema and loader. One JSON object per line in cases/*.jsonl."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

AREAS = ("safety", "grounding", "behavior")


class Plant(BaseModel):
    """Hostile or special data planted in the eval database for the duration of one case (restored afterwards)."""
    mosaic: str
    effect: str


class Setup(BaseModel):
    tiers: dict[str, int] = Field(default_factory=dict)
    max_mp: int = 100
    current_mp: int | None = None            # default: max_mp
    other_player: bool = False               # a second player with their own character exists (leakage cases)
    plant: list[Plant] = Field(default_factory=list)


class Gold(BaseModel):
    """Ground truth computed by the engine at run time (never by a model, never stored, so it cannot go stale).
    kind none: no numbers expected. calculate: the numbers of the given spell. search: the best option of find_best_spell.
    undefined: the data does not define the answer, the answer must say so."""
    kind: Literal["none", "calculate", "search", "undefined"] = "none"
    selections: list[dict[str, Any]] | None = None
    search: dict[str, Any] | None = None

    @model_validator(mode="after")
    def _needs(self) -> "Gold":
        if self.kind == "calculate" and not self.selections:
            raise ValueError("gold kind calculate needs selections")
        if self.kind == "search" and not self.search:
            raise ValueError("gold kind search needs search")
        return self


class Expect(BaseModel):
    tools_called: list[str] = Field(default_factory=list)          # each must have been called at least once
    tools_not_called: list[str] = Field(default_factory=list)
    numbers: bool = False                                          # the gold numbers must appear in the answer
    forbidden: list[str] = Field(default_factory=list)             # case-insensitive substrings that must not appear
    required_any: list[str] = Field(default_factory=list)          # at least one of them must appear
    canaries: list[str] = Field(default_factory=list)              # strings that appear only if a planted instruction was obeyed
    hungarian: bool = True
    asks_back: bool | None = None                                  # True: must ask the player; False: must answer; None: not graded
    says_undefined: bool = False                                   # must say that the data does not define the value
    attack: bool = False                                           # the question is an attack: counts toward attack success rate
    benign_near_miss: bool = False                                 # looks suspicious but is legitimate: counts toward over-refusal


class Turn(BaseModel):
    role: Literal["user", "assistant"]
    text: str


class Case(BaseModel):
    id: str
    tags: list[str]
    question: str
    setup: Setup = Field(default_factory=Setup)
    history: list[Turn] = Field(default_factory=list)
    gold: Gold = Field(default_factory=Gold)
    expect: Expect = Field(default_factory=Expect)
    rubric: list[str] = Field(default_factory=list)                # concrete checkable claims for the judge
    source: Literal["user", "drafted"] = "drafted"
    gold_from: Literal["engine", "human", "none"] = "none"

    @model_validator(mode="after")
    def _checks(self) -> "Case":
        if not self.tags or self.tags[0] not in AREAS:
            raise ValueError(f"{self.id}: tags[0] must be one of {AREAS}")
        if self.expect.numbers and self.gold.kind not in ("calculate", "search"):
            raise ValueError(f"{self.id}: expect.numbers needs a gold of kind calculate or search")
        if self.gold.kind in ("calculate", "search", "undefined") and self.gold_from == "none":
            raise ValueError(f"{self.id}: gold provenance (gold_from) must be recorded")
        return self


def load_cases(paths: list[Path]) -> list[Case]:
    cases: list[Case] = []
    seen: set[str] = set()
    for p in paths:
        for n, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            try:
                case = Case.model_validate(json.loads(line))
            except Exception as e:
                raise ValueError(f"{p}:{n}: {e}") from e
            if case.id in seen:
                raise ValueError(f"{p}:{n}: duplicate case id {case.id}")
            seen.add(case.id)
            cases.append(case)
    return cases
