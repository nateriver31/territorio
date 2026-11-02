# Roteiro de homologação — Território 2.0

Este roteiro contém testes a executar, não resultados aprovados. Resultados realmente obtidos no ambiente de desenvolvimento são registrados no relatório de entrega. Livro, partículas e integrações exigem homologação com clientes e os plugins reais.

## Preparação

Use uma instância de testes com cópia de segurança, Paper 26.2 e Java 25. Não use mapas ou inventários de produção para experimentar saques. Instale Território, LuckPerms, PvPManager 4.0.22, CoreProtect, PlaceholderAPI, TAB e Geyser nas versões compatíveis escolhidas pela administração. Não substitua configurações desses plugins automaticamente.

Prepare pelo menos sete jogadores/contas de teste: A (líder), B (colíder), C (oficial), D (membro), E (líder de outro grupo), S (solo) e R (invasor). Uma conta administrativa deve possuir somente os nós explícitos necessários. Separe chunks adjacentes, chunks de donos distintos e um segundo mundo. Registre versão do JAR, servidor, plugins, horário, ação, resultado esperado e resultado obtido em cada caso. Para cooldowns longos, use uma cópia descartável com valores de configuração reduzidos; repita as verificações do cálculo nos testes automatizados. Não avance o relógio da máquina de produção.

## Grupos, hierarquia e identidade

| Caso | Procedimento e resultado esperado |
| --- | --- |
| G01 | Criar ROMA com nome `Império Romano`. Consultar nome, TAG e líder. Rejeitar nome ou TAG em uso, inclusive variantes de caixa. |
| G02 | Rejeitar TAG de um ou seis caracteres, `&a`, `§a`, quebras de linha e caracteres indevidos. Rejeitar nomes fora dos limites configurados. |
| G03 | Confirmar que jogador comum/líder não consegue renomear grupo ou TAG. A correção administrativa exige permissão e gera registro com valores anteriores/novos. |
| G04 | Convidar B, C e D; testar aceitação e recusa. Oficial pode convidar; membro não pode. Convite perde validade após cinco minutos, ao sumir o grupo e quando o destinatário entra em outro grupo. |
| G05 | Nomear B colíder e C oficial. Só um colíder; líder pode substituir/remover. Colíder não nomeia outro colíder nem altera o líder. |
| G06 | Líder/colíder promovem membro a oficial e rebaixam oficial; oficial/membro não. Colíder não expulsa superiores. Expulsão de membro por líder/colíder funciona fora de guerra. |
| G07 | Os quatro cargos constroem, quebram e abrem recipientes/portas no coletivo. Somente líder/colíder administram claims. Só líder decide diplomacia e dissolve. |
| G08 | Líder sai definitivamente: colíder assume. Repetir sem colíder e verificar oficial mais antigo; sem oficial, membro mais antigo. Empates têm resultado determinístico após reinício. |
| G09 | Líder desconecta: continua líder; colíder não herda o posto. |
| G10 | Último integrante sai ou líder confirma dissolução: grupo e claims coletivos deixam de existir; blocos permanecem. Claims pessoais dos antigos membros continuam privados. |
| G11 | Saída voluntária bloqueia criar/aceitar outro grupo por duas horas, inclusive após reinício. Expulsão não cria esse cooldown. |

## Claims e proteção

| Caso | Procedimento e resultado esperado |
| --- | --- |
| C01 | Solo protege nove chunks; décimo é recusado. Claim já existente nunca é sobrescrito por outro jogador. |
| C02 | Ao criar ou entrar em grupo, os claims solo migram uma única vez para o coletivo. Verificar capacidade nove por integrante, sem duplicação ao sair/entrar. |
| C03 | Cada integrante cria no máximo um pessoal extra. Outros membros e aliados não recebem acesso automático. Conceder e remover confiança pessoal, verificando persistência. |
| C04 | Remover integrante reduz capacidade coletiva. Se houver excesso, nenhum claim/bloco é removido arbitrariamente; novos claims ficam bloqueados até regularização conforme regra documentada. |
| C05 | Um grupo acima do limite tenta receber jogador com claims solo e transferi-los. Aceitação deve preservar o limite consistente e nunca duplicar a propriedade. |
| C06 | Estranho tenta quebrar, colocar blocos, abrir porta/alçapão/botão, baú simples/duplo, barril, shulker, funil, fornalha, crafter e recipientes de entidades. Verificar ações autorizadas e negadas. |
| C07 | Testar baldes, fogo, fluidos, TNT, creeper, cama/âncora explosiva, pistão normal/pegajoso, bloco de slime/honey, explosão adjacente e entidades que alteram blocos. TNT atribuída e creeper com responsável confiável respeitam construção/guerras; creeper desconhecido preserva claims. TNT sem autoria pode destruir claims fora de saque, conforme exceção aprovada. Fogo natural pode queimar dentro do território. Recipientes e demais proteções mantêm suas regras. |
| C08 | Tentar extrair itens via hopper e hopper minecart através da fronteira, inclusive baú duplo dividido entre chunks. Acesso a construção não concede acesso a recipientes de aliado. |
| C09 | Testar quadro, armor stand, item frame, leash e veículo/animal com inventário quando aplicável. PvP é bloqueado somente entre jogadores do mesmo grupo, independentemente do claim. |
| C10 | Testar coordenadas negativas e dois mundos com mesmo x/z. Claim pertence ao UUID do mundo e chunk correto. |

