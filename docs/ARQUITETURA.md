# Arquitetura e decisões

## Módulos

- `Domain`: regras e estado sem dependência Bukkit, relógio injetável nos testes. Índice de membros e de guerras ativas; claim consultado por `(UUID mundo, chunkX, chunkZ)` em mapa.
- `StateStore`: snapshots JSON imutáveis enviados a um único escritor assíncrono, `fsync` do arquivo temporário e substituição atômica. Backup anterior e auditoria JSONL. Nunca acessa diretórios de outros plugins.
- `RaidService`: sessões temporárias em memória, deadlines absolutos, cooldowns persistentes, contagem privada, títulos curtos e atributo de mineração transitório. Só sessões atuais concedem acesso; antes da primeira concessão, o serviço aguarda confirmação assíncrona da gravação dos cooldowns.
- `ProtectionListener`: autorização por ação e eventos Bukkit/Paper, incluindo inventários já abertos e automação de fronteira. Não cancela dano de jogador para jogador.
- `Commands` e `TerritoryExpansion`: comandos, livro, partículas, avisos com debounce e placeholders; o cache da expansão é imutável para consultas assíncronas do TAB.
- `QuarantineListener`: bloqueia interações/alterações se houver falha de dados ou persistência; não desliga o servidor nem apaga dados. Pare o servidor e corrija/restaure o armazenamento antes de liberá-lo.

Toda mutação e todo acesso Bukkit ocorrem na thread do servidor. O escritor assíncrono recebe apenas bytes/strings; não consulta jogadores ou blocos. No desligamento normal, os saques encerram e as gravações são drenadas. Ao voltar, registros incompletos tornam-se `REINICIO`, sem reconstruir permissões de saque.

## Contribuições e troca de grupo

Uma contribuição padrão sustenta nove claims, independentemente de trocas de grupo. Cada claim coletivo registra o jogador cuja contribuição o sustenta, sem lhe dar propriedade individual nem poder de remover o terreno. O proprietário continua sendo o grupo.

Ao entrar/criar grupo, os claims solo migram para o coletivo com o mesmo contribuinte. O chunk pessoal é independente e não migra. O gestor escolhe onde reivindicar; o sistema atribui a contribuição disponível de forma determinística.

Quando alguém sai ou é expulso:

1. A capacidade do grupo é recalculada. Claims existentes permanecem, sem apagar blocos.
2. Se o grupo exceder a capacidade, fica impedido de expandir até regularizar.
3. A contribuição que ainda sustenta claims nesse grupo não pode ser reutilizada pelo ex-membro como solo ou em outro grupo.
4. Cada claim liberado devolve uma unidade dessa contribuição. Dissolver o grupo libera todas as contribuições coletivas. A dissolução não transfere terrenos a ex-membros.

Exemplo: A e B sustentam 18 claims. B sai e seus nove claims respaldados continuam no grupo de A. B pode criar outro grupo após seu cooldown, mas esses nove créditos continuam ocupados. A pode liberar claims ou novos integrantes com créditos disponíveis podem regularizar a capacidade. B não tem poder de retirar claims de A. Esta regra fecha a multiplicação de terras por expulsão/entrada, sem remoção arbitrária.

Por padrão, o solo tem nove créditos e cada integrante oferece nove; os valores são configuráveis. O chunk extra pessoal é único por UUID, continua privado ao sair/dissolver e não é renovado a cada troca de grupo. Para movê-lo, o dono remove a proteção anterior e reivindica outro enquanto integrante de grupo. Os blocos nunca são movidos ou apagados.

## Guerra

A declaração inicia imediatamente apenas com os dois líderes online, como na versão antiga. Não existe início automático no login, aceitação de guerra nova nem cooldown preventivo. Claims coletivos do atacante são acessíveis a todos; claims do defensor podem ser acessados pelos membros do atacante. Claims solo/pessoais não se tornam públicos por guerra.

Alianças dos oponentes e seus acessos são removidos na declaração; terminar guerra não restaura permissões antigas. Proposta de paz é direcionada e persistente; só o líder destinatário aceita/recusa. O histórico conserva os nomes/TAGs no momento da declaração mesmo após dissolução ou correção administrativa.

## Saques e tempo

Padrão: duração original de cinco minutos; teto de 30 blocos compartilhado por todos os claims solo da vítima; um invasor autorizado por vítima; cooldown de vítima 12h, invasor 2h. A defesa estende a proteção da vítima até início do saque +24h, sem somar períodos. Cooldowns são timestamps persistidos por UUID.

Desconexão do dono abre uma janela de no máximo 60s limitada pelo deadline original; reconectar a tempo retira essa restrição. Desconexão do invasor termina. Morte atribuída por `Player.getKiller()` concede defesa; morte sem essa atribuição só encerra. O Território não decide Combat Log e não inventa responsáveis por dano ambiental.

Portas/camas completas consomem duas unidades porque removem dois blocos. Escadas e alçapões são blocos simples. Recipientes não podem ser destruídos. Ao gastar a cota, o acesso a recipientes dura até o prazo. A tributação não é por chunk, item ou recipiente.

A mineração usa `BLOCK_BREAK_SPEED` com multiplicador transitório 0,5 somente ao mirar bloco autorizado da vítima. Não aplica efeito permanente. O tempo observado continua sujeito a ticks, ferramenta, encantamentos, condições vanilla e latência: é velocidade relativa, não uma duração fixa de animação. A precisão prática precisa de teste real nos dois clientes.

## Limites operacionais

Esta implementação usa snapshots do estado e históricos em memória, adequada para homologação e servidores de porte moderado. Os cooldowns do início do saque chegam ao disco antes das permissões serem concedidas. O mapa evita buscas lineares de claims nos eventos comuns. Serialização de snapshots ainda tem custo proporcional ao estado; não foi realizado benchmark com milhares de grupos ou histórico de longa duração. Quedas abruptas podem perder alterações ainda na fila; arquivo parcial não substitui o estado válido e a parada normal aguarda gravações. Faça backups externos e não edite o JSON em execução.
