# Território 2.0.1 (Codex IA)

Plugin para **Paper 26.2 / Java 25**. Correções pontuais do beta 2.0: explosões, TAGs, PvP do mesmo grupo, avisos, fogo natural, redstone em saques e comandos/manual.

## Downloads

**Versão 2.0.1 para homologação — Paper 26.2 e Java 25.**

| Opção | O que baixar |
| --- | --- |
| [Plugin JAR](dist/Territorio-2.0.1.jar) | Arquivo para colocar na pasta `plugins` do servidor |
| [Projeto completo ZIP](dist/Territorio-2.0.1-projeto.zip) | Código-fonte, JAR, configurações padrão, testes e manuais |
| [Verificação SHA-256](dist/SHA256SUMS-2.0.1.txt) | Checksums dos arquivos distribuídos |
| [Resultados dos testes](dist/VALIDACAO-2.0.1.txt) | Resumo dos 101 testes automatizados aprovados |
| [Relatório desta correção](docs/ENTREGA.md) | Alterações, decisões aprovadas e limitações |

O **projeto completo ZIP** inclui código, JAR, configuração e manuais desta correção. A versão publicada 2.0.0 permanece como histórico; os downloads acima correspondem à 2.0.1 desta branch.

### Instalação rápida

1. Use uma instância de testes com **Paper 26.2 e Java 25** e faça backup.
2. Pare o servidor. Retire o JAR antigo do Território e coloque `Territorio-2.0.1.jar` em `plugins`. Não mantenha as duas versões instaladas.
3. Siga [as instruções sobre os dados antigos](docs/INSTALACAO.md). O `data.yml` 0.1.2 não é importado e não será apagado automaticamente.
4. Inicie e use `/t` para abrir o manual; `/territorio` funciona se `/t` pertencer a outro plugin.
5. Configure manualmente as permissões e a integração visual, e execute o [roteiro de homologação](docs/TESTES-MANUAIS.md).

**Validação:** 101 testes automatizados aprovados, sem falhas ou ignorados, incluindo testes no MockBukkit 26.2. Ainda falta homologar em servidor real com clientes Java/Bedrock e as versões instaladas de Geyser, TAB, PvPManager e CoreProtect. A redução de mineração usa atributo de velocidade 0,5; o tempo observado depende das condições vanilla e dos ticks. Consulte [o relatório completo](docs/ENTREGA.md).

## O que o plugin oferece

- Claims solo, coletivos e um chunk pessoal extra privado por integrante.
- Grupos com TAG, quatro cargos, convites, sucessão e limites sem duplicar contribuição na troca de grupo.
- Guerras com líderes online no início, vulnerabilidade assimétrica, propostas de paz corrigidas e histórico.
- Alianças com permissões direcionais de mecanismos, recipientes e construção.
- `/roubar`: saque solo de cinco minutos, um invasor, até 30 blocos compartilhados, cooldowns e defesa.
- `/verchunk`, avisos discretos de território, livro de ajuda e expansão opcional PlaceholderAPI.
- Ferramentas administrativas com permissões explícitas, auditoria e persistência própria.

Jogadores do mesmo grupo não causam PvP entre si. O Território não cria zonas de PvP, não usa ActionBar constante, não disputa scoreboard com o TAB e não configura outros plugins.

## Compilar e testar

Use um JDK 25 e Maven 3.9 ou superior:

```sh
java -version
mvn clean verify
```

O resultado é `target/territorio-2.0.1.jar`. As dependências Paper e PlaceholderAPI são `provided`: não são incluídas no JAR. PlaceholderAPI é opcional no servidor. MockBukkit e JUnit só são usados nos testes. A API está fixada em `26.2.build.135-stable`; não se presume compatibilidade com versões antigas, Spigot ou Folia.

Se seu ambiente exigir proxy ou CA corporativa, configure-os externamente ao projeto e preserve a verificação TLS.

## Documentação

- [Instalação e dados antigos](docs/INSTALACAO.md)
- [Comandos e permissões](docs/COMANDOS.md)
- [Texto atualizado do livro /t](docs/MANUAL.md)
- [TAB, PlaceholderAPI e integrações](docs/INTEGRACOES.md)
- [Arquitetura, capacidade e persistência](docs/ARQUITETURA.md)
- [Diagnóstico do JAR 0.1.2](docs/DIAGNOSTICO-0.1.2.md)
- [Política das proteções](docs/PROTECAO.md)
- [Roteiro para servidor real](docs/TESTES-MANUAIS.md)
- [Resultados e limitações da entrega](docs/ENTREGA.md)

Não existe wipe automático. O `data.yml` antigo é preservado e não é importado. A publicação no GitHub não instala o JAR no servidor, não modifica outros plugins nem publica o ambiente na nuvem. Homologue em uma instância de testes antes de usar em produção.
