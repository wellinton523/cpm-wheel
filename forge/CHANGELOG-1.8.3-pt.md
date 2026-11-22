# CPM Wheel 1.8.3 — Olhar: área na tela, privacidade (câmera livre e vanish) e piscar suave

Versões: NeoForge 1.21.1 (`cpmwheel-1.8.3-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.8.3.jar`).
A configuração passa para a versão 26. A migração é automática: o alvo antigo continua ("jogadores" ou "qualquer criatura" vira "jogadores e criaturas"), os filtros de privacidade vêm ligados e as piscadas já salvas mantêm os mesmos tempos, agora com a curva suave.

## Por que os olhos seguiam a câmera livre
- Até a 1.8.2, o "Olhar" pegava o **jogador mais perto** da lista de jogadores do mundo do cliente.
- O mod Freecam cria a câmera como uma **cópia falsa do seu jogador** (outro `LocalPlayer`, com id negativo) e a coloca nessa lista. Ela era sempre o "jogador" mais perto, então os olhos apontavam para a câmera.
- No modo "qualquer criatura" ela também entrava, porque um jogador também é uma criatura.
- As câmeras do Flashback e do ReplayMod funcionam de forma parecida.

## Olhar: novo modo "área na tela" (padrão)
- O alvo é quem aparece **dentro de uma área em volta do centro da sua visão** e está **mais perto desse centro**. A distância só desempata e serve de alcance máximo.
- A projeção usa a visão do **seu personagem** (posição dos olhos e direção para onde ele olha), nunca a câmera. Com Freecam, F5 ou espectando outra entidade, nada muda.
- Área configurável: **círculo** (raio em % da altura da tela) ou **retângulo** (largura e altura em % da tela), mais deslocamento horizontal e vertical.
- **Prévia da área**: mostra o contorno e o alvo atual enquanto a tela de opções está aberta.
- **Depuração na tela** (desligada por padrão): mostra a área, o alvo, e quantas entidades foram ignoradas e por quê, durante o jogo. Só você vê.
- O modo antigo continua como **"mais próximo"**, agora com um cone (ângulo) configurável.
- **Manter alvo** (ligado, 25 %): o alvo atual continua até sair da área aumentada pela tolerância, e outro só ganha se for melhor por essa mesma porcentagem. Assim o olhar não fica pulando entre dois alvos.
- **Troca de alvo suave**: logo depois de trocar de alvo, o olhar se move mais devagar (padrão 250 ms).
- **Alvos**: jogadores, criaturas, os dois ou nenhum.

## Olhar: filtros de privacidade (todos ligados)
- **Ignorar câmera livre**: nunca mira a entidade da câmera nem cópias do seu jogador. Isso cobre Freecam, Flashback, ReplayMod, espectar e entidades falsas só do cliente.
- **Ignorar ocultos**: ignora invisíveis (`isInvisible` e `isInvisibleTo`), espectadores, jogadores fora da lista do Tab ou desconhecidos do servidor, e entidades que o jogo não desenharia.
- **Ignorar marcados vanish**: ignora jogadores com marcas como "[V]", "Vanished" ou "vanish" no nome do Tab ou no time. É só uma tentativa, porque nem todo plugin marca.
- **Só com linha de visão**: um raycast dos seus olhos até o alvo. Se houver blocos no caminho, ele não conta.

## Olhar: suavização dos olhos
- **Mola** (criticamente amortecida, sem passar do ponto), **exponencial** ou **desligada**.
- Ajustes: **tempo de resposta**, **velocidade máxima** (°/s), **micro-movimentos** (sacadas de até 4°), **voltar ao centro depois de** (ms) e **giro máximo dos olhos**.
- As contas usam o tempo que passou, então o resultado não depende da taxa de atualização.

## Piscar suave
- Tempos separados para **fechar**, ficar **fechado** e **abrir**.
- Curva: **suave**, **senoidal**, **rápida no início** ou **linear**, com gráfico de uma piscada na tela.
- **Piscada dupla**: chance de piscar de novo logo em seguida, nunca três vezes.
- **Camadas extras**: até 4 camadas movidas junto com o mesmo gerador, por exemplo as duas pálpebras ao mesmo tempo. Também funciona na respiração.
- Fica em Física Live2D › Extras › selecione o gerador › **"Piscar: suavização e camadas…"**.
- Limite: as camadas do CPM mudam 20 vezes por segundo. Tempos abaixo de ~50 ms viram um passo só, e a suavidade aparece melhor com piscadas de 150 ms ou mais. Uma piscada nova vem com 100 + 60 + 140 ms.

## Onde fica
- Física Live2D › **Extras** › **"Olhar: opções…"**: alvos, modo, área, prévia, depuração, filtros e suavização.
- Física Live2D › **Extras** › gerador de piscar › **"Piscar: suavização e camadas…"**.

## Privacidade: o que outros jogadores veem
- Com "Sincronizar no multiplayer" ligado (o padrão da Física Live2D), os valores das camadas vão para o servidor pelo próprio CPM, e quem vê seu modelo vê os olhos se mexendo.
- A escolha do alvo acontece **só no seu cliente**. Nenhuma posição, nome ou id é enviado, só o valor da camada (um ângulo arredondado e limitado).
- Até a 1.8.2, com a sincronização ligada, outros jogadores podiam ver seus olhos virando para onde estava sua câmera livre. Em tese, também podiam ver os olhos seguindo um jogador que o servidor mandava para você como invisível. Isso inclui o caso de staff que enxerga quem está em vanish.
- Um jogador em vanish que o servidor **esconde de você** não existe no seu cliente. Ele nunca podia ser seguido.
- Agora a câmera nunca é alvo, e invisíveis, espectadores, quem está fora do Tab, marcados com vanish e quem está atrás de blocos são ignorados por padrão.
