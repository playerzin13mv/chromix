#!/data/data/com.termux/files/usr/bin/bash
# Envia o projeto Chromix para o seu GitHub (use dentro da pasta Chromix)
set -e
NOME="${1:-chromix}"

if [ ! -d .git ]; then
  git init -b main
fi
git add -A
git commit -m "Chromix: atualização" || echo "(nada novo para commitar)"
git branch -M main

if git remote get-url origin >/dev/null 2>&1; then
  git push -u origin main
elif command -v gh >/dev/null 2>&1; then
  gh repo create "$NOME" --public --source=. --remote=origin --push
else
  echo "Crie um repositório vazio chamado '$NOME' no GitHub e cole a URL dele:"
  read -r URL
  git remote add origin "$URL"
  git push -u origin main
fi

echo
echo "Pronto! Agora abra o GitHub > aba Actions e espere o build ficar verde."
echo "Depois baixe o Chromix.apk em Releases."
