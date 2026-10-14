# Python Greeting Starter

A small Python command-line project with a reusable greeting function and tests.
It uses only the Python standard library; no dependency installation is needed.

## Run

With Python 3 installed, run these commands from this project folder:

```sh
python3 greeting.py
# Hello, World!

python3 greeting.py --name "Ada Lovelace"
# Hello, Ada Lovelace!

python3 greeting.py --help
```

Names are trimmed of outer whitespace. A blank name reports an argument error
and exits with status 2.

## Use from Python

```python
from greeting import greet

print(greet("Ada"))  # Hello, Ada!
```

`greet` raises `ValueError` when the name is empty or contains only whitespace.

## Test

```sh
python3 -m unittest discover -s tests -v
```

The tests cover named greetings, whitespace trimming, and blank-name validation.

## Files

- `greeting.py`: CLI and importable `greet` function.
- `tests/test_greeting.py`: standard-library unit tests.
- `.gitignore`: Python environments and generated cache files.
