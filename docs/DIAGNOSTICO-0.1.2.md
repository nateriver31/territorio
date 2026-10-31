# Diagnóstico do Território 0.1.2

## Evidência examinada

O checkout recebido estava sem fontes ou commits. A análise foi feita no JAR fornecido pelo proprietário, `Territorio-0.1.2.jar` (31.757 bytes, SHA-256 `b82d8d6014fbf75544ccdeeb550773a4979589799c3a01e1a73351d6d3bdf448`). O conteúdo foi inspecionado e as quatro classes principais foram decompiladas com CFR 0.152, baixado do Maven Central e conferido contra o checksum publicado. A decompilação serve para recuperar o comportamento observável; não é o código-fonte original e contém alguns artefatos de tipos locais.

O JAR declara `api-version: '26.2'`; isso sozinho não demonstra que a API existe ou que o artefato carregou nesse servidor. A classe principal é `dev.nateriver.territorio.TerritorioPlugin`.

## Estrutura encontrada

- `TerritorioPlugin`: carrega configuração/dados, registra cinco comandos e o listener, salva ao desabilitar.
- `TerritoryManager`: concentra grupos, convites, claims, alianças, guerras e acesso em uma classe, com métodos `synchronized`.
- `TerritorioCommands`: comandos de jogador/administração, ajuda por chat e sugestões.
- `ProtectionListener`: eventos de blocos, recipientes, entidades, explosões, pistões e líquidos.

O armazenamento é `plugins/Territorio/data.yml`. Salva grupos (`name`, `leader`, lista `members`, lista `allies`), claims (`type`, `owner`) e guerras (`attacker`, `defender`, `started-at`). Os identificadores de jogadores, grupos e mundos são UUID. O índice de claim é uma chave `UUID-do-mundo;x-do-chunk;z-do-chunk` em mapa; a consulta por chunk não percorre todos os claims. As contagens por proprietário, por outro lado, percorrem o mapa inteiro.

As gravações YAML são completas, síncronas e feitas dentro das operações de jogo. Não há arquivo temporário com substituição atômica, backup ou detecção transacional de gravação incompleta. Convites de grupo, convites de aliança e propostas de paz existem somente em memória. `load()` limpa essas coleções e não as reconstrói.

## Claims e ingresso em grupos

Existem somente os tipos `PLAYER` e `GROUP`.

- Solo recebe até nove claims `PLAYER` por padrão.
- Criar grupo converte **todos** os seus claims `PLAYER` em `GROUP` pelo método `convertPersonalClaimsToGroup`.
- Aceitar convite faz a mesma conversão, após verificar o novo limite coletivo.
- O limite coletivo padrão é `membros * (9 + 1)`, não nove coletivos mais um pessoal separado.
- **Não existe um chunk pessoal privado extra** no modelo 0.1.2. Tampouco existe configuração individual de permissões pessoais.
- Ao sair ou ser expulso, os claims coletivos continuam coletivos. A capacidade cai com a quantidade de membros, mas o sistema não corrige nem documenta grupos acima da capacidade.
- Dissolver remove somente registros de claims coletivos, sem apagar blocos, mas também remove guerras desse grupo sem histórico.

Não há cooldown de saída. O líder não pode sair: precisa transferir liderança ou dissolver. Não existe sucessão automática. Todos os integrantes são tratados como membro ou líder; colíder e oficial não existem. Convites não expiram.

## Acesso e vulnerabilidade de guerra

`canAccess` oferece uma única decisão para todas as interações:

1. `territorio.admin.bypass` permite tudo.
2. Terreno sem claim permite tudo.
3. Claim `PLAYER` aceita somente o dono.
4. Claim `GROUP` aceita todos os membros do mesmo grupo.
5. Claims de um grupo atacante em qualquer guerra ficam abertos a **todos**.
6. Claims de um defensor também podem ser acessados por integrantes do grupo atacante daquela guerra.

Assim, a assimetria solicitada tem precedente confirmado: o atacante assume a abertura global dos próprios claims, enquanto o defensor continua protegido contra terceiros. A 2.0 deve preservar essa regra explicitamente, sem abrir todos os claims de todos os participantes.

Alianças são bidirecionais e persistem como listas de UUID. Contudo, `canAccess` não consulta `allies`: não existe permissão de acesso de aliados implementada. Não há distinção entre mecanismos, recipientes e construção.

