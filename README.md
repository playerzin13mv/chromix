# Chromix 🎹🌈

App de celular (modo horizontal) para criar **Chromatics de FNF** e exportar como **SoundFont (.sf2)**.

## O que ele faz
- **Importa** qualquer áudio (WAV, MP3, OGG, M4A...) ou **grava** pelo microfone
- **Corta** o trecho que você quer e define um **loop** (com botão "Suavizar loop" para emendar sem estalo)
- **Detecta a nota** do som automaticamente (e você pode ajustar a nota raiz e a afinação fina)
- **Modo vogais (A, E, I, O e U opcional)**: grave ou importe um áudio para cada vogal e elas se repetem pelo teclado todo (com 4 vogais: Dó = A, Dó# = E, Ré = I, Ré# = O, Mi = A de novo...; com o U, a sequência passa a ter 5 vogais). Cada vogal tem seu próprio corte, loop e nota raiz, e fica com o volume nivelado na exportação
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

## Como usar o modo vogais
1. Dentro do projeto, toque em **Modo vogais** (acima da onda). Aparecem os botões **A E I O U** (o U é opcional).
2. Escolha a vogal **A**, depois **Gravar** (ao parar, o app já seleciona a próxima vogal para você gravar) ou **Importar áudio**.
3. Dá para **importar vários áudios de uma vez**: eles vão para as vogais em ordem, a partir da selecionada (1º arquivo = A, 2º = E...).
4. Cada vogal tem seu próprio corte, loop e nota raiz (use **Detectar nota** em cada uma). Os pontinhos ● mostram quais já têm áudio.
5. No piano, a tecla mostra qual vogal está tocando. Use **Só esta vogal** para ouvir e afinar uma vogal isolada.
6. **Exportar .sf2**: o arquivo sai com a mesma sequência de vogais do piano.
7. **Voz de exemplo** no modo vogais carrega A, E, I, O sintéticas nas vogais vazias, para você testar sem gravar.

## Dicas
- Para um "sample de voz" (tipo um "ah" ou "oh"), o loop deixa o som segurar como um instrumento.
- Para sons curtos (tipo um "hey"), deixe o loop desligado: cada tecla toca o som inteiro no tom da tecla.
- Se o arquivo estiver grande, use **Nota única**, reduza a faixa de notas ou diminua a taxa de amostragem.
- Se o som do piano falhar, aumente a latência em Configurações.

## Detalhes técnicos
Android nativo (Java, sem bibliotecas externas), minSdk 26 (Android 8+), orientação horizontal. O .sf2 (SoundFont 2.01) é gerado pelo próprio app, com preset único (banco 0, preset 0).