## Avisos, comandos e interface

| Caso | Procedimento e resultado esperado |
| --- | --- |
| U01 | Entrar/sair de claim próprio, solo alheio, coletivo e pessoal. Apenas quem atravessa recebe aviso, com `> ` verde e `< ` vermelho; coletivo mostra `do grupo [TAG] Nome` e individual mostra o dono. |
| U02 | Percorrer dois chunks do mesmo dono e mesmo tipo: sem mensagens repetidas. Trocar ROMA por VIK: saída anterior e entrada nova. Solo e pessoal do mesmo UUID são tipos distintos. |
| U03 | Oscilar sobre borda, teleportar e mudar de mundo: mensagens coerentes, sem spam e sem ActionBar. |
| U04 | `/t`, `/t ajuda` e ajuda antiga abrem livro sem item permanente no inventário. Verificar inventário cheio e mão ocupada; não perder nem duplicar itens. |
| U05 | Livro legível no celular, sem comandos administrativos e sem depender de cliques. Os cinco assuntos e os comandos/documentação correspondem à implementação. O livro explica que o invasor pode acionar circuitos que abrem portas/alçapões, embora não possa abri-los manualmente pela permissão de saque. |
| U06 | `/verchunk` liga/desliga somente para o usuário; quatro cantos acompanham chunk e altura. Outros jogadores não veem marcadores. Desconectar desativa. Frequência/quantidade respondem à configuração. |
| U07 | Testar aliases antigos úteis e autocompletar. Jogador sem cargo/permissão não recebe sugestões administrativas. Não sequestrar `/help` ou `/ajuda` globais. |
| U08 | Com outro plugin já usando `/t`, verificar aviso de conflito e acesso via `/territorio` ou comando com namespace. Não sobrescrever silenciosamente o comando de terceiros. |

## Guerras, paz e alianças

| Caso | Procedimento e resultado esperado |
| --- | --- |
| W01 | Declarar guerra com líder defensor offline: negar com motivo. Colíder online não substitui líder. Ambos líderes online: `/t guerra TAG` declara imediatamente, sem aceitação; `/t guerra declarar TAG` é equivalente. `status`/`historico` não são interpretados como TAG. Testar grupo com TAG `PAZ`. |
| W02 | Líder volta online após tentativa negada: nenhuma guerra começa automaticamente. Após início válido, desconexão dos líderes não encerra guerra. |
| W03 | Terceiro acessa claims do atacante; membro atacante acessa defensor conforme regra herdada; terceiro não ganha acesso geral ao defensor. Solo/pessoais não ficam públicos incidentalmente. |
| W04 | Durante guerra, todos os cargos tentam sair, expulsar, dissolver, transferir liderança, remover/transferir coletivo: negar. Não há bloqueio preventivo de 12/24 horas. |
| W05 | Enviar paz: membros online do grupo destinatário recebem nome/TAG da origem e instruções. Líder offline consegue consultar pendência ao retornar. |
| W06 | Destinatário aceita paz: somente líder correto, pedido pendente e guerra ativa. Guerra termina, acessos temporários cessam, histórico informa início/fim/resultado. |
| W07 | Destinatário recusa paz: remover pedido e manter guerra. Rejeitar aceitação sem pedido, do próprio remetente, de oficial/colíder e de guerra encerrada. Sem TAG, zero propostas informa ausência, uma escolhe a recebida e várias exigem TAG sem modificar nenhuma. |
| W08 | Reiniciar com guerra e paz pendente: ambos persistem, incluindo atacante/defensor. Histórico concluído não desaparece. |
| W09 | Propor/aceitar/recusar/remover aliança apenas como líder. Acesso inicial às três categorias está desligado. |
| W10 | Liberar separadamente mecanismos, recipientes e construção para aliado. Construção cobre colocar/quebrar; nenhuma opção concede administração. Verificar funis/mecanismos indiretos sem permissão de recipientes. |
| W11 | Iniciar hostilidade entre ex-aliados: permissões são suspensas/removidas imediatamente e não reaparecem automaticamente após paz. Reiniciar não restaura permissões antigas. |

