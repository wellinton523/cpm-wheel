# CPM Wheel 1.9.2 — Física Live2D com o movimento real da parte do corpo no mundo

Versões: NeoForge 1.21.1 (`cpmwheel-1.9.2-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.9.2.jar`).
A configuração passa para a versão 29. A migração é automática: o movimento no mundo vem **ligado a 100%** e cada corrente fica em 100% com a parte em "Auto".

## O que mudou
- As correntes da Física Live2D agora sentem o **movimento real no mundo da parte do corpo onde estão presas**, integrado na própria física (não é uma entrada nova, não precisa configurar nada).
- Funciona como o ponto de ancoragem do Live2D: quando o ponto **acelera**, a corrente balança por inércia; movimento constante volta ao repouso; as pontas (elos "anterior") chicoteiam depois do primeiro elo.
- Reage a: pulo, queda, empurrão (dano/knockback), montaria (cavalo, barco, carrinho), elytra, virar o corpo rápido (a parte é carregada na curva), agachar/levantar, o balanço de braços e pernas ao andar e **as animações do CPM que mexem aquela parte**.
- **Sem contar duas vezes:** se o elo já tem uma entrada de velocidade num eixo (frente/andar, lado, vertical/queda/aterrissar), nesse eixo ele só recebe o movimento da parte em relação ao corpo; se já tem "virar corpo"/"virar cabeça", não recebe de novo o giro do corpo; entradas da cabeça (virar, inclinar, gravidade) não recebem de novo o giro da cabeça. Por isso as correntes e predefinições antigas não ficam muito mais fortes.
- Direção e intensidade por eixo vêm das entradas do próprio elo (mesmo sinal e tamanho do "tranco" das entradas de velocidade; nos eixos só com entradas de ângulo, a mais forte × 0,5).

## De onde vem o movimento
- **Parte:** descoberta sozinha pelo **osso do CPM que a primeira camada da corrente mexe** (o pivô dele, subindo até a parte do corpo). Se não der para descobrir, usa o nome da corrente/camada (cabelo, orelha, cauda, asa, manga…) e as entradas. Dá para escolher manualmente.
- **Terceira pessoa / quando o seu modelo é desenhado no mundo:** usa a **pose real** de cada frame, depois das animações do CPM (transformações do osso-raiz da parte e de todos os ossos acima do pivô), medindo a velocidade com o tempo real entre os frames.
- **Primeira pessoa** (o seu modelo não é desenhado): a pose é reconstruída como a do Minecraft (cabeça, andar, agachar, sentado), com o último ponto de ancoragem conhecido.
- O movimento do jogador e o giro do corpo vêm sempre das posições de cada tick (inclui veículo).

## Ajustes (discretos)
- Física Live2D, coluna da esquerda: **"Mundo: ON/OFF"** (geral) e **"Força: 100%"** (0–300%).
- Em cada corrente: **"Mundo 100%"** (−300% a 300%; 0 = desliga só nela; negativo = balança para o outro lado) e **"Parte: Auto"** (Auto mostra a parte encontrada; clique troca entre cabeça, corpo, braço esq./dir., perna esq./dir.; Shift volta).
- **Prévia ao vivo:** linha com a parte, se a pose é **real** ou **aproximada**, a velocidade da âncora (frente/lado/cima) e o ângulo somado ao 1º elo, e o botão **"Empurrão no mundo"** para testar parado.
- "Espelhar" troca braço/perna esquerdo ↔ direito.

## Proteções
- Teleporte (10+ blocos num tick), renascer, trocar de dimensão, dormir, morrer: recomeça do repouso, sem tranco.
- Travadas e picos: a velocidade e a mudança por tick têm limite (nunca um impulso gigante); pausa do jogo não gera tranco.
- Tudo à prova de falha: se a versão do CPM não deixar ler os ossos, usa a pose do Minecraft; qualquer erro só desliga o extra.

## Física de pendurado (itens na mão)
- Montado, a lanterna/chaveiro agora sente o movimento do cavalo/barco/carrinho (mesmas unidades de antes; a pé fica igual). Segue o botão geral "Mundo".

## Testes
- Testes offline: WorldMathCheck 48/48 (eixos, nada contado duas vezes, inércia = tranco da entrada equivalente, "segue", chicote, inverter, força 0, reinício sem tranco, limite de picos, espaço do modelo → mundo, giro do corpo, agachar, andar, espelhar, palpite da parte), Live2dMathCheck 142/142, LookLogicCheck 74/74, TriggerLogicCheck 61/61, EmoteLogicCheck 49/49, Feature180Check 25/25, HandsOffCheck 11/11, PendulumMathCheck OK.
