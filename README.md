# Chromix 🎹🌈

App de celular (modo horizontal) para criar **Chromatics de FNF** e exportar como **SoundFont (.sf2)**.

## O que ele faz
- **Importa** qualquer áudio (WAV, MP3, OGG, M4A...) ou **grava** pelo microfone
- **Corta** o trecho que você quer e define um **loop** (com botão "Suavizar loop" para emendar sem estalo)
- **Detecta a nota** do som automaticamente (e você pode ajustar a nota raiz e a afinação fina)
- **Piano para testar** a Chromatic na hora (multitoque, glissando, cada tecla acende numa cor)
- **Escala cromática automática** para ouvir a sequência completa
- **Envelope** (ataque e liberação), volume e normalização
- **Exporta .sf2** em dois modos:
  - *Nota única*: arquivo pequeno, o programa muda o tom
  - *Uma amostra por nota*: cada tecla com seu áudio já afinado (mais fiel, arquivo maior)
- **Lista de projetos** (renomear, duplicar, excluir), salvos automaticamente
- **Configurações**: tema escuro/claro/sistema, nomes das notas (C D E ou Dó Ré Mi), oitavas do piano, latência, volume, vibração, padrões de exportação, autor do .sf2

## Como instalar (tudo pelo celular, no Termux)

1. Coloque o `Chromix.zip` na pasta Downloads e, no Termux:
   ```bash
   termux-setup-storage          # só na primeira vez
   cd ~
   unzip ~/storage/downloads/Chromix.zip
   cd Chromix
   bash enviar.sh chromix
   ```
   (o `enviar.sh` cria o repositório no seu GitHub e envia tudo; se o `gh` não estiver instalado, ele pede a URL de um repositório vazio)

2. No GitHub, abra o repositório > aba **Actions**. Espere o build **Build Chromix APK** ficar verde (uns 3 a 6 minutos).

3. Vá em **Releases** (lado direito do repositório) > **Chromix (última versão)** > baixe o **Chromix.apk** e instale.
   (Se o Android perguntar, permita "instalar apps desconhecidos" para o navegador.)

Para atualizar o app depois: troque os arquivos e rode `bash enviar.sh` de novo.

## Como usar
1. **Novo projeto** > dê um nome.
2. **Importar áudio** (ou **Gravar**, ou **Voz de exemplo** para testar).
3. Em **Cortar**, arraste as barras laranjas para deixar só o trecho do som.
4. Confira a **nota raiz** (use **Detectar nota** se não souber) e teste no piano.
5. Se quiser que a nota segure enquanto o dedo está na tecla, ligue o **loop** e arraste as barras verdes na aba **Loop**.
6. Toque em **Exportar .sf2** e escolha onde salvar.

## Dicas
- Para um "sample de voz" (tipo um "ah" ou "oh"), o loop deixa o som segurar como um instrumento.
- Para sons curtos (tipo um "hey"), deixe o loop desligado: cada tecla toca o som inteiro no tom da tecla.
- Se o arquivo estiver grande, use **Nota única**, reduza a faixa de notas ou diminua a taxa de amostragem.
- Se o som do piano falhar, aumente a latência em Configurações.

## Detalhes técnicos
Android nativo (Java, sem bibliotecas externas), minSdk 26 (Android 8+), orientação horizontal. O .sf2 (SoundFont 2.01) é gerado pelo próprio app, com preset único (banco 0, preset 0).
