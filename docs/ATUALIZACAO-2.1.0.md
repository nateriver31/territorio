# Atualização Território 2.1.0

Atualização aditiva sobre `main` no commit `18aae4c8f26ecc36a42017bcc91b9116330301b5`. O servidor de produção não foi acessado. Os testes usaram arquivos gerados em diretórios temporários; não foi disponibilizada uma cópia dos dados reais.

## Diagnóstico e alterações localizadas

| Área | Causa identificada | Métodos/arquivos e solução |
| --- | --- | --- |
| `/t claims` | `claimInfo()` sempre mostrava a contagem solo | `Commands`: escolher informações pelo grupo atual; separar normais, conquistados, utilizados, capacidade, pessoal e dono/tipo atual |
| Zumbi e aldeão | `canHarmEntity()` exigia autoria de jogador para dano em qualquer entidade protegida | `ProtectionListener`: exceção somente para `Zombie` atacando `Villager`; ataques de jogador/projétil e alterações de blocos continuam protegidos |
| Bônus | Não existiam saldo, categoria ou ordem persistida | `Domain`: acrescentar `BONUS`, saldo e sequência por grupo; reutilizar autorização coletiva e índices atuais |
| Mortes e livros | Não existia processamento dessas recompensas | `ProgressListener`: observar morte não cancelada, resolver `DamageSource`, deduplicar por ocorrência/vida e adicionar um livro físico ao drop |
| Recarga | Opções novas não existiam | `UpdateOptions` e `TerritorioPlugin`: validar a configuração inteira antes de substituir os valores em execução |
| Dados | `state.json` usava esquema 2 | `StateStore`: migração validada em memória para esquema 3, com cópia `pre-v3`; sem conversão de claims existentes |
| Logs | Havia somente auditoria administrativa JSONL | `ActivityLog`: escritor assíncrono separado, arquivos diários e retenção restrita a esses logs |
| Exclusão administrativa | Não existia comando seguro de exclusão | `Commands`/`Domain`: confirmação por executor e fingerprint; mutação única consistente, seguida de checkpoint |
| Discord | Não existia integração | `DiscordAnnouncements`: API pública do DiscordSRV 1.30.5 e JDA sombreada; envio assíncrono somente após persistência |
| LPC MiniMessage | A `main` 2.0.1 não tinha o placeholder da correção 2.0.2 | `TerritoryExpansion`: manter `%territorio_prefixo_minimessage%` para preservar configurações de chat já preparadas |

`RaidService` e `QuarantineListener` não foram alterados. Em `ProtectionListener`, além da exceção zumbi/aldeão, apenas a comparação de propriedade foi estendida para considerar normal e bônus do mesmo grupo como território coletivo contínuo.

## Claims Bônus

- Capacidade padrão: `6 × integrantes`; saldo inicial dos grupos novos e existentes: zero.
- O saldo conquistado e os chunks utilizados são independentes dos limites normais e pessoais: `0 <= utilizados <= conquistados <= capacidade`.
- Morte atribuída a jogador de outro grupo: vencedor ganha até +1 e derrotado perde até -1, de forma independente. Aliados de grupos diferentes contam; não exige guerra. Solo, mesmo grupo e morte sem jogador responsável não transferem bônus.
- Sem cooldown antifarm. A mesma dupla pode contar em vidas seguintes; cada morte é processada uma única vez.
- `/t claim bonus` reivindica um novo chunk. Reutiliza `territorio.claim`, líder/colíder e bloqueio de alteração coletiva durante guerra. Não converte terrenos normais ou pessoais.
- `/t unclaim` libera um bônus sem gastar saldo conquistado. A próxima reivindicação recebe uma sequência maior; não reutiliza a posição anterior.
- Se houver mais chunks que o novo saldo, apenas os bônus excedentes perdem proteção, do mais recente ao mais antigo. Nenhum bloco ou item é apagado.
- Saída/expulsão recalcula a capacidade e perde definitivamente o excedente. Retorno não restaura saldo nem transporta bônus. Claims normais e pessoais mantêm as regras anteriores.
- Bônus herdam acessos coletivos, alianças explícitas e vulnerabilidade assimétrica das guerras. Perdas por PvP continuam ocorrendo durante guerras.
- Entrada em cada chunk bônus mostra ao integrante sua posição entre os bônus atualmente protegidos; o mais recente recebe aviso de prioridade de perda. Visitantes veem apenas o grupo proprietário.
- A perda efetiva é imediata, registrada e avisada somente aos integrantes online do grupo afetado. Reduzir só saldo livre não emite aviso de chunk perdido.

## Livros

Chance padrão de 25%, com sorteio próprio e no máximo um `WRITTEN_BOOK` original assinado por ocorrência elegível. Pilhagem não influencia. O livro entra no drop da morte; pode ser guardado e negociado e obedece ao comportamento normal do servidor para drops/keepInventory.

A vítima precisa pertencer a um grupo. Um assassino solo pode obter o livro, mas não transfere bônus. Mesmo grupo, morte cancelada, mobs, ambiente sem autoria e `/kill` não geram livros. Não se usa `Player.getKiller()` antigo nem jogador mais próximo. NPCs identificados pela metadata `NPC` são ignorados.

