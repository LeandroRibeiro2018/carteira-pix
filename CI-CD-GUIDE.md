# 🚀 Guia de CI/CD - Carteira Pix

## 📋 Visão Geral

Este projeto utiliza GitHub Actions para implementar um pipeline completo de CI/CD com o seguinte fluxo:

```
feature/* → dev → homolog → main (produção)
                                   ↓
                            rollback branches
```

## 🌳 Estratégia de Branches

### Branches Principais

| Branch | Ambiente | Proteção | Deploy Automático |
|--------|----------|----------|-------------------|
| `feature/*` | - | ❌ Não | ❌ Não |
| `dev` | Development | ✅ Sim | ✅ Sim |
| `homolog` | Homologação | ✅✅ Sim | ✅ Sim |
| `main` | Produção | ✅✅✅ Sim | ✅ Sim |
| `rollback/*` | Produção | 🔒 Somente Leitura | - |

### Fluxo de Trabalho

#### 1. Desenvolvimento de Feature

```bash
# Criar branch de feature a partir de dev
git checkout dev
git pull origin dev
git checkout -b feature/nome-da-feature

# Desenvolver e commitar
git add .
git commit -m "feat: implementar nova funcionalidade"

# Push para GitHub
git push origin feature/nome-da-feature
```

**O que acontece:**
- ✅ Pipeline executa testes automaticamente
- ✅ Valida qualidade do código
- ✅ Gera relatório de cobertura

#### 2. Promoção para DEV

```bash
# Criar Pull Request: feature/* → dev
# Via GitHub UI ou:
gh pr create --base dev --head feature/nome-da-feature
```

**O que acontece:**
- ✅ Validação de testes
- ✅ Análise de código
- ✅ Build da aplicação
- ✅ Deploy automático em DEV após merge

#### 3. Promoção para HOMOLOG

```bash
# Opção A: Workflow manual
# GitHub → Actions → "Promover entre Ambientes"
# Source: dev, Target: homolog

# Opção B: Pull Request
gh pr create --base homolog --head dev
```

**O que acontece:**
- ✅ Validação completa de testes
- ✅ Build Docker image
- ✅ Deploy automático em HOMOLOG
- ✅ Testes de fumaça

#### 4. Promoção para PRODUÇÃO

```bash
# Pull Request: homolog → main
gh pr create --base main --head homolog --title "Release v1.0.0"
```

**O que acontece:**
- ✅ Validação rigorosa
- ✅ Requer 2 aprovações
- ✅ Build Docker image com tag de produção
- ✅ **Criação automática de branch de rollback**
- ✅ Deploy em produção com estratégia blue-green
- ✅ Verificação de métricas
- ✅ Criação de tag de release

## 🔄 Rollback

### Quando Usar Rollback

Use rollback quando:
- ❌ Deploy causou problemas em produção
- ❌ Bugs críticos detectados
- ❌ Performance degradada
- ❌ Incidentes de segurança

### Como Executar Rollback

1. **Via GitHub Actions (Recomendado)**

```
GitHub → Actions → "Rollback para Produção" → Run workflow
- Selecionar branch: rollback/prod-20251107-120000
- Informar motivo: "Bug crítico no processo de transferência"
- Executar
```

2. **Manual (Emergência)**

```bash
# Listar branches de rollback disponíveis
git branch -r | grep rollback/prod

# Fazer rollback para versão anterior
git checkout rollback/prod-20251107-120000
git checkout -b hotfix/emergency-rollback
git push origin hotfix/emergency-rollback

# Criar PR para main
gh pr create --base main --head hotfix/emergency-rollback --title "🚨 ROLLBACK EMERGENCIAL"
```

**O que acontece no rollback:**
- ✅ Validação da branch de rollback
- ✅ Build da versão anterior
- ✅ Deploy da versão anterior
- ✅ Verificação de saúde
- ✅ Atualização da branch main
- ✅ Criação de issue de incidente
- ✅ Notificação da equipe

## 📊 Workflows Disponíveis

### 1. CI/CD Pipeline (Automático)
**Arquivo:** `.github/workflows/ci-cd-pipeline.yml`

**Triggers:**
- Push em: `feature/*`, `dev`, `homolog`, `main`
- Pull Request para: `dev`, `homolog`, `main`

**Jobs:**
1. ✅ Validação e Testes
2. 🔍 Análise de Qualidade
3. 🐳 Build Docker Image
4. 🚀 Deploy DEV
5. 🚀 Deploy HOMOLOG
6. 🚀 Deploy PRODUÇÃO
7. 📢 Notificações

