# 🇧🇷 Guia Rápido - Serviço de Carteira Pix

## 📋 Resumo

Microserviço de carteira digital com suporte a transferências Pix, desenvolvido em Java 17 com Spring Boot.

## 📚 Documentação

- **[DOCUMENTATION.md](DOCUMENTATION.md)** - Documentação técnica completa
- **[FAQ.md](FAQ.md)** - Perguntas frequentes e respostas detalhadas
- **[ANALYSIS_REPORT.md](ANALYSIS_REPORT.md)** - Análise de código e correções
- **[CI-CD-GUIDE.md](CI-CD-GUIDE.md)** - Guia do pipeline CI/CD

## 🚀 Como Executar

### 1. Iniciar o Banco de Dados

```bash
docker-compose up -d
```

### 2. Executar a Aplicação

**Opção A - Via VS Code:**
- Abra `PixWalletServiceApplication.java`
- Clique com botão direito → "Run Java"

**Opção B - Via Maven:**
```bash
mvn spring-boot:run
```

### 3. Verificar Saúde

```bash
curl http://localhost:8080/actuator/health
```

## 📝 Fluxo Completo de Teste

### 1. Criar Carteiras

```bash
# Criar carteira Alice
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "alice"}'

# Criar carteira Bob
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"userId": "bob"}'
```

### 2. Cadastrar Chave Pix para Bob

```bash
curl -X POST http://localhost:8080/wallets/{walletId_bob}/pix-keys \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "EMAIL",
    "keyValue": "bob@example.com"
  }'
```

### 3. Adicionar Saldo para Alice

```bash
curl -X POST http://localhost:8080/wallets/{walletId_alice}/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 1000.00}'
```

### 4. Consultar Saldo

```bash
curl http://localhost:8080/wallets/{walletId_alice}/balance
```

### 5. Realizar Transferência Pix

```bash
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromWalletId": "{walletId_alice}",
    "toPixKey": "bob@example.com",
    "amount": 150.00
  }'
```

### 6. Confirmar Transferência via Webhook

```bash
curl -X POST http://localhost:8080/pix/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "$(uuidgen)",
    "endToEndId": "{endToEndId}",
    "eventType": "CONFIRMED",
    "occurredAt": "2025-11-07T10:00:00Z"
  }'
```

## 📦 Usar Postman

Importe a collection em português:
- **Arquivo:** `postman_collection_ptbr.json`
- Collection organizada com exemplos de todos os endpoints
- Variáveis automáticas para facilitar testes

## 🎯 Endpoints Principais

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/wallets` | Criar carteira |
| POST | `/wallets/{id}/pix-keys` | Cadastrar chave Pix |
| GET | `/wallets/{id}/balance` | Consultar saldo |
| POST | `/wallets/{id}/deposit` | Realizar depósito |
| POST | `/wallets/{id}/withdraw` | Realizar saque |
| POST | `/pix/transfers` | Transferir via Pix |
| POST | `/pix/webhook` | Webhook de confirmação/rejeição |
| GET | `/actuator/health` | Status da aplicação |

## 🔑 Headers Importantes

- **Content-Type:** `application/json`
- **Idempotency-Key:** Obrigatório em operações de depósito, saque e transferência

## ⚠️ Mensagens de Erro em Português

Todas as mensagens de erro e validação estão em português do Brasil:

```json
{
  "message": "Saldo insuficiente. Saldo atual: 100.00, Necessário: 150.00",
  "status": 400
}
```

```json
{
  "message": "Carteira não encontrada: abc-123",
  "status": 404
}
```

```json
{
  "message": "Chave Pix já cadastrada: bob@example.com",
  "status": 409
}
```

## 🌍 Internacionalização

A aplicação está configurada com:
- **Locale padrão:** pt-BR
- **Encoding:** UTF-8
- **Timezone:** UTC
- **Arquivos de mensagens:** `messages.properties`

## 📚 Documentação Completa

Para mais detalhes, consulte:
- **DOCUMENTATION.md** - Documentação técnica completa
- **ANALYSIS_REPORT.md** - Relatório de análise e correções

## 🛠️ Configurações

### Banco de Dados
- **Host:** localhost
- **Porta:** 5432
- **Database:** pixwallet
- **Usuário:** pixuser
- **Senha:** pixpass

### Aplicação
- **Porta:** 8080
- **Perfil:** default

## ✅ Checklist de Testes

- [ ] Criar duas carteiras
- [ ] Cadastrar chave Pix
- [ ] Fazer depósito
- [ ] Consultar saldo
- [ ] Fazer transferência Pix
- [ ] Confirmar transferência via webhook
- [ ] Testar idempotência (mesma chave)
- [ ] Testar webhook duplicado
- [ ] Testar saldo insuficiente
- [ ] Consultar saldo histórico

## 🎓 Conceitos Implementados

- ✅ Clean Architecture
- ✅ Pessimistic Locking
- ✅ Optimistic Locking
- ✅ Idempotência
- ✅ Ledger Imutável
- ✅ State Machine
- ✅ Exactly-Once Semantics
- ✅ Auditoria Completa

## 📞 Suporte

Em caso de dúvidas:
1. Consulte a documentação completa
2. Verifique os logs da aplicação
3. Use a collection do Postman para exemplos

---

**Desenvolvido com ❤️ em Java + Spring Boot**
