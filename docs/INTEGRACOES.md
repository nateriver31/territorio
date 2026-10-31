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
| `%territorio_tem_grupo%` | `true` | `false` |

Teste manualmente `/papi parse me %territorio_tag_formatada%` com alguém que tem grupo e depois de sair. Sem PlaceholderAPI, os sistemas de jogo continuam funcionando; só a integração visual dependente dela fica indisponível. Não cria times de scoreboard.

## TAB

Faça backup e revise a documentação da sua versão do TAB. Acrescente a TAG **antes dos valores que sua instalação já usa**, para que o reset da TAG seja seguido das cores/prefixos normais. Exemplo de sugestão para os campos de grupo aplicáveis na sua configuração, preservando o restante:

```yaml
# Exemplo parcial — acrescente aos valores existentes, não sobrescreva seu arquivo.
tabprefix: '%territorio_tag_formatada%%luckperms-prefix%'
tagprefix: '%territorio_tag_formatada%%luckperms-prefix%'
```

Se seus valores existentes incluem placeholders/cores diferentes, mantenha-os depois da TAG. `tabprefix`/`tagprefix` devem ficar no local apropriado da configuração de grupos do TAB; não cole cegamente no topo do `config.yml`. A ativação da formatação da lista e do sistema de nametags fica sob controle do administrador do TAB. Nunca crie times paralelos pelo Território.

Para atualização periódica, a configuração da sua versão pode aceitar este trecho parcial:

```yaml
placeholder-refresh-intervals:
  '%territorio_tag_formatada%': 1000
  '%territorio_grupo%': 1000
  '%territorio_cargo%': 1000
  '%territorio_tem_grupo%': 1000
```

Mescle com o bloco já existente; não descarte outros placeholders. Verifique no servidor real: nome acima da cabeça, TAB, ingresso/saída/expulsão, renomeação administrativa, Shift e invisibilidade. O Território não modifica nick, Glowing, equipes nem visibilidade durante saques.

## LuckPerms

Use os nós de [COMANDOS.md](COMANDOS.md). Atribuições são feitas pela staff. Não são necessárias APIs proprietárias nem alteração da configuração global de permissões.

## PvPManager

Nada deve ser configurado pelo Território. Ele não cancela PvP, não cria zonas, não altera as consequências do Combat Log e não usa ActionBar constante. A morte de um logger fake/NPC depende da implementação do PvPManager: a desconexão do invasor encerra o saque pelo evento normal de saída, sem simular uma morte ou conceder bônus de defesa fictício.

## CoreProtect

Quebras de saque são quebras Bukkit normais, contabilizadas após um evento não cancelado; o Território não destrói blocos silenciosamente. Valide a gravação no CoreProtect com a versão instalada e compare autoria/blocos. Portas e camas removem duas partes por comportamento vanilla: confira também os registros da parte complementar. Não foi embutida uma API de CoreProtect nem foram modificadas suas configurações.

## Geyser e SurvivalPlusCore

O manual abre via API Adventure do Paper, sem inserir livro no inventário. Comandos não dependem de clique, o chat é privado quando aplicável e partículas são enviadas somente ao jogador. O Geyser real deve ser testado; tradução de livros, partículas e títulos depende da versão/cliente. `visual.particle` permite escolher outra partícula sem dados extras.

Portais, escala Nether 1:6, avisos de fronteira e todos os arquivos do SurvivalPlusCore permanecem sob responsabilidade daquele plugin.
