# 🚀 Guia CI/CD - Carteira Pix

## 📋 Visão Geral

Este projeto utiliza **GitHub Actions** para automatizar a validação de código através de Pull Requests. O workflow executa **apenas testes** e não realiza builds Docker ou deploys automáticos.

## 🔄 Fluxo de Trabalho

### Estrutura de Branches

```
feature/* ──► dev ──► homolog ──► main
   │           │         │          │
   └─ Desenvolvimento    │     Produção
                    Homologação
```

### Regras de Fluxo

1. **feature/\* → dev**: Novas funcionalidades e correções
2. **dev → homolog**: Promoção para homologação após testes em dev
3. **homolog → main**: Promoção para produção após validação em homolog

⚠️ **IMPORTANTE**: PRs só são aceitos seguindo este fluxo!

## 🧪 Pipeline de Validação

O pipeline é **disparado automaticamente** quando um Pull Request é:
- Aberto (`opened`)
- Atualizado (`synchronize`)
- Reaberto (`reopened`)
- Marcado como pronto para revisão (`ready_for_review`)

### Jobs Executados

#### 1️⃣ Validação do Fluxo de Branches
- ✅ Verifica se o PR segue o fluxo correto
- ✅ Valida a branch de origem e destino
- ⏱️ Duração: ~5 segundos

#### 2️⃣ Execução de Testes
- ✅ Testes unitários (`mvn test`)
- ✅ Testes de integração (`mvn verify`)
- ✅ Relatório de cobertura (JaCoCo)
- 🐘 PostgreSQL 15 (container de teste)
- ⏱️ Duração: ~2-5 minutos

#### 3️⃣ Análise de Qualidade
- ✅ Checkstyle (análise estática)
- ✅ SpotBugs (detecção de bugs)
- ⏱️ Duração: ~1-2 minutos

#### 4️⃣ Resumo e Aprovação
- ✅ Consolida resultados de todos os jobs
- ✅ Gera relatório final
- ✅ Notifica sucesso ou falha

## 🎯 Como Usar

### 1. Criar uma Feature Branch

```bash
# Certificar-se de estar na branch dev atualizada
git checkout dev
git pull origin dev

# Criar nova feature
git checkout -b feature/minha-funcionalidade
```

### 2. Desenvolver e Commitar

```bash
# Fazer alterações no código
git add .
git commit -m "feat: adicionar nova funcionalidade"
```

### 3. Enviar para o GitHub

```bash
# Push da feature branch
git push -u origin feature/minha-funcionalidade
```

### 4. Abrir Pull Request

1. Acesse o repositório no GitHub
2. Clique em **"Compare & pull request"**
3. **Base branch**: `dev`
4. **Compare branch**: `feature/minha-funcionalidade`
5. Preencha:
   - **Título**: Descrição clara e objetiva
   - **Descrição**: Detalhes das mudanças, motivação, impacto
6. Clique em **"Create pull request"**

### 5. Aguardar Validação Automática

O GitHub Actions irá:
- ⚙️ Validar o fluxo de branches
- 🧪 Executar todos os testes
- 🔍 Analisar qualidade do código
- 📊 Gerar relatório de cobertura

**Status possíveis:**
- ✅ **Checks passed**: Tudo OK, pronto para revisão
- ❌ **Checks failed**: Corrija os erros e faça novo push

### 6. Revisão Manual

Após aprovação automática:
- 👥 Solicitar revisão de código de um colega
- 💬 Responder aos comentários
- ✏️ Fazer ajustes se necessário

### 7. Merge do PR

Após aprovação manual:
- ✅ Clicar em **"Merge pull request"**
- 🎯 Escolher estratégia: **"Squash and merge"** (recomendado)
- 🗑️ Deletar a branch feature após merge

## 🔐 Configuração de Ambientes (GitHub)

### Ambientes Necessários

Configure em: **Settings → Environments**

#### 1. Development
- **Nome**: `development`
- **URL**: `https://dev.pixwallet.example.com`
- **Proteção**: Nenhuma (deploy automático)

#### 2. Homologation
- **Nome**: `homologation`
- **URL**: `https://homolog.pixwallet.example.com`
- **Proteção**: 
  - ✅ Required reviewers: 1 pessoa
  - ⏱️ Wait timer: 0 minutos

