# Território 2.1.0 (Codex IA)

Plugin para **Paper 26.2 / Java 25**. Atualização aditiva: Claims Bônus por PvP, livros de informações, logs diários e anúncios pelo DiscordSRV existente. Corrige `/t claims` de integrantes de grupos e dano de zumbis a aldeões, preservando as regras anteriores.

## Downloads

**Versão 2.1.0 para homologação em cópia dos dados — Paper 26.2 e Java 25.**

| Opção | O que baixar |
| --- | --- |
| [Plugin JAR](dist/Territorio-2.1.0.jar) | Compilado para testar antes da troca manual em produção |
| [Projeto completo ZIP](dist/Territorio-2.1.0-projeto.zip) | Código-fonte, JAR, configurações padrão, testes e manuais |
| [Verificação SHA-256](dist/SHA256SUMS-2.1.0.txt) | Checksums dos arquivos distribuídos |
| [Resultados dos testes](dist/VALIDACAO-2.1.0.txt) | Resumo dos 167 testes automatizados aprovados |
| [Relatório desta correção](docs/ENTREGA.md) | Alterações, decisões aprovadas e limitações |

As distribuições 2.0.x permanecem no histórico. Leia [as regras, configurações e migração da 2.1.0](docs/ATUALIZACAO-2.1.0.md).

### Instalação rápida

1. Faça backup recuperável e use uma cópia isolada dos dados/mundos com **Paper 26.2 e Java 25**.
2. Com a instância de testes parada, substitua apenas o JAR por `Territorio-2.1.0.jar`. Não mantenha duas versões instaladas.
3. Preserve `state.json`, backups, auditoria e configuração. A migração de esquema 2 para 3 cria `state.json.pre-v3.bak`; não converte claims antigos nem concede bônus retroativos.
4. Inicie e use `/t` para abrir o manual; `/territorio` funciona se `/t` pertencer a outro plugin.
5. Execute o [roteiro 2.1.0](docs/TESTES-MANUAIS-2.1.0.md) antes de programar a instalação manual em produção. Confira [backup e recuperação](docs/ATUALIZACAO-2.1.0.md#migração-backup-e-recuperação).

**Validação:** 167 testes aprovados, sem falhas, erros ou ignorados; `mvn clean verify` concluído. A migração foi testada com fixtures completas de dados antigos, sem acesso aos dados reais. Ainda é necessária homologação com cópia da produção, clientes Java/Bedrock, PvPManager, DiscordSRV e demais plugins instalados. Consulte [o relatório](docs/ENTREGA.md).

## O que o plugin oferece

- Claims solo, coletivos e um chunk pessoal extra privado por integrante.
- Claims Bônus coletivos separados: capacidade padrão de seis por integrante, saldo por PvP e perda de proteção do mais recente quando necessário.
- Livro físico de informações de um claim, com chance configurável de 25% e sem influência de Pilhagem.
- Grupos com TAG, quatro cargos, convites, sucessão e limites sem duplicar contribuição na troca de grupo.
- Guerras com líderes online no início, vulnerabilidade assimétrica, propostas de paz corrigidas e histórico.
- Alianças com permissões direcionais de mecanismos, recipientes e construção.
- `/roubar`: saque solo de cinco minutos, um invasor, até 30 blocos compartilhados, cooldowns e defesa.
- `/verchunk`, avisos discretos de território, livro de ajuda e expansão opcional PlaceholderAPI.
- Ferramentas administrativas com permissões explícitas, auditoria e persistência própria.
- Exclusão administrativa confirmada, logs diários e integração opcional com o bot de chat existente do DiscordSRV 1.30.5.

Jogadores do mesmo grupo não causam PvP entre si. O Território não cria zonas de PvP, não usa ActionBar constante, não disputa scoreboard com o TAB e não configura outros plugins.

## Compilar e testar

Use um JDK 25 e Maven 3.9 ou superior:

```sh
java -version
mvn clean verify
```

O resultado é `target/territorio-2.1.0.jar`. As dependências existentes e a API `26.2.build.135-stable` foram preservadas; nenhuma dependência nova foi acrescentada. PlaceholderAPI e DiscordSRV são opcionais. MockBukkit e JUnit só são usados nos testes. Não se presume compatibilidade com versões antigas, Spigot ou Folia.

Se seu ambiente exigir proxy ou CA corporativa, configure-os externamente ao projeto e preserve a verificação TLS.

## Documentação

- [Instalação e dados antigos](docs/INSTALACAO.md)
- [Atualização 2.1.0, configuração e recuperação](docs/ATUALIZACAO-2.1.0.md)
- [Comandos e permissões](docs/COMANDOS.md)
- [Texto atualizado do livro /t](docs/MANUAL.md)
- [TAB, PlaceholderAPI e integrações](docs/INTEGRACOES.md)
- [Arquitetura, capacidade e persistência](docs/ARQUITETURA.md)
- [Diagnóstico do JAR 0.1.2](docs/DIAGNOSTICO-0.1.2.md)
- [Política das proteções](docs/PROTECAO.md)
- [Roteiro para servidor real](docs/TESTES-MANUAIS.md)
- [Homologação da atualização 2.1.0](docs/TESTES-MANUAIS-2.1.0.md)
- [Resultados e limitações da entrega](docs/ENTREGA.md)

Não existe wipe automático. Não apague o estado para atualizar. A publicação no GitHub não instala o JAR, não modifica outros plugins nem acessa produção. O JAR antigo exige o backup de dados correspondente para rollback; não lê o esquema 3.
