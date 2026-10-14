import ast
from pathlib import Path

import pytest

from workboard.domain.entities import TaskStatus, validate_transition
from workboard.domain.errors import InvalidTransition


@pytest.mark.parametrize("current", list(TaskStatus))
@pytest.mark.parametrize("target", list(TaskStatus))
def test_transition_matrix(current, target):
    # Independent business specification: changes to the implementation's table must fail tests.
    allowed = {
        ("todo", "in_progress"),
        ("todo", "cancelled"),
        ("in_progress", "todo"),
        ("in_progress", "done"),
        ("in_progress", "cancelled"),
        ("done", "in_progress"),
        ("cancelled", "todo"),
    }
    if current == target or (current.value, target.value) in allowed:
        validate_transition(current, target)
    else:
        with pytest.raises(InvalidTransition):
            validate_transition(current, target)


def test_inner_layers_do_not_import_outer_layers():
    root = Path(__file__).parents[1] / "src" / "workboard"
    forbidden = {"fastapi", "starlette", "sqlalchemy", "pydantic", "jwt", "pwdlib"}
    for layer in ("domain", "application"):
        for path in (root / layer).glob("*.py"):
            for node in ast.walk(ast.parse(path.read_text())):
                imports = []
                if isinstance(node, ast.Import):
                    imports = [alias.name for alias in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module:
                    imports = [node.module]
                for module in imports:
                    assert module.split(".")[0] not in forbidden, (path, module)
                    assert not module.startswith(("workboard.api", "workboard.infrastructure"))
                    if layer == "domain":
                        assert not module.startswith("workboard.application")
