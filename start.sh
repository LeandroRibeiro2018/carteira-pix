#!/bin/bash

# Script para iniciar o Pix Wallet Service

echo "========================================="
echo "   Pix Wallet Service - Startup Script  "
echo "========================================="
echo ""

# Verificar se Docker está rodando
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker não está rodando. Por favor, inicie o Docker primeiro."
    exit 1
fi

echo "✅ Docker está rodando"
echo ""

# Iniciar PostgreSQL
echo "🐘 Iniciando PostgreSQL..."
docker-compose up -d

# Aguardar PostgreSQL estar pronto
echo "⏳ Aguardando PostgreSQL estar pronto..."
sleep 5

# Verificar se PostgreSQL está pronto
until docker-compose exec -T postgres pg_isready -U pixuser > /dev/null 2>&1; do
    echo "⏳ Aguardando PostgreSQL..."
    sleep 2
done

echo "✅ PostgreSQL está pronto"
echo ""

# Compilar projeto
echo "🔨 Compilando projeto..."
./mvnw clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Erro ao compilar o projeto"
    exit 1
fi

echo "✅ Projeto compilado com sucesso"
echo ""

# Iniciar aplicação
echo "🚀 Iniciando aplicação..."
echo ""
java -jar target/pix-wallet-service-1.0.0.jar
