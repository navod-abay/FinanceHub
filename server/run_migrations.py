"""
Migration runner - Apply pending migrations to the database
"""
import psycopg2
from app.config import settings
import os
from pathlib import Path

def get_db_connection():
    """Parse database URL and create connection"""
    db_url = settings.database_url
    
    # Parse PostgreSQL connection string
    # Format: postgresql://user:password@host:port/database
    if db_url.startswith('postgresql://'):
        return psycopg2.connect(db_url)
    else:
        raise ValueError(f"Unsupported database URL format: {db_url}")

def run_migration(migration_file: Path):
    """Run a single migration file"""
    print(f"Running migration: {migration_file.name}")
    
    with open(migration_file, 'r') as f:
        sql = f.read()
    
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(sql)
            conn.commit()
            print(f"✓ Migration {migration_file.name} completed successfully")
    except Exception as e:
        conn.rollback()
        print(f"✗ Migration {migration_file.name} failed: {e}")
        raise
    finally:
        conn.close()

def main():
    """Run all migrations"""
    migrations_dir = Path(__file__).parent / "migrations"
    
    # Run the PostgreSQL-specific migration
    migration_file = migrations_dir / "001_add_id_columns_postgresql.sql"
    
    if migration_file.exists():
        run_migration(migration_file)
    else:
        print(f"Migration file not found: {migration_file}")

if __name__ == "__main__":
    main()
