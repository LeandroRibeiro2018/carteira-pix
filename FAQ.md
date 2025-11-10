# FAQ - Perguntas Frequentes

## Questoes sobre Arquitetura

### Q1: Por que Clean Architecture?

**R:** Clean Architecture foi escolhida para:
- Separar logica de negocio de infraestrutura
- Facilitar testes unitarios sem dependencias externas
- Permitir evolucao independente de cada camada
- Garantir que regras de negocio nao dependam de frameworks

### Q2: Por que PostgreSQL ao inves de MySQL?

**R:** PostgreSQL oferece:
- ACID completo e robusto
- Melhor suporte a locks (pessimistic, row-level)
- Performance superior para operacoes transacionais
- Tipos de dados mais ricos (JSONB, UUID nativo)
- Conformidade com padroes SQL

### Q3: Por que nao usar MongoDB?

**R:** Para sistemas financeiros, transacoes ACID multi-documento sao essenciais. MongoDB historicamente tinha limitacoes nesse aspecto, e PostgreSQL e mais maduro para esse caso de uso.

---

## Questoes sobre Consistencia e Concorrencia

### Q4: Como o sistema garante exactly-once em transferencias?

**R:** Atraves de multiplos mecanismos:
1. **Idempotency-Key** unica por transferencia
2. **Pessimistic locking** ao modificar saldo
3. **Constraint unica** em banco de dados
4. **Transacoes ACID** garantindo atomicidade

### Q5: O que acontece se duas requisicoes com mesmo Idempotency-Key chegarem simultaneamente?

**R:** Uma sera processada, a outra recebera o resultado da primeira:
1. Constraint unica no banco garante apenas uma insercao
2. A que perder a "corrida" verifica registro existente
3. Retorna o mesmo resultado (idempotente)

### Q6: Como evitar race conditions em operacoes de saldo?

**R:** Usando **pessimistic locking** (`SELECT FOR UPDATE`):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Wallet> findByIdWithLock(UUID id);
```

Isso garante que apenas uma thread por vez pode modificar uma carteira.

### Q7: Por que pessimistic lock e nao optimistic?

**R:** Para operacoes financeiras, preferimos **consistencia** sobre **performance**:
- Pessimistic: Garante exclusao mutua desde o inicio
- Optimistic: Pode causar muitos retries sob alta concorrencia
- Trade-off consciente: melhor para operacoes criticas

---

## Questoes sobre Webhooks Pix

### Q8: O que acontece se webhook REJECTED chegar antes de CONFIRMED?

**R:** A **state machine** valida transicoes:
- Se transferencia ja esta CONFIRMED, rejeitar lanca exception
- Se transferencia esta PENDING, ambos sao validos
- Garante consistencia independente da ordem

### Q9: Como garantir que webhook duplicado nao credite duas vezes?

**R:** Atraves de **deduplicacao por eventId**:
```java
if (webhookEventRepository.existsByEventId(eventId)) return; // Idempotente
```

Primeiro webhook processa, demais sao ignorados.

### Q10: E se recebermos webhook para uma transferencia inexistente?

**R:** Use case lanca `ApplicationException` com mensagem clara:
```
"Pix transfer not found: E123456..."
```

HTTP 400 e retornado ao chamador.

### Q13: Como simular uma rejeicao de Pix?

**R:** Enviar webhook com `eventType: REJECTED`:
```bash
curl -X POST http://localhost:8080/pix/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "unique-event-id",
    "endToEndId": "E1730512345678ABC",
    "eventType": "REJECTED",
    "occurredAt": "2025-11-07T10:05:00Z"
  }'
```

Isso ira:
1. Estornar o valor para a carteira de origem
2. Marcar transferencia como REJECTED
3. Criar transacao de estorno no ledger

---

## Questoes sobre Auditoria

### Q11: Por que usar ledger imutavel (append-only)?

**R:** Beneficios:
- **Auditoria completa**: Toda operacao registrada permanentemente
- **Saldo historico**: Reconstruir saldo em qualquer timestamp
- **Compliance**: Atende requisitos regulatorios (BACEN)
- **Deteccao de fraudes**: Discrepancias sao detectaveis

### Q12: Como calcular saldo historico?

**R:** Somando todas transacoes ate o timestamp:
```sql
SELECT SUM(amount) 
FROM transactions 
WHERE wallet_id = ? AND created_at <= ?
```

---

## Questoes sobre Operacoes

### Q14: Como monitorar a aplicacao?

**R:** Use os endpoints Actuator:
- `/actuator/health` - Health check
- `/actuator/metrics` - Metricas gerais
- `/actuator/prometheus` - Metricas para Prometheus
- `/actuator/info` - Informacoes da aplicacao

### Q15: Como fazer backup do banco de dados?

**R:** PostgreSQL:
```bash
# Backup
docker exec -t pix-wallet-postgres pg_dump -U pixuser pixwallet > backup.sql

