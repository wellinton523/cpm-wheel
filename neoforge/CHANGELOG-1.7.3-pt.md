# CPM Wheel 1.7.3 — Física Live2D: "Tranco ↔ Mantido"

Versões: NeoForge 1.21.1 (`cpmwheel-1.7.3-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.7.3.jar`).
A configuração passa para a versão 23 e é migrada automaticamente da 22. As entradas de velocidade que já existem ganham 75% de "Mantido". Os giros da cabeça e do corpo continuam como antes (0%).

## Problema corrigido: as asas voltavam ao repouso andando
- Na saída "atraso", o elo mostrava só a diferença entre a mola e a entrada (a − x).
- Com uma entrada constante, como andar para frente sem parar, a mola alcançava a entrada e a diferença voltava a 0.
- Por isso as asas faziam o movimento certo ao começar a andar e depois voltavam à posição inicial, mesmo andando.
- Para o giro da cabeça isso é o certo, porque o osso já gira junto com a cabeça. Para velocidades, não.

## Novo: "Tranco ↔ Mantido" por entrada
- Embaixo de cada entrada fica o controle **↳ Tranco ↔ Mantido** (0–100%, de 5 em 5). Ele aparece só na saída "atraso" e não aparece para "Elo anterior".
- **0% = tranco:** o elo balança quando a entrada muda e volta ao repouso. É exatamente o comportamento da 1.7.2.
- **100% = mantido:** enquanto a entrada durar, o elo fica deslocado, como no Live2D. Ele ainda balança ao começar e ao parar.
- O deslocamento mantido fica do mesmo lado do balanço inicial (as asas continuam para trás enquanto você anda).
- Ao parar, o elo volta ao repouso com um balanço.
- **Padrões:**
  - Velocidade, Andar frente/trás, Andar para os lados e Vertical: **75%**;
  - Giro da cabeça, Inclinação, Giro do corpo e Cabeça vs. corpo: **0%**.
- Ao trocar a fonte de uma entrada, o padrão da nova fonte é usado, a menos que você tenha mudado o valor.
- A dica do elo na lista mostra "mantido N%".
- "Saída: segue" já mantinha a entrada e não muda.

## Velocidades mais estáveis
- A velocidade agora é medida pelo deslocamento real do jogador em cada tick.
  - Antes era usada a velocidade depois do atrito do jogo. Ela muda de ×0,55 no chão para ×0,91 no ar, então cada pulo mudava a "velocidade de andar".
- As velocidades são suavizadas (≈0,15 s) e têm uma pequena zona morta perto de 0. Picos de um tick, pulos e chão/ar não fazem o efeito piscar.
- Teleportes (mais de 2 blocos num tick) não geram balanço.

## Guia
- "Como montar" ganhou a seção **Tranco ↔ Mantido**, com o exemplo das asas.

## Testes
- `Live2dMathCheck` passa 87/87. Novos testes:
  - andando para frente sem parar, o elo segura um deslocamento diferente de 0 (75% → 6,45°, 100% → 8,6°);
  - com 0% volta ao repouso, e o resultado é idêntico ao da 1.7.2 passo a passo;
  - ao parar, passa do repouso (balanço) e depois assenta;
  - "Elo anterior" nunca é mantido, e "segue" mantém a entrada;
  - migração 22→23;
  - suavização: pulos 43/56 nunca caem perto de 0, um tick zerado não desliga o andar, a zona morta funciona e a suavização é segura com NaN;
  - andar com ruído e picos mantém o deslocamento longe do repouso.
- `TriggerLogicCheck` passa 61/61.
