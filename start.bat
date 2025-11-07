@echo off
REM Script para iniciar o Pix Wallet Service no Windows

echo =========================================
echo    Pix Wallet Service - Startup Script
echo =========================================
echo.

REM Verificar se Docker está rodando
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo X Docker não está rodando. Por favor, inicie o Docker primeiro.
    exit /b 1
)

echo + Docker está rodando
echo.

REM Iniciar PostgreSQL
echo Iniciando PostgreSQL...
docker-compose up -d

REM Aguardar PostgreSQL estar pronto
echo Aguardando PostgreSQL estar pronto...
timeout /t 5 /nobreak >nul

echo + PostgreSQL está pronto
echo.

REM Compilar projeto
echo Compilando projeto...
call mvnw.cmd clean package -DskipTests

if %errorlevel% neq 0 (
    echo X Erro ao compilar o projeto
    exit /b 1
)

echo + Projeto compilado com sucesso
echo.

REM Iniciar aplicação
echo Iniciando aplicação...
echo.
java -jar target\pix-wallet-service-1.0.0.jar
