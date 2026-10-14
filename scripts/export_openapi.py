"""Export the contract without runtime credentials or a live database."""

import argparse
import json
from pathlib import Path

from pydantic import SecretStr

from workboard.infrastructure.config import Settings
from workboard.main import create_app

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
settings = Settings(
    jwt_secret=SecretStr("schema-export-only-not-a-runtime-secret-1234"),
    database_url="sqlite:///:memory:",
    _env_file=None,
)
app = create_app(settings)
contents = json.dumps(app.openapi(), indent=2, sort_keys=True) + "\n"
app.state.engine.dispose()
target = Path(__file__).resolve().parents[1] / "docs" / "openapi.json"
if args.check:
    if not target.exists() or target.read_text() != contents:
        raise SystemExit("OpenAPI contract differs. Run python scripts/export_openapi.py.")
    print("OpenAPI contract matches the application.")
else:
    target.parent.mkdir(exist_ok=True)
    target.write_text(contents)
    print("Exported docs/openapi.json")
