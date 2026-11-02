# Integrações externas — aplicar separadamente

Nenhum trecho deste documento é instalado automaticamente, e nenhum arquivo de outro plugin foi alterado.

## PlaceholderAPI

Instale uma versão compatível com Paper 26.2, junto do Território, e reinicie normalmente. A expansão é embutida; não é preciso baixar uma expansão do eCloud.

| Placeholder | Com grupo | Solo |
| --- | --- | --- |
| `%territorio_tag%` | `ROMA` | vazio |
| `%territorio_tag_formatada%` | `§6[ROMA]§r ` | vazio |
| `%territorio_grupo%` | `Império Romano` | vazio |
| `%territorio_cargo%` | `Líder`, `Colíder`, `Oficial` ou `Membro` | vazio |
| `%territorio_cargo_simbolo%` | líder `§6♛§r `, colíder `§6◆§r `, oficial `§6•§r `; membro vazio | vazio |
| `%territorio_tem_grupo%` | `true` | `false` |

Teste manualmente `/papi parse me %territorio_tag_formatada%` com alguém que tem grupo e depois de sair. Sem PlaceholderAPI, os sistemas de jogo continuam funcionando; só a integração visual dependente dela fica indisponível. Não cria times de scoreboard.

O símbolo e a TAG incluem sua própria cor dourada, reset e espaço final. Sem grupo ambos retornam vazio; membros comuns recebem somente a TAG. Não acrescente um espaço fixo entre esses placeholders e o nome. O snapshot é atualizado ao entrar no servidor e após os comandos, incluindo ingresso, saída, expulsão, cargos, sucessão e correções administrativas de nome, TAG e líder. TAB e chat consultam esse snapshot sem acessar o estado mutável dos grupos em threads assíncronas.

## TAB

Faça backup e revise a documentação da sua versão do TAB. Acrescente a TAG **antes dos valores que sua instalação já usa**, para que o reset da TAG seja seguido das cores/prefixos normais. Exemplo de sugestão para os campos de grupo aplicáveis na sua configuração, preservando o restante:

```yaml
# Exemplo parcial — acrescente aos valores existentes, não sobrescreva seu arquivo.
tabprefix: '%territorio_cargo_simbolo%%territorio_tag_formatada%%luckperms-prefix%'
tagprefix: '%territorio_cargo_simbolo%%territorio_tag_formatada%%luckperms-prefix%'
```

Se seus valores existentes incluem placeholders/cores diferentes, mantenha-os depois da TAG. `tabprefix`/`tagprefix` devem ficar no local apropriado da configuração de grupos do TAB; não cole cegamente no topo do `config.yml`. A ativação da formatação da lista e do sistema de nametags fica sob controle do administrador do TAB. Nunca crie times paralelos pelo Território.

Para atualização periódica, a configuração da sua versão pode aceitar este trecho parcial:

```yaml
placeholder-refresh-intervals:
  '%territorio_tag_formatada%': 1000
  '%territorio_cargo_simbolo%': 1000
  '%territorio_grupo%': 1000
  '%territorio_cargo%': 1000
  '%territorio_tem_grupo%': 1000
```

Mescle com o bloco já existente; não descarte outros placeholders. A atualização visual acompanha o intervalo do TAB, normalmente até um segundo neste exemplo. Preserve também os campos existentes de nome/apelido, cores, ordenação e integração com vanish. Verifique no servidor real: nome acima da cabeça, TAB, ingresso/saída/expulsão, cargos, sucessão, renomeação administrativa, Shift e invisibilidade. O Território não modifica nick, Glowing, equipes nem visibilidade durante saques.

Os símbolos usam caracteres Unicode simples, sem emoji colorido, mas a fonte do cliente e os pacotes de recursos podem mudar sua aparência. Verifique `♛`, `◆` e `•` nos clientes Java e Bedrock realmente utilizados. Testes automatizados não comprovam sua renderização. Se algum glifo ficar ilegível, retire `%territorio_cargo_simbolo%` do formato externo e use o cargo textual já disponível em `%territorio_cargo%`, conforme os condicionais do formatador escolhido.

## Chat com PlaceholderAPI

O Território fornece placeholders e não registra um formatador próprio de chat. Use um formatador compatível com PlaceholderAPI; apenas instalar TAB não modifica o chat. Acrescente os dois placeholders **antes do formato de nome já usado**, preservando os prefixos, apelidos e cores de LuckPerms. Sem símbolos, basta `%territorio_tag_formatada%`.

