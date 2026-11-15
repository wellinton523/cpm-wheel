# CPM Wheel 1.5.8 — Changelog

## Grupos de itens por nome (tipo "SQL") + nova tela "Criar grupo"
- Itens e mods → modo **Grupo** → **"+ Criar grupo"** abre uma tela passo a passo:
  1. **Escolha o mod** (lista com busca, ou "Todos os mods").
  2. **O que o nome do item deve ter** — basta escrever `backpack` (não precisa de `$`). Pega `netherite_backpack`, `iron_backpack`, etc.
  3. **Não incluir (opcional)** — ex.: `upgrade` tira os upgrades das mochilas.
  4. **Também conta o nome traduzido** — procura também no nome que aparece no jogo, no seu idioma (ex.: "mochila").
- **Prévia ao vivo** com os ícones dos itens e "N itens encontrados". Clique num item da prévia para tirar do grupo (fica com um X vermelho); clique de novo para pôr de volta.
- **Exemplos com um clique**: Todas as mochilas, Todas as espadas, Tudo de netherite, Ferramentas, Lanternas, Poções (ou sugestões do mod escolhido).
- Nome e **cor do grupo** (a cor aparece como ■ na lista de grupos).
- **"Salvar e ajustar"** salva e já abre o ajuste de posição do grupo. "Editar grupo" abre a mesma tela.
- "Sugestões…" agora abre a tela já preenchida para você conferir antes de salvar.
- **Modo avançado** (recolhível): padrão com `*`, regex `re:`, tag `#c:backpacks`, classe, lista, "± Item da mão" e a sintaxe completa:
  - espaço = E (`iron backpack` precisa ter os dois)
  - `|` = OU (`backpack | mochila`)
  - `-palavra` ou `!palavra` = tirar (`backpack -upgrade`)
  - `@mod` = só desse mod (`@sophisticatedbackpacks backpack`, `@a|b` para vários)
  - `$palavra` = o mesmo que `palavra`; com `:` compara o id inteiro (`minecraft:lantern`)
  - maiúsculas/minúsculas não importam; espaço no nome traduzido conta como `_`
- Grupos antigos (padrão/classe/tag/lista) continuam funcionando sem mudança.

## Física de pendurado (novo)
- O item balança na mão em 1ª pessoa como uma lanterna: ao virar a câmera, olhar para cima/baixo, andar/correr, pular, cair, aterrissar, arrancar e parar.
- Pode ser ligada para **qualquer item, grupo ou mod** (Itens e mods → botão **"Física"**) e para **tipos de item** (Ajuste por tipo → "Física deste tipo…"). Ex.: as mochilas do Sophisticated Backpacks penduradas na mão.
- Mesma prioridade do resto: item > grupo > mod > tipo, separado por mão. "Física: DESLIGADA" num nível vence o que vem de cima.
- Ajustes: ponto de giro (**alça** = na mão / **topo** = acima do item, com desenho de prévia), intensidade, rigidez, amortecimento, peso, ângulo máximo e reação a pulo/queda/parada.
- Presets: **Lanterna**, **Mochila** (pesada) e **Chaveiro** (leve). Botão "Balançar" para testar.
- Lanternas usam o preset **Lanterna** por padrão: os mesmos números do balanço antigo.
- Calculada a cada frame com sub-passos de 10 ms (suave mesmo com FPS alto).
- A opção das animações agora se chama "Lanterna/tocha e física de pendurado" e liga/desliga tudo.

## Avisos de gatilho mais bonitos (toasts)
- No lugar da mensagem acima da hotbar: um aviso pequeno com o **ícone do efeito**, o **nome da regra** e **o que mudou** (ex.: "Veneno → Humano desativado").
- Entra e sai deslizando com fade; **barra lateral colorida** com a cor do botão/grupo da roda.
- **Barra de tempo** fininha com o tempo restante do efeito.
- Depois da duração, vira um **mini aviso** (ícone + nome + barra de tempo) enquanto o efeito durar (opcional).
- Quando acaba: "**Efeito acabou → restaurado**".
- HUD → "Avisos de gatilho": ligar/desligar, duração, tamanho, opacidade, mini aviso e "Ver exemplo".

## HUD inteiro movível
- HUD → **"Mover HUD…"** (ou a tecla nova "Mover HUD do CPM Wheel", sem tecla por padrão em Controles).
- Arraste cada elemento (itens ativos e avisos de gatilho) com o mouse; **roda do mouse = tamanho**; **botão direito = restaurar** aquele elemento; "Restaurar tudo".
- **Ímã**: gruda nas bordas, no centro e numa grade de 8 px (Shift = mover livre).
- A posição é salva em relação ao canto/lado/centro mais próximo da tela, então continua certa ao mudar resolução ou escala da interface.
- A posição antiga (canto + escala) é convertida sozinha.

## Botões bloqueados durante o gatilho
- Nova opção por regra: **"Bloquear: ON"** (bloquear botões enquanto ativo) — vem ligada.
- Enquanto o gatilho segura uma animação, o botão dela na roda (e qualquer grupo que a contenha) mostra um **cadeado**, a dica "**Bloqueado por gatilho: Veneno**", e clicar não faz nada (também nos atalhos e no anel interno).
- Mudanças por fora são desfeitas, então ao acabar o efeito tudo volta exatamente ao estado de antes do gatilho.
- O bloqueio some quando o efeito acaba. Com "Bloquear: OFF" vale o comportamento antigo (sua mudança manual fica).

## Observações
- Config atualizada para a versão 17 automaticamente (nada se perde).
- Feito para NeoForge 1.21.1 e Forge 1.20.1.
