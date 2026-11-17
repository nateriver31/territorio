# Homologação manual — Território 2.1.0

**Pendente.** Executar em instância isolada, com cópia recuperável dos mundos e da pasta do Território. Os testes automatizados e a conferência de assinaturas do DiscordSRV não substituem este roteiro. Não executar exclusões, corrupção ou testes de falha na produção.

## Dados e inicialização

1. Com a origem parada, copiar `state.json` esquema 2, configuração, auditoria, mundos e JAR antigo para backup externo. Registrar números de grupos, claims normais/pessoais, cargos, permissões, guerras ativas/históricas, paz e alianças. Isolar a cópia para não conectar jogadores nem o bot de produção por engano.
2. Na cópia Paper 26.2/Java 25, instalar somente o JAR 2.1.0 e iniciar. Conferir `pre-v3`, esquema 3, todos os saldos em zero e todos os registros anteriores preservados. Testar acesso permitido/negado nos claims antigos e `/t claims` de solo, líder e membro.
3. Reiniciar normalmente e conferir os mesmos dados. Em outra cópia descartável, provocar JSON inválido ou permissão de gravação negada: esperar manutenção, sem substituição por arquivo vazio; restaurar o backup válido com o servidor parado. Ensaiar a recuperação completa sem misturar JAR antigo com dados esquema 3.

## Claims Bônus e combate

4. Dois grupos começam em zero. Matar entre grupos diferentes sem guerra: +1/-1 independente, mínimo zero; repetir em novas vidas sem cooldown. Testar vencedor cheio e derrotado zero. Testar aliados de grupos diferentes, corpo a corpo, flechas, tridente e TNT com autoria Paper identificada. Ambiente, `/kill`, mesmo grupo e solo não transferem saldo.
5. Reivindicar três bônus em ordem conhecida. Gastar saldo livre primeiro: nenhum aviso/perda indevida. Quando utilizados excederem o saldo, somente o último perde proteção. Conferir que blocos, normal e pessoal continuam no mundo e que terceiros só ganham acesso no chunk efetivamente liberado. Reiniciar entre reivindicação e perda para validar LIFO.
6. Dois membros, saldo 6/12: saída deixa 6/6. Dois membros, saldo e chunks 12/12: saída/expulsão deixa 6/6 e os seis últimos sem proteção. Voltar depois do cooldown aumenta capacidade, sem restaurar os pontos. Entrar noutro grupo não transporta saldo. Testar também saldo 12 com apenas três chunks: mantém os três.
7. Entrar nos bônus em ordem diferente: somente o próprio jogador recebe mensagem; integrantes veem posição e prioridade do último, visitantes somente grupo proprietário. Permanecer no chunk não repete. Avisos de perda só para integrantes online do grupo afetado, com coordenadas/mundo e motivo; inimigos/offline não recebem.
8. Conferir a vulnerabilidade assimétrica também nos bônus, sem abrir pessoais por guerra. Declaração continua unilateral, com ambos os líderes online. Não alterar as regras de saque, cooldowns ou Combat Log.
9. No PvPManager real, verificar que ataques de companheiros não criam/renovam combate nem punições indevidas; combate legítimo mantém sua configuração atual. Repetir os testes de poções do [roteiro anterior](TESTES-MANUAIS.md). Não esperar recompensa criada por desconexão ou logger NPC.

## Livros e aldeões

10. Na configuração da cópia, colocar chance 1 e matar vítima de grupo com claims elegíveis: exatamente um livro físico assinado, uma localização válida e dimensão correta; guardar, negociar e reiniciar. Verificar nomes longos, acentos, coordenadas negativas, Nether e End. Um assassino solo pode obter livro; vítimas solo, companheiros, ambiente, mobs, `/kill` e morte cancelada não geram.
11. Testar chance 0, padrão 0.25 e chances intermediárias, com Pilhagem 0/I/II/III. Usar amostra suficiente; um punhado de mortes não comprova probabilidade. Confirmar que não há duplicação por plugin de mortes e que o mesmo jogador pode morrer novamente após respawn.
12. Testar opções de claims normais/bônus/pessoais, grupo sem claim elegível, chunk bônus perdido na mesma morte e mundo descarregado. Verificar interação com `keepInventory`, plugins que retêm inventários e recolhimento dos drops. Nenhum livro já existente deve desaparecer ao desativar/recarregar.
13. Farm real: zumbis atacam aldeões em claim, aldeões fogem/reagem e a farm mantém funcionamento. Jogador/projétil não autorizado continua impedido de ferir aldeões e alterar blocos. Reprodução não foi modificada.

## Recarga, logs e administração

14. Recarregar pelos dois comandos existentes. Suspender/reativar bônus/PvP/livros sem apagar saldo, proteção ou ordem, nem aplicar mortes retroativas. Saída/expulsão ainda aplica a regra explícita de capacidade. Reduzir capacidade abaixo de saldo existente: recarga inteira recusada. Testar YAML e valores inválidos, preservando opções em execução. Devolver os valores desejados ao final.
15. Conferir logs de kills no máximo/mínimo, UUIDs, saldos, reivindicações/perdas, saída/expulsão/criação/exclusão, livros e administração. Reiniciar e conferir append. Na cópia, criar somente arquivos fictícios de log antigo e conferir retenção; dados, backups, auditoria e outros nomes devem permanecer. Provocar falha apenas no diretório de logs: aviso no servidor e proteção mantida.
16. Grupo descartável na cópia: testar `/t admin deletar TAG`, executor diferente, expiração de 60s, mudança de membros/claims após solicitação e ausência da permissão. Com confirmação válida, verificar limpeza das referências, encerramento das guerras, histórico preservado, pessoais/blocos/inventários intactos e sucesso só após gravação. Testar console e registrar executor no log. Não usar grupos reais para esse ensaio.

## DiscordSRV e clientes reais

17. Usar DiscordSRV 1.30.5 com bot/canal **de teste**. Vazio deve selecionar o canal principal mapeado de chat; testar chave explícita válida e inválida. Testar comando sem permissão, por staff e console: sucesso apenas após ack; sem alteração de grupos/guerras. Conferir falhas desconectado, bot sem permissão e canal removido.
18. Declarar guerra e aceitar paz: um anúncio de cada, depois de salvar; propostas/recusas não anunciam. Reload/reinício não repete. Simular nomes com tentativa de menção apenas em fixture descartável: não deve pingar ninguém. Interrupção de rede não bloqueia diplomacia; não há promessa de reenviar anúncio perdido.
19. Nos clientes Java e Bedrock reais: ler as 28 páginas do `/t` e as páginas do livro físico; testar comandos/aliases/autocomplete, cores/símbolos das TAGs, LPC MiniMessage/TAB, ausência de espaço solo, vanish, partículas e avisos. Confirmar atualização após expulsão, liderança, correção de TAG e exclusão administrativa.
20. Executar as regressões de explosões, fogo, redstone/recipientes/30 blocos de saque e CoreProtect do [roteiro anterior](TESTES-MANUAIS.md). O efeito de blocos fantasmas do Geyser continua fora do escopo.

Registrar versões, configuração relevante, data, resultado e evidência de cada teste. Só programar a troca manual em produção depois da homologação da cópia e de outro backup atualizado. Não há instalação ou exclusão automática nesta entrega.
