"""A dependency-free greeting CLI and reusable greeting function."""

import argparse


def greet(name: str) -> str:
    """Return a greeting for a nonblank name, removing outer whitespace."""
    cleaned_name = name.strip()
    if not cleaned_name:
        raise ValueError("Name must not be blank.")
    return f"Hello, {cleaned_name}!"


def main() -> None:
    parser = argparse.ArgumentParser(description="Print a friendly greeting.")
    parser.add_argument("--name", default="World", help="Name to greet (default: World)")
    args = parser.parse_args()
    try:
        message = greet(args.name)
    except ValueError as error:
        parser.error(str(error))
    print(message)


if __name__ == "__main__":
    main()
