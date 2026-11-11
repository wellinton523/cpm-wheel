# CPM Wheel 1.5.5 — Changelog

- Itens "planos" de bloco (flores, portas, mudas, plantas, trilhos, painéis de vidro, escadas de mão etc.) agora são detectados pelo modelo (sprite 2D) e seguram na mão como um item, com o cabo no punho, sem flutuar nem ficar torto. Nova categoria "bloco plano" em Ajuste do item.
- Nova tela "Itens e mods..." (em Animações da mão):
  - Modo item: buscar pelo id (ex.: poppy, oak_door) ou "Usar item da mão".
  - Modo mod inteiro: escolher um mod (namespace) da lista/busca para ajustar todos os itens dele.
  - Para cada um: X/Y/Z, tamanho, girar X/Y/Z, separado por mão principal/secundária, com prévia ao vivo e botão Resetar.
  - Prioridade: ajuste do item > ajuste do mod > padrão da categoria. Os valores somam ao padrão da categoria.
  - Itens/mods ajustados aparecem com ★ na lista. Chave "Ajustes por item/mod" liga/desliga tudo.
- Config migrada automaticamente (versão 14). Opções anteriores e o modo de compatibilidade com Better Combat não mudaram.
- NeoForge: compilado contra 21.1.262 e aceita qualquer NeoForge 21.1.x (faixa [21.1.0,)).
