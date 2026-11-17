# Manual do livro /t — Território 2.1.0

Texto das 28 páginas, com os valores padrão de 9 claims solo, 9 normais e 6 bônus por integrante e chance de livro de 25%. O livro usa os limites e a chance configurados em execução. Os quatro tópicos de bônus/livros foram inseridos na parte de claims; os cinco assuntos originais permanecem. Leitura Java/Bedrock exige teste real.

## Página 1

```text
Território

1. Claims e proteção
2. Grupos e cargos
3. Guerras e paz
4. Alianças
5. Saques

/t abre este manual.
Os atalhos antigos continuam válidos.
```

## Página 2

```text
1. Claims

/t claim
Protege o chunk atual.
/t unclaim
Remove a proteção.
/t claims
Consulta os terrenos.

Solo: até 9 chunks.
/verchunk mostra os cantos.
```

## Página 3

```text
Claim pessoal

Cada membro soma 9 chunks coletivos.

/t claim pessoal
Um chunk extra privado.

/t confiar Jogador
/t desconfiar Jogador
Acesso às suas claims individuais.
```

## Página 4

```text
TNT de jogador

Exige acesso de construção: dono, grupo, confiança, permissão explícita ou guerra. Só ser aliado não basta.
```

## Página 5

```text
Creepers

A explosão usa autoria confiável. Sem ela, os claims ficam protegidos.
```

## Página 6

```text
TNT automática

TNT sem autoria pode destruir claims, fora de guerra também. Isso inclui autoria perdida ao recarregar chunks. Sujeito às regras do servidor.

Em saque ativo, explosões não destroem os claims da vítima.
```

## Página 7

```text
Fogo natural

Quem tem permissão pode acender fogo diretamente. Terceiros sem acesso não podem.
Fogo, lava e raios podem iniciar incêndios naturais e consumir blocos. Proteções extras continuam durante saques.
```

## Página 8

```text
Claims Bônus

/t claim bonus
Somente líder/colíder.
/t claims consulta os saldos.

Cada membro dá capacidade para 6 bônus; entrar não dá saldo. Grupos começam em zero.
```

## Página 9

```text
Conquistar e perder

PvP entre grupos: vencedor +1, derrotado -1. Saldo entre zero e capacidade. Sem cooldown.

Sem saldo para manter chunks, o bônus mais recente perde proteção. Blocos ficam.
```

## Página 10

```text
Mudança de membros

Saída/expulsão reduz a capacidade. Bônus excedentes são perdidos. Chunks são liberados do mais recente ao mais antigo, com aviso privado.

Retornar não recupera bônus.
```

## Página 11

```text
Livro de informações

PvP válido: 25% de chance de livro com um claim do grupo da vítima.

Pilhagem não altera a chance. Sem cooldown. Pessoais excluídos por padrão. Livro negociável.
```

## Página 12

```text
2. Criar grupo

/t grupo criar TAG Nome
TAG: 2 a 5 letras ou números. Nome e TAG são fixos para jogadores.

/t grupo info [TAG]
/t grupo lista [página]
/t grupo membros [TAG]
```

## Página 13

```text
Entrar no grupo

/t grupo convidar Jogador
Convite dura 5 minutos.
/t grupo aceitar
/t grupo recusar

Ao entrar, claims solo passam ao coletivo, respeitando os limites.
```

## Página 14

```text
Cargos

Líder: gestão e diplomacia.
Colíder: gestão; só um.
Oficial: convida.
Membro: usa coletivos.

/t grupo cargo Jogador Cargo
membro, oficial, colider.
```

## Página 15

```text
PvP do grupo

Mesmo grupo não causa PvP entre si, em qualquer local.

Aliados, jogadores de confiança, grupos diferentes e solo mantêm PvP normal.
```

## Página 16

```text
Saída e limites

/t grupo sair
Espera: 2h para entrar ou criar grupo.

/t grupo expulsar Jogador
Expulso não tem espera.
Normais em excesso bloqueiam novas claims. Blocos ficam.
```

## Página 17

```text
Liderança

/t grupo lider Jogador confirmar
Ao sair, sucede o colíder, oficial ou membro mais antigo.

/t grupo dissolver confirmar
Libera coletivos. Preserva pessoais e blocos.
```

## Página 18

```text
3. Guerras

/t guerra TAG
Declaração unilateral: não depende de aceitação.

Os líderes dos dois grupos precisam estar online no início. Desconectar depois não encerra a guerra.
```

## Página 19

```text
Territórios na guerra

Coletivos do atacante: abertos a todos.
Coletivos do defensor: abertos aos atacantes.

Solo e pessoais seguem privados.

TNT automática: vale a exceção.
```

## Página 20

```text
Bloqueios e histórico

Na guerra: sem saída, expulsão, dissolução, transferência de liderança, entrada ou alteração dos coletivos.
/t guerra status
/t guerra historico [TAG] [página]
Início, fim e resultado.
```

## Página 21

```text
Paz

/t paz TAG
Envia proposta. Só o líder decide.

/t paz aceitar [TAG]
/t paz recusar [TAG]

Uma proposta recebida: TAG opcional. Várias: informe a TAG. Recusar mantém a guerra.
```

## Página 22

```text
4. Alianças

/t aliados lista
/t aliados propor TAG
/t aliados aceitar TAG
/t aliados recusar TAG
/t aliados remover TAG

Somente líderes decidem. Os acessos começam desativados.
```

## Página 23

```text
Acesso de aliados

/t aliados permissao TAG portas sim
Tipos: portas, recipientes, construcao. Revogar: nao.

Só coletivos. Só a aliança não autoriza explosões; nunca dá administração.
```

## Página 24

```text
5. Saques de solo

Entre no claim e use /roubar. Dono deve estar online.

Dura 5 minutos e abrange os claims solo do dono.
Um invasor, até 30 blocos, mineração mais lenta.
/roubar status
```

## Página 25

```text
Blocos e recipientes

Pode abrir baús e barris e retirar itens sem limite. Não pode quebrar recipientes.

Após 30 blocos, baús seguem acessíveis até o fim.
Explosões e mecanismos não podem burlar o limite.
```

## Página 26

```text
Redstone no saque

Dono e invasor usam botões, alavancas e placas de pressão.

Sem acesso para terceiros.

Pistões e dispensers continuam sujeitos às proteções do saque.
```

## Página 27

```text
Portas no saque

O saque não permite abrir portas ou alçapões manualmente.

Porém, circuitos acionados pelo invasor podem abri-los.
```

## Página 28

```text
Defesa e cooldowns

Invasor morto por jogador: defesa, identidade revelada e 24h de proteção.
Normal: dono 12h, invasor 2h.

Invasor desconecta: encerra. Dono offline: até 60s, dentro do prazo original.
```
