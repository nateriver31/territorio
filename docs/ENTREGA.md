# Entrega — Território 2.1.0

Implementada sobre `main` em `18aae4c8f26ecc36a42017bcc91b9116330301b5`. Nenhum acesso ou alteração em produção, wipe, exclusão de dados reais, instalação automática ou alteração de arquivos de outros plugins.

## Resultado

- `/t claims` distingue grupo/solo e mostra normais, bônus conquistados/capacidade, utilizados/saldo, pessoais e tipo/dono atual.
- Zumbi pode atacar aldeão protegido; ataques não autorizados de jogadores e demais proteções permanecem.
- Claims Bônus exclusivos de grupos, seis de capacidade por integrante por padrão, zero inicial, PvP +1/-1 independente, sem cooldown e uma ocorrência por morte. Ordem persistida; somente bônus excedentes perdem proteção em LIFO. Saída/expulsão perde excesso definitivamente; retorno não restaura.
- Livros físicos assinados com chance padrão de 25%, independente de Pilhagem, revelando um único claim elegível e sua dimensão. Sem geração por ambiente sem autoria, mesmo grupo, comando administrativo ou NPC identificado.
- Recarga validada sem apagar dados; redução de capacidade incompatível é recusada integralmente.
- Logs diários assíncronos, com horário de São Paulo e retenção padrão de 30 dias restrita aos próprios logs.
- `/t admin deletar TAG [confirmar]`, permissão `territorio.admin.deletar`, console, confirmação própria por 60s e revalidação. Preserva blocos, inventários e pessoais; limpa referências e encerra guerras, mantendo histórico.
- DiscordSRV 1.30.5 opcional pelo bot/mapeamento existentes: anuncia guerra declarada e paz aceita após persistência. `/t admin discord teste`, permissão `territorio.admin.discord`, confirma sucesso somente após ack; falhas não bloqueiam diplomacia.
- Livro `/t` em 28 páginas e placeholder `%territorio_prefixo_minimessage%` incluído para manter a configuração de chat anteriormente preparada. TAB/LPC continuam externos.

## Arquivos de implementação

| Arquivos | Alteração |
| --- | --- |
| `Domain.java` | Categoria/saldo/ordem bônus, PvP e redução LIFO, extensão das permissões coletivas e exclusão consistente |
| `StateStore.java` | Esquema 2→3 validado, backup original, validação dos bônus/números e estado de saúde |
| `Commands.java` | Consulta corrigida, reivindicação bônus, administração confirmada, avisos privados e manual |
| `ProtectionListener.java` | Exceção zumbi/aldeão e continuidade entre coletivo normal/bônus do mesmo grupo |
| `TerritorioPlugin.java` | Registro das partes aditivas, configuração validada, logs e checkpoint das confirmações |
| `TerritoryExpansion.java` | Prefixo MiniMessage compatível com LPC; placeholders antigos preservados |
| `ProgressListener.java` (novo) | Autoria/deduplicação das mortes e livro físico |
| `UpdateOptions.java` (novo) | Validação das opções e impacto da capacidade |
| `ActivityLog.java` (novo) | Logs diários assíncronos e retenção localizada |
| `DiscordAnnouncements.java` (novo) | Adaptador público opcional DiscordSRV/JDA e callbacks |
| `pom.xml`, `plugin.yml`, `config.yml` | Versão 2.1.0, softdepend opcional, dois nós administrativos e padrões novos; nenhuma dependência nova |

Testes adicionados: `BonusDomainTest`, `BonusPersistenceTest`, `ProgressUpdateTest`, `UpdateCommandsTest`, `UpdateOptionsTest`, `ProductionCopyTest`, `ActivityLogTest`, `DiscordUpdateTest` e fixture `UpdateTestBase`. Acrescentado um caso ao `PlaceholderRegressionTest`. As regressões anteriores foram mantidas. `RaidService` e `QuarantineListener` não foram alterados.

Documentação atualizada: `README.md`, `ARQUITETURA.md`, `COMANDOS.md`, `ENTREGA.md`, `INSTALACAO.md`, `INTEGRACOES.md` e `MANUAL.md`. Acrescentados `ATUALIZACAO-2.1.0.md` e `TESTES-MANUAIS-2.1.0.md`; o relatório anterior foi arquivado em `ENTREGA-2.0.1.md`. Distribuição nova em `dist/`, mantendo os artefatos anteriores.

## Dados e validação

`state.json` esquema 2 é validado e migrado em memória para esquema 3, começando todos os grupos em zero bônus. `state.json.pre-v3.bak` preserva o original e não é sobrescrito. Nenhum claim antigo vira bônus; claims normais/pessoais, integrantes/cargos, permissões, guerras, propostas e relações são preservados. Falha não cria substituição vazia; o plugin mantém manutenção. A versão antiga requer seu backup correspondente para rollback.

**`mvn clean verify`: BUILD SUCCESS — 167 testes, zero falhas, erros ou ignorados.** JDK Temurin 25.0.5+8, Maven 3.9.11, Paper API 26.2.build.135-stable e MockBukkit 4.117.0. São 101 regressões da base e 66 testes acrescentados. As assinaturas usadas foram conferidas no JAR oficial DiscordSRV 1.30.5; não houve conexão a um bot real. [Detalhamento por conjunto](../dist/VALIDACAO-2.1.0.txt).

As cópias de migração são fixtures completas geradas, não dados reais da produção. Permanecem pendentes testes com cópia real, Paper e clientes Java/Bedrock, farm, mortes/drops/keepInventory, PvPManager e DiscordSRV reais. Não foi implementada nova integração de recompensa por Combat Logging: versão/configuração e contrato do logger não confirmados. Desconexão não simula morte ou penalidade. Não se declara compatibilidade real comprovada por testes simulados.

## Arquivos para baixar e próxima etapa

- [JAR 2.1.0](../dist/Territorio-2.1.0.jar)
- [Projeto completo ZIP](../dist/Territorio-2.1.0-projeto.zip)
- [SHA-256](../dist/SHA256SUMS-2.1.0.txt)
- [Regras, configurações, migração e recuperação](ATUALIZACAO-2.1.0.md)
- [Comandos e permissões](COMANDOS.md), [manual atualizado](MANUAL.md), [integrações externas](INTEGRACOES.md)
- [Roteiro de homologação real](TESTES-MANUAIS-2.1.0.md)

Homologar primeiro uma cópia isolada dos dados e mundos, com backup recuperável. Esta entrega não instala o JAR no servidor principal.
