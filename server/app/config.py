from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    database_url: str
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = False
    
    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()