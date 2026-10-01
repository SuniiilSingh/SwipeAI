#!/bin/bash
# ==============================================================================
# SwipeAI — Production Automated Deployment Script for Hetzner
# Usage: ./deploy.sh
# ==============================================================================

set -e

echo "🚀 [1/5] Pulling latest changes from Git..."
git pull origin main

# Check if .env.prod exists
if [ ! -f .env.prod ]; then
    echo "❌ ERROR: .env.prod not found! Copy .env.prod.example to .env.prod and configure your secrets."
    exit 1
fi

echo "📦 [2/5] Building updated backend image..."
docker compose -f docker-compose.prod.yml build backend

echo "🔄 [3/5] Starting services with zero-downtime reload..."
docker compose -f docker-compose.prod.yml up -d --remove-orphans

echo "🧹 [4/5] Cleaning up old unused Docker images..."
docker image prune -f

echo "🏥 [5/5] Checking container status..."
docker compose -f docker-compose.prod.yml ps

echo "==================================================================="
echo "✅ SwipeAI backend successfully deployed and running on Hetzner!"
echo "==================================================================="
