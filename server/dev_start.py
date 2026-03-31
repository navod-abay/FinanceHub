#!/usr/bin/env python3
"""
Development startup script for FinanceHub API
"""

import os
import sys
import subprocess
from pathlib import Path

def main():
    # Get the directory where this script is located
    script_dir = Path(__file__).parent
    
    # Set environment variables for development
    os.environ.setdefault("DATABASE_URL", "postgresql://financehub_user:Towel;2340@127.0.0.1:5432/financehub_db")
    os.environ.setdefault("DEBUG", "True")
    os.environ.setdefault("HOST", "0.0.0.0")
    os.environ.setdefault("PORT", "8000")
    
    print("🚀 Starting FinanceHub API Server...")
    print(f"📁 Working directory: {script_dir}")
    print(f"🐍 Python executable: {sys.executable}")
    print(f"🗄️  Database URL: {os.environ.get('DATABASE_URL')}")
    print(f"🔧 Debug mode: {os.environ.get('DEBUG')}")
    print(f"🌐 Host: {os.environ.get('HOST')}:{os.environ.get('PORT')}")
    print()
    
    # Check if requirements are installed
    try:
        import fastapi
        import sqlalchemy
        import psycopg2
        print("✅ All required packages are installed")
    except ImportError as e:
        print(f"❌ Missing required package: {e.name}")
        print("Please install requirements: pip install -r requirements.txt")
        return 1
    
    # Change to script directory
    os.chdir(script_dir)
    
    # Start the server
    try:
        cmd = [
            sys.executable, "-m", "uvicorn",
            "app.main:app",
            "--host", os.environ.get("HOST", "0.0.0.0"),
            "--port", os.environ.get("PORT", "8000"),
            "--reload"
        ]
        
        print(f"🎯 Running command: {' '.join(cmd)}")
        print("📡 API will be available at: http://localhost:8000")
        print("📚 API docs will be available at: http://localhost:8000/docs")
        print("🔄 Press Ctrl+C to stop the server")
        print()
        
        subprocess.run(cmd)
        
    except KeyboardInterrupt:
        print("\n👋 Server stopped")
        return 0
    except Exception as e:
        print(f"❌ Error starting server: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(main())