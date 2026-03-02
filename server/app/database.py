from sqlalchemy import create_engine, event
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from .config import settings
import logging

logger = logging.getLogger(__name__)

print("Database URL:", settings.database_url)  # Debug print to verify the URL

# Create database engine
engine = create_engine(settings.database_url, echo=False)  # Set echo=True for SQL logging

# Log database connection events
@event.listens_for(engine, "connect")
def receive_connect(dbapi_conn, connection_record):
    logger.info(f"[DB ENGINE] New database connection established")

@event.listens_for(engine, "commit")
def receive_commit(conn):
    logger.info(f"[DB ENGINE] COMMIT executed on connection")

@event.listens_for(engine, "rollback")
def receive_rollback(conn):
    logger.warning(f"[DB ENGINE] ROLLBACK executed on connection")

# Create SessionLocal class
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Create Base class for models
Base = declarative_base()


def get_db():
    """Dependency to get database session"""
    logger.info("[DB SESSION] Creating new database session")
    db = SessionLocal()
    logger.info(f"[DB SESSION] Session created - ID: {id(db)} (configured with autocommit=False, autoflush=False)")
    try:
        yield db
        logger.info(f"[DB SESSION] Request completed for session {id(db)}")
    except Exception as e:
        logger.error(f"[DB SESSION] Exception in session {id(db)}: {e}")
        raise
    finally:
        logger.info(f"[DB SESSION] Closing session {id(db)}")
        db.close()
        logger.info(f"[DB SESSION] Session {id(db)} closed")


def create_tables():
    """Create all tables in the database"""
    Base.metadata.create_all(bind=engine)