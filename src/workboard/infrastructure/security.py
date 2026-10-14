from datetime import timedelta
from uuid import UUID, uuid4

import jwt
from pwdlib import PasswordHash

from workboard.domain.entities import utc_now
from workboard.domain.errors import AuthenticationFailed
from workboard.infrastructure.config import Settings


class ArgonPasswords:
    def __init__(self) -> None:
        self.hasher = PasswordHash.recommended()
        self.dummy_hash = self.hasher.hash(str(uuid4()))

    def hash(self, password: str) -> str:
        return self.hasher.hash(password)

    def verify(self, password: str, password_hash: str | None) -> bool:
        valid = self.hasher.verify(password, password_hash or self.dummy_hash)
        return valid and password_hash is not None


class JwtTokens:
    def __init__(self, settings: Settings):
        self.settings = settings

    def issue(self, user_id: UUID) -> str:
        now = utc_now()
        return jwt.encode(
            {
                "sub": str(user_id),
                "iat": now,
                "exp": now + timedelta(minutes=self.settings.token_minutes),
                "iss": self.settings.jwt_issuer,
                "aud": self.settings.jwt_audience,
            },
            self.settings.jwt_secret.get_secret_value(),
            algorithm="HS256",
        )

    def subject(self, token: str) -> UUID:
        try:
            payload = jwt.decode(
                token,
                self.settings.jwt_secret.get_secret_value(),
                algorithms=["HS256"],
                issuer=self.settings.jwt_issuer,
                audience=self.settings.jwt_audience,
                options={"require": ["sub", "exp", "iat", "iss", "aud"]},
            )
            return UUID(payload["sub"])
        except (jwt.InvalidTokenError, ValueError, TypeError) as error:
            raise AuthenticationFailed("Invalid or expired access token.") from error