# Restore
docker exec -i pix-wallet-postgres psql -U pixuser pixwallet < backup.sql
```

---

## Questoes sobre Testes

### Q16: Como rodar testes localmente?

**R:** 
```bash
# Testes unitarios
mvn test

# Testes de integracao
mvn verify -P integration-tests

# Todos os testes
mvn clean verify
```

### Q17: Como rodar apenas um teste especifico?

**R:**
```bash
mvn test -Dtest=WalletTest#shouldCreateWallet
```

### Q18: Por que os testes usam H2 em memoria?

**R:** Para:
- Velocidade: Testes rodam mais rapido
- Isolamento: Cada teste tem banco limpo
- Simplicidade: Nao precisa configurar PostgreSQL para testes
- CI/CD: Funciona sem dependencias externas

---

## Questoes sobre Deploy

### Q19: Como fazer deploy em producao?

**R:** Consulte o arquivo `DEPLOYMENT_GUIDE.md` para instrucoes completas de deploy.

### Q20: Quais variaveis de ambiente sao necessarias?

**R:**
- `DATABASE_URL` - URL do PostgreSQL
- `DATABASE_USER` - Usuario do banco
- `DATABASE_PASSWORD` - Senha do banco
- `SPRING_PROFILES_ACTIVE` - Perfil (prod, test, dev)

### Q21: Como configurar HTTPS?

**R:** Adicione no `application-prod.yml`:
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
    key-store-type: PKCS12
```

---

## Questoes sobre Seguranca

### Q22: Como proteger a API?

**R:** Implemente:
- Spring Security com JWT
- Rate limiting (Bucket4j)
- HTTPS obrigatorio
- Validacao de input
- CORS configurado corretamente

### Q23: Como mascarar dados sensiveis nos logs?

**R:** Crie funcoes de mascaramento:
```java
private String maskPixKey(String pixKey) {
    if (pixKey.length() <= 4) return "****";
    return pixKey.substring(0, 4) + "****";
}
```

---

## Questoes sobre Performance

### Q24: Como melhorar performance em alta concorrencia?

**R:**
- Use cache (Redis) para consultas frequentes
- Configure connection pool adequadamente
- Adicione indices no banco de dados
- Use optimistic locking onde possivel
- Configure timeouts apropriados

### Q25: Qual o throughput esperado?

**R:** Depende da infraestrutura, mas configuracao basica suporta:
- ~1000 operacoes de saldo/segundo
- ~500 transferencias/segundo
- ~2000 consultas/segundo

---

## Questoes sobre Troubleshooting

### Q26: Como debugar uma transferencia que falhou?

**R:**
1. Busque nos logs pelo `idempotencyKey`
2. Verifique tabela `idempotency_records`
3. Verifique tabela `pix_transfers`
4. Verifique tabela `transactions` (ledger)
5. Verifique tabela `webhook_events`

### Q27: O que fazer se houver divergencia de saldo?

**R:**
1. Calcule saldo pela tabela `transactions`:
```sql
SELECT SUM(amount) FROM transactions WHERE wallet_id = ?
```
2. Compare com `wallets.current_balance`
3. Se diferente, ha inconsistencia que precisa investigacao

### Q28: Como identificar deadlocks?

**R:**
```sql
SELECT * FROM pg_locks WHERE NOT granted;
SELECT * FROM pg_stat_activity WHERE state = 'active';
```

---

## Questoes sobre Compliance

### Q29: O sistema atende LGPD?

**R:** Parcialmente. Necessario:
- Implementar consentimento de dados
- Mascarar dados sensiveis em logs
- Implementar direito ao esquecimento
- Adicionar auditoria de acesso

### Q30: Como garantir auditoria completa?

**R:** O sistema ja possui:
- Ledger imutavel de transacoes
- Timestamps em todas operacoes
- Idempotency keys para rastreamento
- Webhook events registrados

---

**Ultima Atualizacao:** 10/11/2025
**Versao:** 1.0.0
