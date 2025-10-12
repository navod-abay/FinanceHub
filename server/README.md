# FinanceHub API Server

A FastAPI-based server for the FinanceHub personal finance tracking application with sync capabilities.

## Features

- 🔄 **Functionality-oriented APIs** for complex operations (add-expense, update-expense, etc.)
- 📱 **Mobile sync support** with conflict resolution
- 📊 **Tag-based expense tracking** with recommendation engine
- 🎯 **Target setting and monitoring**
- 🔍 **Advanced querying and filtering**
- 🐳 **Docker deployment ready**

## Quick Start

### Prerequisites

- Python 3.11+
- PostgreSQL 13+
- Docker & Docker Compose (optional)

### Local Development

1. **Clone and navigate to server directory:**
   ```bash
   cd server
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

3. **Set up environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env with your database credentials
   ```

4. **Start the development server:**
   ```bash
   python dev_start.py
   ```

   The API will be available at:
   - API: http://localhost:8000
   - Documentation: http://localhost:8000/docs
   - Health check: http://localhost:8000/health

### Docker Deployment

1. **Build and start services:**
   ```bash
   docker-compose up --build
   ```

2. **The API will be available at http://localhost:8000**

## API Endpoints

### Core Operations
- `POST /api/v1/operations/add-expense` - Add expense with tags atomically
- `PUT /api/v1/operations/update-expense/{id}` - Update expense and tags
- `DELETE /api/v1/operations/delete-expense/{id}` - Delete expense
- `POST /api/v1/operations/add-target` - Add spending target

### Synchronization
- `GET /api/v1/sync/delta?since={timestamp}` - Get changes since timestamp
- `POST /api/v1/sync/push` - Push local changes to server
- `POST /api/v1/sync/full` - Full sync for initial setup

### Queries
- `GET /api/v1/expenses` - Get expenses with pagination
- `GET /api/v1/tags` - Get tags with search
- `GET /api/v1/targets` - Get targets with filtering
- `POST /api/v1/recommendations` - Get tag recommendations
- `GET /api/v1/stats/summary` - Get dashboard statistics

## Database Schema

The server uses PostgreSQL with the following main entities:

- **Expenses**: Core expense data with sync metadata
- **Tags**: Category system with usage tracking
- **Targets**: Monthly spending goals
- **ExpenseTagsCrossRef**: Many-to-many expense-tag relationships
- **GraphEdges**: Tag relationship data for recommendations

## Project Structure

```
server/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI application
│   ├── config.py            # Configuration settings
│   ├── database.py          # Database connection
│   ├── models/
│   │   ├── __init__.py      # SQLAlchemy models
│   │   └── schemas.py       # Pydantic models
│   ├── routes/
│   │   ├── operations.py    # Business operation endpoints
│   │   ├── sync.py          # Synchronization endpoints
│   │   └── query.py         # Query endpoints
│   ├── services/
│   │   ├── expense_service.py  # Expense business logic
│   │   └── graph_service.py    # Recommendation engine
│   └── utils/
│       └── date_utils.py    # Date utility functions
├── db_init/
│   └── 01_init.sql          # Database initialization
├── requirements.txt         # Python dependencies
├── Dockerfile              # Container configuration
├── docker-compose.yml      # Multi-service deployment
├── nginx.conf              # Reverse proxy configuration
└── dev_start.py            # Development startup script
```

## Configuration

Environment variables (`.env` file):

```env
DATABASE_URL=postgresql://username:password@host:port/database
HOST=0.0.0.0
PORT=8000
DEBUG=True
```

## Sync Strategy

The server implements a **functionality-oriented sync approach**:

1. **Delta Sync**: Only sync changes since last timestamp
2. **Atomic Operations**: Complex operations (like add-expense) are handled in single API calls
3. **Server Authority**: Server timestamp is authoritative for conflict resolution
4. **Soft Deletes**: Deleted items are marked with `deleted_at` timestamp

## Recommendation Engine

The server ports the Android tag recommendation algorithm:

- **Graph-based**: Uses expense-tag co-occurrence data
- **Random Walk**: Implements probabilistic recommendations
- **Real-time Updates**: Graph edges update with each expense

## Development

### Adding New Endpoints

1. Create the endpoint in the appropriate router (`routes/`)
2. Add business logic to services (`services/`)
3. Update Pydantic schemas if needed (`models/schemas.py`)
4. Add tests and documentation

### Database Migrations

The server uses SQLAlchemy with automatic table creation. For schema changes:

1. Update models in `models/__init__.py`
2. Consider backward compatibility for mobile clients
3. Test migrations with existing data

## Deployment to Proxmox

1. **Transfer files to Proxmox host:**
   ```bash
   scp -r server/ user@proxmox-host:/path/to/financehub/
   ```

2. **Run with Docker Compose:**
   ```bash
   docker-compose up -d
   ```

3. **Check logs:**
   ```bash
   docker-compose logs -f api
   ```

## Troubleshooting

### Common Issues

- **Database connection errors**: Check PostgreSQL is running and credentials are correct
- **Import errors**: Ensure all dependencies are installed (`pip install -r requirements.txt`)
- **Port conflicts**: Change PORT in `.env` if 8000 is already in use

### Logs

- **Development**: Logs appear in console
- **Docker**: Use `docker-compose logs api`
- **Production**: Configure proper logging in `config.py`

## License

Personal use project - part of FinanceHub monorepo.