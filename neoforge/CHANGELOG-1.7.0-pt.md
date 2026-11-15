# CPM Wheel 1.7.0 — Física Live2D

Versões: NeoForge 1.21.1 (`cpmwheel-1.7.0-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.7.0.jar`).
A configuração passa para a versão 20 e é migrada automaticamente da 19. A Física Live2D vem **desligada**, então nada muda até você ligar.

## Física Live2D
Faz cabelo, cauda e outras partes do modelo balançarem com o movimento. O mod calcula uma física de mola por elo e escreve o resultado numa **camada de valor (Value Layer)** do seu modelo CPM. O CPM interpola entre os quadros, e o movimento fica suave.

- Onde fica: **Configurações › Gatilhos de efeito › Física Live2D** (botão "Abrir Física Live2D…").
- **Coluna da esquerda:**
  - **Física Live2D: ON/OFF**;
  - **Sinc. multiplayer**: ligado, os valores vão para o servidor como qualquer animação do CPM; desligado, só você vê e nada é enviado;
  - **Pausar c/ gatilho**: a física para enquanto uma regra de Gatilhos segura a mesma animação;
  - **Zerar**: volta tudo ao repouso;
  - **Como montar**: passo a passo para o editor do CPM;
  - a lista de correntes, mais **+ Cabelo** e **+ Cauda** (correntes prontas de 2 elos).
- **Coluna do meio:**
  - nome da corrente, **Ativa**, **Remover**, **▲ Subir / ▼ Descer**;
  - a lista de elos, com o **valor ao vivo** de cada um (barra + número);
  - **+ Elo** (até 8 por corrente).
- **Coluna da direita (elo selecionado):**
  - **Camada**: lista todas as camadas de valor, inclusive as de comando/ocultas, ou aceita um nome digitado;
  - **Entrada**: Giro da cabeça, Inclinação da cabeça, Giro do corpo, Velocidade (andar), Velocidade vertical ou Elo anterior;
  - **Saída**: atraso / segue;
  - **Inverter**;
  - **Comprimento**, **Peso**, **Amortecimento**, **Intensidade**;
  - **Mín/Máx**: o ângulo que vira valor 0 e valor máximo;
  - mover o elo (▲ ▼) e **✖ Elo**;
  - embaixo, "Agora: X° → valor Y de Z".
- Com a entrada "Elo anterior", as pontas atrasam em relação ao centro, como uma corrente.
- Tudo é salvo por modelo, como o resto da configuração.
- Os valores são escritos no começo do tick (antes do CPM enviar), 20 vezes por segundo, e só quando o número muda.
- O seu próprio modelo mostra o valor na hora (previsão local), inclusive em servidores sem CPM.

### Como montar no CPM (resumo)
1. Osso "cabelo_centro" filho da cabeça, com o pivô na raiz do cabelo. Osso "cabelo_pontas" filho do centro, com o pivô onde a mecha dobra.
2. Uma animação para o centro e outra para as pontas:
   - Value Layer, Max value 255, Interpolate Value ligado;
   - Command Activated + Hidden;
   - Linear Single, 1000 ms;
   - Quadro 1 = -45°, Quadro 2 = +45°. O meio é o repouso.
3. No jogo: "+ Cabelo", elo 1 = camada do centro (Giro da cabeça), elo 2 = camada das pontas (Elo anterior). Ligue a Física Live2D.

## Testes
- Novo `tools/Live2dMathCheck.java` (28 verificações):
  - mapeamento ângulo → valor, incluindo limites, faixa invertida e camadas pequenas;
  - giro contínuo do yaw;
  - mola, amortecimento, comprimento e peso;
  - corrente com pontas atrasando o centro;
  - estabilidade em configurações extremas e recuperação de NaN.
- Os testes anteriores continuam passando: TriggerLogicCheck 50/50 e PendulumMathCheck OK.