O livro revela somente um claim elegível aleatório: nome/TAG do grupo, vítima, centro X/Z, mundo e chave da dimensão. Pessoais são excluídos por padrão; se habilitados, entram os pessoais dos integrantes atuais. A seleção ocorre depois da perda de bônus, para não revelar um chunk que acabou de perder proteção. Se o mundo estiver descarregado, o livro informa seu UUID e essa condição; não carrega mundos automaticamente.

Não há integração nova com Combat Logging: a versão/configuração efetiva do PvPManager não foi confirmada e não foi identificado um contrato confiável de morte do logger no código existente. Desconectar não simula morte, livro nem penalidade. Uma morte Bukkit real, não cancelada e com autoria válida segue as condições acima. As proteções anteriores do mesmo grupo e regras de saque foram preservadas.

## Configuração e recarga

Acrescente **somente à configuração do Território**, preservando as opções atuais:

```yaml
claim-bonus:
  enabled: true
  max-per-member: 6
  pvp:
    enabled: true
intel-book:
  enabled: true
  drop-chance: 0.25
  include-normal-claims: true
  include-bonus-claims: true
  include-personal-claims: false
discordsrv:
  enabled: true
  announce-war: true
  announce-peace: true
  channel: ""
logs:
  retention-days: 30
```

Configurações antigas sem essas chaves usam os padrões acima, sem reescrever o arquivo. `/t reload` e `/t admin reload` continuam exigindo `territorio.admin.reload`; a recarga própria não troca o JAR.

Booleans devem ser `true`/`false`, capacidades/retenção inteiros positivos e chance um número finito entre 0 e 1. YAML inválido ou valor inválido recusa toda a recarga, preservando a configuração anterior em execução.

**Redução de capacidade:** se qualquer grupo já tiver mais bônus conquistados que a capacidade proposta, a recarga inteira é recusada, sem perdas. Corrija o valor; não edite saldos no JSON. No início do servidor, uma configuração incompatível deixa o plugin em manutenção, com dados intactos, até corrigir a configuração e reiniciar.

`claim-bonus.pvp.enabled: false` suspende somente ganhos/perdas por PvP. `claim-bonus.enabled: false` também impede novas reivindicações. Desativar não apaga saldo, ordem nem proteção e não reproduz mortes ao reativar. **As regras explícitas de saída/expulsão/dissolução continuam valendo**, inclusive a redução de capacidade com aviso privado e log; desativar a geração de bônus não permite manter saldo acima da capacidade. Livros têm configuração independente; desativá-los impede novos drops e preserva os já existentes.

## Comandos e permissões novos

| Comando | Permissão e comportamento |
| --- | --- |
| `/t claim bonus` | `territorio.claim`; integrante líder/colíder, saldo disponível e fora de guerra |
| `/t admin deletar TAG` | `territorio.admin.deletar`; mostra membros/claims e prepara confirmação por 60 segundos |
| `/t admin deletar TAG confirmar` | Mesma permissão; mesmo executor, mesmo grupo revalidado |
| `/t admin discord teste` | `territorio.admin.discord`; teste sem mudar grupos, guerras ou claims |

Os dois nós administrativos novos têm `default: false`; conceda explicitamente no LuckPerms ou no mecanismo de permissões do console. Console é suportado nos dois comandos administrativos. Os comandos e permissões anteriores continuam disponíveis, inclusive aliases e autocomplete. Veja [a árvore completa](COMANDOS.md).

Excluir um grupo remove suas associações e proteções normais/bônus, convites e alianças; encerra guerras relacionadas e limpa propostas, preservando o histórico. Claims pessoais, blocos, inventários, nomes de jogadores e cooldowns permanecem. A confirmação é invalidada por mudança nos membros, identidade, saldos, relações do grupo ou claims coletivos. Sucesso só é informado depois de gravar. Se o armazenamento falhar, o modo de manutenção impede novas ações; o último arquivo válido permanece e a operação não recebe confirmação de sucesso.

## DiscordSRV 1.30.5

Usa o bot existente e seu mapeamento de chat. `channel: ""` resolve `getMainChatChannel()`; alternativamente informe a chave lógica já existente, por exemplo `global` **somente se ela estiver mapeada**. Não informe token, webhook nem ID de canal nesse campo. Nenhum arquivo do DiscordSRV é alterado.

O adaptador opcional resolve os métodos públicos `getJda()`, `getMainChatChannel()` e `getDestinationTextChannelForGameChannelName(String)`, depois `MessageChannel.sendMessage`, `allowedMentions(Collection)` e `RestAction.queue`. As assinaturas foram conferidas no JAR oficial 1.30.5. A API é acessada por reflexão pública para não adicionar uma dependência rígida; versões futuras precisam ser homologadas.

