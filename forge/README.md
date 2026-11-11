# CPM Wheel (Minecraft 1.20.1 Forge, client-side)
Roda radial para organizar e tocar as animações do Customizable Player Models.
- Instalar: coloque cpmwheel-1.0.0.jar na pasta mods junto com o CPM (Forge 1.20.1, CPM 0.6.x recente).
- Tecla G (segurar): abre a roda; mova o mouse e solte (ou clique) para tocar. Scroll/Tab troca página, 1-9 atalhos, botão direito favorita.
- Botão "Editar": arraste animações da lista para os slots; nome, ícone (id de item), cor, valor, favorito, páginas.
- Config: config/cpmwheel/<id-do-modelo>.json (ou default.json).
- Ícones personalizados: coloque PNGs (qualquer tamanho, transparência mantida) em config/cpmwheel/icons/ (a pasta é criada sozinha). No editor, selecione um slot e clique em "Escolher ícone…": a grade mostra seus PNGs (borda ciana) e todos os itens, com busca. Botões "Abrir pasta de ícones" e "Recarregar"; arquivos novos/alterados também são recarregados sozinhos. No JSON fica icon: "file:nome.png" ou um id de item ("minecraft:diamond").
- Ao passar o mouse num slot, o ícone aparece grande no centro da roda.
- Autor: Zeraph.
- Compilar: ./gradlew build (Java 17) -> build/libs.
