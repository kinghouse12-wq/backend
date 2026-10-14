"""Tests for greeting content and input validation."""

import unittest

from greeting import greet


class GreetingTests(unittest.TestCase):
    def test_named_greeting(self):
        self.assertEqual(greet("Ada"), "Hello, Ada!")

    def test_outer_whitespace_is_removed(self):
        self.assertEqual(greet("  Ada Lovelace\t"), "Hello, Ada Lovelace!")

    def test_blank_names_are_rejected(self):
        for name in ("", " ", "\t\n"):
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError, "Name must not be blank"):
                    greet(name)


if __name__ == "__main__":
    unittest.main()
