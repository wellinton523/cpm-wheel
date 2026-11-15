# CPM Wheel 1.8.2 — Ícones e informações dos emotes do Emotecraft

Versões: NeoForge 1.21.1 (`cpmwheel-1.8.2-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.8.2.jar`).
A configuração continua na versão 25 (nada para migrar).

## Escolher emote: ícone e informações
- A lista de emotes do Emotecraft (botão "Emote do Emotecraft", condição "Emote (Emotecraft)" e botão **EC** de Encadear animações) agora mostra o **ícone** de cada emote ao lado do nome. A busca continua no topo.
- Clicar num emote mostra um **painel de detalhes** à direita:
  - ícone grande, nome, autor e descrição;
  - duração, se repete (e o tamanho do trecho que repete), formato do arquivo;
  - avisos quando o emote tem música (Note Block), veio do servidor ou está marcado como NSFW.
- **Duplo clique** ou **Escolher** usa o emote.

## Ícone do emote na roda e no HUD
- Os botões "Emote do Emotecraft" mostram o ícone do emote **na fatia da roda por padrão**, seguindo o tamanho de ícone e o modo "só ícone" de sempre.
- O HUD mostra o mesmo ícone pequeno ao lado do nome do botão.
- Emote sem ícone, Emotecraft ausente ou botão "parar qualquer emote": aparece um **ícone genérico** (bonequinho acenando).
- Um ícone escolhido no campo **Ícone** do botão tem prioridade. Para não mostrar o ícone do emote, desligue **Ícone do emote** no editor do botão.

## Por dentro
- O ícone vem do próprio emote (PNG guardado pelo Emotecraft) e é lido por reflexão nas duas versões (2.4.x no NeoForge, 2.2.7 no Forge).
- Cada ícone vira uma textura do CPM Wheel, guardada em cache (até 96) e liberada quando o emote some da lista, quando o cache enche, quando os ícones são recarregados ou se a integração falhar.
- Dados que não sejam PNG ou que falhem ao abrir usam o ícone genérico, sem travar.
