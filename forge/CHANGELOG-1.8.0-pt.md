# CPM Wheel 1.8.0 — Prévia ao vivo, extras da física, presets, teclas de emotes, encadear animações e perfis

Versões: NeoForge 1.21.1 (`cpmwheel-1.8.0-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.8.0.jar`).
A configuração passa para a versão **24** e é migrada sozinha da 23. **Tudo o que é novo vem desligado** e não faz nada até você ligar e configurar.

## Física Live2D
- **Prévia ao vivo** (botão "Prévia"): gráficos dos últimos 5 s do ângulo (ciano) e do valor (amarelo) de cada elo, desenho animado da corrente e controles "Sim." que fingem andar, vertical, giro da cabeça, vento, olhar e voz só enquanto a tela está aberta. O botão "Impacto!" dispara um tranco de teste.
- **Entradas novas:**
  - **Queda** (só a velocidade de queda) e **Gravidade/olhar** (contrário da inclinação da cabeça: o cabelo fica pendurado ao olhar para cima/baixo). A "Velocidade vertical" (pulo e queda) já existia.
  - **Vento frente/trás** e **Vento lados**: rajadas; chuva ×1,8, tempestade ×3; mais forte no alto e em biomas abertos, fraco em florestas e embaixo de teto.
  - **Impacto: dano** (pelo dano levado) e **Impacto: aterrissar** (pela velocidade da queda): um tranco único que decai.
  - **Olhar: lados** e **Olhar: cima/baixo**: ângulo até o jogador (ou qualquer criatura, opcional) mais perto dentro do raio.
  - **Voz** (Simple Voice Chat, opcional): boca abrindo/fechando enquanto você fala.
  - **Camada/animação (movimento)**: lê uma animação/camada do CPM em modo **taxa** (o quanto muda), **valor** ou **oscilar** (empurrão ±45° na frequência escolhida enquanto a animação toca — ex.: asas batendo empurram o cabelo). Tem peso, sinal e Tranco ↔ Mantido como as outras.
- **Extras** (botão "Extras"): liga/desliga e força de Vento, Impactos, Olhar (raio, jogadores/criaturas), Voz (mostra se o Simple Voice Chat foi encontrado) e Animação em movimento.
- **Respirar e piscar**: geradores automáticos que movem uma camada de valor (onda suave ou piscadas em intervalos aleatórios), com período, mín./máx., variação e tempo de olho fechado.
- **Limite suave** por elo (botão ⚙): o ângulo freia perto do mín./máx. em vez de passar.
- **Espelhar**: copia a corrente com esquerda/direita trocadas (giros e entradas laterais invertidos). Camadas com esq/dir, left/right, e/d no nome são trocadas sozinhas; as outras você escolhe numa tela. Tem "Desfazer".
- **Presets**: salvar/importar correntes (uma ou todas, com os geradores) em `config/cpmwheel/l2d/` ou copiar/colar como texto `CPMW-L2D1:…`. Importar sempre ADICIONA.
- Shift+clique na fonte de uma entrada volta para a fonte anterior.

## Teclas de emotes
- Até 8 teclas (Controles › CPM Wheel, sem tecla por padrão) que tocam um emote escolhido sem abrir a roda. Se a animação estiver num botão da roda, a tecla age como o botão.
- Ligue em Configurações › Roda e botões › Teclas de emotes.

## Encadear animações
- Regras "quando A terminar (ou começar), depois de X ms, ativar/desativar/alternar B". Ex.: "acenar" termina → "sorrir".
- Ligue em Configurações › Efeitos › Encadear animações.

## Perfis automáticos por modelo
- Cada modelo do CPM já tinha a própria configuração. Agora você pode vincular vários modelos a um **perfil com nome** (roda, gatilhos, física e mãos), e trocar de modelo no CPM troca o perfil sozinho, com aviso opcional.
- Configurações › Avançado › Perfis automáticos por modelo. Desligado por padrão.

## Padrões novos de mãos e itens
- Os ajustes por tipo de item (escudo, espada, outros, tridente, besta, arco, ferramenta, machado, lanterna, bloco plano), o ajuste do braço (principal/secundária) e a física da lanterna agora vêm com valores afinados por padrão.
- **Suas configurações salvas não mudam.** Só entradas que ainda estavam zeradas (o padrão antigo) passam a usar os novos padrões. "Restaurar" volta para o novo padrão.

## Outros
- Ícone novo do mod (aparece na lista de mods).
- Licença MIT.
