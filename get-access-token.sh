#!/usr/bin/env bash

MY_ACCESS_TOKEN_FULL=$(
  docker exec -t -e CLIENT_SECRET=$1 -e KEYCLOAK_HOST=${2:-localhost} my-keycloak bash -c '
    curl -s -X POST \
    http://$KEYCLOAK_HOST:8080/auth/realms/company-services/protocol/openid-connect/token \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=ivan.franchin" \
    -d "password=123" \
    -d "grant_type=password" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "client_id=book-service"
  ')

MY_ACCESS_TOKEN=$(echo $MY_ACCESS_TOKEN_FULL | jq -r .access_token)
echo "Bearer $MY_ACCESS_TOKEN"