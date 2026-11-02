# Entrega — Território 2.0.1

Correções pontuais do beta sobre o commit `8b37a79`, com as decisões D1-B e D2-A aprovadas. Não houve wipe, exclusão de dados, migração, alteração de mapas ou arquivos de outros plugins. O JAR destina-se à homologação; não foi instalado no servidor.

## Correções

1. Explosões atribuídas usam permissões existentes; TNT sem autoria segue a exceção aprovada. Creeper usa ignitor/alvo informado pelo servidor e protege claims quando a autoria é desconhecida. Blocos são filtrados sem cancelar som/efeitos.
2. TAGs aproveitam PlaceholderAPI e o fluxo de atualização atual. Acrescentado somente `cargo_simbolo`; TAB/nametag/chat dependem da configuração manual de [INTEGRACOES.md](INTEGRACOES.md).
3. Bloqueado dano atribuível entre integrantes do mesmo grupo em qualquer local, incluindo projéteis, combustão, raios atribuídos e poções prejudiciais. Outros grupos, aliados, confiados e solo mantêm as regras externas.
4. Avisos coletivos mostram `[TAG] Nome`, preservando mensagens privadas, debounce e comparação por dono/tipo.
5. Fogo natural pode iniciar, espalhar internamente e consumir blocos; ações diretas identificáveis e fronteiras de projéteis de dispenser continuam protegidas.
6. Acionadores e sinais seguros funcionam em saque. Pistões destrutivos, extração automática, crafter, cargas destrutivas, priming e explosões/fogo nos claims da vítima continuam restritos para preservar 30 blocos.
7. Comandos de grupo sob `/t grupo`, guerra unilateral direta, paz sem TAG apenas para uma proposta recebida, aliases e autocompletar preservados. Livro em 24 páginas curtas nos cinco assuntos, com explicação expressa de D1-B.

## Decisões aprovadas e limites

- **D1-B:** o saque não concede abertura manual de portas/alçapões; o invasor pode acioná-los por circuitos ligados aos botões/alavancas/placas autorizados. Isso está explicado no livro e no manual.
- **D2-A:** TNT sem fonte jogador rastreável pode destruir claims mesmo fora de guerra. Inclui autoria manual perdida ao recarregar chunk; abusos serão regulados pela administração, sem novo rastreamento. A exceção não permite destruir claims da vítima durante saque ativo.
- Mera aliança não autoriza explosões; construção explicitamente concedida ao aliado permanece válida. Guerra mantém a vulnerabilidade assimétrica somente coletiva.
- Creeper sem responsável confiável e outras explosões sem autoria, como cama/âncora, conservam proteção. Não há heurística de jogador mais próximo.

## Compilação e testes

JDK Temurin 25.0.5+8, Maven 3.9.11, Paper API `26.2.build.135-stable`, MockBukkit 4.117.0. `mvn clean verify` com settings de proxy externo: **BUILD SUCCESS — 101 testes, zero falhas, erros ou ignorados**. A base anterior passou em 47 testes; foram acrescentadas 54 regressões.

| Conjunto | Testes |
| --- | ---: |
| CommandsTest | 11 |
| DomainTest | 20 |
| ExplosionFireRegressionTest | 11 |
| GroupPvpRegressionTest | 16 |
| PlaceholderRegressionTest | 4 |
| PluginIntegrationTest | 8 |
| RaidBlockRulesTest | 5 |
| RaidMechanismRegressionTest | 12 |
| RaidSessionTest | 8 |
| StateStoreTest | 6 |

Os testes verificam eventos e regras, com substituições explícitas apenas para campos/consulta de suporte não simulados pelo MockBukkit e ray tracing do saque. A simulação de monitores/override não executa o JAR real do PvPManager. O teste de corrupção provoca deliberadamente log SEVERE e confirma preservação dos dados inválidos.

## Homologação ainda necessária

- Paper real: atribuição de TNT/creeper, priming/cadeias, redstone/física, suportes, dispensers/crafter, drops e limite de saque.
- Clientes Java e Bedrock reais: glifos, cores de nickname, TAB/nametag/chat, páginas do livro e partículas. A estimativa de tamanho do livro não comprova sua renderização.
- PvPManager **4.0.22**, inclusive combate já ativo, configuração de overrides e lista de poções: companheiros não iniciam/renovam combate; combate legítimo mantém 30 segundos e consequências. Testar splash/lingering/Mestre Tartaruga/efeitos neutros configuráveis.
- A compatibilidade de poções mascara temporariamente companheiros antes dos monitores e restaura alvos com buffs depois do monitor do PvPManager4.0.22 para aplicação vanilla, sem sintetizar efeitos/durações. Essa exceção de ordem em MONITOR exige homologação com outros plugins que filtrem alvos de poções; cancelamentos globais são respeitados. Sem PvPManager não há mascaramento.
- LuckPerms, vanish, CoreProtect e formatação externa: configurações reais não foram modificadas nem homologadas.
- O bug de blocos fantasmas do Geyser permanece fora do escopo. Não foram alterados Geyser, Floodgate, ViaVersion, Paper ou cancelamentos de quebra para tratá-lo.

## Arquivos alterados/adicionados

- `README.md`
- `dist/SHA256SUMS-2.0.1.txt`
- `dist/Territorio-2.0.1-projeto.zip`
- `dist/Territorio-2.0.1.jar`
- `dist/VALIDACAO-2.0.1.txt`
- `docs/ARQUITETURA.md`
- `docs/COMANDOS.md`
- `docs/ENTREGA.md`
- `docs/INSTALACAO.md`
- `docs/INTEGRACOES.md`
- `docs/MANUAL.md`
- `docs/PROTECAO.md`
- `docs/TESTES-MANUAIS.md`
- `pom.xml`
- `src/main/java/dev/nateriver/territorio/Commands.java`
- `src/main/java/dev/nateriver/territorio/ProtectionListener.java`
- `src/main/java/dev/nateriver/territorio/TerritoryExpansion.java`
- `src/main/resources/plugin.yml`
- `src/test/java/dev/nateriver/territorio/CommandsTest.java`
- `src/test/java/dev/nateriver/territorio/ExplosionFireRegressionTest.java`
- `src/test/java/dev/nateriver/territorio/GroupPvpRegressionTest.java`
- `src/test/java/dev/nateriver/territorio/PlaceholderRegressionTest.java`
- `src/test/java/dev/nateriver/territorio/RaidMechanismRegressionTest.java`

`Domain.java`, `RaidService.java`, `StateStore.java` e `config.yml` permanecem sem alteração. O esquema persistido, hierarquia, limites, cooldowns, duração e encerramento dos saques não foram modificados. `plugin.yml` preserva comandos/permissões e acrescenta somente softdepend PvPManager, versão e descrição neutra.

## Pacote

- `dist/Territorio-2.0.1.jar`
- `dist/Territorio-2.0.1-projeto.zip`
- `dist/VALIDACAO-2.0.1.txt`
- `dist/SHA256SUMS-2.0.1.txt`

[Árvore de comandos](COMANDOS.md), [livro atualizado](MANUAL.md), [configurações externas](INTEGRACOES.md) e [roteiro de testes reais](TESTES-MANUAIS.md). Atualize somente o JAR, com servidor parado e backup, preservando o estado existente. A branch de revisão não altera automaticamente a `main` nem instala o plugin.
