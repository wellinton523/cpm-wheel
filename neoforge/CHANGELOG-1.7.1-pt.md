# CPM Wheel 1.7.1 — Física Live2D: várias entradas e camadas compartilhadas

Versões: NeoForge 1.21.1 (`cpmwheel-1.7.1-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.7.1.jar`).
A configuração passa para a versão 21 e é migrada automaticamente da 20: a entrada única de cada elo vira uma lista com essa entrada a 100%. Nada muda no movimento.

## Várias entradas por elo (como no Live2D)
- Cada elo agora tem uma **lista de entradas**. Cada linha é **[fonte] [peso %] [+/−] [✖]**:
  - o peso vai de 0 a 300%;
  - **+** é normal e **−** é invertido.
- O elo é movido pela **soma ponderada** de todas as entradas. A **Intens.** multiplica o total.
- **+ Entrada** adiciona até 4 entradas por elo.
- Assim o cabelo mexe com a cabeça **e** com o corpo ao mesmo tempo.
- Fontes novas:
  - **Andar frente/trás**: velocidade com direção em relação ao corpo, **+ para frente** e **− para trás**;
  - **Andar para os lados**: + para a direita, − para a esquerda;
  - **Cabeça vs. corpo**: quanto a cabeça está virada em relação ao corpo.
- **Velocidade (andar)** continua igual: só a rapidez, sem direção.

## Camadas compartilhadas
- Vários elos, até de correntes diferentes, podem usar a **mesma camada**. Antes, só o primeiro funcionava.
- Os elos são combinados e a camada é escrita **uma vez por tick**.
- Modo por camada, no botão ao lado de "Camada":
  - **Soma** (padrão): soma o quanto cada elo sai do repouso;
  - **Média**;
  - **Maior**: vale o que estiver mais longe do repouso.
- O resultado é limitado a 0..máximo.
- Na lista de elos aparece **⧉N** quando a camada é usada por N elos. Embaixo aparece "compartilhada (N): modo → valor".

## Predefinições e guia
- **+ Cabelo**:
  - centro = Giro da cabeça 100% + Giro do corpo 50% (−) + Andar frente/trás 20% (−);
  - pontas = Elo anterior.
- **+ Cauda**:
  - base = Giro do corpo 100% + Andar para os lados 30% (−);
  - ponta = Elo anterior.
- O guia "Como montar" foi atualizado com as várias entradas, a direção do andar e as camadas compartilhadas.
- A coluna da direita ficou mais compacta: Compr./Peso, Amort./Intens. e Mín/Máx ficam em pares.

## Testes
`tools/Live2dMathCheck.java` agora tem 56 verificações. As novas cobrem:
- soma ponderada, inversão, peso 0 e "Elo anterior" junto com outras entradas;
- o sinal de andar para frente/trás e para os lados em vários ângulos do corpo;
- os modos Soma/Média/Maior, offsets que se cancelam e o limite 0..máximo;
- a migração 20 → 21.
