# CPM Wheel (Minecraft 1.20.1 NeoForge 1.21.1, client-side)
Roda radial para organizar e tocar as animações do Customizable Player Models.
- Instalar: coloque cpmwheel-1.0.0.jar na pasta mods junto com o CPM (NeoForge 1.21.1, CPM 0.6.x para 1.21.1 NeoForge).
- Tecla G (segurar): abre a roda; mova o mouse e solte (ou clique) para tocar. Scroll/Tab troca página, 1-9 atalhos, botão direito favorita.
- Botão "Editar": arraste animações da lista para os slots; nome, ícone (id de item), cor, valor, favorito, páginas.
- Config: config/cpmwheel/<id-do-modelo>.json (ou default.json).
- Ícones personalizados: coloque PNGs (qualquer tamanho, transparência mantida) em config/cpmwheel/icons/ (a pasta é criada sozinha). No editor, selecione um slot e clique em "Escolher ícone…": a grade mostra seus PNGs (borda ciana) e todos os itens, com busca. Botões "Abrir pasta de ícones" e "Recarregar"; arquivos novos/alterados também são recarregados sozinhos. No JSON fica icon: "file:nome.png" ou um id de item ("minecraft:diamond").
- Ao passar o mouse num slot, o ícone aparece grande no centro da roda.
- Sub-rodas (1.2.0): no editor, selecione um slot e clique "Criar sub-roda"; "Abrir sub-roda" entra nela e você arrasta animações para dentro (ou solte uma animação em cima do slot-pasta). Pode ter sub-rodas dentro de sub-rodas. Na roda: clique na pasta para entrar; botão direito fora de um slot, Esc, Backspace ou clique no centro voltam. O caminho aparece no topo.
- Animação ao abrir (1.2.0): a roda abre com zoom/fade (~150ms), o slot sob o mouse cresce suave, e há transição ao entrar/sair de sub-rodas. Desligue em Editar > "Animações".
- Prévia ao vivo (1.2.0): seu jogador aparece no centro da roda (Editar > "Jogador no centro"). Com "Prévia ao passar o mouse" ligada, passar o mouse num slot liga/desliga a animação de verdade e desfaz ao tirar o mouse; clicar mantém. Atenção: a prévia usa o mesmo comando do CPM, então outros jogadores no servidor podem ver a animação piscar.
- Animações de valor (1.2.0): animações do CPM com valor (0-255 etc.) são detectadas sozinhas. Na roda, um arco mostra o valor atual e o % aparece acima; o scroll ajusta ao vivo (Shift = mais rápido), clique confirma. Modo "presets": cada clique vai pro próximo preset (ex.: 0,25,50,75,100 %). No editor: Tipo (auto/liga-desliga/slider/presets), Mín/Máx/Passo e lista de presets.
- Autor: Zeraph.
- Compilar: ./gradlew build (Java 21) -> build/libs.
