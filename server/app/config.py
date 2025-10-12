from pydantic_settings import BaseSettings

from typing import Optional


class Settings(BaseSettings):
    database_url: str = "postgresql://financehub_user:Towel;2340@localhost:5432/financehub"
    host: str = "localhost"
    port: int = 8000
    debug: bool = True
    
    class Config:
        env_file = ".env"


settings = Settings()