### 2. Rollback (Manual)
**Arquivo:** `.github/workflows/rollback.yml`

**Como executar:**
```
Actions → Rollback para Produção → Run workflow
```

**Parâmetros:**
- `rollback_branch`: Branch de rollback
- `reason`: Motivo do rollback

### 3. Promover entre Ambientes (Manual)
**Arquivo:** `.github/workflows/promote.yml`

**Como executar:**
```
Actions → Promover entre Ambientes → Run workflow
```

**Parâmetros:**
- `source_branch`: dev ou homolog
- `target_branch`: homolog ou main
- `merge_strategy`: merge ou squash

### 4. Proteção de Branches (Automático)
**Arquivo:** `.github/workflows/branch-protection.yml`

**Triggers:**
- Pull Requests para `dev`, `homolog`, `main`

**Validações:**
- ✅ Origem do PR
- ✅ Título do PR (conventional commits)
- ✅ Cobertura de testes
- ✅ Número de aprovações

## 🔒 Regras de Proteção

### Branch `dev`
- ✅ Requer PR
- ✅ Requer 1 aprovação
- ✅ Requer testes passando
- ❌ Não permite force push
- ✅ Permite de: `feature/*`

### Branch `homolog`
- ✅ Requer PR
- ✅ Requer 1 aprovação
- ✅ Requer testes passando
- ✅ Requer análise de código
- ❌ Não permite force push
- ✅ Permite de: `dev`

### Branch `main`
- ✅ Requer PR
- ✅ Requer 2 aprovações
- ✅ Requer testes passando
- ✅ Requer análise de código
- ✅ Requer aprovação de code owners
- ❌ Não permite force push
- ✅ Permite apenas de: `homolog`
- ✅ Cria branch de rollback automaticamente

### Branches `rollback/*`
- 🔒 Somente leitura
- 🔒 Criadas automaticamente no deploy
- 🔒 Nunca devem ser deletadas
- 🔒 Usadas apenas para rollback

## 📈 Métricas e Monitoramento

### Métricas Coletadas

- ✅ Tempo de build
- ✅ Taxa de sucesso dos deploys
- ✅ Cobertura de testes
- ✅ Número de rollbacks
- ✅ Tempo médio de recovery

### Dashboards

- **GitHub Actions**: Ver histórico de execuções
- **Artifacts**: Logs, relatórios de cobertura, imagens Docker

## 🚨 Troubleshooting

### Pipeline falhou nos testes

```bash
# Rodar testes localmente
mvn clean test

# Ver logs detalhados
mvn test -X
```

### Deploy falhou

```bash
# Verificar logs do workflow
GitHub → Actions → Selecionar run → Ver logs

# Verificar saúde do ambiente
curl https://[ambiente].pixwallet.example.com/actuator/health
```

### Rollback necessário

```bash
# Ver branches de rollback disponíveis
git branch -r | grep rollback/prod

# Executar workflow de rollback via UI
GitHub → Actions → Rollback para Produção
```

## 📝 Convenções

### Mensagens de Commit

Seguir [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: adiciona nova funcionalidade
fix: corrige bug crítico
chore: atualiza dependências
docs: atualiza documentação
refactor: refatora código
test: adiciona testes
style: formatação de código
perf: melhoria de performance
ci: mudanças no CI/CD
```

### Nomenclatura de Branches

```
feature/nome-da-feature       # Nova funcionalidade
fix/nome-do-bug              # Correção de bug
chore/nome-da-tarefa         # Tarefas gerais
docs/nome-da-doc             # Documentação
hotfix/nome-do-hotfix        # Correção urgente em produção
```

### Tags de Release

```
v2025.11.07                  # Release de produção
v2025.11.07-hotfix.1        # Hotfix
```

## 🎯 Checklist de Deploy

### Antes do Deploy

- [ ] Todos os testes passando
- [ ] Code review aprovado
- [ ] Documentação atualizada
- [ ] Changelog atualizado
- [ ] Variáveis de ambiente configuradas
- [ ] Database migrations testadas

### Durante o Deploy

- [ ] Monitorar logs
- [ ] Verificar métricas
- [ ] Executar testes de fumaça
- [ ] Verificar saúde da aplicação

### Após o Deploy

- [ ] Verificar funcionalidades críticas
- [ ] Monitorar alertas
- [ ] Documentar mudanças
- [ ] Notificar stakeholders

## 📞 Suporte

Em caso de problemas:

1. Verificar logs do GitHub Actions
2. Consultar documentação
3. Contatar time de DevOps
4. Em emergência: executar rollback

---

**Última Atualização:** 07/11/2025
**Mantido por:** Equipe DevOps
