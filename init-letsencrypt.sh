#!/bin/bash
# ==============================================================================
# SwipeAI — Let's Encrypt SSL Bootstrap Script for Hetzner
# Run this ONCE when setting up a fresh server:
# ./init-letsencrypt.sh
# ==============================================================================

set -e

# Configuration
DOMAINS=("api.blunderr.in") # Change to your domain
EMAIL="admin@blunderr.in"   # Change to your email
STAGING=0                   # Set to 1 if you are testing to avoid Let's Encrypt rate limits

DATA_PATH="./certbot"
RSA_KEY_SIZE=4096

if [ -d "$DATA_PATH/conf/live/${DOMAINS[0]}" ]; then
  read -p "Existing certificate found for ${DOMAINS[0]}. Continue and overwrite? (y/N) " decision
  if [ "$decision" != "Y" ] && [ "$decision" != "y" ]; then
    exit
  fi
fi

echo "### Creating directory structure..."
mkdir -p "$DATA_PATH/conf/live/${DOMAINS[0]}"
mkdir -p "$DATA_PATH/www"

echo "### Creating temporary dummy certificate for ${DOMAINS[0]}..."
path="/etc/letsencrypt/live/${DOMAINS[0]}"
docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1\
    -keyout '$path/privkey.pem' \
    -out '$path/fullchain.pem' \
    -subj '/CN=localhost'" certbot

echo "### Starting Nginx..."
docker compose -f docker-compose.prod.yml up --force-recreate -d nginx

echo "### Deleting dummy certificate..."
docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  rm -Rf /etc/letsencrypt/live/${DOMAINS[0]} && \
  rm -Rf /etc/letsencrypt/archive/${DOMAINS[0]} && \
  rm -Rf /etc/letsencrypt/renewal/${DOMAINS[0]}.conf" certbot

echo "### Requesting real Let's Encrypt SSL Certificate..."
domain_args=""
for domain in "${DOMAINS[@]}"; do
  domain_args="$domain_args -d $domain"
done

# Select staging or production
if [ $STAGING != "0" ]; then staging_arg="--staging"; fi

docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $staging_arg \
    $domain_args \
    --email $EMAIL \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --force-renewal \
    --non-interactive" certbot

echo "### Reloading Nginx with real SSL certificate..."
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload

echo "==================================================================="
echo "✅ SSL Certificate successfully installed for ${DOMAINS[0]}!"
echo "==================================================================="