## Saques

| Caso | Procedimento e resultado esperado |
| --- | --- |
| R01 | R entra em claim SOLO de S online e usa `/roubar`: inicia uma única sessão de cinco minutos. Dono não pode roubar a si; negar alvo offline, coletivo e pessoal. |
| R02 | Outro invasor tenta mesmo S; R tenta segundo alvo: negar ambos. Jogadores sem autorização continuam podendo lutar, mas não quebrar/abrir recipientes. |
| R03 | Validar proteção da vítima, cooldown do invasor e permissão de comando. Cooldowns normais começam no início, são por UUID do dono/invasor e persistem após reinício. |
| R04 | Quebrar 15 blocos em um claim e 15 em outro do mesmo S. A 31ª quebra é negada em todos; recipientes continuam acessíveis no tempo restante. |
| R05 | Quebra do saqueador fica mais lenta somente nas quebras autorizadas dos claims atacados. Testar ferramenta, eficiência, haste, fadiga, água, airborne e bloco instantâneo; nenhum modificador permanece após saída/fim/morte. |
| R06 | Abrir baú simples/duplo, barril e shulker; retirar itens sem limite artificial. Quebrar recipientes é negado, mesmo vazios. Ender Chest mantém isolamento vanilla. |
| R07 | Porta e alçapão não abrem diretamente por autorização de saque. Quebrar porta conta no limite; testar estrutura com duas metades e blocos dependentes para evitar remoções extras não contabilizadas. |
| R08 | Tentar TNT direta/automática/remota/em cadeia, priming de TNT já colocada, pistões, líquidos, fogo e suporte de blocos dependentes para contornar orçamento. Nenhum caminho deve remover blocos protegidos adicionalmente. Dono/invasor acionam botões/alavancas/placas; terceiro não. Circuitos podem abrir portas/alçapões (D1-B); armadilhas de projéteis autorizadas funcionam sem liberar extração de itens. |
| R09 | Trocar de território e voltar não reinicia tempo nem orçamento. Teleporte/respawn não transfere autorização para terceiros/aliados. |
| R10 | Todos recebem anúncio anônimo sem coordenadas. Dono recebe título por cerca de dois segundos. Nenhum Glowing, cor de TAB, exposição por partículas ou ActionBar permanente. |
| R11 | Somente R e S recebem 4, 3, 2, 1 minuto, 30 segundos e 10…1 segundo, uma vez por marco. `/roubar status` mostra estado correto. |
| R12 | Ao completar cinco minutos, terminar, revogar acessos e anunciar fim sem revelar invasor. Itens já retirados não são devolvidos. |
| R13 | Dono mata R; repetir com terceiro e projétil. Encerrar imediatamente, revelar somente invasor, nunca assassino, anunciar defesa e proteger dono por 24h sem acumulação ilimitada. |
| R14 | R morre por queda/lava/mob sem crédito de jogador: encerrar sem bônus/revelação especial. Testar dano antigo e dano indireto para evitar crédito falso. |
| R15 | R desconecta: encerrar imediatamente. PvPManager aplica suas próprias consequências sem interferência. Nenhum aviso privado atrasado continua. |
| R16 | S desconecta: no máximo 60s extras e nunca além do prazo original. Reconectar dentro da janela cancela encerramento offline; desconectar novamente aplica a regra documentada sem estender os cinco minutos. |
| R17 | Enquanto alvo de saque, S tenta criar/entrar em grupo: negar. Mudanças de claim não podem cancelar cooldown ou criar novo alvo para burlar proteção. |
| R18 | Reiniciar durante saque: não restaurar autorização ativa; preservar cooldowns e registrar encerramento por reinício. Nenhum recipiente aberto permite continuar retirando itens após perda da permissão. |
| R19 | Auditar início/fim, invasor/vítima, causa, blocos e defesa como staff. Jogador comum não lê identidade anônima através de comandos, sugestões ou placeholders. |

## Administração, persistência e integrações

