# Pix Wallet Service - Documentação Completa

Bem-vindo! 👋 Esta é a documentação do nosso serviço de carteira digital com suporte a transferências Pix. Criamos este guia pensando em você, desenvolvedor, que precisa entender rapidamente como tudo funciona.

---

## 📋 O que você vai encontrar aqui

1. [O que é este projeto?](#-o-que-%C3%A9-este-projeto)

2. [O que ele faz?](#-o-que-ele-faz)

3. [Como funciona por dentro](#-como-funciona-por-dentro)

4. [Por que fizemos assim?](#-por-que-fizemos-assim)

5. [Tecnologias que usamos](#%EF%B8%8F-tecnologias-que-usamos)

6. [Como está organizado](#-como-est%C3%A1-organizado)

7. [Como garantimos que tudo funcione direitinho](#-como-garantimos-que-tudo-funcione-direitinho)

8. [Diagramas para visualizar](#-diagramas-para-visualizar)

9. [Rodando o projeto](#-rodando-o-projeto)

10. [Colocando em produção](#-colocando-em-produ%C3%A7%C3%A3o)

11. [Testando na mão](#-testando-na-m%C3%A3o)

12. [Exemplos práticos de uso](#-exemplos-pr%C3%A1ticos-de-uso)

13. [Dúvidas frequentes](#-d%C3%BAvidas-frequentes)

14. [Problemas comuns e soluções](#-problemas-comuns-e-solu%C3%A7%C3%B5es)

---

## 🎯 O que é este projeto?

Este é um microserviço de carteira digital que permite fazer transferências via Pix. Ele foi desenvolvido pensando em **produção real**, com todas as garantias que um sistema financeiro precisa ter.

### O que queríamos alcançar

Criar um sistema de carteira que garanta:

* ✅ **Nenhuma transferência duplicada** - mesmo que o usuário clique duas vezes

* ✅ **Dados sempre consistentes** - mesmo com milhares de pessoas usando ao mesmo tempo

* ✅ **Operações que podem ser repetidas sem problema** - se algo falhar, podemos tentar de novo

* ✅ **Histórico completo de tudo** - para auditorias e compliance

* ✅ **Fácil de monitorar** - logs estruturados e métricas em tempo real

---

## ✅ O que ele faz?

### Funcionalidades principais

* ✅ **Criar conta/carteira** - cada usuário tem sua carteira digital

* ✅ **Registrar chave Pix** - EMAIL, TELEFONE, CPF ou chave aleatória

* ✅ **Consultar saldo atual** - quanto você tem agora

* ✅ **Consultar saldo histórico** - quanto você tinha em qualquer momento do passado

* ✅ **Fazer depósitos** - adicionar dinheiro na carteira

* ✅ **Fazer saques** - retirar dinheiro da carteira

* ✅ **Transferir via Pix** - enviar dinheiro para outras pessoas

* ✅ **Receber confirmações** - webhook que confirma ou rejeita transferências

### Garantias técnicas

* ✅ **Sem duplicação** - mesmo clicando 100 vezes, só processa uma vez

* ✅ **Rastreável** - todo centavo tem histórico completo

* ✅ **Seguro em concorrência** - milhares de operações simultâneas sem problema

* ✅ **Idempotente** - pode repetir a operação que o resultado é o mesmo

* ✅ **Observável** - você sabe exatamente o que está acontecendo

### Cenários que tratamos

* ✅ **Duplo clique** - usuário clica duas vezes no botão → só debita uma vez

* ✅ **Webhook duplicado** - recebemos a mesma confirmação duas vezes → só credita uma vez

* ✅ **Ordem trocada** - webhooks chegam fora de ordem → sistema se organiza

* ✅ **Retry automático** - falhou? Tenta de novo sem bagunçar nada

---

## 🏗️ Como funciona por dentro

### Arquitetura Limpa (Clean Architecture)

Organizamos o código em 4 camadas bem separadas:

```
Domínio (Regras de negócio e entidades)
   ↓
Aplicação (Casos de uso)
   ↓
Infraestrutura (Banco de dados)
   ↓
Adaptadores (APIs REST)
```

### Por que assim?

Escolhemos Clean Architecture porque:

* **Independente de frameworks** - a lógica de negócio não depende do Spring

* **Fácil de testar** - podemos testar sem precisar de banco de dados

* **Responsabilidades claras** - cada camada tem seu papel bem definido

* **Fácil de manter** - mudanças ficam isoladas em suas camadas

### Por que não fizemos do jeito tradicional?

A arquitetura tradicional (Controller → Service → Repository) tem problemas:

* **Muito acoplado** - Service depende diretamente do banco

* **Difícil de testar** - precisa do banco para testar qualquer coisa

* **Difícil de evoluir** - mudanças em infraestrutura afetam a lógica de negócio

---

## 💡 Por que fizemos assim?

### 1\. Bloqueio pessimista para operações críticas

**Onde usamos**: Sempre que mexemos no saldo (depósito, saque, transferência)

**Como funciona**:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdWithLock(@Param("id") UUID id);
```

**Vantagens**:

* ✅ Garante que ninguém mais mexe no saldo ao mesmo tempo

* ✅ Simples de entender e debugar

* ✅ Zero chance de perder atualizações

**Desvantagens**:

* ❌ Pode ficar mais lento com muita gente ao mesmo tempo

* ❌ Risco de deadlock (mas a gente previne isso)

**Outras opções que consideramos**:

* Bloqueio otimista apenas: precisaria de lógica complexa de retry

* Locks distribuídos (Redis): adiciona complexidade e mais um ponto de falha

### 2\. Bloqueio otimista como segunda camada

**Onde usamos**: Nas entidades `Wallet` e `PixTransfer`

**Como funciona**:

```java
@Version
private Long version;
```

**Para que serve**: Funciona como uma rede de segurança junto com o bloqueio pessimista

**Vantagens**:

* ✅ Detecta conflitos que escapem do lock pessimista

* ✅ O Spring faz retry automático

### 3\. Chave de idempotência

**Onde usamos**: Todas operações críticas (transferências, depósitos, webhooks)

**Como funciona**:

```sql
CREATE UNIQUE INDEX idx_idempotency_key ON pix_transfers(idempotency_key);
CREATE UNIQUE INDEX idx_event_id ON webhook_events(event_id);
```

**O fluxo**:

1. Cliente envia uma chave única no header `Idempotency-Key`

2. Backend verifica se essa chave já foi usada

3. Se já foi: retorna o resultado anterior

4. Se não foi: processa e salva com essa chave

**Vantagens**:

* ✅ Garante que nada é processado duas vezes

* ✅ Protege contra duplo clique, retries, problemas de rede

* ✅ Implementação robusta usando constraint do banco

### 4\. Livro-razão imutável (Ledger)

**Como funciona**: A tabela `transactions` é **append-only** (só adiciona, nunca atualiza ou deleta)

**Campos principais**:

* `wallet_id`: Qual carteira foi afetada

* `type`: DEPOSIT, WITHDRAWAL, PIX_IN, PIX_OUT

* `amount`: Valor (positivo ou negativo)

* `balance_after`: Saldo depois da operação

* `created_at`: Quando aconteceu

**Vantagens**:

* ✅ **Auditoria completa** - tudo fica registrado para sempre

* ✅ **Saldo histórico** - podemos saber quanto tinha em qualquer momento

* ✅ **Compliance** - atende requisitos do Banco Central

* ✅ **Detecção de fraudes** - qualquer inconsistência é detectável

**Como calcular saldo histórico**:

```sql
SELECT SUM(amount) 
FROM transactions 
WHERE wallet_id = ? AND created_at <= ?
```

### 5\. Máquina de estados para transferências

**Estados possíveis**: `PENDING` → `CONFIRMED` ou `REJECTED`

**Transições válidas**:

```
PENDING → CONFIRMED ✅
PENDING → REJECTED  ✅
CONFIRMED → REJECTED ❌ (erro!)
REJECTED → CONFIRMED ❌ (erro!)
```

**Como implementamos**:

```java
public void confirm() {
    if (status == PixTransferStatus.CONFIRMED) {
        return; // Já confirmado, tudo bem
    }
    if (status == PixTransferStatus.REJECTED) {
        throw new IllegalStateException("Não pode confirmar uma transferência rejeitada");
    }
    this.status = PixTransferStatus.CONFIRMED;
}
```

**Vantagens**:

* ✅ Impede estados inválidos

* ✅ Garante consistência mesmo se webhooks chegarem fora de ordem

* ✅ Operações são idempotentes

### 6\. Deduplicação de webhooks

**O problema**: Webhooks podem chegar duplicados ou fora de ordem

**Nossa solução**:

* Cada webhook tem um `eventId` único

* Bloqueio pessimista previne race conditions

* Webhooks duplicados ou fora de ordem são tratados corretamente

**Como implementamos**:

```java
if (webhookEventRepository.existsByEventId(eventId)) {
    return; // Já processamos, ignora
}
```

---

## 🛠️ Tecnologias que usamos

### Backend

* **Java 17** - versão LTS com performance moderna

* **Spring Boot 3.2.0** - framework consolidado e confiável

* **Spring Data JPA** - facilita o trabalho com banco de dados

* **Hibernate** - ORM robusto e maduro

### Banco de dados

* **PostgreSQL 15** - ACID completo, locks robustos

* **HikariCP** - gerenciamento eficiente de conexões

### Observabilidade

* **Logback** - logs estruturados

* **Logstash Encoder** - formato JSON para logs

* **Micrometer + Prometheus** - métricas e monitoramento

### Testes

* **JUnit 5** - framework de testes moderno

* **Mockito** - simula dependências nos testes

* **Awaitility** - testa cenários de concorrência

* **H2** - banco em memória para testes

---

## 📁 Como está organizado

```
pix-wallet-service/
├── src/
│   ├── main/
│   │   ├── java/com/pixservice/
│   │   │   ├── domain/              # Entidades e regras de negócio
│   │   │   │   ├── entity/          # Wallet, PixKey, Transaction, etc.
│   │   │   │   └── enums/           # Status, tipos, etc.
│   │   │   ├── application/         # Casos de uso
│   │   │   │   ├── usecase/         # CreateWallet, PixTransfer, etc.
│   │   │   │   └── exception/       # Exceções de negócio
│   │   │   ├── infrastructure/      # Infraestrutura
│   │   │   │   └── repository/      # Repositórios JPA
│   │   │   └── adapter/             # Adaptadores
│   │   │       └── rest/
│   │   │           ├── controller/  # Controllers REST
│   │   │           ├── dto/         # Request/Response
│   │   │           └── exception/   # Tratamento de erros
│   │   └── resources/
│   │       ├── application.yml      # Configuração principal
│   │       ├── application-test.yml # Configuração de testes
│   │       ├── application-prod.yml # Configuração de produção
│   │       └── logback-spring.xml   # Configuração de logs
│   └── test/                        # Testes
│       └── java/com/pixservice/
│           ├── application/usecase/ # Testes de casos de uso
│           ├── domain/entity/       # Testes de entidades
│           └── integration/         # Testes de integração
├── docker-compose.yml               # PostgreSQL local
├── pom.xml                          # Dependências Maven
└── README.md                        # Esta documentação
```

---

## 🔒 Como garantimos que tudo funcione direitinho

### 1\. Bloqueio pessimista

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Wallet> findByIdWithLock(UUID id);
```

Garante que só uma operação mexe no saldo por vez.

### 2\. Bloqueio otimista

```java
@Version
private Long version;
```

Segunda camada de defesa contra atualizações concorrentes.

### 3\. Idempotência por chave única

```sql
CREATE UNIQUE INDEX idx_idempotency_key 
ON pix_transfers(idempotency_key);
```

O próprio banco impede duplicação.

### 4\. Livro-razão imutável

Tabela `transactions` só adiciona registros:

* Auditoria completa

* Saldo histórico calculável

* Compliance regulatório

### 5\. Máquina de estados

```
PENDING → CONFIRMED ✅
PENDING → REJECTED  ✅
CONFIRMED → REJECTED ❌ (erro!)
```

### 6\. Transações ACID

```java
@Transactional
public PixTransfer execute(...) {
    // Todas operações em uma transação atômica
}
```

---

## 📊 Diagramas para visualizar

### Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENTE                                  │
│  (Postman, cURL, App Frontend, Serviços Externos)           │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP/REST
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  CAMADA DE API                               │
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
│  │   Tratamento Global de Erros                     │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              CAMADA DE APLICAÇÃO (Casos de Uso)              │
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
│                  CAMADA DE DOMÍNIO                           │
│  ┌────────────────────────────────────────────────┐         │
│  │           Entidades do Domínio                  │         │
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
│  │      Regras de Negócio (nas Entidades)         │         │
│  │  - deposit()                                    │         │
│  │  - withdraw()                                   │         │
│  │  - confirm()  [Máquina de Estados]            │         │
│  │  - reject()   [Máquina de Estados]            │         │
│  └────────────────────────────────────────────────┘         │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│         CAMADA DE INFRAESTRUTURA (Persistência)              │
│  ┌────────────────────────────────────────────────┐         │
│  │           Repositórios JPA                      │         │
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
│                 CAMADA DE BANCO DE DADOS                     │
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

### Como funciona uma transferência Pix

```
┌────────┐                ┌────────────┐                ┌──────────┐
│ Cliente│                │   Backend  │                │PostgreSQL│
└───┬────┘                └──────┬─────┘                └────┬─────┘
    │                            │                           │
    │ POST /pix/transfers        │                           │
    │ Idempotency-Key: ABC123    │                           │
    ├───────────────────────────>│                           │
    │                            │                           │
    │                            │ INICIA TRANSAÇÃO          │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Verifica se chave ABC123  │
    │                            │ já foi usada              │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │ (não foi usada)           │
    │                            │                           │
    │                            │ Bloqueia carteira origem  │
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ Atualiza saldo            │
    │                            │ balance = balance - 150   │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Registra no ledger        │
    │                            │ (PIX_OUT)                 │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Cria transferência        │
    │                            │ (status = PENDING)        │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ CONFIRMA TRANSAÇÃO        │
    │                            ├──────────────────────────>│
    │                            │                           │
    │<───────────────────────────┤                           │
    │ 201 Created                │                           │
    │ {endToEndId, status:PENDING}                          │
    │                            │                           │
```

### Como funciona um webhook

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
    │                            │ INICIA TRANSAÇÃO          │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Verifica se eventId XYZ   │
    │                            │ já foi processado         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │ (primeira vez)            │
    │                            │                           │
    │                            │ Registra evento           │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Busca transferência       │
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ Bloqueia carteira destino │
    │                            │ FOR UPDATE (LOCK)         │
    │                            ├──────────────────────────>│
    │                            │<──────────────────────────┤
    │                            │                           │
    │                            │ Atualiza saldo            │
    │                            │ balance = balance + 150   │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Registra no ledger        │
    │                            │ (PIX_IN)                  │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Atualiza transferência    │
    │                            │ status = CONFIRMED        │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ Marca evento processado   │
    │                            ├──────────────────────────>│
    │                            │                           │
    │                            │ CONFIRMA TRANSAÇÃO        │
    │                            ├──────────────────────────>│
    │                            │                           │
    │<───────────────────────────┤                           │
    │ 200 OK                     │                           │
```

---

## 🚀 Rodando o projeto

### O que você precisa ter instalado

* Java 17 ou superior

* Maven 3.8 ou superior

* Docker e Docker Compose (para o PostgreSQL)

### Passo 1: Subir o banco de dados

```bash
docker-compose up -d
```

Isso vai iniciar um container PostgreSQL na porta 5432.

### Passo 2: Compilar o projeto

```bash
mvn clean install
```

### Passo 3: Rodar a aplicação

```bash
mvn spring-boot:run
```

Ou se preferir rodar o JAR:

```bash
java -jar target/pix-wallet-service-1.0.0.jar
```

### Passo 4: Verificar se está tudo OK

```bash
curl http://localhost:8080/actuator/health
```

Você deve ver:

```json
{
  "status": "UP"
}
```

### Passo 5: Ver as métricas

* **Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

* **Metrics**: [http://localhost:8080/actuator/metrics](http://localhost:8080/actuator/metrics)

* **Prometheus**: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

## 📦 Colocando em produção

### Rodando localmente (desenvolvimento)

Já explicamos acima na seção "Rodando o projeto".

### Rodando com Docker

#### 1\. Criar o Dockerfile

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

#### 2\. Criar a imagem

```bash
mvn clean package -DskipTests
docker build -t pix-wallet-service:1.0.0 .
```

#### 3\. Rodar com Docker Compose (produção)

Crie um arquivo `docker-compose.prod.yml`:

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

Rodar:

```bash
export DB_PASSWORD=senhasegura123
docker-compose -f docker-compose.prod.yml up -d
```

### Rodando no Kubernetes

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

## 🧪 Testando na mão

### O que você precisa

1. Aplicação rodando em `http://localhost:8080`

2. PostgreSQL rodando (via Docker Compose)

3. `curl` instalado

4. `jq` instalado (opcional, para formatar JSON)

### 1\. Criar carteiras

#### Carteira da Alice (quem vai enviar)

```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "alice"}' | jq

# Você vai ver algo assim:
# {
#   "id": "123e4567-e89b-12d3-a456-426614174000",
#   "userId": "alice",
#   "balance": 0.00,
#   "createdAt": "2025-11-07T10:00:00Z"
# }
```

Guarde o `id` que apareceu como `WALLET_ID_ALICE`

#### Carteira do Bob (quem vai receber)

```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "bob"}' | jq
```

Guarde o `id` que apareceu como `WALLET_ID_BOB`

### 2\. Registrar chave Pix do Bob

```bash
curl -X POST http://localhost:8080/wallets/$WALLET_ID_BOB/pix-keys \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "EMAIL",
    "keyValue": "bob@example.com"
  }' | jq

# Você vai ver:
# {
#   "id": "...",
#   "keyType": "EMAIL",
#   "keyValue": "bob@example.com",
#   "walletId": "...",
#   "createdAt": "..."
# }
```

### 3\. Colocar dinheiro na carteira da Alice

```bash
curl -X POST http://localhost:8080/wallets/$WALLET_ID_ALICE/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 1000.00}' | jq
```

#### Ver o saldo

```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Deve mostrar:
# {
#   "balance": 1000.00
# }
```

### 4\. Fazer uma transferência Pix

#### Alice envia R$ 150 para Bob

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

# Você vai ver:
# {
#   "endToEndId": "E1730512345678ABC",
#   "status": "PENDING",
#   "amount": 150.00,
#   "toPixKey": "bob@example.com",
#   "createdAt": "2025-11-07T10:30:00Z"
# }
```

Guarde o `endToEndId` como `END_TO_END_ID`

#### Ver saldo da Alice (já foi debitado)

```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Deve mostrar: 850.00
```

#### Ver saldo do Bob (ainda não foi creditado)

```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Deve mostrar: 0.00 (transferência ainda está PENDING)
```

### 5\. Simular confirmação do Pix

#### Confirmar a transferência

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

#### Ver saldo do Bob (agora foi creditado!)

```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Deve mostrar: 150.00
```

### 6\. Testar idempotência

#### Tentar fazer a mesma transferência de novo

```bash
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "fromWalletId": "'$WALLET_ID_ALICE'",
    "toPixKey": "bob@example.com",
    "amount": 150.00
  }' | jq

# Resposta: Mesma transferência anterior (não debitou de novo!)
```

#### Verificar que o saldo não mudou

```bash
curl http://localhost:8080/wallets/$WALLET_ID_ALICE/balance | jq

# Deve continuar: 850.00 (não debitou novamente)
```

### 7\. Testar webhook duplicado

#### Enviar o mesmo webhook de novo

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

#### Verificar que o saldo do Bob não mudou

```bash
curl http://localhost:8080/wallets/$WALLET_ID_BOB/balance | jq

# Deve continuar: 150.00 (não creditou novamente)
```

### 8\. Testar saldo histórico

#### Ver quanto a Alice tinha antes da transferência

```bash
# Timestamp antes da transferência
PAST_TIMESTAMP="2025-11-07T10:00:00Z"

curl "http://localhost:8080/wallets/$WALLET_ID_ALICE/balance?at=$PAST_TIMESTAMP" | jq

# Deve mostrar: 1000.00 (antes da transferência)
```

---

## 📝 Exemplos práticos de uso

### Criar uma carteira

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

### Registrar chave Pix

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

### Ver o saldo

```bash
GET /wallets/{walletId}/balance

# Resposta: 200 OK
{
  "balance": 1000.00
}

# Ver saldo em um momento do passado:
GET /wallets/{walletId}/balance?at=2025-11-07T10:00:00Z

# Resposta: 200 OK
{
  "balance": 500.00
}
```

### Fazer um depósito

```bash
POST /wallets/{walletId}/deposit
Content-Type: application/json
Idempotency-Key: chave-unica-123

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

### Fazer um saque

```bash
POST /wallets/{walletId}/withdraw
Content-Type: application/json
Idempotency-Key: chave-unica-456

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

### Fazer uma transferência Pix

```bash
POST /pix/transfers
Content-Type: application/json
Idempotency-Key: chave-unica-789

{
  "fromWalletId": "uuid-carteira-origem",
  "toPixKey": "destino@example.com",
  "amount": 150.00
}

# Resposta: 201 Created
{
  "endToEndId": "E1730512345678ABC",
  "status": "PENDING",
  "amount": 150.00,
  "toPixKey": "destino@example.com",
  "createdAt": "2025-11-07T10:00:00Z"
}
```

### Receber webhook do Pix

```bash
POST /pix/webhook
Content-Type: application/json

{
  "eventId": "uuid-evento-webhook",
  "endToEndId": "E1730512345678ABC",
  "eventType": "CONFIRMED",
  "occurredAt": "2025-11-07T10:05:00Z"
}

# Resposta: 200 OK
```

---

## ❓ Dúvidas frequentes

### Por que usar bloqueio pessimista em vez de otimista?

**Resposta**: Para operações financeiras, preferimos garantir consistência absoluta. O bloqueio pessimista garante que apenas uma operação por vez mexe no saldo, eliminando race conditions. Embora possa reduzir o throughput, a segurança é mais importante que performance neste caso.

### O que acontece se o webhook chegar antes da transferência ser criada?

**Resposta**: O webhook vai falhar porque não encontra a transferência. O PSP/BACEN vai reenviar o webhook automaticamente (retry), e na próxima tentativa a transferência já estará criada.

### Como funciona o saldo histórico?

**Resposta**: Mantemos um ledger imutável de todas as transações. Para calcular o saldo em qualquer momento do passado, somamos todas as transações até aquele timestamp.

### Por que usar UUID em vez de ID sequencial?

**Resposta**: UUIDs são globalmente únicos e não revelam informações sobre o volume de transações. Também facilitam merge de dados de diferentes ambientes.

### O sistema suporta múltiplas moedas?

**Resposta**: Atualmente não. O sistema foi projetado para uma única moeda (Real). Para suportar múltiplas moedas, seria necessário adicionar um campo `currency` e lógica de conversão.

### Como escalar horizontalmente?

**Resposta**: O sistema foi projetado para ser stateless. Você pode adicionar mais instâncias da aplicação atrás de um load balancer. O PostgreSQL garante a consistência através dos locks.

### O que é idempotência e por que é importante?

**Resposta**: Idempotência significa que você pode executar a mesma operação múltiplas vezes e o resultado será o mesmo da primeira execução. Isso é crucial para sistemas distribuídos onde retries são comuns devido a falhas de rede ou timeouts.

### Como o sistema lida com deadlocks?

**Resposta**: Minimizamos deadlocks adquirindo locks sempre na mesma ordem (primeiro carteira origem, depois destino). O PostgreSQL também tem timeout configurado para detectar e resolver deadlocks automaticamente.

### Posso usar este sistema em produção?

**Resposta**: Este projeto foi desenvolvido como code assessment com foco em boas práticas. Para produção real, seria necessário adicionar: autenticação/autorização, rate limiting, circuit breakers, monitoramento avançado, testes de carga, e integração real com PSP/BACEN.

### Como contribuir com o projeto?

**Resposta**:

1. Faça um fork do repositório

2. Crie uma branch para sua feature (`git checkout -b feature/MinhaFeature`)

3. Commit suas mudanças (`git commit -m 'Adiciona MinhaFeature'`)

4. Push para a branch (`git push origin feature/MinhaFeature`)

5. Abra um Pull Request

---

## 🔧 Problemas comuns e soluções

### Erro: "Could not acquire lock"

**Causa**: Deadlock ou timeout de lock

**Solução**:

* Verifique se há operações travadas no banco

* Aumente o timeout de lock se necessário

* Garanta que locks são sempre adquiridos na mesma ordem

**Como verificar locks no PostgreSQL**:

```sql
SELECT * FROM pg_locks WHERE NOT granted;
```

### Erro: "Insufficient balance"

**Causa**: Tentativa de saque/transferência sem saldo suficiente

**Solução**: Verifique o saldo antes de tentar a operação

**Como verificar**:

```bash
curl http://localhost:8080/wallets/{walletId}/balance
```

### Erro: "Duplicate key violation"

**Causa**: Tentativa de usar a mesma Idempotency-Key para operações diferentes

**Solução**: Gere uma nova Idempotency-Key única para cada operação

**Exemplo correto**:

```bash
# Gera uma nova chave única
IDEMPOTENCY_KEY=$(uuidgen)
curl -H "Idempotency-Key: $IDEMPOTENCY_KEY" ...
```

### Erro: "Pix key not found"

**Causa**: Chave Pix do destinatário não está registrada

**Solução**: Registre a chave Pix antes de fazer a transferência

**Como registrar**:

```bash
curl -X POST http://localhost:8080/wallets/{walletId}/pix-keys \
  -H "Content-Type: application/json" \
  -d '{"keyType": "EMAIL", "keyValue": "user@example.com"}'
```

### Erro: "Cannot confirm a rejected transfer"

**Causa**: Tentativa de confirmar uma transferência que já foi rejeitada

**Solução**: Verifique o estado da transferência antes de processar o webhook

**Estados válidos**:

* `PENDING` → `CONFIRMED` ✅

* `PENDING` → `REJECTED` ✅

* `CONFIRMED` → `REJECTED` ❌

* `REJECTED` → `CONFIRMED` ❌

### Erro: "Connection refused" ao acessar o banco

**Causa**: PostgreSQL não está rodando

**Solução**: Inicie o PostgreSQL via Docker Compose

```bash
docker-compose up -d
```

**Verificar se está rodando**:

```bash
docker ps | grep postgres
```

### Erro: "Port 8080 already in use"

**Causa**: Outra aplicação está usando a porta 8080

**Solução**:

1. Pare a aplicação que está usando a porta

2. Ou mude a porta da aplicação no `application.yml`:

```yaml
server:
  port: 8081
```

### Aplicação lenta ou travando

**Causa**: Muitas conexões abertas ou locks não liberados

**Solução**:

1. Verifique o pool de conexões:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
```

1. Verifique locks no banco:

```sql
SELECT * FROM pg_stat_activity WHERE state = 'active';
```

### Logs não aparecem

**Causa**: Configuração de log incorreta

**Solução**: Verifique o `logback-spring.xml` e o nível de log no `application.yml`:

```yaml
logging:
  level:
    com.pixservice: DEBUG
```

---

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

## 👥 Autores

* **Seu Nome** - _Desenvolvimento inicial_ - [seu-github](https://github.com/LeandroRibeiro2018)

---

## 🙏 Agradecimentos

* Comunidade Spring Boot

* Documentação do PostgreSQL

* Banco Central do Brasil (especificações Pix)

* Todos que contribuíram com feedback e sugestões

---

## 📞 Contato

Para dúvidas, sugestões ou reportar problemas:

* **Email**: [devleandroribeiro@gmail.com](mailto:devleandroribeiro@gmail.com)

* **GitHub Issues**: [https://github.com/seu-usuario/carteira-pix](https://github.com/LeandroRibeiro2018/carteira-pix)

* **LinkedIn**: [Leandro Ribeiro](https://linkedin.com/in/leandro-ribeiro-dev)

---

**Desenvolvido com ❤️ e ☕ por Leandro Ribeiro**
