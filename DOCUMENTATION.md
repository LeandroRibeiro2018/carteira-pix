# Pix Wallet Service - Documentação Completa

## 📋 Índice
1. [Visão Geral](#visão-geral)
2. [Requisitos Atendidos](#requisitos-atendidos)
3. [Arquitetura](#arquitetura)
4. [Decisões de Design](#decisões-de-design)
5. [Stack Tecnológica](#stack-tecnológica)
6. [Estrutura do Projeto](#estrutura-do-projeto)
7. [Garantias de Consistência](#garantias-de-consistência)
8. [Diagramas de Arquitetura](#diagramas-de-arquitetura)
9. [Como Executar](#como-executar)
10. [Guia de Deploy](#guia-de-deploy)
11. [Testes Manuais](#testes-manuais)
12. [Exemplos de API](#exemplos-de-api)
13. [Análise de Problemas e Erros](#análise-de-problemas-e-erros)

> 💡 **Nota:** Para perguntas frequentes e respostas detalhadas, consulte [FAQ.md](FAQ.md)

---

## 🎯 Visão Geral

Microserviço de carteira digital com suporte a transferências Pix, desenvolvido como code assessment focado em **produção real** com garantias de consistência, concorrência e idempotência.

### Objetivo

Criar um sistema de carteira que garanta:
- ✅ **Exactly-once semantics** em transferências
- ✅ **Consistência** sob alta concorrência
- ✅ **Idempotência** em todas as operações críticas
- ✅ **Auditabilidade** completa através de ledger imutável
- ✅ **Observabilidade** com logs estruturados e métricas

---

## ✅ Requisitos Atendidos

### Requisitos Funcionais
- ✅ Criar Conta/Carteira
- ✅ Registrar Chave Pix (EMAIL, PHONE, CPF, EVP)
- ✅ Consultar Saldo Atual
- ✅ Saldo Histórico (timestamp passado)
- ✅ Depósito
- ✅ Saque
- ✅ Transferência Pix com endToEndId
- ✅ Webhook Pix (CONFIRMED/REJECTED)

### Requisitos Não-Funcionais
- ✅ **Exactly-once semantics**: Idempotency-Key + constraints únicas
- ✅ **Rastreabilidade/Auditoria**: Ledger imutável de todas operações
- ✅ **Concorrência**: Pessimistic locking + optimistic locking
- ✅ **Idempotência**: Implementada em todos endpoints críticos
- ✅ **Observabilidade**: Logs estruturados + métricas Prometheus

### Cenários de Concorrência Cobertos
- ✅ Duplo disparo: Mesmo Idempotency-Key → um único débito
- ✅ Webhook duplicado: Mesmo eventId → aplicar uma vez
- ✅ Ordem trocada: State machine garante consistência
- ✅ Reprocesso: At-least-once sem mudar saldo final

---

## 🏗️ Arquitetura

### Clean Architecture (4 Camadas)

O projeto segue **Clean Architecture** com separação clara de responsabilidades:

```
Domain (Entities + Business Rules)
   ↓
Application (Use Cases)
   ↓
Infrastructure (Repositories)
   ↓
Adapter (Controllers + DTOs)
```

### Motivação

Clean Architecture foi escolhida para garantir:
- **Independência de frameworks**: Lógica de negócio não depende de Spring
- **Testabilidade**: Casos de uso podem ser testados sem infraestrutura
- **Separação de responsabilidades**: Cada camada tem papel bem definido
- **Facilidade de manutenção**: Mudanças isoladas em camadas específicas

### Por que não Arquitetura Tradicional (3 camadas)?
- **Acoplamento**: Service diretamente depende de infraestrutura
- **Testabilidade**: Difícil testar sem banco de dados
- **Evolução**: Mudanças em infraestrutura afetam lógica de negócio

---

## 💡 Decisões de Design

### 1. Pessimistic Locking para Operações Críticas

**Onde**: Operações que modificam saldo (depósito, saque, transferência)

**Implementação**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdWithLock(@Param("id") UUID id);
```

**Vantagens**:
- ✅ Garante consistência absoluta
- ✅ Simples de entender e debugar
- ✅ Sem risco de lost updates

**Desvantagens**:
- ❌ Reduz throughput sob alta concorrência
- ❌ Pode causar deadlocks (mitigado por ordem de locks)

**Alternativas Consideradas**:
- Optimistic locking apenas: Requer retry logic complexa
- Distributed locks (Redis): Adiciona complexidade e ponto de falha

### 2. Optimistic Locking em Entidades Críticas

**Onde**: Entidades `Wallet` e `PixTransfer`

**Implementação**:
```java
@Version
private Long version;
```

**Uso**: Como segunda camada de defesa junto com pessimistic lock

**Vantagens**:
- ✅ Detecta conflitos que escapem do lock pessimista
- ✅ Permite retry automático do Spring

### 3. Idempotência por Idempotency-Key

**Onde**: Todas operações críticas (transferências, depósitos, webhooks)

**Implementação**:
```sql
CREATE UNIQUE INDEX idx_idempotency_key ON pix_transfers(idempotency_key);
CREATE UNIQUE INDEX idx_event_id ON webhook_events(event_id);
```

**Fluxo**:
1. Cliente envia `Idempotency-Key` no header
2. Backend verifica se chave já existe
3. Se existe: retorna resultado anterior
4. Se não existe: processa e salva com a chave

**Vantagens**:
- ✅ Exactly-once semantics garantido
- ✅ Protege contra duplo clique, retries, network issues
- ✅ Implementação robusta via constraint de banco

### 4. Ledger Imutável (Event Sourcing Light)

**Design**: Tabela `transactions` é **append-only** (nunca atualiza/deleta registros)

**Campos principais**:
- `wallet_id`: Carteira afetada
- `type`: DEPOSIT, WITHDRAWAL, PIX_IN, PIX_OUT
- `amount`: Valor (positivo ou negativo)
- `balance_after`: Saldo após operação
- `created_at`: Timestamp da transação

**Vantagens**:
- ✅ **Auditoria completa**: Toda operação registrada permanentemente
- ✅ **Saldo histórico**: Reconstruir saldo em qualquer timestamp
- ✅ **Compliance**: Atende requisitos regulatórios (BACEN)
- ✅ **Detecção de fraudes**: Discrepâncias são detectáveis

**Como calcular saldo histórico**:
```sql
SELECT SUM(amount) 
FROM transactions 
WHERE wallet_id = ? AND created_at <= ?
```

### 5. State Machine para Transferências Pix

**Estados**: `PENDING` → `CONFIRMED` ou `REJECTED`

**Transições Válidas**:
```
PENDING → CONFIRMED ✅
PENDING → REJECTED  ✅
CONFIRMED → REJECTED ❌ (IllegalStateException)
REJECTED → CONFIRMED ❌ (IllegalStateException)
```

**Implementação**:
```java
public void confirm() {
    if (status == PixTransferStatus.CONFIRMED) {
        return; // Idempotente
    }
    if (status == PixTransferStatus.REJECTED) {
        throw new IllegalStateException("Cannot confirm a rejected transfer");
    }
    this.status = PixTransferStatus.CONFIRMED;
}
```

**Vantagens**:
- ✅ Impede estados inválidos
- ✅ Garante consistência independente da ordem de webhooks
- ✅ Operações idempotentes

### 6. Deduplicação de Webhooks

**Problema**: Webhooks podem chegar duplicados ou fora de ordem

**Solução**:
- `eventId` único por webhook garante processamento único
- Lock pessimista previne race conditions ao processar webhook
- Webhooks duplicados ou fora de ordem são tratados corretamente

**Implementação**:
```java
if (webhookEventRepository.existsByEventId(eventId)) {
    return; // Idempotente
}
```

---

## 🛠️ Stack Tecnológica

### Backend
- **Java 17**: LTS, performance moderna
- **Spring Boot 3.2.0**: Framework consolidado
- **Spring Data JPA**: Abstração de persistência
- **Hibernate**: ORM robusto

### Database
- **PostgreSQL 15**: ACID completo, locks robustos
- **HikariCP**: Connection pooling eficiente

### Observability
- **Logback**: Logging estruturado
- **Logstash Encoder**: Formato JSON para logs
- **Micrometer + Prometheus**: Métricas e monitoring

### Testing
- **JUnit 5**: Framework de testes moderno
- **Mockito**: Mocking de dependências
- **Awaitility**: Testes de concorrência
- **H2**: Banco em memória para testes

---

## 📁 Estrutura do Projeto

```
pix-wallet-service/
├── src/
│   ├── main/
│   │   ├── java/com/pixservice/
│   │   │   ├── domain/              # Entidades e regras de negócio
│   │   │   │   ├── entity/          # Wallet, PixKey, Transaction, PixTransfer, etc.
│   │   │   │   └── enums/           # Status, tipos, etc.
│   │   │   ├── application/         # Casos de uso (Application layer)
│   │   │   │   ├── usecase/         # CreateWallet, PixTransfer, ProcessWebhook, etc.
│   │   │   │   └── exception/       # Exceções de negócio
│   │   │   ├── infrastructure/      # Camada de infraestrutura
│   │   │   │   └── repository/      # Repositórios JPA
│   │   │   └── adapter/             # Adapters (Controllers, DTOs)
│   │   │       └── rest/
│   │   │           ├── controller/  # WalletController, PixController
│   │   │           ├── dto/         # Request/Response DTOs
│   │   │           └── exception/   # GlobalExceptionHandler
│   │   └── resources/
│   │       ├── application.yml      # Configuração principal
│   │       ├── application-test.yml # Configuração de testes
│   │       ├── application-prod.yml # Configuração de produção
│   │       └── logback-spring.xml   # Configuração de logs
│   └── test/                        # Testes unitários e de integração
│       └── java/com/pixservice/
│           ├── application/usecase/ # Testes de use cases
│           ├── domain/entity/       # Testes de entidades
│           └── integration/         # Testes de integração
├── docker-compose.yml               # PostgreSQL local
├── pom.xml                          # Dependências Maven
└── README.md                        # Documentação principal
```

---

## 🔒 Garantias de Consistência

### 1. Pessimistic Locking
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Wallet> findByIdWithLock(UUID id);
```
Garante exclusão mútua ao modificar saldo.

### 2. Optimistic Locking
```java
@Version
private Long version;
```
Segunda camada de defesa contra updates concorrentes.

### 3. Idempotência por Chave Única
```sql
CREATE UNIQUE INDEX idx_idempotency_key 
ON pix_transfers(idempotency_key);
```
Constraint de banco impede duplicação.

### 4. Ledger Imutável
Tabela `transactions` append-only:
- Auditoria completa
- Saldo histórico calculável
- Compliance regulatório

### 5. State Machine
```
PENDING → CONFIRMED ✅
PENDING → REJECTED  ✅
CONFIRMED → REJECTED ❌ (IllegalStateException)
```

### 6. Transações ACID
```java
@Transactional
public PixTransfer execute(...) {
    // Todas operações em uma transação atômica
}
```

---

## 📊 Diagramas de Arquitetura

### Visão Geral da Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                          │
│  (Postman, cURL, Frontend App, External Services)           │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP/REST
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    ADAPTER LAYER (API)                       │
│  ┌────────────────┐              ┌────────────────┐         │
│  │ WalletController│              │  PixController │         │
│  │  - POST /wallets│              │  - POST /pix/  │         │
│  │  - GET /balance│              │    transfers   │         │
│  │  - POST /deposit│              │  - POST /pix/  │         │
│  └────────────────┘              │    webhook     │         │
│          │                        └────────────────┘         │
│          │                                │                  │
│          ▼                                ▼                  │
│  ┌─────────────────────────────────────────────────┐       │
│  │      GlobalExceptionHandler (Error Handling)     │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER (Use Cases)              │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │ CreateWalletUseCase│  │PixTransferUseCase│               │
│  └──────────────────┘  └──────────────────┘                │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  DepositUseCase  │  │ProcessPixWebhook │                │
│  └──────────────────┘  │     UseCase      │                │
│  ┌──────────────────┐  └──────────────────┘                │
│  │ GetBalanceUseCase│  ┌──────────────────┐                │
│  └──────────────────┘  │RegisterPixKeyUse │                │
│  ┌──────────────────┐  │     Case         │                │
│  │ WithdrawUseCase  │  └──────────────────┘                │
│  └──────────────────┘                                       │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                             │
│  ┌────────────────────────────────────────────────┐         │
│  │              Domain Entities                    │         │
│  │  ┌──────────┐  ┌────────────┐  ┌────────────┐ │         │
│  │  │  Wallet  │  │PixTransfer │  │Transaction │ │         │
│  │  │          │  │            │  │  (Ledger)  │ │         │
│  │  │- balance │  │- status    │  │- amount    │ │         │
│  │  │- version │  │- version   │  │- timestamp │ │         │
│  │  └──────────┘  └────────────┘  └────────────┘ │         │
│  │  ┌──────────┐  ┌────────────┐                  │         │
│  │  │ PixKey   │  │WebhookEvent│                  │         │
│  │  │          │  │            │                  │         │
│  │  │- keyType │  │- eventId   │                  │         │
│  │  │- keyValue│  │- processed │                  │         │
│  │  └──────────┘  └────────────┘                  │         │
│  └────────────────────────────────────────────────┘         │
│  ┌────────────────────────────────────────────────┐         │
│  │           Business Rules (in Entities)          │         │
│  │  - deposit()                                    │         │
│  │  - withdraw()                                   │         │
│  │  - confirm()  [State Machine]                  │         │
│  │  - reject()   [State Machine]                  │         │
│  └────────────────────────────────────────────────┘         │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              INFRASTRUCTURE LAYER (Persistence)              │
│  ┌────────────────────────────────────────────────┐         │
│  │              JPA Repositories                   │         │
│  │  ┌──────────────────┐  ┌──────────────────┐   │         │
│  │  │WalletRepository  │  │PixTransferRepo   │   │         │
│  │  │                  │  │                  │   │         │
│  │  │- findByIdWithLock│  │- findByEndToEnd  │   │         │
│  │  │  (PESSIMISTIC)   │  │  IdWithLock      │   │         │
│  │  └──────────────────┘  └──────────────────┘   │         │
│  │  ┌──────────────────┐  ┌──────────────────┐   │         │
│  │  │TransactionRepo   │  │WebhookEventRepo  │   │         │
│  │  └──────────────────┘  └──────────────────┘   │         │
│  └────────────────────────────────────────────────┘         │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    DATABASE LAYER                            │
│  ┌──────────────────────────────────────────────┐           │
│  │         PostgreSQL 15                         │           │
│  │  ┌────────────┐  ┌──────────────┐           │           │
│  │  │  wallets   │  │ pix_transfers│           │           │
│  │  │  + version │  │  + version   │           │           │
│  │  │  + balance │  │  + status    │           │           │
│  │  └────────────┘  └──────────────┘           │           │
│  │  ┌────────────┐  ┌──────────────┐           │           │
│  │  │transactions│  │webhook_events│           │           │
│  │  │ (ledger)   │  │  + eventId   │           │           │
│  │  └────────────┘  └──────────────┘           │           │
│  └──────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

### Fluxo de Transferência Pix

```
┌────────┐                ┌────────────┐                ┌──────────┐
│ Cliente│                │   Backend  │                │PostgreSQL│
└───┬────┘                └──────┬─────┘                └────┬─────┘
    │                            │                           │
    │ POST /pix/transfers        │                           │
    │ Idempotency-Key: ABC123    │                           │
    ├───────────────────────────>│                           │
    │                            │                           │
    │                            │ BEGIN TRANSACTION         │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ SELECT * FROM pix_transfers
    │                            │ WHERE idempotency_key = ABC123
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │ (vazio)                   │
    │                            │                           │
    │                            │ SELECT * FROM wallets     │
    │                            │ WHERE id = source_id      │
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ UPDATE wallets            │
    │                            │ SET balance = balance - 150
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ INSERT INTO transactions  │
    │                            │ (PIX_OUT)                 │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ INSERT INTO pix_transfers │
    │                            │ (status = PENDING)        │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ COMMIT                    │
    │                            ├──────────────────────────>│
    │                            │                           │
    │<───────────────────────────┤                           │
    │ 201 Created                │                           │
    │ {endToEndId, status:PENDING}                          │
    │                            │                           │
```

### Fluxo de Webhook

```
┌────────┐                ┌────────────┐                ┌──────────┐
│PSP/BACEN│               │   Backend  │                │PostgreSQL│
└───┬────┘                └──────┬─────┘                └────┬─────┘
    │                            │                           │
    │ POST /pix/webhook          │                           │
    │ eventId: XYZ               │                           │
    │ eventType: CONFIRMED       │                           │
    ├───────────────────────────>│                           │
    │                            │                           │
    │                            │ BEGIN TRANSACTION         │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ SELECT * FROM webhook_events
    │                            │ WHERE event_id = XYZ      │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │ (vazio - primeira vez)    │
    │                            │                           │
    │                            │ INSERT INTO webhook_events│
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ SELECT * FROM pix_transfers
    │                            │ WHERE end_to_end_id = E123│
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ SELECT * FROM wallets     │
    │                            │ WHERE id = dest_id        │
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ UPDATE wallets            │
    │                            │ SET balance = balance + 150
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ INSERT INTO transactions  │
    │                            │ (PIX_IN)                  │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ UPDATE pix_transfers      │
    │                            │ SET status = CONFIRMED    │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ UPDATE webhook_events     │
    │                            │ SET processed = true      │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ COMMIT                    │
    │                            ├──────────────────────────>│
    │                            │                           │
    │<───────────────────────────┤                           │
    │ 200 OK                     │                           │
```

---

## 🚀 Como Executar

### Pré-requisitos

- Java 17+
- Maven 3.8+
- Docker e Docker Compose (para PostgreSQL)

### 1. Iniciar o Banco de Dados

```bash
docker-compose up -d
```

Isso iniciará um container PostgreSQL na porta 5432.

### 2. Compilar o Projeto

```bash
mvn clean install
```

### 3. Executar a Aplicação

```bash
mvn spring-boot:run
```

Ou executar o JAR gerado:

```bash
java -jar target/pix-wallet-service-1.0.0.jar
```

### 4. Verificar Saúde da Aplicação

```bash
curl http://localhost:8080/actuator/health
```

Resposta esperada:
```json
{
  "status": "UP"
}
```

### 5. Acessar Métricas

- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus

---

## 📦 Guia de Deploy

### Deploy Local (Desenvolvimento)

Já coberto na seção "Como Executar" acima.

### Deploy com Docker

#### 1. Criar Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/pix-wallet-service-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Xmx512m", \
    "-Xms256m", \
    "-jar", \
    "app.jar"]
```

#### 2. Build da Imagem

```bash
mvn clean package -DskipTests
docker build -t pix-wallet-service:1.0.0 .
```

#### 3. Executar com Docker Compose (Produção)

Criar `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: pixwallet
      POSTGRES_USER: pixuser
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - pix-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pixuser"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: pix-wallet-service:1.0.0
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/pixwallet
      DATABASE_USER: pixuser
      DATABASE_PASSWORD: ${DB_PASSWORD}
      SPRING_PROFILES_ACTIVE: prod
    ports:
      - "8080:8080"
    networks:
      - pix-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

volumes:
  postgres_data:

networks:
  pix-network:
    driver: bridge
```

Executar:

```bash
export DB_PASSWORD=securepassword123
docker-compose -f docker-compose.prod.yml up -d
```

### Deploy em Kubernetes

#### Manifests básicos:

**deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pix-wallet-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pix-wallet-service
  template:
    metadata:
      labels:
        app: pix-wallet-service
    spec:
      containers:
      - name: app
        image: pix-wallet-service:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        - name: DATABASE_USER
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
```

**service.yaml**:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: pix-wallet-service
spec:
  selector:
    app: pix-wallet-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

---

## 🧪 Testes Manuais

### Pré-requisitos

1. Aplicação rodando em `http://localhost:8080`
2. PostgreSQL rodando (via Docker Compose)
3. `curl` instalado
4. `jq` instalado (opcional, para formatar JSON)

### 1. Criar Carteiras

#### Carteira 1 (Alice - Origem)
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "alice"}' | jq

# Resposta:
# {
#   "id": "123e4567-e89b-12d3-a456-426614174000",
#   "userId": "alice",
#   "balance": 0.00,
#   "createdAt": "2025-11-07T10:00:00Z"
# }
```

Salve o `id` retornado como `WALLET_ID_ALICE`

#### Carteira 2 (Bob - Destino)
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "bob"}' | jq
```

Salve o `id` retornado como `WALLET_ID_BOB`

### 2. Registrar Chaves Pix

#### Chave Pix para Bob (destino)
```bash
curl -X POST http://localhost:8080/wallets/$WALLET_ID_BOB/pix-keys \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "EMAIL",
    "keyValue": "bob@example.com"
  }' | jq

# Resposta:
# {
#   "id": "...",
#   "keyType": "EMAIL",
#   "keyValue": "bob@example.com",
#   "walletId": "...",
#   "createdAt": "..."
# }
```

### 3. Adicionar Saldo (Depósito)

#### Depósito na carteira de Alice
```bash
curl -X POST http://localhost:8080/wallets/$WALLET_ID_ALICE/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 1000.00}' | jq
```

#### Verificar saldo
```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Resposta:
# {
#   "balance": 1000.00
# }
```

### 4. Realizar Transferência Pix

#### Transferir de Alice para Bob
```bash
IDEMPOTENCY_KEY=$(uuidgen)

curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "fromWalletId": "'$WALLET_ID_ALICE'",
    "toPixKey": "bob@example.com",
    "amount": 150.00
  }' | jq

# Resposta:
# {
#   "endToEndId": "E1730512345678ABC",
#   "status": "PENDING",
#   "amount": 150.00,
#   "toPixKey": "bob@example.com",
#   "createdAt": "2025-11-07T10:30:00Z"
# }
```

Salve o `endToEndId` retornado como `END_TO_END_ID`

#### Verificar saldo de Alice (deve ter sido debitado)
```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Esperado: 850.00
```

#### Verificar saldo de Bob (ainda não creditado)
```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Esperado: 0.00 (transferência ainda PENDING)
```

### 5. Simular Webhook de Confirmação

#### Confirmar transferência
```bash
curl -X POST http://localhost:8080/pix/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "'$(uuidgen)'",
    "endToEndId": "'$END_TO_END_ID'",
    "eventType": "CONFIRMED",
    "occurredAt": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }' | jq

# Resposta: 200 OK
```

#### Verificar saldo de Bob (agora creditado)
```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Esperado: 150.00
```

### 6. Testar Idempotência

#### Tentar duplicar a transferência
```bash
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "fromWalletId": "'$WALLET_ID_ALICE'",
    "toPixKey": "bob@example.com",
    "amount": 150.00
  }' | jq

# Resposta: Mesma transferência anterior (idempotente)
```

#### Verificar que saldo não mudou
```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Esperado: 850.00 (não debitou novamente)
```

### 7. Testar Webhook Duplicado

#### Reenviar webhook de confirmação
```bash
curl -X POST http://localhost:8080/pix/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "'$(uuidgen)'",
    "endToEndId": "'$END_TO_END_ID'",
    "eventType": "CONFIRMED",
    "occurredAt": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }' | jq

# Resposta: 200 OK (idempotente)
```

#### Verificar que saldo de Bob não mudou
```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Esperado: 150.00 (não creditou novamente)
```

### 8. Testar Saldo Histórico

#### Consultar saldo em timestamp passado
```bash
# Timestamp antes da transferência
PAST_TIMESTAMP="2025-11-07T10:00:00Z"

curl "http://localhost:8080/wallets/$WALLET_ID_ALICE/balance?at=$PAST_TIMESTAMP" | jq

# Esperado: 1000.00 (antes da transferência)
```

---

## 📝 Exemplos de API

### Criar Carteira
```bash
POST /wallets
Content-Type: application/json

{
  "userId": "user123"
}

# Resposta: 201 Created
{
  "id": "uuid",
  "userId": "user123",
  "balance": 0.00,
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Registrar Chave Pix
```bash
POST /wallets/{walletId}/pix-keys
Content-Type: application/json

{
  "keyType": "EMAIL",
  "keyValue": "user@example.com"
}

# Resposta: 201 Created
{
  "id": "uuid",
  "keyType": "EMAIL",
  "keyValue": "user@example.com",
  "walletId": "uuid",
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Consultar Saldo
```bash
GET /wallets/{walletId}/balance

# Resposta: 200 OK
{
  "balance": 1000.00
}

# Saldo histórico:
GET /wallets/{walletId}/balance?at=2025-11-07T10:00:00Z

# Resposta: 200 OK
{
  "balance": 500.00
}
```

### Depósito
```bash
POST /wallets/{walletId}/deposit
Content-Type: application/json
Idempotency-Key: unique-key-123

{
  "amount": 500.00
}

# Resposta: 201 Created
{
  "id": "uuid",
  "walletId": "uuid",
  "type": "DEPOSIT",
  "amount": 500.00,
  "balanceAfter": 1500.00,
  "description": "Deposit",
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Saque
```bash
POST /wallets/{walletId}/withdraw
Content-Type: application/json
Idempotency-Key: unique-key-456

{
  "amount": 200.00
}

# Resposta: 201 Created
{
  "id": "uuid",
  "walletId": "uuid",
  "type": "WITHDRAWAL",
  "amount": -200.00,
  "balanceAfter": 1300.00,
  "description": "Withdrawal",
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Transferência Pix
```bash
POST /pix/transfers
Content-Type: application/json
Idempotency-Key: unique-key-789

{
  "fromWalletId": "source-wallet-uuid",
  "toPixKey": "destination@example.com",
  "amount": 150.00
}

# Resposta: 201 Created
{
  "endToEndId": "E1730512345678ABC",
  "status": "PENDING",
  "amount": 150.00,
  "toPixKey": "destination@example.com",
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Webhook Pix
```bash
POST /pix/webhook
Content-Type: application/json

{
  "eventId": "webhook-event-uuid",
  "endToEndId": "E1730512345678ABC",
  "eventType": "CONFIRMED",
  "occurredAt": "2025-11-07T10:05:00Z"
}

# Resposta: 200 OK
```

---

## 🔍 Análise de Problemas e Erros

### Problemas Identificados

Durante a análise completa do código, foram identificados os seguintes problemas:

#### 1. **Imports Não Utilizados** (Warnings)

**Arquivos afetados:**
- `ConcurrencyIntegrationTest.java`
- `WebhookEventRepository.java`
- `CreateWalletUseCase.java`

**Problema:**
```java
import com.pixservice.domain.enums.PixTransferStatus; // Nunca usado
import static org.awaitility.Awaitility.await; // Nunca usado
import org.springframework.data.repository.query.Param; // Nunca usado
import java.util.UUID; // Nunca usado
```

**Impacto:** Baixo - apenas poluição do código, não afeta funcionalidade

**Correção:**
- Remover imports não utilizados

#### 2. **Null Safety** (Avisos do compilador)

**Arquivos afetados:**
- `ConcurrencyIntegrationTest.java`
- `WebhookIntegrationTest.java`
- `CreateWalletUseCase.java`
- `DepositUseCase.java`
- `GetBalanceUseCase.java`
- `PixTransferUseCase.java`
- `ProcessPixWebhookUseCase.java`
- `RegisterPixKeyUseCase.java`
- `WithdrawUseCase.java`
- `DepositUseCaseTest.java`

**Problema:**
```java
Wallet wallet = walletRepository.findById(walletId).orElseThrow();
// O compilador avisa que UUID pode ser null

Transaction savedTransaction = transactionRepository.save(transaction);
// O compilador avisa sobre conversão não verificada
```

**Impacto:** Baixo - avisos de segurança de tipo, mas não causam erros em runtime

**Causa:** Falta de anotações `@NonNull` ou configuração do Eclipse/IDE

**Correção:**
- Adicionar validação explícita de nulidade
- Ou configurar IDE para não exigir null safety estrito

#### 3. **Versão do Spring Boot Desatualizada** (Warning)

**Arquivo:** `pom.xml`

**Problema:**
```xml
<version>3.2.0</version>
<!-- Existe versão mais nova: 3.2.12 -->
<!-- OSS support para 3.2.x terminou em 2024-12-31 -->
```

**Impacto:** Médio - perde patches de segurança e bug fixes

**Correção:**
```xml
<version>3.2.12</version>
```

Ou migrar para Spring Boot 3.3.x:
```xml
<version>3.3.5</version>
```

#### 4. **Idempotency-Key Opcional no Controller** (Design Problem)

**Arquivo:** `WalletController.java`

**Problema:**
```java
@PostMapping("/{id}/deposit")
public ResponseEntity<TransactionResponse> deposit(
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    
    if (idempotencyKey == null) {
        idempotencyKey = UUID.randomUUID().toString(); // Auto-gera se não fornecido
    }
```

**Impacto:** Alto - **Este é o principal problema de design!**

**Por que é problema:**
1. **Perde a garantia de exactly-once**: Se cliente não fornece chave, cada retry será processado como operação nova
2. **Dificulta debugging**: Operações duplicadas não terão mesma chave
3. **Inconsistente com boas práticas**: Idempotency-Key deve ser obrigatório

**Correção:** Tornar `Idempotency-Key` obrigatório:
```java
@PostMapping("/{id}/deposit")
public ResponseEntity<TransactionResponse> deposit(
    @RequestHeader("Idempotency-Key") String idempotencyKey) { // required = true (default)
```

#### 5. **Falta de Validação de Null em Parâmetros**

**Arquivos:** Vários use cases

**Problema:**
```java
public PixTransfer execute(UUID fromWalletId, String toPixKey, BigDecimal amount, String idempotencyKey) {
    // Não valida se parâmetros são null antes de usar
    validateTransferRequest(fromWalletId, toPixKey, amount, idempotencyKey);
```

**Impacto:** Baixo - validação existe no método `validateTransferRequest`, mas seria melhor usar anotações

**Correção:** Adicionar `@NonNull` ou usar `Objects.requireNonNull`:
```java
public PixTransfer execute(
    @NonNull UUID fromWalletId, 
    @NonNull String toPixKey, 
    @NonNull BigDecimal amount, 
    @NonNull String idempotencyKey) {
```

#### 6. **Falta de Índice no Campo `user_id`**

**Arquivo:** `Wallet.java`

**Problema:**
```java
@Column(nullable = false, unique = true)
private String userId;
// Falta @Index ou índice explícito no banco
```

**Impacto:** Médio - performance pode degradar ao buscar carteiras por userId

**Correção:**
```java
@Table(name = "wallets", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id", unique = true)
})
```

#### 7. **Falta de Timeout em Operações com Lock**

**Problema:** Locks pessimistas podem causar deadlock sem timeout configurado

**Impacto:** Médio - em alta concorrência pode travar threads

**Correção:** Adicionar timeout no `application.yml`:
```yaml
spring:
  jpa:
    properties:
      javax:
        persistence:
          lock:
            timeout: 10000  # 10 segundos
```

#### 8. **Falta de Rate Limiting**

**Problema:** Não há proteção contra abuso/flooding de requests

**Impacto:** Médio - sistema pode ser sobrecarregado

**Correção:** Implementar rate limiting com Bucket4j ou Spring Security

#### 9. **Logs com Informações Sensíveis**

**Arquivos:** Controllers e Use Cases

**Problema:**
```java
log.info("Processing Pix transfer: fromWallet={}, toPixKey={}, amount={}, idempotencyKey={}", 
        fromWalletId, toPixKey, amount, idempotencyKey);
// Expõe dados sensíveis em logs
```

**Impacto:** Médio - compliance (LGPD/GDPR)

**Correção:** Mascarar dados sensíveis:
```java
log.info("Processing Pix transfer: fromWallet={}, toPixKey={}, amount={}, idempotencyKey={}", 
        fromWalletId, maskPixKey(toPixKey), amount, maskIdempotencyKey(idempotencyKey));
```

#### 10. **Falta de Documentação OpenAPI/Swagger**

**Problema:** Não há documentação automática da API

**Impacto:** Baixo - dificulta uso por terceiros

**Correção:** Adicionar SpringDoc:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### Resumo de Severidade

| Severidade | Quantidade | Descrição |
|------------|------------|-----------|
| 🔴 Alta    | 1          | Idempotency-Key opcional |
| 🟡 Média   | 4          | Spring Boot desatualizado, falta índices, timeout, rate limiting |
| 🟢 Baixa   | 5          | Imports não usados, null safety warnings, falta Swagger |

### Recomendações Prioritárias

1. **🔴 CRÍTICO:** Tornar `Idempotency-Key` obrigatório
2. **🟡 IMPORTANTE:** Atualizar Spring Boot para versão com suporte
3. **🟡 IMPORTANTE:** Adicionar índice em `user_id`
4. **🟡 IMPORTANTE:** Configurar timeout para locks
5. **🟢 DESEJÁVEL:** Remover imports não utilizados
6. **🟢 DESEJÁVEL:** Adicionar documentação Swagger

---

## 📊 Checklist de Produção

Antes de colocar em produção, verificar:

- [ ] Todos testes passando
- [ ] Idempotency-Key obrigatório
- [ ] Spring Boot atualizado
- [ ] Índices criados no banco
- [ ] Timeout de locks configurado
- [ ] Rate limiting implementado
- [ ] Logs sem dados sensíveis
- [ ] Monitoramento configurado (Prometheus/Grafana)
- [ ] Alertas configurados
- [ ] Backup automático do banco
- [ ] Secrets em variáveis de ambiente (não hardcoded)
- [ ] HTTPS configurado
- [ ] Documentação Swagger disponível

---

## 📞 Suporte

Para dúvidas ou problemas:
- Consulte este documento
- Verifique logs da aplicação
- Revise métricas no Prometheus
- Consulte a equipe de desenvolvimento

---

**Última Atualização:** 07/11/2025
**Versão:** 1.0.0
