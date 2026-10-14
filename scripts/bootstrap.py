"""Generate local secrets once, without overwriting an existing environment."""

import os
import secrets
from pathlib import Path

root = Path(__file__).resolve().parents[1]
target = root / ".env"
contents = (root / ".env.example").read_text()
contents = contents.replace(
    "replace-me-with-a-random-secret-at-least-32-characters", secrets.token_urlsafe(48)
).replace("replace-me-with-a-random-database-password", secrets.token_urlsafe(32))
try:
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    print(".env already exists; left unchanged.")
else:
    with os.fdopen(descriptor, "w") as output:
        output.write(contents)
    print("Created .env with random local credentials. Keep it out of version control.")
