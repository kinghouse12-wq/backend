from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./workboard.db"
    jwt_secret: SecretStr
    token_minutes: int = Field(default=15, ge=1, le=60)
    jwt_issuer: str = "workboard-api"
    jwt_audience: str = "workboard-client"
    cors_origins: list[str] = []
    allowed_hosts: list[str] = ["localhost", "127.0.0.1", "testserver"]

    @field_validator("jwt_secret")
    @classmethod
    def strong_secret(cls, value: SecretStr) -> SecretStr:
        secret = value.get_secret_value()
        if len(secret) < 32 or "replace-me" in secret:
            raise ValueError("JWT_SECRET must be a random secret of at least 32 characters.")
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()
