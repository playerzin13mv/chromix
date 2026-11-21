#!/data/data/com.termux/files/usr/bin/bash
# Envia o projeto Chromix para o GitHub (rode dentro da pasta Chromix).
# Usa o repositório que já existe e mantém o histórico dele (não apaga nada).
set -e
GH_USER="playerzin13mv"
NOME="${1:-chromix}"
URL="${REMOTE_URL:-https://github.com/$GH_USER/$NOME.git}"

if [ ! -d .git ]; then
  git init -b main
fi
git branch -M main

# usa as credenciais do gh (se estiver instalado)
if command -v gh >/dev/null 2>&1; then
  gh auth setup-git >/dev/null 2>&1 || true
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$URL"
else
  git remote add origin "$URL"
fi

# traz o histórico que já está no GitHub e coloca as suas mudanças por cima
if git fetch origin main; then
  git reset --soft origin/main
fi

git add -A
git commit -m "Chromix: atualização" || echo "(nada novo para commitar)"
git push -u origin main

echo
echo "Pronto! Agora abra o GitHub > aba Actions e espere o build ficar verde."
echo "Depois baixe o Chromix.apk em Releases."
