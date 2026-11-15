# CPM Wheel 1.5.9 — Changelog

## Física de pendurado: corrigida nas duas mãos
- **Mão principal invertida (corrigido).** Na 1.5.8 o ângulo de lado era multiplicado pelo lado do braço e aplicado dentro do quadro já espelhado do item, então na mão direita o item ia para o lado errado. Agora a física é calculada e aplicada no espaço da câmera, no ponto de pega do item, igual para as duas mãos:
  - virar para a direita → o item fica para trás, indo para a esquerda na tela (nas duas mãos);
  - andar/arrancar para frente → balança para trás; parar → balança para frente; andar de lado → fica para o lado oposto;
  - pular/cair/aterrissar → pequeno balanço para frente/trás;
  - lanterna pendurada e itens "em pé" na mão (que balançam com o topo) usam o sentido certo cada um.
- **Mesmos parâmetros = mesma sensação nas duas mãos.** Antes, se você configurava só a mão principal, a secundária caía em outro padrão. Agora, em cada nível (item > grupo > mod > tipo), a mão sem valor próprio usa o valor da outra mão. As duas mãos usam a mesma conta, o mesmo passo de tempo e os mesmos eixos.
- Configs da 1.5.8 são convertidas sozinhas.

## Seguir a gravidade (ângulo da câmera)
- Novo ajuste **"Seguir a gravidade: 0–100%"**: olhando para baixo, a lanterna fica **reta para o chão** em vez de ir para trás; olhando para cima, ela pende para o seu lado. Funciona junto com o balanço (a mudança de ângulo da câmera ainda balança o item).
- **"Limite: maior"**: o ângulo máximo passa a contar a partir da gravidade, para a lanterna conseguir apontar reto para baixo mesmo olhando bem para baixo.
- Presets: **Lanterna** e **Chaveiro** com gravidade 100% e limite maior; **Mochila** com 40%.
- Configs antigas recebem 60% de gravidade com limite normal (ajuste à vontade).

## Inverter balanço (depuração)
- Na tela de física: **"Inverter balanço"** por mão (desligado por padrão). Inverte só o balanço, não a gravidade. É uma saída de emergência; o normal é deixar desligado.

## Igual nas duas mãos
- Em Itens e mods (ajuste do item, ajuste do braço) e na tela de física, o botão de mão agora alterna: **Mão principal → Mão secundária → Igual nas duas mãos**.
- Ligado: um só conjunto de valores, aplicado nas duas mãos e espelhado certo na outra mão (posição X e giros espelhados pelo lado do braço).
- Ao ligar, se as mãos têm valores diferentes, pergunta qual mão manter ("Usar princ." / "Usar sec.").
- Ao desligar, os valores viram duas cópias independentes.
- Salvo por alvo (item, grupo, mod, tipo) e segue a herança normal. Na lista aparece **⇆** ao lado do nome.
- "Copiar p/…" para um alvo com "Igual nas duas mãos" copia para as duas mãos.

## Testes
- Conta da física separada (PendulumMath) com uma checagem offline (`tools/PendulumMathCheck.java`): sentido ao virar, andar, parar, andar de lado, gravidade olhando para baixo/cima, limite, inverter, e mãos principal/secundária iguais. Todos passam.
