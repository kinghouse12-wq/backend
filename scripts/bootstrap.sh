#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then
  printf '%s\n' '.env already exists; left unchanged.'
  exit 0
fi
umask 077
signing=$(openssl rand -hex 32)
database=$(openssl rand -hex 24)
# Noclobber also protects an environment created concurrently by another process.
(set -C; sed -e "s/replace-me-with-a-random-secret/$signing/" \
  -e "s/replace-me-with-a-random-password/$database/" .env.example > .env)
printf '%s\n' 'Created .env with random local credentials; it is ignored by Git.'
