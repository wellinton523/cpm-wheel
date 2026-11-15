# CPM Wheel 1.6.0

Versões: NeoForge 1.21.1 (`cpmwheel-1.6.0-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.6.0.jar`).
A configuração passa para a versão 19 e é migrada automaticamente: as regras de efeito antigas viram uma condição "Efeito" e continuam funcionando igual.

## Gatilhos com novas condições
- Em **Gatilhos**, cada regra agora tem o botão **Condições (N)…**, que abre a tela **Condições da regra**.
- Tipos de condição:
  - Efeito (com nível mínimo);
  - Pouca vida (limite em corações);
  - Levou dano (nos últimos X segundos);
  - Noite, Dia, Chuva, Tempestade, Debaixo d'água;
  - Bioma (id como `desert` ou tag como `#minecraft:is_ocean`);
  - Dimensão (`the_nether`, `the_end`…);
  - Agachado, Correndo;
  - Segurando item: busca igual à do "Criar grupo" ou um grupo salvo, em qualquer mão, na principal ou na secundária.
- No topo, **TODAS (E)** / **QUALQUER (OU)** define como as condições se combinam. Cada linha tem **É / NÃO** para inverter a condição.
- O botão **+ Condição** abre uma lista de tipos com a explicação de cada um.
- Bioma e Dimensão têm o botão **Aqui**, que preenche com o lugar onde você está.
- A tela mostra ao vivo **sim/não** em cada condição e se a regra ligaria agora.
- Quando as condições deixam de valer, o estado anterior volta, como já acontecia com os efeitos.
- A notificação do gatilho mostra a condição em texto ("Noite e Chuva → …").
  - Regras de efeito mostram o ícone do efeito.
  - Os outros tipos mostram o ícone de um item: relógio, balde d'água, obsidiana etc.
- Novo interruptor **Extras: ON/OFF** na tela Gatilhos. Desligado, só as regras feitas apenas de efeitos funcionam, como na 1.5.9.

## Transições com tempo
- Os atrasos "só depois de X s" para ligar e para restaurar continuam na regra. Eles evitam que a animação pisque quando a condição liga e desliga rápido (debounce).
- O botão **Transição…** da regra permite configurar:
  - uma animação que toca antes da troca, escolhida da roda ou do CPM;
  - a duração da transição (0,1 a 10 s);
  - **Também ao restaurar**, para tocar a transição antes de voltar ao estado anterior.
- Se a condição some durante a transição, nada é trocado e a animação de transição é desfeita.
- Novo interruptor global **Transição: ON/OFF** na tela Gatilhos.
- O CPM não tem fade suave entre animações. Por isso a transição é feita com tempo + animação.

## Sons
- **Botões da roda:** no editor, o botão **♪ Som** ao lado da tecla define um som ao ativar e, opcionalmente, outro ao desativar.
- **Regras:** o botão **Som…** define um som quando a regra liga e, opcionalmente, outro ao restaurar.
- O seletor de sons tem busca e lista todos os sons do jogo, dos mods e dos resource packs. Cada som tem ▶ para ouvir antes.
- Também dá para ajustar volume (0–200%) e tom (50–200%) e ouvir uma prévia.
- **Sons personalizados (todos): ON/OFF** liga ou desliga tudo de uma vez.
- Os sons tocam só para você (no cliente).

## Testes
- Novo teste offline `tools/TriggerLogicCheck.java` com 50 verificações:
  - condições E/OU/NÃO e cada tipo;
  - dia e noite, inclusive Nether/End;
  - dimensão;
  - debounce, incluindo liga/desliga rápido sem piscar.
