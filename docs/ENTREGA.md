# Entrega — Território 2.0.0

Implementação concluída e compilada para homologação. Esta entrega não declara aprovação no Survival+ real nem em clientes Bedrock. O código, o JAR, a configuração padrão, os testes e os manuais estão no projeto.

## Compilação e testes executados

- JDK: Eclipse Temurin 25.0.5+8, compilação `--release 25`.
- API: Paper `26.2.build.135-stable`.
- Maven: 3.9.11; comando final equivalente a `mvn clean verify`, com proxy e truststore da sessão fornecidos externamente.
- Simulador: MockBukkit 26.2, versão 4.117.0. Nenhum cliente Minecraft foi conectado.
- Resultado final: **47 executados, 47 aprovados, 0 falhas, 0 erros, 0 ignorados**. Compilação limpa e empacotamento bem-sucedidos.

| Conjunto | Executados | Cobertura principal |
| --- | ---: | --- |
| DomainTest | 20 | Validação, quatro cargos, convites, sucessão, cooldown de saída, claims, contribuição, capacidade, guerra, paz e alianças |
| StateStoreTest | 6 | Round-trip JSON, UUID/chunk, cooldowns/paz/confiança, snapshot imutável, backup, rejeição de corrupção/estado incompleto e auditoria |
| RaidSessionTest | 8 | Deadline original, janela offline, reconexão, limite compartilhado, marcos únicos e término |
| RaidBlockRulesTest | 5 | Portas/camas duplas, escadas/alçapões simples e bloqueio de destruição de armazenamentos |
| PluginIntegrationTest | 8 | Carregamento/comandos reais no mock, proteção de intrusos, notificação/decisão de paz, pistões, saque/grants, preservação do PvP, modo de manutenção e reinício durante saque |

O teste de saque usa um `PlayerMock` com ray tracing substituído por um bloco conhecido, pois esse método não está implementado no MockBukkit. A substituição é exclusivamente de teste; o plugin usa a API real em produção. Não valida a animação/velocidade de mineração do cliente. Nenhum teste foi deixado ignorado para esconder essa limitação.

O teste de corrupção emite deliberadamente um log SEVERE e ativa o guard de manutenção; o resultado esperado foi verificado e os bytes inválidos permaneceram intactos. Isso não representa falha na compilação. A distribuição inclui `VALIDACAO.txt` com o resumo extraído dos relatórios da execução final.

## Funcionalidades entregues

Grupos com nome/TAG únicos, quatro cargos, convites e sucessão persistentes; claims coletivos, solo e pessoal extra privado; alianças direcionais; declaração de guerra com ambos os líderes online; vulnerabilidade assimétrica; paz com recebimento, aceitação e recusa; histórico; saques com anonimato, defesa, cooldowns, desconexões e teto compartilhado; partículas privadas; avisos de transição por proprietário/tipo; manual em livro; comandos curtos, aliases e autocompletar filtrado; administração por permissões explícitas e auditoria; expansão PlaceholderAPI opcional, sem times concorrentes de scoreboard.

As permissões de saque só são liberadas depois da confirmação assíncrona da gravação dos cooldowns. Reinícios não reativam sessões. A contribuição de terrenos permanece comprometida em claims existentes de antigos grupos até sua liberação, impedindo reutilização artificial da mesma cota. Claims excedentes não são removidos arbitrariamente.

## Bugs corrigidos em relação à 0.1.2

1. Proposta de paz não notificava o destinatário; agora notifica os dois grupos e reapresenta pendências no login.
2. Propostas de paz não persistiam e não havia recusa; ambos foram implementados.
3. Saída, expulsão, dissolução e transferência de liderança permitiam escapar de guerra; agora são bloqueadas.
4. Permissões de aliados eram armazenadas, mas não eram consideradas na proteção; agora são verificadas por ação e revogadas em guerra.
5. O bônus de chunk pessoal era apenas capacidade coletiva; agora é um terreno separado, privado e persistente por dono.
6. Hoppers e baús duplos tinham cobertura insuficiente; ambas as metades e transferências são verificadas.
7. OP tinha poderes administrativos/bypass implícitos; os novos nós são `default: false` e bypass exige ativação explícita.
8. Possível reutilização de contribuição ao trocar de grupo foi fechada com lastro por contribuinte.
9. Retirada de suporte, blocos duplos e destruição indireta recebem verificações específicas no novo saque.

Saques, cargos, TAGs, cooldowns, histórico, livro e visualização não existiam integralmente no JAR antigo; são sistemas novos, não correções alegadamente testadas na versão antiga. Veja o [diagnóstico](DIAGNOSTICO-0.1.2.md).

## Homologação ainda necessária e limites conhecidos

- **Paper real + Java e Bedrock/Geyser:** livro, tamanho das páginas, partículas, títulos, quebra vanilla e latência. Não há cliente real neste ambiente.
- **TAB/PlaceholderAPI reais:** configuração manual dos prefixos/nametags, atualização visual, Shift e invisibilidade. Os snippets estão em [INTEGRACOES.md](INTEGRACOES.md); arquivos externos não foram alterados.
- **PvPManager/CoreProtect reais:** Combat Log/NPC, atribuição de mortes e registros das quebras, incluindo partes complementares de portas/camas. O plugin usa eventos normais; isso não comprova a configuração dos outros plugins.
- **Velocidade:** atributo multiplicativo 0,5, temporário e limitado ao alvo autorizado. Ferramentas, encantamentos, ticks e latência afetam o tempo observado; não foi medida uma duração exata de quebra em clientes.
- **Automação:** proteções são conservadoras; durante saque, automação/física pertinente é suspensa nos claims solo da vítima. Hoppers móveis e fronteiras de propriedade têm restrições. Homologue suas máquinas conforme [PROTECAO.md](PROTECAO.md).
- **Escala e quedas abruptas:** não foi executado benchmark de milhares de grupos. Snapshots não substituem backups externos; alterações posteriores ao checkpoint que ainda estiverem na fila podem se perder com encerramento forçado. Parada normal drena as gravações.
- **Plugins que alteram o mundo sem eventos:** precisam de integração própria se usados; listeners não interceptam alterações silenciosas de terceiros.

Não houve instalação no Crafty, conexão ao Survival+, publicação de ambiente, push Git, wipe, importação dos dados antigos nem mudança em mundos, inventários ou arquivos de outros plugins. O único dado antigo confirmado é `plugins/Territorio/data.yml`, cuja eventual remoção/arquivamento é manual, com servidor parado e backup.

Siga [INSTALACAO.md](INSTALACAO.md), conceda somente as permissões necessárias e execute [TESTES-MANUAIS.md](TESTES-MANUAIS.md) antes de liberar o JAR em produção.
