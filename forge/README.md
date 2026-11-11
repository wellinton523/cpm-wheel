# CPM Wheel (Minecraft 1.20.1 Forge, client-side)
Roda radial para organizar e tocar as animações do Customizable Player Models.
- Instalar: coloque cpmwheel-1.0.0.jar na pasta mods junto com o CPM (Forge 1.20.1, CPM 0.6.x recente).
- Tecla G (segurar): abre a roda; mova o mouse e solte (ou clique) para tocar. Scroll/Tab troca página, 1-9 atalhos, botão direito favorita.
- Botão "Editar": arraste animações da lista para os slots; nome, ícone (id de item), cor, valor, favorito, páginas.
- Config: config/cpmwheel/<id-do-modelo>.json (ou default.json).
- Ícones personalizados: coloque PNGs (qualquer tamanho, transparência mantida) em config/cpmwheel/icons/ (a pasta é criada sozinha). No editor, selecione um slot e clique em "Escolher ícone…": a grade mostra seus PNGs (borda ciana) e todos os itens, com busca. Botões "Abrir pasta de ícones" e "Recarregar"; arquivos novos/alterados também são recarregados sozinhos. No JSON fica icon: "file:nome.png" ou um id de item ("minecraft:diamond").
- Ao passar o mouse num slot, o ícone aparece grande no centro da roda.
- Sub-rodas (1.2.0): no editor, selecione um slot e clique "Criar sub-roda"; "Abrir sub-roda" entra nela e você arrasta animações para dentro (ou solte uma animação em cima do slot-pasta). Pode ter sub-rodas dentro de sub-rodas. Na roda: clique na pasta para entrar; botão direito fora de um slot, Esc, Backspace ou clique no centro voltam. O caminho aparece no topo.
- Animação ao abrir (1.2.0): a roda abre com zoom/fade (~150ms), o slot sob o mouse cresce suave, e há transição ao entrar/sair de sub-rodas. Desligue em Editar > "Animações".
- Centro da roda (1.2.1): Editar > "Centro: Jogador / Somente ícone / Nada" e o slider "Tamanho do centro" (50-200%) mudam o que aparece no meio e o tamanho do jogador/ícone.
- Prévia ao passar o mouse (1.2.1): DESLIGADA por padrão. O CPM não tem como animar uma cópia só local do modelo: qualquer animação tocada muda o seu modelo de verdade (e é sincronizada com o servidor). Por isso o jogador no centro é só uma vista do seu modelo atual. Se você ligar a prévia no botão da roda, ela toca a animação no seu modelo real enquanto o mouse está em cima e desfaz ao sair.
- Animações de valor (1.2.0): animações do CPM com valor (0-255 etc.) são detectadas sozinhas. Na roda, um arco mostra o valor atual e o % aparece acima; o scroll ajusta ao vivo (Shift = mais rápido), clique confirma. Modo "presets": cada clique vai pro próximo preset (ex.: 0,25,50,75,100 %). No editor: Tipo (auto/liga-desliga/slider/presets), Mín/Máx/Passo e lista de presets.
- Frames (1.3.0): para animações de valor com estados exatos (ex.: bracelete com 8 cores = emoções). No editor, Tipo: "frames" e "Editar frames (N)…": quantidade de frames, nome de cada um, valor exato do CPM (vazio = distribuído entre Mín e Máx) e cor. Na roda: scroll anda exatamente 1 frame, clique vai pro próximo, e os segmentinhos em volta do slot são um por frame (passe o mouse para ver o nome, clique para escolher aquele). O nome do frame aparece grande, com "frame k/N" e o valor.
- HUD (1.3.0): uma caixinha no jogo normal mostra o que está ativo (ex.: "Bracelete: Feliz"). Tecla H liga/desliga (muda em Controles > CPM Wheel). Botão "HUD…" na roda: posição (4 cantos), escala, opacidade e "só o que não é padrão". Lê o estado 4x por segundo.
- Autor: Zeraph.
- Compilar: ./gradlew build (Java 17) -> build/libs.
