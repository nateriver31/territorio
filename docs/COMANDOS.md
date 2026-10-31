# Comandos e permissões

`/territorio` é o comando principal; `/t` e `/terr` são aliases. Se `/t` já tiver dono, ele é preservado e o log informa o conflito. Use `/territorio` ou `/territorio:territorio`. `/grupo` e `/g` aceitam os mesmos subcomandos. `/claim`, `/unclaim` e `/claims` continuam disponíveis.

## Jogadores

| Comando | Função |
| --- | --- |
| `/t`, `/t ajuda`, `/t help` | Abre o manual em livro, sem entregar item permanente |
| `/t criar ROMA Império Romano` | Cria grupo com TAG e nome; identidade é fixa para o líder |
| `/t info [TAG ou nome completo]` | Consulta grupo e/ou claim atual |
| `/t lista [página]` | Lista grupos; `/t grupos` é equivalente |
| `/t membros [TAG]` | Lista cargos e membros |
| `/t claim` | Reivindica chunk solo ou coletivo |
| `/t claim pessoal`, `/t pessoal` | Protege o único chunk extra pessoal do integrante |
| `/t unclaim`, `/t desclaim` | Remove proteção própria autorizada; não remove blocos |
| `/t claims` | Consulta terreno atual e claims pessoais/solo |
| `/t confiar Jogador`, `/t desconfiar Jogador` | Concede/revoga acesso às claims individuais do dono; não concede administração |
| `/t convidar Jogador` | Convite de cinco minutos |
| `/t aceitar`, `/t recusar` | Decide convite de grupo |
| `/t sair` | Saída com duas horas de cooldown, fora de guerra |
| `/t expulsar Jogador` | Expulsa membro comum, sem cooldown de expulsão |
| `/t cargo Jogador membro\|oficial\|colider` | Gerencia cargos dentro da hierarquia |
| `/t lider Jogador confirmar` | Transfere liderança fora de guerra |
| `/t dissolver confirmar`, `/t desfazer confirmar` | Dissolve grupo fora de guerra; libera apenas claims coletivos |
| `/t guerra declarar TAG` | Inicia guerra por declaração, exigindo os dois líderes online |
| `/t guerra status` | Consulta guerras ativas |
| `/t paz propor\|aceitar\|recusar TAG` | Negocia paz, com proposta persistida e notificações aos grupos |
| `/t guerra paz TAG`, `/t guerra aceitarpaz TAG`, `/t guerra recusarpaz TAG` | Acesso compatível à negociação de paz |
| `/t guerras [TAG] [página]` | Histórico básico de guerras |
| `/t aliados lista` | Lista alianças e acessos concedidos |
| `/t aliados propor\|aceitar\|recusar\|remover TAG` | Decide alianças; `/t alianca` também funciona |
| `/t aliados permissao TAG portas\|recipientes\|construcao sim\|nao` | Permissões direcionais do seu território para o aliado |
| `/roubar`, `/roubar status` | Inicia/consulta saque solo |
| `/verchunk` | Alterna partículas individuais nos cantos do chunk |

Todos os membros usam claims coletivos. Somente líder/colíder reivindicam ou removem coletivos. Oficial também convida. Diplomacia e dissolução são exclusivas do líder. Durante guerra, saída, expulsão, transferência de liderança, dissolução e alteração de claims coletivos são bloqueadas; entradas também são bloqueadas para não transferir terrenos durante conflito.

A confiança pessoal vale para solo e chunk pessoal do dono. Não é acesso automático de grupo. Ela é persistente até revogação. No saque, o invasor autorizado fica sujeito às restrições do saque mesmo se já era de confiança.

## Staff

| Comando | Permissão |
| --- | --- |
| `/t admin inspecionar [TAG]` | `territorio.admin.inspect` |
| `/t admin nome TAG Novo nome` | `territorio.admin.rename` |
| `/t admin tag TAG NovaTAG` | `territorio.admin.rename` |
| `/t admin lider TAG Jogador` | `territorio.admin.leader` |
| `/t admin removerclaim` | `territorio.admin.unclaim` |
| `/t admin guerras [TAG] [página]` | `territorio.admin.wars` |
| `/t admin saques [página]` | `territorio.admin.raids` |
| `/t admin reload`, `/t reload` | `territorio.admin.reload` |
| `/t admin bypass` | `territorio.admin.bypass` |
| `/t admin encerrarguerra TAG1 TAG2` | `territorio.admin.wars` **e** `territorio.admin.force` |

Correções de nome/TAG/liderança e remoção de claims coletivos são bloqueadas na guerra. A intervenção excepcional usa o argumento final `--forcar` e requer **também** `territorio.admin.force`. O administrador e os valores anteriores/novos são registrados em `audit.jsonl`. A consulta de saques é privada da staff e revela invasor, vítima, horários, blocos e resultado.

Bypass começa desativado e é retirado ao desconectar/reiniciar. Não concede permissão para alterar os registros de claims ou ignorar bloqueios de guerra nos comandos comuns. Não atribua o nó indiscriminadamente. A alteração do modo bypass é auditada; alterações físicas de blocos seguem os eventos normais e podem ser auditadas pelo CoreProtect.

## LuckPerms

Nós comuns, `default: true`: `territorio.use`, `territorio.claim`, `territorio.grupo`, `territorio.roubar`, `territorio.verchunk`.

Todos os nós administrativos da tabela, inclusive bypass e força, têm `default: false`: não há concessão automática por OP. Exemplo de concessão **manual**:

```text
lp group moderador permission set territorio.admin.inspect true
lp group administrador permission set territorio.admin.rename true
lp group administrador permission set territorio.admin.reload true
```

Escolha cada autorização; não é necessário um wildcard. O livro comum não lista administração. Os comandos globais `/help` e `/ajuda` não são registrados pelo Território.