Explosões são proibidas em solo e permitidas em claims coletivos se o grupo for atacante **ou defensor**. A definição de zona compara tipo e UUID do proprietário; essa é uma boa base para avisos sem repetição entre chunks do mesmo território.

## Guerras e bug de paz confirmado

`declareWar` é início imediato pelo líder, sem aceitação e **sem checar se o líder defensor está online**. Armazena UUID de atacante/defensor e instante de início. Não há estado encerrado ou histórico. Aliados precisam romper a aliança antes de declarar guerra.

A causa da notificação de paz está confirmada no fluxo de comando:

- `TerritoryManager.requestPeace` encontra a guerra e adiciona o UUID do remetente a `peaceRequests[warId]`.
- `TerritorioCommands.handleWar`, caso `paz`, chama apenas `send(remetente, requestPeace(...))`.
- Diferentemente do convite de aliança, não chama `notifyGroupLeader` nem `broadcastGroup`. O destinatário nunca recebe a mensagem.
- O pedido não chama `save()` e o formato salvo nem contém propostas de paz; reiniciar elimina o pedido.
- `acceptPeace` exige pedido do outro lado e remove a guerra, mas não existe ação para **recusar** paz.

A correção exige notificar os membros online do grupo destinatário, identificar o grupo solicitante, persistir o pedido e oferecer aceitação/recusa com liderança e guerra ativa validadas. Ao reconectar, o líder precisa conseguir descobrir propostas pendentes, mesmo que estivesse offline no envio.

## Comandos existentes que merecem continuar acessíveis

| Entrada antiga | Funções |
| --- | --- |
| `/claim`, `/unclaim`, `/claims` | Criar, remover e consultar claim |
| `/grupo` (`/g`) | Ajuda por chat |
| `/grupo criar <nome>` | Criar grupo sem TAG |
| `/grupo convidar`, `aceitar`, `recusar` | Convites |
| `/grupo sair`, `expulsar <jogador>`, `lider <jogador>` | Membros/liderança |
| `/grupo info [grupo]`, `lista` | Consulta |
| `/grupo desfazer confirmar` | Dissolução explícita |
| `/grupo alianca convidar/aceitar/recusar/remover/lista` | Diplomacia |
| `/grupo guerra declarar/paz/aceitarpaz/status` | Guerra/paz |
| `/territorio reload` (`/terr`) | Recarregar configuração |
| `/territorio guerra encerrar <A> <B>` | Intervenção administrativa |

`/help` global não é registrado. Convite, expulsão e transferência de liderança exigem alvo online nos comandos antigos. O autocompletar lista ações sem filtrar o cargo. `territorio.admin` e `territorio.admin.bypass` eram `default: op`, sem auditoria.

## Outros defeitos e lacunas relevantes

- Saída, expulsão, transferência de líder e dissolução não bloqueiam grupos em guerra. Dissolver encerra guerras como efeito colateral.
- Qualquer integrante pode criar claim coletivo; somente o líder pode remover. A 2.0 exige líder/colíder para administrar ambos.
- `unclaim` concede exceção de guerra a quem possui `territorio.admin`, sem autorização excepcional separada ou registro.
- `InventoryOpenEvent` só identifica donos `BlockState` e `Entity`; não trata `DoubleChest` explicitamente.
- Faltam eventos de transferência automática de itens (`InventoryMoveItemEvent`, `InventoryPickupItemEvent`), permitindo caminhos de acesso indireto não cobertos.
- Pistões comparam bloco movido e destino, mas não incluem a origem do mecanismo na decisão. Líquidos e fogo entre claims do mesmo proprietário continuam possíveis; isso exige endurecimento no contexto de saques com orçamento de quebras.
- Não existem TAG, ranks, PlaceholderAPI, `/roubar`, cooldowns, histórico, notificações de território, partículas ou livro de ajuda.
- Não foram encontrados wipes automáticos, alteração de inventário, manipulação direta de mapas ou interceptação do PvP entre jogadores.

## O que reutilizar

É adequado reutilizar os conceitos de UUID, chave de chunk com UUID do mundo, consulta em mapa, identidade do território por tipo/dono, migração explícita de claims solo para grupo e abertura assimétrica do atacante. Não é recomendável reutilizar a classe central como base de toda a 2.0: o modelo não representa ranks, chunk extra, proposta persistente, permissões por ação, saques ou auditoria.

O diagnóstico foi estático. Nenhuma destas observações equivale a um teste em Paper, Geyser ou no Survival+ real.