Anuncia somente declaração efetiva e paz aceita, depois do checkpoint. Proposta, recusa, consulta, exclusão administrativa e intervenção da staff não simulam paz aceita. Não reenvia eventos antigos no login/reload/reinício. `@` é neutralizado e menções JDA ficam desativadas. Rede usa a fila assíncrona do JDA; falhas não impedem diplomacia.

`/t admin discord teste` informa primeiro o envio pendente; sucesso só após callback de confirmação do Discord. Desconexão, canal ausente, falta de permissão e timeout de 30s produzem falha compreensível e log sem credenciais. Não há replay persistente ou repetição automática de mensagens que falharam; queda entre persistência e envio pode perder um anúncio. O teste pode ser repetido manualmente.

## Logs

`plugins/Territorio/logs/territorio-AAAA-MM-DD.log`, horário `America/Sao_Paulo`, escrita ordenada em executor próprio e fila limitada. Registra kills/UUIDs/grupos, alterações e limites dos saldos, reivindicações e perdas com mundo/coordenadas, mudanças de membros/grupos, livros, administração e falhas Discord.

O primeiro registro de cada dia mantém os últimos 30 dias por padrão. Somente arquivos regulares com o nome exato `territorio-AAAA-MM-DD.log`, dentro de `logs/`, participam da retenção. `state.json`, backups, auditoria e outros arquivos não são apagados. Falha ou fila cheia avisa no log do servidor; não zera dados nem desativa a proteção por uma falha no log diário. `audit.jsonl` e sua rotina anterior continuam existindo.

## Migração, backup e recuperação

O armazenamento atual está em `plugins/Territorio/state.json`, esquema 2. O esquema 3 acrescenta `bonusMaxPerMember` ao estado, `bonusEarned`/`bonusSequence` aos grupos e `bonusOrder` aos claims; mantém os demais campos e referências.

1. Com o servidor parado, faça backup externo recuperável da pasta inteira `plugins/Territorio`, do JAR atual e dos mundos. Não teste sobre a pasta original. Não use Bukkit `/reload` ou PlugMan.
2. Copie esses dados para uma instância isolada Paper 26.2/Java 25, usando os mesmos mundos/UUIDs e configurações. Instale somente o JAR 2.1.0 nessa cópia.
3. A leitura valida o arquivo antigo, migra em memória com todos os grupos em zero bônus e cria `state.json.pre-v3.bak` sem substituir uma cópia existente. O arquivo original só é substituído pelo escritor normal depois da validação. Também continua existindo `state.json.bak` do snapshot anterior.
4. Confira quantidades, cargos, permissões, claims normais/pessoais, guerras, propostas e relações antes de testar a nova mecânica. Nenhum claim antigo vira bônus e nenhuma morte anterior é aplicada.
5. Arquivo inválido, versão desconhecida, números inválidos ou falha ao criar backup interrompem o carregamento seguro. Não há criação de estado vazio no lugar do arquivo existente. Preserve os arquivos para diagnóstico e corrija/restaure manualmente com servidor parado.
6. Só após homologar a cópia, programe a troca manual do JAR em produção com outro backup atualizado. Esta entrega não faz essa instalação.

**Rollback:** a versão antiga não entende esquema 3/BONUS. Não coloque o JAR antigo sobre o novo estado. Para voltar, pare o servidor, arquive o estado atual e restaure conjuntamente o JAR e a pasta do backup anterior. Isso volta ao instante do backup e não contém ganhos/alterações posteriores; não automatizamos essa operação nem uma migração reversa. `pre-v3` é uma cópia original preservada, não substitui backups externos atualizados. Nunca apague `state.json` para tentar corrigir um erro.

Não houve acesso aos dados reais; a preservação foi validada em fixtures completas de esquema 2, incluindo inicialização do plugin, e falhas provocadas em cópias temporárias. A homologação com uma cópia dos dados reais permanece obrigatória.

## Validação e testes reais

JDK Temurin 25.0.5+8, Maven 3.9.11, Paper API `26.2.build.135-stable`, MockBukkit 4.117.0. `mvn clean verify`: **167 testes, zero falhas, erros ou ignorados; BUILD SUCCESS**. As 101 regressões da `main` foram mantidas e 66 testes foram acrescentados. [Resumo por conjunto](../dist/VALIDACAO-2.1.0.txt).

Cobertura: saldos independentes e limites, vidas repetidas/deduplicação, LIFO e reinício, saída/expulsão/retorno, preservação de normal/pessoal, configuração inválida e toggles, livros/chance/Pilhagem/elegibilidade, permissões/console/expiração/revalidação administrativa, migração completa e corrupção, falha de gravação/manutenção, privacidade, logs/retenção, envio assíncrono/ack/falhas/menções e ausência de replay. Logs SEVERE nos testes de corrupção/IO são provocados e esperados.

Ainda necessários: [roteiro 2.1.0](TESTES-MANUAIS-2.1.0.md) em Paper real, cópia dos dados de produção, clientes Java e Bedrock, farm real de aldeões, mortes/projéteis/TNT, drops/keepInventory, JAR real do PvPManager e bot/canal/permissões reais do DiscordSRV. As assinaturas de API e os testes simulados **não comprovam** essas integrações reais.
