# Instalação e atualização — Território 2.0

## Requisitos

- Paper **26.2**, executado com **Java 25**. Confirme com `java -version` e no log de inicialização do Paper; não basta o painel mostrar uma versão selecionada.
- LuckPerms para atribuir os nós de permissão do Território.
- PlaceholderAPI e TAB são opcionais, necessários apenas para a integração visual descrita abaixo.
- PvPManager, CoreProtect, Geyser e SurvivalPlusCore continuam independentes. O Território não substitui suas configurações nem as modifica.

O JAR de entrega deve corresponder ao projeto e ao resultado de compilação informado no relatório. Não mantenha dois JARs do Território simultaneamente na pasta `plugins`.

## Instalação segura no Crafty Controller

1. Faça um backup dos mundos e da pasta `plugins/Territorio`. Guarde o JAR 0.1.2 fora da pasta de carregamento. O backup deve ser recuperável; não o apague depois do primeiro teste.
2. Pare completamente o servidor pelo Crafty. Não use `/reload` do Bukkit, PlugMan ou substituição do JAR com o servidor rodando.
3. Remova **manualmente** o JAR antigo da pasta `plugins` e coloque o JAR 2.0. Não altere arquivos de outros plugins, mundos, dados dos jogadores ou inventários.
4. Se desejar começar com a configuração padrão 2.0, renomeie manualmente `plugins/Territorio/config.yml` para um backup fora da pasta ativa. O novo arquivo será gerado na próxima inicialização. Compare opções antes de transferir ajustes antigos.
5. Inicie em uma instância de testes e examine o log. Confirme que Território foi habilitado sem erros, que Java/Paper estão corretos e que nenhum conflito de comandos foi ignorado.
6. Atribua permissões administrativas explicitamente pelo LuckPerms. Teste com uma conta sem OP e outra de staff; não suponha que OP represente autorização para ações perigosas.
7. Execute o roteiro [TESTES-MANUAIS.md](TESTES-MANUAIS.md), incluindo Java e Bedrock reais, antes de liberar produção.

O Território não inicia wipe, não elimina blocos e não apaga dados de outros plugins. O programa não precisa acessar o Crafty nem receber suas credenciais para esta instalação.

## Dados antigos: decisão e exclusão somente manuais

A análise do JAR 0.1.2 confirmou **um único arquivo de dados**, `plugins/Territorio/data.yml`, além de `config.yml`. Não foram encontrados `claims.yml`, `groups.yml` ou `wars.yml` separados. Não apague arquivos por nomes presumidos.

A 2.0 não migra nem lê `data.yml`. Se ele existir, a instalação deve advertir que os registros antigos não são usados. O usuário pode arquivá-lo ou removê-lo **manualmente, com servidor parado e após backup**, conforme a decisão de zerar os dados de testes. Nenhum comando ou rotina automática realiza essa exclusão.

A proteção antiga não é transferida para o novo armazenamento: chunks precisam ser reivindicados novamente no novo estado. Blocos e construções permanecem nos mundos; registros de claims e terrenos físicos são coisas distintas. Não abra a instância aos jogadores antes de preparar os claims necessários.

## Armazenamento 2.0 e backups

Os arquivos próprios são criados em `plugins/Territorio`:

| Arquivo | Finalidade |
| --- | --- |
| `config.yml` | Configuração do plugin |
| `state.json` | Estado persistente, esquema 2: grupos, claims, permissões, diplomacia, cooldowns e históricos |
| `state.json.bak` | Cópia de segurança do estado anterior, conforme rotina de persistência |
| `audit.jsonl` | Auditoria administrativa, restrita à equipe |

Snapshots de dados são preparados na thread correta e gravados por um único escritor assíncrono com substituição atômica. Isso reduz risco de arquivo parcial; não substitui backup externo nem garante gravação de ações ainda pendentes se o processo ou máquina forem encerrados abruptamente. Prefira sempre parada normal do servidor.

Ao encontrar arquivo inválido ou falha de persistência, leia os logs e mantenha o servidor fora de produção. Quando o carregamento dos dados/configuração falha ou o armazenamento deixa de gravar, o plugin mantém um guard de manutenção que bloqueia interações e alterações. Isso não substitui parar o servidor e restaurar o estado válido; se o próprio JAR não carregar por incompatibilidade, suas proteções não estarão disponíveis. Pare o servidor e restaure uma cópia válida, preservando o arquivo com defeito para diagnóstico. Nunca resolva um erro apagando `state.json` em produção: ausência do arquivo representa estado inicial e perderia os registros de proteção. Não edite JSON enquanto o plugin estiver ativo.

Saques ativos não restauram permissões após reinício; cooldowns e histórico permanecem. Processos ativos e sessões dos jogadores não fazem parte do snapshot de armazenamento.

## TAGs com PlaceholderAPI e TAB

O Território fornece dados por PlaceholderAPI e não administra times concorrentes de scoreboard. Sem PlaceholderAPI, claims, grupos, guerras e saques continuam funcionando; a parte visual externa depende da instalação/configuração do administrador.

Use os identificadores de placeholders listados no manual de comandos da versão entregue. Os valores disponibilizam TAG formatada, nome do grupo, cargo e estado de pertencimento. Teste os placeholders com o comando de análise do PlaceholderAPI antes de inseri-los no TAB.

No TAB, insira a TAG formatada do Território antes do prefixo/nome existente nos campos de lista de jogadores e nametag suportados pela sua versão. Preserve os placeholders LuckPerms e as regras já existentes. Configure a atualização dinâmica para criação/saída/troca de grupo. Um solo deve retornar TAG vazia. O nick deve conservar sua cor atual; o dourado pertence à TAG.

**Não copie um arquivo TAB completo sobre sua instalação.** A sintaxe e os campos variam entre versões e entre o uso de nametag por scoreboard ou outras opções. Qualquer trecho proposto deve ser revisado e aplicado separadamente pelo administrador, sem o Território sobrescrever os arquivos. Na homologação, valide `[ROMA] Jogador` no TAB e acima da cabeça, saída/expulsão, Shift e invisibilidade.

## Outras integrações

- **LuckPerms:** consulte os nós do `plugin.yml` e o manual entregue. Use `lp user <jogador> permission set <nó> true` somente para nós que deseja conceder. Remova permissões antigas amplas de contas que não devem manter acesso administrativo. O bypass de proteção deve ser excepcional, consciente e auditável.
- **PvPManager:** nenhuma mudança de Combat Log ou PvP é necessária. Confirme que combate e consequências de desconexão continuam funcionando; o Território não deve ocupar a ActionBar continuamente.
- **CoreProtect:** as quebras normais autorizadas seguem os eventos de blocos. Verifique o log na versão real instalada; não assuma integração comprovada sem realizar o teste.
- **Geyser:** nenhuma alteração automática é feita. Use cliente Bedrock real para livro, comandos e partículas. A representação de algumas partículas depende do tradutor/cliente.
- **SurvivalPlusCore:** mantenha suas configurações e dados intactos. Portais Nether, escala 1:6 e avisos de fronteira pertencem a ele.

## Atualizações posteriores

Pare o servidor, faça backup da pasta do Território, troque apenas o JAR e leia as notas da versão. Não apague o estado 2.0 para atualizar. A recarga própria de configuração não substitui reiniciar o processo para atualizar classes. Mudanças de limites não autorizam remoção arbitrária de claims; grupos acima da capacidade precisam regularizar conforme regra documentada.

Este documento não afirma testes em Crafty, Geyser ou no Survival+ real. Consulte o relatório da entrega para saber quais verificações foram executadas.
