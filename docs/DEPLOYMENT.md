# Deployment Guide

## Prerequisites

### 1. Proxmox VM Setup

On your Proxmox VM:
```bash
# Install Docker and Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Create application directory
mkdir -p ~/financehub-server
cd ~/financehub-server

# Create data directories
mkdir -p data db_init migrations
```

### 2. Tailscale Setup

On your Proxmox VM:
```bash
# Install Tailscale
curl -fsSL https://tailscale.com/install.sh | sh

# Connect to your Tailnet
sudo tailscale up

# Note your Tailscale IP
tailscale ip -4
```

### 3. GitHub Secrets Configuration

Add these secrets to your GitHub repository (Settings > Secrets and variables > Actions):

**Required Secrets:**
- `TAILSCALE_OAUTH_CLIENT_ID` - OAuth client ID from Tailscale admin console
- `TAILSCALE_OAUTH_SECRET` - OAuth secret from Tailscale admin console
- `VM_TAILSCALE_IP` - Your VM's Tailscale IP (e.g., 100.x.x.x)
- `VM_SSH_USER` - SSH username for VM (e.g., your username)
- `VM_SSH_KEY` - Private SSH key for VM access

**To generate Tailscale OAuth credentials:**
1. Go to https://login.tailscale.com/admin/settings/oauth
2. Create a new OAuth client
3. Add tag `tag:ci` to the OAuth client
4. Copy the Client ID and Secret to GitHub Secrets

**To generate SSH key for deployment:**
```bash
# On your local machine
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/financehub_deploy

# Copy public key to VM
ssh-copy-id -i ~/.ssh/financehub_deploy.pub user@<vm-ip>

# Copy private key content to GitHub Secret VM_SSH_KEY
cat ~/.ssh/financehub_deploy
```

### 4. VM Configuration Files

Copy these files to your VM at `~/financehub-server/`:

**docker-compose.prod.yml** - Copy from `server/docker-compose.prod.yml` in the repository
```bash
# On your local machine
scp server/docker-compose.prod.yml <user>@<tailscale-ip>:~/financehub-server/
```

**`.env`** - Create from `.env.example`:
```bash
cd ~/financehub-server
cat > .env << 'EOF'
# Database Configuration
DATABASE_URL=postgresql://financehub_user:your-password-here@db:5432/financehub

# PostgreSQL Configuration
POSTGRES_USER=financehub_user
POSTGRES_PASSWORD=your-password-here
POSTGRES_DB=financehub

# Server Configuration
HOST=0.0.0.0
PORT=8000
DEBUG=false
EOF
```

**Generate secure password:**
```bash
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

### 5. Initial Setup

Pull the initial image:
```bash
# On VM
cd ~/financehub-server
docker pull ghcr.io/<your-github-username>/financehub-server:latest
docker tag ghcr.io/<your-github-username>/financehub-server:latest financehub-server:latest
docker-compose -f docker-compose.prod.yml up -d
```

## Deployment Process

### Automatic Deployment

The deployment happens automatically when you push to the `main` branch:
```bash
git push origin main
```

Changes in the `server/` directory trigger the deployment workflow.

### Manual Deployment

Trigger deployment manually from GitHub:
1. Go to Actions tab
2. Select "Deploy Server to Proxmox VM"
3. Click "Run workflow"
4. Select branch and click "Run workflow"

### Deployment Steps

The workflow automatically:
1. Builds Docker image from `server/Dockerfile`
2. Pushes to GitHub Container Registry
3. Connects to VM via Tailscale
4. Pulls new image to VM
5. Stops old container
6. Starts new container with updated image
7. Runs health check
8. Cleans up old images

## Monitoring

### View Container Logs
```bash
ssh <user>@<tailscale-ip>
docker logs -f financehub-server
```

### Check Container Status
```bash
docker ps
docker-compose -f docker-compose.prod.yml ps
```

### Health Check
```bash
curl http://<tailscale-ip>:8000/health
```

### View Deployment History
Check the Actions tab in GitHub for deployment logs and status.

## Rollback

If deployment fails, rollback to previous version:
```bash
# On VM
cd ~/financehub-server

# Find previous image
docker images financehub-server

# Update docker-compose to use specific tag
docker tag financehub-server:<previous-sha> financehub-server:latest
docker-compose -f docker-compose.prod.yml up -d
```

Or manually pull a specific commit:
```bash
docker pull ghcr.io/<your-username>/financehub-server:main-<commit-sha>
docker tag ghcr.io/<your-username>/financehub-server:main-<commit-sha> financehub-server:latest
docker-compose -f docker-compose.prod.yml up -d
```

## Database Migrations

Run migrations manually:
```bash
# Copy migration files to VM
scp server/migrations/*.sql <user>@<tailscale-ip>:~/financehub-server/migrations/

# Run migrations (if using SQLite)
ssh <user>@<tailscale-ip>
docker exec -it financehub-server sqlite3 /app/data/financehub.db < migrations/001_migration.sql

# Or run via Python/FastAPI migration script in container
docker exec -it financehub-server python -m app.migrate
```

## Troubleshooting

### Deployment fails on health check
```bash
# Check logs
docker logs financehub-server

# Check if port is accessible
curl http://localhost:8000/health

# Restart container
docker-compose -f docker-compose.prod.yml restart
```

### Cannot connect via Tailscale
```bash
# On GitHub Actions runner, check Tailscale status in logs
# On VM, verify Tailscale is running
sudo tailscale status

# Verify tags on OAuth client allow 'tag:ci'
```

### Image pull fails
```bash
# Authenticate Docker with GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Make package public or add VM's GitHub token
```

### SSH connection fails
- Verify SSH key is correctly added to VM's `~/.ssh/authorized_keys`
- Check VM_SSH_USER and VM_TAILSCALE_IP in GitHub Secrets
- Test SSH manually: `ssh -i <key> user@<tailscale-ip>`

## Security Notes

- Never commit `.env` file with real credentials
- Keep SSH private keys secure in GitHub Secrets
- Use Tailscale OAuth with appropriate tags
- Regularly rotate secrets and SSH keys
- Consider enabling GitHub deployment protection rules