| Caso | Procedimento e resultado esperado |
| --- | --- |
| A01 | OP sem nós administrativos explícitos não recebe ações perigosas automaticamente. LuckPerms concede cada nó necessário e a operação correspondente funciona. |
| A02 | Inspecionar dono/grupo/rank, corrigir liderança/nome/TAG, consultar guerras/saques, intervir em claim e recarregar: alterações relevantes registram autor, data, operação, alvo, antes/depois. |
| A03 | Administração comum não ignora guerra implicitamente. Intervenção excepcional exige autorização explícita e auditoria. Bypass tem ativação consciente e não é herdado por todos os operadores. |
| A04 | Reiniciar e conferir grupos, cargos, antiguidade, claims, confiança, alianças, guerra, propostas, cooldowns e históricos. Verificar falha de escrita sem sobrescrever silenciosamente estado válido. |
| A05 | Configuração inválida ou armazenamento inválido deve produzir falha visível e segura, sem wipe ou liberação silenciosa de todos os claims. Recarregar configuração não reinicia saques nem limpa dados. |
| I01 | Sem PlaceholderAPI/TAB, plugin continua funcional. Com PAPI, placeholders retornam TAG/nome/cargo/estado de grupo. TAG e símbolos por cargo dourados aparecem antes do nick via configuração externa aprovada de TAB/nametag/chat, sem alterar cor normal do nick. Sem grupo não há espaços extras; conferir glifos e alternativa ASCII nos dois clientes. |
| I02 | Entrar, sair, ser expulso e trocar grupo atualizam TAB/nametag. Solo não recebe TAG. Território não cria equipes concorrentes de scoreboard. |
| I03 | Geyser: executar comandos, aceitar convites, administrar grupo, abrir livro, ver chat e partículas com conta Bedrock real. Registrar partículas não exibidas e escolher configuração compatível. |
| I04 | Shift e invisibilidade continuam vanilla, durante saque inclusive. Não há identificação visual especial do invasor. |
| I05 | Mesmo grupo não sofre PvP por corpo a corpo, flechas comuns/com efeitos/em chamas, tridentes, TNT atribuída e poções prejudiciais splash/lingering; ataque não inicia/renova Combat Log. Outros grupos, aliados, confiados e solo continuam sujeitos ao PvP normal dentro/fora dos claims. Testar override/vulnerabilidade do PvPManager 4.0.22. Combate legítimo conserva 30s e punições por desconexão; poções benéficas e a resistência de Mestre Tartaruga continuam permitidas. Conferir a lista configurável de poções do PvPManager (incluindo Glowing), nuvens de poção base e cancelamentos de outros plugins. |
| I06 | CoreProtect registra as quebras/colocações reais autorizadas, inclusive do saqueador. Comparar log de auditoria do Território com logs de blocos. |
| I07 | Comparar checksums/backups dos arquivos de SurvivalPlusCore, PvPManager, LuckPerms, TAB, Geyser e CoreProtect: Território não os reescreve. Portais Nether, escala 1:6 e mensagens de fronteira continuam inalterados. |

## Critério de aprovação

Não declarar homologação concluída com casos obrigatórios falhando ou não executados. Classificar cada caso como passou, falhou, bloqueado ou não executado, incluindo evidência. Falha de gameplay, acesso indevido, perda de dados ou duplicação impede implantação em produção. Compatibilidade real Java/Bedrock e com os plugins externos exige execução dos casos I01–I07 na instância de testes.

## Casos adicionais do beta 2.0.1

- Acender TNT com pederneira e seta incendiária, como dono, confiado, membro, estranho e aliado com/sem construção expressa. Conferir também o bloco TNT ao primar.
- Testar creeper com ignitor autorizado/negado, perseguição a alvo autorizado/negado, ignitor desconhecido e ausência de alvo. Jogador mais próximo não deve determinar autoria.
- TNT sem fonte após recarga de chunk continua abrangida por D2-A fora de saque. Essa limitação é documentada; abusos serão regulados pela administração, sem rastreamento novo.
- Fogo de lava/raio e propagação interna consomem blocos fora de saque. Fronteiras e baldes continuam protegidos; sob saque, ignição/queima/propagação destrutiva permanecem bloqueadas.
- Durante saque, verificar reset de botões, repetidores/comparadores/tochas, componentes sem suporte, pistões com slime/honey, dispensers de flecha versus TNT/fogo/baldes e dropper/hopper. Nenhuma nova destruição ou extração automática deve escapar do limite/regras.
- O efeito visual de blocos fantasmas do Geyser não faz parte desta correção; não alterar proteção de quebra ou os plugins de protocolo para esse caso.