#### 3. Production
- **Nome**: `production`
- **URL**: `https://pixwallet.example.com`
- **Proteção**:
  - ✅ Required reviewers: 2 pessoas
  - ⏱️ Wait timer: 5 minutos
  - 🔒 Branch restriction: apenas `main`

### Como Configurar Revisores (GRATUITO)

GitHub Free permite configurar **Environment protection rules**:

1. Acesse **Settings → Environments**
2. Clique no ambiente (ex: `production`)
3. Marque **"Required reviewers"**
4. Adicione os revisores (até 6 no plano gratuito)
5. Salve as configurações

Quando um deploy para produção for iniciado:
- 🔔 Revisores receberão notificação
- ⏸️ Deploy ficará aguardando aprovação
- ✅ Após aprovação, deploy prossegue
- ❌ Se rejeitado, deploy é cancelado

## 📊 Monitoramento

### Visualizar Execuções

1. Acesse a aba **"Actions"** no GitHub
2. Selecione o workflow **"🧪 Validação de Pull Request"**
3. Clique na execução desejada
4. Visualize os logs de cada job

### Artefatos Gerados

- **Relatório de Cobertura**: Disponível para download por 30 dias
- **Logs de Testes**: Visualize falhas e erros diretamente nos logs

## 🐛 Troubleshooting

### ❌ Testes Falhando

```bash
# Rodar testes localmente
mvn clean test

# Ver logs detalhados
mvn test -X

# Testar com PostgreSQL local
docker-compose up -d postgres
mvn test
```

### ❌ Fluxo de Branches Inválido

```
Erro: PRs para 'dev' devem vir de branches 'feature/*'
```

**Solução**: Certifique-se de estar criando a feature a partir da branch correta:
```bash
git checkout dev
git pull origin dev
git checkout -b feature/nome-da-feature
```

### ❌ Compilação Falhando

```bash
# Limpar cache do Maven
mvn clean

# Recompilar
mvn clean compile

# Verificar dependências
mvn dependency:tree
```

## 📝 Boas Práticas

### Commits

Use **Conventional Commits**:
```
feat: adicionar endpoint de consulta de saldo
fix: corrigir validação de CPF
chore: atualizar dependências
docs: atualizar documentação da API
test: adicionar testes unitários para Wallet
refactor: simplificar lógica de transferência
```

### Pull Requests

✅ **Bom PR**:
- Título claro e objetivo
- Descrição detalhada
- Mudanças focadas e pequenas
- Testes incluídos
- Sem conflitos

❌ **PR problemático**:
- Título vago ("fix bugs")
- Sem descrição
- Muitas mudanças não relacionadas
- Sem testes
- Conflitos de merge

### Branches

✅ **Nomes claros**:
```
feature/adicionar-webhook-pix
feature/melhorar-validacao-cpf
fix/corrigir-calculo-saldo
```

❌ **Nomes vagos**:
```
feature/update
fix/bug
test123
```

## 🚀 Próximos Passos

### Deploy Manual (Futuro)

Quando estiver pronto para implementar deploys automáticos:

1. **Configurar Secrets**:
   - `DOCKER_USERNAME`
   - `DOCKER_PASSWORD`
   - Chaves SSH para servidores
   - Tokens de API

2. **Adicionar Job de Build Docker**:
   - Build da imagem
   - Push para Docker Hub
   - Versionamento com tags

3. **Adicionar Jobs de Deploy**:
   - Deploy para dev (automático)
   - Deploy para homolog (com aprovação)
   - Deploy para prod (com aprovação + rollback)

## 📚 Recursos Adicionais

- [GitHub Actions Documentation](https://docs.github.com/actions)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [JaCoCo Coverage](https://www.jacoco.org/jacoco/trunk/doc/)
- [Conventional Commits](https://www.conventionalcommits.org/)

## 🆘 Suporte

Problemas com o CI/CD?
1. Verifique os logs no GitHub Actions
2. Execute os testes localmente
3. Consulte a documentação do projeto
4. Abra uma issue no repositório

---

✅ **Pipeline configurado e pronto para uso!**
🧪 **Testes executados automaticamente em cada PR**
📊 **Relatórios de cobertura disponíveis**
🔒 **Aprovações manuais configuráveis (GitHub Free)**