Exemplo parcial para [LPC](https://github.com/ThePM2/LPC), cuja configuração oferece `chat-format`, `{displayname}` e `{username-color}` e resolve placeholders do PlaceholderAPI:

```yaml
# No config.yml do LPC, somente se este for o formatador escolhido.
chat-format: "%territorio_cargo_simbolo%%territorio_tag_formatada%{prefix}{username-color}{displayname}{suffix}&r: {message-color}{message}"
```

Mescle a TAG ao formato que sua instalação já usa; este exemplo não substitui os formatos específicos de outros plugins ou grupos do LuckPerms. O reset da TAG é seguido pelas cores originais do nickname. Um membro comum aparece como `[ROMA] Jogador123: Olá!`, e um jogador sem grupo mantém seu formato anterior, sem espaço extra. Não ative dois formatadores de chat simultaneamente.

Valide a versão escolhida do formatador com Paper 26.2 e confira apelidos coloridos, cargos, mensagens Java/Bedrock e jogadores ocultos. Nenhum arquivo de TAB, LuckPerms ou do formatador é alterado pelo Território.

## LuckPerms

Use os nós de [COMANDOS.md](COMANDOS.md). Atribuições são feitas pela staff. Não são necessárias APIs proprietárias nem alteração da configuração global de permissões.

## PvPManager

O Território bloqueia dano entre jogadores do mesmo grupo em qualquer localização. Grupos diferentes, aliados, jogadores confiados e jogadores solo continuam seguindo as regras normais do PvPManager. Não são criadas zonas de PvP; o Combat Log de 30 segundos, suas consequências e a ActionBar permanecem sob controle do PvPManager. Combate legítimo já existente não deve ser limpo ao bloquear um ataque de companheiro.

O alvo desta integração é PvPManager 4.0.22. O Território é carregado depois dele quando instalado, cancela o dano de companheiros antes da avaliação de combate e reafirma a proteção após os overrides de dano. Com PvPManager instalado, splash e nuvens atribuídas excluem temporariamente companheiros dos monitores de combate. Alvos com efeitos benéficos/neutros são restaurados após o monitor do PvPManager 4.0.22 para aplicação vanilla; efeitos prejudiciais individuais são cancelados separadamente. Isso preserva poções mistas como Mestre Tartaruga e evita que a lista configurável de poções do PvPManager crie combate entre companheiros. Essa restauração é uma exceção localizada à convenção de não alterar alvos em MONITOR: depende da ordem de registro garantida pelo softdepend. Outros plugins que filtram individualmente alvos de poções precisam ser homologados nessa ordem; alvos cancelados não são restaurados. Sem PvPManager, os alvos vanilla não são mascarados. A versão pública [4.0.22](https://github.com/ChanceSD/PvPManager/tree/4.0.22) foi examinada, mas as versões e configurações reais precisam de homologação.

Teste corpo a corpo, flechas comuns/com efeitos/em chamas, tridente, projéteis explosivos, splash e lingering: companheiros não devem sofrer dano nem iniciar ou renovar Combat Log, e inimigos devem manter combate e punições normais por desconexão. Confira também as configurações de override/vulnerabilidade do PvPManager. Fontes sem autoria identificável não são atribuídas ao jogador mais próximo nem ganham um sistema persistente de rastreamento.

A morte de um logger fake/NPC depende da implementação do PvPManager: a desconexão do invasor encerra o saque pelo evento normal de saída, sem simular uma morte ou conceder bônus de defesa fictício. Nenhum arquivo do PvPManager é modificado.

## CoreProtect

Quebras de saque são quebras Bukkit normais, contabilizadas após um evento não cancelado; o Território não destrói blocos silenciosamente. Valide a gravação no CoreProtect com a versão instalada e compare autoria/blocos. Portas e camas removem duas partes por comportamento vanilla: confira também os registros da parte complementar. Não foi embutida uma API de CoreProtect nem foram modificadas suas configurações.

## Geyser e SurvivalPlusCore

O manual abre via API Adventure do Paper, sem inserir livro no inventário. Comandos não dependem de clique, o chat é privado quando aplicável e partículas são enviadas somente ao jogador. O Geyser real deve ser testado; tradução de livros, partículas e títulos depende da versão/cliente. `visual.particle` permite escolher outra partícula sem dados extras.

Portais, escala Nether 1:6, avisos de fronteira e todos os arquivos do SurvivalPlusCore permanecem sob responsabilidade daquele plugin.

## LPC MiniMessage (Ayont) — Território 2.0.2

Este fork usa MiniMessage e não interpreta a saída legacy de `tag_formatada`. A versão 2.0.2 acrescenta somente `%territorio_prefixo_minimessage%`, contendo símbolo de cargo e TAG dourados, com estilos delimitados. Sem grupo retorna vazio, sem espaço extra. Os placeholders antigos permanecem iguais.

No arquivo enviado pelo administrador, substituir somente `chat-format` por:

```yaml
chat-format: "%territorio_prefixo_minimessage%{prefix}{name}<dark_gray> »<reset> {message}"
```

O restante do formato original, prefixos LuckPerms, separador e mensagem é preservado. Após atualizar o JAR do Território com o servidor parado, iniciar e executar `/lpc reload` depois de editar o formato. Não instalar simultaneamente LPC original e LPC MiniMessage. Requer PlaceholderAPI e LuckPerms habilitados; a expansão territorio é embutida. Testar jogador com grupo/sem grupo e cargos em chat real; não altera TAB/nametags nem os arquivos externos automaticamente.
