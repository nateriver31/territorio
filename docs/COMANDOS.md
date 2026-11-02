# Comandos e permissões

`/territorio` é o comando principal; `/t` e `/terr` são aliases. Se `/t` já tiver dono, ele é preservado e o log informa o conflito. Use `/territorio` ou `/territorio:territorio`. `/grupo` e `/g` aceitam os mesmos subcomandos. `/claim`, `/unclaim` e `/claims` continuam disponíveis.

## Árvore principal

```text
/t
├─ claim [pessoal]
├─ unclaim
├─ claims
├─ confiar Jogador
├─ desconfiar Jogador
├─ grupo
│  ├─ criar TAG Nome
│  ├─ convidar Jogador
│  ├─ aceitar
│  ├─ recusar
│  ├─ info [TAG ou nome completo]
│  ├─ lista [página]
│  ├─ membros [TAG ou nome completo]
│  ├─ cargo Jogador membro|oficial|colider
│  ├─ lider Jogador confirmar
│  ├─ expulsar Jogador
│  ├─ sair
│  └─ dissolver confirmar
├─ guerra TAG
├─ guerra status
├─ guerra historico [TAG] [página]
├─ paz TAG
├─ paz aceitar [TAG]
├─ paz recusar [TAG]
├─ aliados [lista]
├─ aliados propor|aceitar|recusar|remover TAG
├─ aliados permissao TAG portas|recipientes|construcao sim|nao
└─ admin … (funções e permissões preservadas)

/verchunk
/roubar
/roubar status
```

## Jogadores

| Comando | Função |
| --- | --- |
| `/t`, `/t ajuda`, `/t help` | Abre o manual em livro, sem entregar item permanente |
| `/t claim` | Reivindica chunk solo ou coletivo |
| `/t claim pessoal`, `/t pessoal` | Protege o único chunk extra pessoal do integrante |
| `/t unclaim`, `/t desclaim` | Remove proteção própria autorizada; não remove blocos |
| `/t claims` | Consulta terreno atual e claims pessoais/solo |
| `/t confiar Jogador`, `/t desconfiar Jogador` | Concede/revoga acesso às claims individuais do dono; não concede administração |
| `/t grupo criar ROMA Império Romano` | Cria grupo com TAG e nome; identidade é fixa para jogadores |
| `/t grupo info [TAG ou nome completo]` | Consulta o grupo informado ou seu próprio grupo |
| `/t grupo lista [página]` | Lista grupos |
| `/t grupo membros [TAG ou nome completo]` | Lista cargos e membros |
| `/t grupo convidar Jogador` | Convite de cinco minutos |
| `/t grupo aceitar`, `/t grupo recusar` | Decide convite de grupo |
| `/t grupo sair` | Saída com duas horas de cooldown, fora de guerra |
| `/t grupo expulsar Jogador` | Expulsa membro comum, sem cooldown de expulsão |
| `/t grupo cargo Jogador membro\|oficial\|colider` | Gerencia cargos dentro da hierarquia |
| `/t grupo lider Jogador confirmar` | Transfere liderança fora de guerra |
| `/t grupo dissolver confirmar` | Dissolve grupo fora de guerra; libera apenas claims coletivos |
| `/t guerra TAG` | Inicia guerra imediatamente por declaração unilateral; exige os dois líderes online, sem aceitação do defensor |
| `/t guerra status` | Consulta guerras ativas do seu grupo |
| `/t guerra historico [TAG] [página]` | Histórico de guerras, com início, fim e resultado; filtro e página opcionais |
| `/t paz TAG` | Envia proposta de paz persistida e notifica os grupos |
| `/t paz aceitar [TAG]`, `/t paz recusar [TAG]` | Decide uma proposta recebida; somente líder, com TAG obrigatória se houver várias propostas |
| `/t aliados`, `/t aliados lista` | Lista alianças e acessos concedidos |
| `/t aliados propor\|aceitar\|recusar\|remover TAG` | Decide alianças; `/t alianca` também funciona |
| `/t aliados permissao TAG portas\|recipientes\|construcao sim\|nao` | Permissões direcionais do seu território para o aliado |
| `/roubar`, `/roubar status` | Inicia/consulta saque solo |
| `/verchunk` | Alterna partículas individuais nos cantos do chunk |

