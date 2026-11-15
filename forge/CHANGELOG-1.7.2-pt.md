# CPM Wheel 1.7.2 — Física Live2D: "Desativar quando"

Versões: NeoForge 1.21.1 (`cpmwheel-1.7.2-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.7.2.jar`).
A configuração passa para a versão 22 e é migrada automaticamente da 21. As correntes existentes ficam sem condições, ou seja, nunca são suspensas. O fade padrão é 0,3 s.

## Desativar quando
- Cada corrente tem o botão **Desativar quando…**, que abre a mesma tela de condições dos Gatilhos.
- No topo da tela ficam **QUALQUER (OU) / TODAS (E)** e o **Fade** (0 a 3 s, padrão 0,3 s).
- Cada linha mostra ao vivo se vale. Embaixo aparece "física SUSPENSA / ativa".
- Cada elo tem o botão **⏸**, com uma lista só dele. Ela vale junto com a da corrente e usa o fade da corrente.
- Condições novas, que também servem para os Gatilhos:
  - **Animação**: uma animação do CPM ou da roda tocando (gestos, camadas de valor e animações ocultas/de comando inclusive), com valor mínimo ajustável (≥1, ≥2… ≥255);
  - **Voando** (voo do criativo ou de habilidade), **Planando** (élitro), **Nadando**, **Montado**, **Dormindo**.
  - **Agachado** e todas as outras condições dos Gatilhos continuam disponíveis.
- **Enquanto suspensa:**
  - o valor escrito volta ao repouso no tempo do fade;
  - depois o mod **para de escrever** naquela camada, e a outra animação (por ex. "voar", batendo as asas) controla tudo;
  - quando a condição acaba, a física **volta com o mesmo fade**.
- Se uma camada é compartilhada, só os elos suspensos saem da combinação. Se todos estão suspensos, a camada deixa de ser escrita.
- A tela mostra o estado ao vivo: a barra fica laranja e aparece "(suspendendo…)", "(suspensa: não escreve)" ou "(voltando…)".
- **Exemplo das asas:** na corrente "Asas", "Desativar quando" = **Animação "voar"** OU **Planando**. Andando, as asas balançam. Ao voar, só a animação de bater as asas aparece.

## Também nesta linha (1.7.1)
- **Várias entradas por elo:** linhas [fonte] [peso %] [+/−] [✖] e "+ Entrada" (até 4).
- **Fontes:** Andar frente/trás (+ frente / − trás), Andar para os lados (+ direita / − esquerda) e Cabeça vs. corpo, além das antigas.
- **Camadas compartilhadas:** Soma / Média / Maior, escritas uma vez por tick, com o indicador ⧉N.
- **Predefinição "+ Cabelo":** cabeça 100% + corpo 50% (−) + frente/trás 20% (−).

## Guia e testes
- O guia "Como montar" ganhou a seção "Desativar quando", com o exemplo das asas.
- `Live2dMathCheck` passa 69/69. Os novos testes cobrem:
  - fade de saída e de volta (0,3 s = 6 ticks), fade 0 instantâneo e retomada no meio do fade;
  - valores descendo até o repouso e a liberação da escrita só depois do fade;
  - camada compartilhada com um elo liberado;
  - os padrões da config 22.
- `TriggerLogicCheck` passa 61/61, com as condições Animação (valor mínimo), Voando, Planando, Nadando, Montado e Dormindo.
