# CPM Wheel 1.9.0 — Overlay dos olhos, olhadinhas, reação a dano, piscar por contexto, olhar objetos, modo ocioso e exportar/importar perfis

Versões: NeoForge 1.21.1 (`cpmwheel-1.9.0-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.9.0.jar`).
A configuração passa para a versão 27. A migração é automática e **todas as novidades vêm desligadas**: nada muda até você ligar.

## Overlay dos olhos (só você vê)
- Um marcador pequeno no HUD mostra **mais ou menos onde os olhos estão mirando** na tela: anel + ponto na posição suavizada e, opcional, uma cruz fraca na mira "crua" (para onde o olho quer ir).
- Opcional: contorno da área de alvo, um colchete no alvo escolhido e um texto pequeno com o estado (seguindo, esperando, voltando, parado, olhadinha, dano, à toa) e o **tipo** do alvo (jogador, criatura, item, bloco).
- **Nunca mostra nomes.** Só aparece o que já passou pelos filtros de privacidade (invisíveis, vanish, fora do Tab, câmera livre, linha de visão).
- Com Freecam, F5 ou espectando, o ponto na tela estaria errado, então ele vira um **medidor pequeno no topo da tela** (direção dos olhos), sem apontar nada no mundo.
- Configurável: tamanho, opacidade, cor (predefinidas ou R/G/B), contorno, mira crua, texto. Tecla para ligar/desligar em Controles › CPM Wheel (**sem tecla por padrão**).
- Onde fica: Física Live2D › Extras › "Olhar: opções…" › "Overlay dos olhos".

## Olhar: extras (botão "Extras do olhar…" em "Olhar: opções…")
- **Olhadinhas:** quando aparece um novo alvo permitido, os olhos dão uma olhada rápida nele e voltam para o alvo principal. Duração (350 ms), intervalo mínimo (4 s) e chance (60 %).
- **Reação a dano:** ao levar dano, as pálpebras apertam com força (força 80 %, 450 ms) e, se quiser, os olhos dão uma olhada rápida em quem bateu (600 ms). O atacante só conta se passar em **todos** os filtros (raio, área, invisível, vanish, fora do Tab, linha de visão).
- **Olhar itens / blocos / mobs:** sem jogador permitido por perto, os olhos seguem o **bloco que você está quebrando**, **itens no chão** ou **mobs**. Cada fonte liga/desliga e a ordem de prioridade é configurável (jogadores sempre primeiro). Usa a mesma área de tela e os mesmos filtros.
- **Modo ocioso:** depois de N segundos parado (padrão 20 s; sem mexer a câmera, andar, bater ou usar itens), os olhos passeiam devagar quando não há alvo (±14°, troca a cada ~2,2 s) e as piscadas ficam mais raras (60 %), mais lentas (180 %) e sonolentas (pálpebra 25 % caída).

## Piscar por contexto (botão "Piscar: contexto…" na tela "Piscar: suavização")
- Multiplica a frequência das piscadas: **sol forte** 150 %, **embaixo d'água** 220 %, **chuva / tempestade de areia** 140 %; **mirando** arco, besta carregada, tridente ou luneta 35 %.
- Os multiplicadores se combinam (ex.: sol + mirando = 52 %) e ficam entre 5 % e 600 %.

## Perfis por modelo: exportar e importar
- Cada modelo do CPM já tinha a própria configuração e troca sozinho (1.8.0). Agora dá para **exportar e importar** essa configuração inteira (roda, gatilhos, mãos, Física Live2D, olhar, piscar, extras).
- **Exportar:** Configurações › Perfis automáticos › "Exportar config deste modelo" → `config/cpmwheel/export/<nome>.json`. Nunca sobrescreve: cria `nome-2`, `nome-3`…
- **Importar:** coloque o arquivo em `config/cpmwheel/import/` e use "Importar perfil…". O arquivo é **validado** (formato, versão, leitura completa com as migrações normais) antes de salvar como `config/cpmwheel/profiles/<nome>.json`.
- Se já existir um perfil com o mesmo nome, ele **só é substituído depois de confirmar** ("Substituir (confirmar)"), e o antigo é copiado antes para `config/cpmwheel/profiles/_backup/<nome>-<data>.json`.
- Importar não muda o modelo atual. Depois, "Usar neste modelo" vincula o perfil ao modelo atual (a config própria do modelo continua salva).
- **Comando:** `/cpmwheel perfil listar`, `/cpmwheel perfil exportar [nome]`, `/cpmwheel perfil importar <arquivo> [nome] [substituir]`, `/cpmwheel perfil usar <nome>`.

## Privacidade
- Os textos de depuração e da tela de opções do olhar mostram só o tipo do alvo, não o nome.
- Olhadinhas, olhar no atacante e olhar objetos passam pelos mesmos filtros do olhar normal.

## Testes
- Testes offline: LookLogicCheck 74/74 (olhadinhas, dano, contexto, ocioso, ordem, frequência e lentidão da piscada, projeção do overlay), mais os testes antigos.