Todos os membros usam claims coletivos. Somente líder/colíder reivindicam ou removem coletivos. Oficial também convida. Diplomacia e dissolução são exclusivas do líder. Durante guerra, saída, expulsão, transferência de liderança, dissolução e alteração de claims coletivos são bloqueadas; entradas também são bloqueadas para não transferir terrenos durante conflito.

A confiança pessoal vale para solo e chunk pessoal do dono. Não é acesso automático de grupo. Ela é persistente até revogação. No saque, o invasor autorizado fica sujeito às restrições do saque mesmo se já era de confiança.

Membros do mesmo grupo não causam dano PvP entre si, em qualquer local. Grupos diferentes, aliados, jogadores de confiança e jogadores solo continuam com PvP normal. A confiança ou aliança não transforma jogadores em integrantes do mesmo grupo.

### Compatibilidade

As formas antigas continuam disponíveis: `/t criar`, `/t convidar`, `/t aceitar`, `/t recusar`, `/t lista`, `/t grupos`, `/t membros`, `/t cargo`, `/t lider`, `/t expulsar`, `/t sair`, `/t dissolver` e `/t desfazer`. O atalho `/t info [TAG ou nome completo]` conserva a consulta anterior de grupo e/ou claim; a forma nova `/t grupo info` consulta somente o grupo.

`/t guerra declarar TAG` equivale a `/t guerra TAG`. `/t guerras [TAG] [página]` continua como atalho do histórico. `/t paz propor TAG` equivale a `/t paz TAG`. Os atalhos `/t guerra paz TAG`, `/t guerra aceitarpaz TAG` e `/t guerra recusarpaz TAG` continuam exigindo TAG explícita.

`/t guerra PAZ` declara guerra a um grupo cuja TAG seja `PAZ`; `/t guerra paz ROMA` propõe paz a ROMA. `status` e `historico` são subcomandos reservados, sem tentativa de interpretá-los como grupos.

Para decidir paz sem TAG, contam apenas propostas **recebidas** em guerras ativas. Sem propostas, a operação é recusada; com uma, ela é selecionada; com várias, a mensagem lista as TAGs e pede escolha explícita. Propostas enviadas pelo próprio grupo não entram nessa seleção. Decisões preservam histórico, notificações e persistência.

### Livro `/t` e regras de proteção

O livro usa o título e autor Território e páginas curtas em cinco assuntos: claims e proteção; grupos e cargos; guerras e paz; alianças; saques. Mostra os limites de claims configurados, chunk pessoal, cooldowns e regras de guerra e saque. A leitura no cliente Bedrock exige homologação real.

Explosões atribuídas a jogadores respeitam propriedade, mesmo grupo, confiança e permissões temporárias da guerra. Só pertencer a um grupo aliado não autoriza explosões; permissões explícitas de construção continuam válidas. Creepers sem atribuição confiável preservam claims. TNT sem autoria pode destruir claims alheios, mesmo fora de guerra; a exceção inclui autoria perdida ao recarregar chunks e é regulada pelas regras do servidor. Nos claims solo da vítima de um saque ativo, explosões continuam sem destruir blocos para preservar o limite de 30.

Fogo natural, lava e raios podem iniciar incêndios que consomem blocos. Acendimento direto por jogador continua sujeito à proteção. Durante saques, permanecem os bloqueios localizados contra remoção indireta de blocos.

No saque, dono e invasor podem usar botões, alavancas e placas de pressão. O saque **não concede abertura manual de portas e alçapões**. **Circuitos acionados pelo invasor podem abrir portas e alçapões**, conforme a regra aprovada. Terceiros não recebem acesso. Recipientes preservam as permissões e restrições existentes; pistões, dispensers e outros mecanismos não podem contornar o limite de blocos.

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
