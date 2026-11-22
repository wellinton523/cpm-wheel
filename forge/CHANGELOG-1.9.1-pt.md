# CPM Wheel 1.9.1 — Animações da mão desligáveis por mod (e por item)

Versões: NeoForge 1.21.1 (`cpmwheel-1.9.1-neoforge-21.1.262.jar`) e Forge 1.20.1 (`cpmwheel-1.9.1.jar`).
A configuração passa para a versão 28. A migração é automática e **nada muda**: todos os mods continuam com as animações ligadas até você desligar.

## Animações da mão por mod
- Se um mod desenha os itens dele do próprio jeito e as animações da mão do CPM Wheel deixam esquisito, agora dá para **desligar só para esse mod**.
- Onde fica: Configurações › "Animações da mão (detalhes)…" › **"Itens e mods..."** › **"Modo: mod inteiro"** › escolha o mod (a lista tem todos os mods carregados que têm itens; a busca procura pelo id e pelo nome do mod) › botão **"Anim. mãos: …"**.
- Clique para trocar: **ON** → **OFF** (nas duas mãos) → **OFF princ.** (só a mão principal) → **OFF secund.** (só a secundária).
- Cada mão é avaliada pelo item que ela segura: com uma arma do mod na mão principal e uma tocha na secundária, só a principal fica sem as animações.
- Desligado = a mão fica como sem o CPM Wheel (desenho normal do Minecraft/CPM): sem balanço, inércia, poses, golpes, braço com o item no punho, física de pendurado. O "Ajuste do braço..." também não é aplicado nessa mão, para ficar igual ao normal.
- Sem pulo: os movimentos somem/voltam com uma transição suave (~0,15 s). A troca do desenho do item acontece junto com a animação de troca de item do próprio jogo.
- Na lista, ✖ marca mods/itens com as animações desligadas (½✖ = só uma mão) e ✔ marca itens forçados como ligados.

## Exceção por item
- Em **"Modo: item"**, o mesmo botão define uma exceção acima da regra do mod: **= mod** (segue o mod) → **ON** (mesmo com o mod desligado) → **OFF** → **OFF princ.** → **OFF secund.**.

## Comando
- `/cpmwheel mod <modid> maos` mostra o estado; `/cpmwheel mod <modid> maos on|off|principal|secundaria` muda.
- `/cpmwheel item <id do item> maos on|off|principal|secundaria|herdar` (ex.: `/cpmwheel item minecraft:diamond_sword maos on`).
- `/cpmwheel maos lista` mostra tudo o que está desligado.
- "principal" / "secundaria" = desliga só naquela mão.

## Observação
- O CPM Wheel só anima a mão em **primeira pessoa**. Em terceira pessoa quem desenha o braço é o CPM/Minecraft, então não existe opção separada de terceira pessoa.

## Testes
- Testes offline: HandsOffCheck 11/11 (regra do mod, exceção do item, mãos separadas, ciclos, comando), mais os testes antigos.
