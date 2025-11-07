#!/bin/bash

# Script helper para gerenciar o fluxo de CI/CD
# Uso: ./deploy-helper.sh [comando]

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Funções auxiliares
print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "ℹ️  $1"
}

# Verificar se gh CLI está instalado
check_gh_cli() {
    if ! command -v gh &> /dev/null; then
        print_error "GitHub CLI (gh) não está instalado"
        print_info "Instale em: https://cli.github.com/"
        exit 1
    fi
}

# Comando: criar feature
cmd_create_feature() {
    print_info "Criando nova branch de feature..."
    
    read -p "Nome da feature: " feature_name
    
    git checkout dev
    git pull origin dev
    git checkout -b "feature/$feature_name"
    
    print_success "Feature branch criada: feature/$feature_name"
    print_info "Agora você pode desenvolver sua feature"
}

# Comando: promover para dev
cmd_promote_to_dev() {
    check_gh_cli
    
    current_branch=$(git branch --show-current)
    
    if [[ ! "$current_branch" =~ ^feature/ ]]; then
        print_error "Você deve estar em uma branch feature/*"
        exit 1
    fi
    
    print_info "Criando PR: $current_branch → dev"
    
    gh pr create \
        --base dev \
        --head "$current_branch" \
        --title "feat: $(echo $current_branch | sed 's/feature\///')" \
        --body "## Descrição\n\n[Descreva as mudanças]\n\n## Checklist\n\n- [ ] Testes adicionados\n- [ ] Documentação atualizada"
    
    print_success "Pull Request criado!"
}

# Comando: promover para homolog
cmd_promote_to_homolog() {
    check_gh_cli
    
    print_info "Promovendo dev → homolog"
    
    git checkout dev
    git pull origin dev
    
    gh pr create \
        --base homolog \
        --head dev \
        --title "chore: promover dev para homolog" \
        --body "## Mudanças\n\nPromovendo alterações testadas de dev para homolog"
    
    print_success "Pull Request criado!"
    print_warning "Aguarde aprovação e merge automático"
}

# Comando: promover para produção
cmd_promote_to_prod() {
    check_gh_cli
    
    print_warning "⚠️  ATENÇÃO: Você está prestes a promover para PRODUÇÃO!"
    read -p "Tem certeza? (sim/não): " confirm
    
    if [[ "$confirm" != "sim" ]]; then
        print_info "Operação cancelada"
        exit 0
    fi
    
    print_info "Promovendo homolog → main (produção)"
    
    git checkout homolog
    git pull origin homolog
    
    version=$(date +%Y.%m.%d)
    
    gh pr create \
        --base main \
        --head homolog \
        --title "Release v$version" \
        --body "## Release v$version\n\n### Mudanças\n\n[Liste as principais mudanças]\n\n### Checklist\n\n- [ ] Testes passando\n- [ ] Aprovado em homologação\n- [ ] Documentação atualizada\n- [ ] Changelog atualizado"
    
    print_success "Pull Request de release criado!"
    print_warning "Requer 2 aprovações antes do merge"
}

# Comando: listar rollbacks disponíveis
cmd_list_rollbacks() {
    print_info "Branches de rollback disponíveis:"
    git branch -r | grep rollback/prod | sed 's/origin\///' | sort -r
}

# Comando: executar rollback
cmd_rollback() {
    check_gh_cli
    
    print_warning "⚠️  ROLLBACK - Use apenas em caso de emergência!"
    
    cmd_list_rollbacks
    
    echo ""
    read -p "Branch de rollback: " rollback_branch
    read -p "Motivo do rollback: " reason
    
    print_warning "Executando rollback via GitHub Actions..."
    
    gh workflow run rollback.yml \
        -f rollback_branch="$rollback_branch" \
        -f reason="$reason"
    
    print_success "Workflow de rollback iniciado!"
    print_info "Acompanhe em: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions"
}

# Comando: status do pipeline
cmd_status() {
    check_gh_cli
    
    print_info "Status dos últimos workflows:"
    gh run list --limit 5
}

# Comando: verificar ambiente
cmd_check_env() {
    read -p "Ambiente (dev/homolog/prod): " env
    
    case $env in
        dev)
            url="https://dev.pixwallet.example.com"
            ;;
        homolog)
            url="https://homolog.pixwallet.example.com"
            ;;
        prod)
            url="https://pixwallet.example.com"
            ;;
        *)
            print_error "Ambiente inválido"
            exit 1
            ;;
    esac
    
    print_info "Verificando $env em $url..."
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url/actuator/health")
    
    if [[ "$response" == "200" ]]; then
        print_success "Ambiente $env está saudável (HTTP $response)"
    else
        print_error "Ambiente $env com problemas (HTTP $response)"
    fi
}

# Comando: ajuda
cmd_help() {
    cat << EOF
🚀 Deploy Helper - Carteira Pix

Uso: ./deploy-helper.sh [comando]

Comandos disponíveis:

  create-feature      Criar nova branch de feature
  promote-dev         Promover feature para dev
  promote-homolog     Promover dev para homolog
  promote-prod        Promover homolog para produção
  list-rollbacks      Listar branches de rollback disponíveis
  rollback            Executar rollback em produção
  status              Ver status dos workflows
  check-env           Verificar saúde de um ambiente
  help                Mostrar esta ajuda

Exemplos:

  # Iniciar nova feature
  ./deploy-helper.sh create-feature

  # Promover para dev
  ./deploy-helper.sh promote-dev

  # Ver status
  ./deploy-helper.sh status

  # Rollback (emergência)
  ./deploy-helper.sh rollback

Para mais informações, consulte: CI-CD-GUIDE.md
EOF
}

# Main
case "${1:-help}" in
    create-feature)
        cmd_create_feature
        ;;
    promote-dev)
        cmd_promote_to_dev
        ;;
    promote-homolog)
        cmd_promote_to_homolog
        ;;
    promote-prod)
        cmd_promote_to_prod
        ;;
    list-rollbacks)
        cmd_list_rollbacks
        ;;
    rollback)
        cmd_rollback
        ;;
    status)
        cmd_status
        ;;
    check-env)
        cmd_check_env
        ;;
    help|*)
        cmd_help
        ;;
esac
