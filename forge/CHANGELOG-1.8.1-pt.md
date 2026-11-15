# CPM Wheel 1.8.1 — Integração com Emotecraft

Versões: NeoForge 1.21.1 (`cpmwheel-1.8.1-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.8.1.jar`).
A configuração passa para a versão **25** e é migrada sozinha da 24. A integração vem **desligada**.

## Emotecraft (opcional)
O [Emotecraft](https://modrinth.com/mod/emotecraft) é lido por reflexão: **o CPM Wheel não depende dele**. Sem o Emotecraft, nada muda e nada quebra; as opções novas ficam escondidas ou aparecem com o aviso "Emotecraft não está instalado".
Ligue em **Configurações › Compatibilidade › Emotecraft › Integração com Emotecraft**.

- **Botão "Emote do Emotecraft"** na roda (Editar › Tipo: emote do Emotecraft):
  - escolha o emote numa lista com busca (os emotes que o seu Emotecraft carregou);
  - ação **tocar/parar** (padrão), **tocar** (sempre recomeça) ou **parar qualquer emote**;
  - o HUD mostra ▶ enquanto o emote toca.
- **Condição "Emote (Emotecraft)"**: qualquer emote ou um pelo nome (com NÃO, E/OU). Funciona em:
  - regras dos Gatilhos de efeito;
  - **Desativar quando** da Física Live2D (corrente ou elo);
  - **Encadear animações**: o botão **EC** ao lado de "Quando" escolhe um emote (ex.: emote "Acenar" termina → animação "sorrir" do CPM).
- **Física durante emotes** (Configurações › Compatibilidade › Emotecraft):
  - **nada** (padrão);
  - **desligar**: todas as correntes da Física Live2D soltam enquanto o emote toca (com o fade de cada corrente);
  - **tranco**: um tranco no começo e no fim do emote, na entrada nova **"Emote do Emotecraft (tranco)"** — adicione essa entrada nos elos que devem reagir. Força ajustável (padrão 30°).

## Versões do Emotecraft
- **NeoForge 1.21.1:** Emotecraft 2.4.x (testado contra a API do 2.4.12): tocar, parar, lista e estado.
- **Forge 1.20.1:** Emotecraft 2.2.7 (com PlayerAnimator): tocar, lista e estado pela mesma API; o 2.2.7 não tem "parar" na API pública, então o CPM Wheel usa a função interna dele que para o emote local e avisa o servidor.
- Se uma versão futura do Emotecraft mudar essas funções, a integração se desliga sozinha e escreve no log (sem travar o jogo).

## Limites
- A condição e a física olham só o **seu** personagem.
- Os emotes do Emotecraft mexem nas partes do modelo vanilla (via PlayerAnimator). Num modelo do CPM, só as partes ligadas às partes vanilla acompanham o emote; as animações e camadas próprias do CPM não são tocadas pelo Emotecraft.
- Para outros jogadores verem o emote, o servidor precisa ter o Emotecraft (ou um proxy dele), como sempre.
