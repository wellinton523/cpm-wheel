# CPM Wheel 1.5.7 — Changelog

## Braço em 1ª pessoa como o Better Combat (emissão)
- Nova opção **"Renderizar braço como Better Combat (emissão)"** (Configurações → Primeira pessoa → Braço e emissão). Vem **ligada**.
  - O braço em 1ª pessoa agora é desenhado junto com as entidades do mundo, do mesmo jeito que o Better Combat desenha os braços. Assim o brilho (emissão) do CPM — olhos, bracelete, braço — funciona com shaders igual ao 3ª pessoa.
  - Valores: ON / só com shader / OFF (método antigo, que continua de reserva).
  - "Profundidade do braço" (20–100%): puxa o braço para perto da câmera para atravessar menos as paredes; o tamanho na tela não muda.
  - Continua valendo: ajuste do braço, ajuste do item, poses por tipo de item, lanterna, ajustes por item/grupo/mod e "Ignorar conflito com Better Combat".
  - Sem braço duplicado: enquanto o Better Combat está animando os braços, o CPM Wheel pausa sozinho e deixa o Better Combat desenhar.
  - Se der qualquer erro, volta sozinho ao método antigo naquela sessão (aparece no log).
  - Mapas, arco/besta puxando, comer e "modo vanilla do item" continuam no método antigo.

## Gatilhos de efeito (novo)
- Configurações → **Gatilhos de efeito** → "Abrir Gatilhos de efeito…".
- Cada regra tem: ligar/desligar, efeito (lista com todos os efeitos do jogo, inclusive de mods), nível mínimo, ações ao começar e "Ao terminar".
  - Ações: qualquer grupo da roda, botão (emote/toggle) ou animação do CPM, cada um com **ATIVAR**, **DESATIVAR** ou **ALTERNAR**. Em um grupo, DESATIVAR faz o contrário do grupo (ex.: tira o disfarce).
  - Ao terminar: **voltar ao estado anterior** (padrão) ou **não fazer nada**.
  - Atraso para começar e para terminar (evita ficar ligando/desligando se o efeito pisca). Padrão: começa na hora, termina 1 s depois.
  - Aviso no HUD (acima da hotbar) opcional por regra.
  - Botão **Testar 5 s** (finge o efeito por 5 segundos e depois restaura).
  - Chave geral "Gatilhos de efeito: ON/OFF".
- Restauração exata: o mod guarda o valor de cada animação antes de mexer e devolve esse mesmo valor quando o efeito acaba, morrendo ou tomando leite.
- Duas regras no mesmo item: vale a regra ativa mais recente; o valor original só volta quando nenhuma regra ativa segura mais aquele item.
- Se você mudar à mão algo que a regra mexeu enquanto o efeito está ativo, o mod não briga com você: no fim fica do jeito que você deixou.
- Exemplo pronto: com a tela vazia, o botão "Criar exemplo: Veneno → DESATIVAR Humano" cria a regra do demônio (o disfarce some envenenado e volta quando o veneno acaba).
- As regras ficam salvas na config do modelo, como o resto.

## Itens e mods: grupos de itens
- Novo nível entre item e mod: **grupos de itens** (ex.: todas as mochilas do Sophisticated Backpacks).
- Prioridade: item > grupo > mod inteiro > tipo de item > padrão. Vale para "Ajustar: item" e "Ajustar: braço", separado por mão.
- Grupos automáticos ("Sugestões…"): mesmo final de id (`*backpack`), mesma classe de item ou mesma tag.
- Grupos próprios ("+ Grupo"): padrão no id com `*` e `?` (ex.: `sophisticatedbackpacks:*backpack*`), regex com `re:`, classe, tag ou lista de itens escolhidos (botão "± Item da mão").
- A tela mostra de onde vem o ajuste de cada item: "herdado de: grupo Mochilas", "mod …", "tipo espada"… e em quais grupos ele está.
- **Copiar p/…**: copia o ajuste para outro item, um grupo ou o mod inteiro (esta mão ou as duas).
- **↺ Herdado** (resetar para herdado): apaga só o ajuste daquele nível.
- Busca e filtro na lista: com ajuste próprio / todos / mesmo mod / herdando de grupo ou mod.
- Ao mexer num item que herdava, ele começa com os valores herdados.

## Tela de configurações reorganizada
- Abas: Roda e botões, Estilo, HUD, Primeira pessoa, Itens e mods, Gatilhos de efeito, Compatibilidade, Avançado.
- Títulos de seção, espaçamento igual, rolagem com a roda do mouse.
- **Busca** no topo: filtra as opções de todas as abas pelo nome ou pela dica.
- Toda opção tem dica (tooltip).
- **↺ Padrões** em cada seção: restaura só as opções daquela seção.
- Nenhuma opção foi removida e as chaves da config são as mesmas: suas configurações continuam. Algumas opções que só existiam na tela de edição ou no HUD também aparecem aqui (segurar/alternar, animações, prévia, página de favoritos, quadros como segmentos, HUD completo).
- O botão "Estilo…" da roda agora se chama "Opções…".

## Outros
- Config migrada automaticamente (versão 16).
- NeoForge: compatível com 21.1.262 e qualquer 21.1.x. Forge 1.20.1 também atualizado.
