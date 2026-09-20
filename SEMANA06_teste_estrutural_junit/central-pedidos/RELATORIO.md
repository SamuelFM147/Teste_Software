# Relatório do grupo

Integrantes:

- Samuel Fuentes Michels — RA 24011114-2
- Felipe de Almeida Matrone — RA 24051005-2

Projeto: `central-pedidos` (SEMANA06 — teste estrutural com JUnit 5 e JaCoCo).

Comando usado: `mvn clean test` · Relatório: `target/site/jacoco/index.html`

---

## Modelo adotado para os grafos

Antes dos números, as três convenções que mudam a contagem:

1. **Curto-circuito.** Cada operando de `&&` e `||` é um **nó de decisão separado**, com
   saídas verdadeira e falsa próprias. Esse é o modelo mais fino e é o que faz
   `V(G) = E − N + 2` coincidir com `decisões + 1` neste relatório. O JaCoCo usa o mesmo
   critério ao contar branches, então os dois números conversam.
2. **Saída unificada.** Todo `return` e todo `throw` liga-se a um único nó **FIM**. Sem isso
   o grafo não seria conectado com saída única e `E − N + 2` não valeria.
3. **Exceções.** Uma chamada dentro de `try` é um nó com **três** arestas de saída
   (retorno normal, `catch` correspondente, propagação). O JaCoCo **não** conta essas
   arestas como branch — por isso o `V(G)` de `pagar` é maior do que a complexidade que o
   relatório de cobertura exibe para o método.
4. **`switch`.** Contado pelo número de arcos de saída, não por `decisões + 1`.
   `case "SP": case "RJ":` são dois arcos que caem no mesmo bloco.

---

## Grafo de chamadas de `PedidoService.fechar`

```
PedidoService.fechar(pedido, cliente)
├── Objects.requireNonNull(pedido)
├── Objects.requireNonNull(cliente)
├── Cliente.bloqueado()
├── Pedido.subtotalCentavos() ──> ItemPedido.totalCentavos()
├── Pedido.estoqueSuficiente() ─> ItemPedido.disponivel()
├── PoliticaDesconto.calcular(cliente, subtotal, cupom)
│      └── Cliente.vip() / Cliente.comprasAnteriores()
├── CalculadoraFrete.calcular(pedido, cliente, liquido)
│      ├── Pedido.uf() / Pedido.expresso()
│      ├── Pedido.pesoGramas() ──> ItemPedido.pesoGramas() x quantidade()
│      ├── Pedido.temFragil()  ──> ItemPedido.fragil()
│      └── Cliente.vip()
├── AnaliseRisco.avaliar(cliente, total, expresso)
└── PagamentoService.pagar(total, 3)
       └── ProcessadorPagamento.autorizar(total)   [dependência substituída por stub]
```

A única fronteira externa é `ProcessadorPagamento`. Nos testes ela é substituída por uma
lambda ou por um stub com contador, que registra **quantas** chamadas houve e **com qual
valor** — é o que permite provar que o pagamento *não* foi acionado nos caminhos que
retornam antes.

---

## CFG 1 — `AnaliseRisco.avaliar`

```
N1  entrada; total < 0 ?
N2  throw IllegalArgumentException
N3  cliente.bloqueado() ?
N4  return "RECUSADO"
N5  comprasAnteriores == 0 ?
N6  total > 100_000 ?          (1º operando do ||)
N7  expresso ?                 (2º operando do ||)
N8  return "REVISAO"           (ramo do cliente novo)
N9  total > 500_000 ?          (1º operando do &&)
N10 !vip ?                     (2º operando do &&)
N11 return "REVISAO"           (ramo do cliente recorrente)
N12 return "APROVADO"
N13 FIM

Arestas: 1→2(V) 1→3(F) 2→13 3→4(V) 3→5(F) 4→13 5→6(V) 5→9(F)
         6→8(V) 6→7(F) 7→8(V) 7→12(F) 8→13
         9→10(V) 9→12(F) 10→11(V) 10→12(F) 11→13 12→13
```

N = 13 · E = 19 · **V(G) = 19 − 13 + 2 = 8** · decisões (1, 3, 5, 6, 7, 9, 10) + 1 = **8** ✔

| # | Caminho | Dados | Esperado |
| --- | --- | --- | --- |
| R1 | 1→2→13 | total = −1 | `IllegalArgumentException` |
| R2 | 1→3→4→13 | bloqueado, total = 0 | `RECUSADO` |
| R3 | 1→3→5→6→8→13 | 0 compras, total 100.001 | `REVISAO` |
| R4 | 1→3→5→6→7→8→13 | 0 compras, total 0, expresso | `REVISAO` |
| R5 | 1→3→5→6→7→12→13 | 0 compras, total 100.000, normal | `APROVADO` |
| R6 | 1→3→5→9→10→11→13 | 5 compras, total 500.001, não VIP | `REVISAO` |
| R7 | 1→3→5→9→10→12→13 | 5 compras, total 500.001, VIP | `APROVADO` |
| R8 | 1→3→5→9→12→13 | 5 compras, total 500.000 | `APROVADO` |

Todos os oito caminhos são viáveis na unidade. **R2 é inviável através de `PedidoService.fechar`**
(ver "Análise crítica").

---

## CFG 2 — `PoliticaDesconto.calcular`

```
N1  entrada; subtotal < 0 ?          N12 comprasAnteriores == 0 ?
N2  throw (subtotal negativo)        N13 subtotal >= 10_000 ?
N3  cliente.vip() ?                  N14 desconto += 2_000
N4  desconto = 10%                   N15 subtotal >= 20_000 ?
N5  subtotal >= 50_000 ?             N16 desconto += 10%
N6  desconto = 5%                    N17 throw (cupom desconhecido)
N7  desconto = 0                     N18 teto = 20%; desconto > teto ?
N8  cupom == null ?                  N19 return teto
N9  cupom.isBlank() ?                N20 return desconto
N10 return desconto (antecipado)     N21 FIM
N11 switch (cupom normalizado)

Arestas: 1→2 1→3 2→21 3→4 3→5 4→8 5→6 5→7 6→8 7→8
         8→10 8→9 9→10 9→11 10→21
         11→12(BEMVINDO) 11→15(EXTRA10) 11→17(default)
         12→13 12→18 13→14 13→18 14→18 15→16 15→18 16→18 17→21
         18→19 18→20 19→21 20→21
```

N = 21 · E = 31 · **V(G) = 31 − 21 + 2 = 12**
Conferência: 9 decisões binárias (1, 3, 5, 8, 9, 12, 13, 15, 18) + `switch` de 3 arcos (+2) + 1 = **12** ✔

| # | Caminho | Dados | Esperado |
| --- | --- | --- | --- |
| D1 | 1→2→21 | subtotal = −1 | `IllegalArgumentException` |
| D2 | 1→3→4→8→10→21 | VIP, 10.000, cupom `null` | 1.000 |
| D3 | 1→3→5→6→8→10→21 | comum, 50.000, cupom `null` | 2.500 |
| D4 | 1→3→5→7→8→10→21 | comum, 49.999, cupom `null` | 0 |
| D5 | 1→3→5→7→8→9→10→21 | comum, 10.000, cupom `"   "` | 0 |
| D6 | …9→11→12→13→14→18→20→21 | 0 compras, 10.000, `BEMVINDO` | 2.000 |
| D7 | …9→11→12→13→18→20→21 | 0 compras, 9.999, `BEMVINDO` | 0 |
| D8 | …9→11→12→18→20→21 | 3 compras, 10.000, `BEMVINDO` | 0 |
| D9 | …9→11→15→16→18→20→21 | comum, 20.000, `EXTRA10` | 2.000 |
| D10 | …9→11→15→18→20→21 | comum, 19.999, `EXTRA10` | 0 |
| D11 | …9→11→17→21 | cupom `"PROMO"` | `IllegalArgumentException` |
| D12 | 1→3→4→8→9→11→12→13→14→18→19→21 | VIP, 0 compras, 10.000, `BEMVINDO` | 2.000 (teto) |

Todos viáveis. Observação de contrato: o **teto de 20% só é avaliado quando há cupom** —
sem cupom o método retorna em N10. Como o desconto base máximo é 10%, o comportamento
visível é o mesmo, mas estruturalmente são caminhos distintos (D2 x D12).

---

## CFG 3 — `CalculadoraFrete.calcular`

```
N1  entrada; liquido < 0 ?      N10 !expresso ?        (2º operando do &&)
N2  throw                       N11 frete = 0
N3  switch (uf)                 N12 cliente.vip() ?
N4  frete = 1_200   (PR)        N13 frete /= 2
N5  frete = 2_000   (SP, RJ)    N14 pedido.expresso() ?
N6  frete = 3_000   (default)   N15 frete += 1_500
N7  excedente = peso − 2_000;   N16 pedido.temFragil() ?
    excedente > 0 ?  (while)    N17 frete += 500
N8  frete += 300;               N18 return frete
    excedente -= 1_000          N19 FIM
N9  liquido >= 30_000 ?

Arestas: 1→2 1→3 2→19
         3→4(PR) 3→5(SP) 3→5(RJ) 3→6(default)
         4→7 5→7 6→7
         7→8(V) 7→9(F) 8→7  [aresta de retorno do laço]
         9→10 9→12 10→11 10→12 11→12
         12→13 12→14 13→14 14→15 14→16 15→16 16→17 16→18 17→18 18→19
```

N = 19 · E = 28 · **V(G) = 28 − 19 + 2 = 11**
Conferência: 7 decisões binárias (1, 7, 9, 10, 12, 14, 16) + `switch` de 4 arcos (+3) + 1 = **11** ✔
Aqui `decisões + 1` **só funciona** porque o `switch` foi contado por arcos, não como uma decisão.

| # | Aresta nova exercitada | Dados | Esperado |
| --- | --- | --- | --- |
| F1 | 1→2 | líquido = −1 | `IllegalArgumentException` |
| F2 | 3→4 | PR, 1 kg, líquido 10.000, comum, normal | 1.200 |
| F3 | 3→5 (SP) | SP, mesmas condições | 2.000 |
| F4 | 3→5 (RJ) | RJ, mesmas condições | 2.000 |
| F5 | 3→6 | MG, mesmas condições | 3.000 |
| F6 | 7→8→7 | PR, 2.001 g | 1.500 |
| F7 | 9→10→11 | PR, líquido 30.000, normal | 0 |
| F8 | 10→12 | PR, líquido 100.000, **expresso** | 2.700 |
| F9 | 12→13 | MG, 3 kg, VIP | 1.650 |
| F10 | 14→15 | PR, expresso | 2.700 |
| F11 | 16→17 | PR, item frágil ativo | 1.700 |

Iterações do laço exercitadas: **0** (peso 1.999 e 2.000), **1** (2.001 e 3.000),
**2** (3.001), **3** (5.000) e **4** (5.001) — limite exato e fração, conforme o contrato
("por quilo adicional **ou fração**").

---

## CFG 4 — `PagamentoService.pagar`

```
N1  entrada; total <= 0 ?
N2  throw ("Total deve ser positivo")
N3  maxTentativas < 1 ?     (1º operando do ||)
N4  maxTentativas > 3 ?     (2º operando do ||)
N5  throw ("Use 1 a 3 tentativas")
N6  tentativa = 0
N7  tentativa++; processador.autorizar(total)      <-- nó com 3 saídas
N8  return (resultado do processador)
N9  catch (IllegalStateException) { }
N10 tentativa < maxTentativas ?   (while do do/while)
N11 return false
N12 propagação de exceção não tratada
N13 FIM

Arestas: 1→2 1→3 2→13 3→5 3→4 4→5 4→6 5→13 6→7
         7→8 (retorno normal) 7→9 (IllegalStateException) 7→12 (outra exceção)
         8→13 9→10 10→7 [retorno do laço] 10→11 11→13 12→13
```

N = 13 · E = 18 · **V(G) = 18 − 13 + 2 = 7**
Conferência: 4 decisões binárias (1, 3, 4, 10) + nó de exceção com 3 arcos (+2) + 1 = **7** ✔
O JaCoCo mostra complexidade **5** para este método: ele não conta as duas arestas
excepcionais. É o exemplo pedido de "exceção não representada no contador de branches".

| # | Caminho | Stub | Esperado |
| --- | --- | --- | --- |
| P1 | 1→2→13 | — | `IllegalArgumentException` (total 0) |
| P2 | 1→3→5→13 | — | `IllegalArgumentException` (limite 0) |
| P3 | 1→3→4→5→13 | — | `IllegalArgumentException` (limite 4) |
| P4 | …6→7→8→13 | `true` | `true`, 1 chamada |
| P5 | …7→9→10→7→8→13 | `ISE`, depois `true` | `true`, 2 chamadas |
| P6 | …7→9→10→11→13 | `ISE` sempre, limite 3 | `false`, 3 chamadas |
| P7 | …7→12→13 | `IllegalArgumentException` | propaga, 1 chamada |

O caminho 7→8→13 cobre tanto a aprovação (`true`) quanto a recusa definitiva (`false`):
estruturalmente é a mesma aresta, mas os dois casos estão testados porque a diferença é de
**dado**, não de caminho — e o contador de chamadas prova que a recusa não é repetida.

---

## CFG 5 — `PedidoService.fechar`

```
N1  pedido == null ?            N10 return SEM_ESTOQUE (zeros)
N2  NullPointerException        N11 desconto → líquido → frete → total → análise
N3  cliente == null ?           N12 !analise.equals("APROVADO") ?
N4  NullPointerException        N13 return ResultadoPedido(analise, valores)
N5  cliente.bloqueado() ?       N14 pagamentos.pagar(total, 3) ?
N6  return BLOQUEADO (zeros)    N15 status = "PAGO"
N7  subtotal == 0 ?             N16 status = "PAGAMENTO_RECUSADO"
N8  throw (sem itens ativos)    N17 return ResultadoPedido(status, valores)
N9  !estoqueSuficiente() ?      N18 FIM

Arestas: 1→2 1→3 2→18 3→4 3→5 4→18 5→6 5→7 6→18 7→8 7→9 8→18
         9→10 9→11 10→18 11→12 12→13 12→14 13→18
         14→15 14→16 15→17 16→17 17→18
```

N = 18 · E = 24 · **V(G) = 24 − 18 + 2 = 8** · decisões (1, 3, 5, 7, 9, 12, 14) + 1 = **8** ✔

| # | Caminho | Dados | Esperado |
| --- | --- | --- | --- |
| S1 | 1→2→18 | `fechar(null, cliente)` | `NullPointerException` |
| S2 | 1→3→4→18 | `fechar(pedido, null)` | `NullPointerException` |
| S3 | …5→6→18 | cliente bloqueado | `BLOQUEADO`, todos os valores zero |
| S4 | …7→8→18 | só linhas inativas | `IllegalArgumentException` |
| S5 | …9→10→18 | quantidade 5, estoque 1 | `SEM_ESTOQUE`, valores zero |
| S6 | …12→13→18 | cliente novo + expresso | `REVISAO`, frete 2.700, total 12.700 |
| S7 | …14→15→17→18 | stub aprova | `PAGO`, total 11.200 |
| S8 | …14→16→17→18 | stub recusa | `PAGAMENTO_RECUSADO`, total 11.200 |

Neste modelo, as exceções que **propagam dos colaboradores** (cupom desconhecido em
`PoliticaDesconto`, exceção não tratada em `PagamentoService`) não aparecem como arestas de
`fechar`: elas atravessam o método sem desvio próprio. Estão testadas mesmo assim
(`cupomDesconhecidoPropagaComEstoqueOk`, `excecaoNaoTratadaInterrompeOFechamento`).

---

## Resumo de McCabe

| Método | Nós | Arestas | V(G) | Caminhos independentes | Restrições de viabilidade |
| --- | --- | --- | --- | --- | --- |
| `AnaliseRisco.avaliar` | 13 | 19 | 8 | 8 (R1–R8) | R2 (`RECUSADO`) inviável via `fechar` |
| `PoliticaDesconto.calcular` | 21 | 31 | 12 | 12 (D1–D12) | todos viáveis |
| `CalculadoraFrete.calcular` | 19 | 28 | 11 | 11 (F1–F11) | N9→N10→N11 exige entrega normal |
| `PagamentoService.pagar` | 13 | 18 | 7 | 7 (P1–P7) | todos viáveis |
| `PedidoService.fechar` | 18 | 24 | 8 | 8 (S1–S8) | S6 exige risco pendente sem bloqueio |

---

## Matriz de testes

Cada linha é o teste que realiza um caminho independente da base. Os demais testes da suíte
(187 no total) cobrem valores-limite e combinações adicionais dentro desses mesmos caminhos.

| ID | Método JUnit | Unidade | Entrada e estado do stub | Resultado esperado | Caminho / aresta | Critério |
| --- | --- | --- | --- | --- | --- | --- |
| R1 | `deveRejeitarTotalNegativo` | `AnaliseRisco` | total = −1 | `IllegalArgumentException` | 1→2 | guarda |
| R2 | `clienteBloqueadoEhRecusado` | `AnaliseRisco` | bloqueado | `RECUSADO` | 3→4 | retorno antecipado |
| R3 | `clienteNovoNaFronteiraDeMilReais[3]` | `AnaliseRisco` | 0 compras, 100.001 | `REVISAO` | 6→8 | limite + 1 |
| R4 | `clienteNovoNaFronteiraDeMilReais[4]` | `AnaliseRisco` | 0 compras, 0, expresso | `REVISAO` | 6→7→8 | 2º operando do `\|\|` |
| R5 | `clienteNovoNaFronteiraDeMilReais[2]` | `AnaliseRisco` | 0 compras, 100.000 | `APROVADO` | 7→12 | limite exato |
| R6 | `clienteRecorrenteNaFronteira…[3]` | `AnaliseRisco` | 5 compras, 500.001 | `REVISAO` | 10→11 | ambos operandos do `&&` |
| R7 | `clienteRecorrenteNaFronteira…[4]` | `AnaliseRisco` | 5 compras, 500.001, VIP | `APROVADO` | 10→12 | 2º operando falso |
| R8 | `clienteRecorrenteNaFronteira…[2]` | `AnaliseRisco` | 5 compras, 500.000 | `APROVADO` | 9→12 | curto-circuito |
| D1 | `deveRejeitarSubtotalNegativo` | `PoliticaDesconto` | subtotal −1 | `IllegalArgumentException` | 1→2 | guarda |
| D2 | `vipRecebeDezPorCento` | `PoliticaDesconto` | VIP, 10.000 | 1.000 | 3→4→8→10 | ramo VIP |
| D3 | `clienteComumNaFronteira…[2]` | `PoliticaDesconto` | comum, 50.000 | 2.500 | 5→6 | limite exato |
| D4 | `clienteComumNaFronteira…[1]` | `PoliticaDesconto` | comum, 49.999 | 0 | 5→7 | limite − 1 |
| D5 | `cupomBrancoMantemDescontoBase` | `PoliticaDesconto` | cupom `"   "` | base | 9→10 | 2º operando do `\|\|` |
| D6 | `bemvindoElegivel` | `PoliticaDesconto` | 0 compras, 10.000 | 2.000 | 11→12→13→14 | case + `&&` |
| D7 | `bemvindoAbaixoDoLimite` | `PoliticaDesconto` | 0 compras, 9.999 | 0 | 13→18 | limite − 1 |
| D8 | `bemvindoParaClienteRecorrente` | `PoliticaDesconto` | 3 compras | 0 | 12→18 | 1º operando do `&&` |
| D9 | `extra10NaFronteira…[2]` | `PoliticaDesconto` | 20.000, `EXTRA10` | 2.000 | 11→15→16 | case + limite |
| D10 | `extra10NaFronteira…[1]` | `PoliticaDesconto` | 19.999, `EXTRA10` | 0 | 15→18 | limite − 1 |
| D11 | `cupomDesconhecidoEhRejeitado` | `PoliticaDesconto` | `"PROMO"` | `IllegalArgumentException` | 11→17 | `default` |
| D12 | `descontoCombinadoEhLimitadoAoTeto` | `PoliticaDesconto` | VIP + `BEMVINDO`, 10.000 | 2.000 | 18→19 | teto |
| F1 | `deveRejeitarLiquidoNegativo` | `CalculadoraFrete` | líquido −1 | `IllegalArgumentException` | 1→2 | guarda |
| F2–F5 | `baseDoFretePorUf` | `CalculadoraFrete` | PR/SP/RJ/MG/ZZ | 1.200/2.000/2.000/3.000 | 3→4, 3→5, 3→6 | `switch` + `default` |
| F6 | `adicionalPorPesoExcedente` | `CalculadoraFrete` | 1.999 → 5.001 g | 1.200 → 2.400 | 7→8→7 | 0/1/2/3/4 iterações |
| F7 | `gratuidadeNaFronteira…[2]` | `CalculadoraFrete` | líquido 30.000, normal | 0 | 10→11 | limite exato |
| F8 | `expressoNaoRecebeGratuidade` | `CalculadoraFrete` | líquido 100.000, expresso | 2.700 | 10→12 | 2º operando do `&&` |
| F9 | `vipPagaMetade` | `CalculadoraFrete` | MG, 3 kg, VIP | 1.650 | 12→13 | ramo VIP |
| F10 | `expressoAcrescentaQuinzeReais` | `CalculadoraFrete` | expresso | 2.700 | 14→15 | adicional |
| F11 | `fragilAcrescentaCincoReais` | `CalculadoraFrete` | item frágil ativo | 1.700 | 16→17 | adicional |
| P1 | `deveRejeitarTotalNaoPositivo` | `PagamentoService` | total 0 | `IllegalArgumentException` | 1→2 | guarda |
| P2/P3 | `deveRejeitarLimiteForaDoIntervalo` | `PagamentoService` | limite 0 e 4 | `IllegalArgumentException` | 3→5, 4→5 | `\|\|` |
| P4 | `aprovacaoNaPrimeiraTentativa` | `PagamentoService` | stub `true` | `true`, 1 chamada | 7→8 | retorno normal |
| P5 | `indisponibilidadeSeguidaDeAprovacao` | `PagamentoService` | `ISE`, `true` | `true`, 2 chamadas | 7→9→10→7 | retorno do laço |
| P6 | `esgotarTentativasRetornaFalse` | `PagamentoService` | `ISE` sempre | `false`, 3 chamadas | 10→11 | esgotamento |
| P7 | `outraExcecaoPropaga` | `PagamentoService` | `IllegalArgumentException` | propaga, 1 chamada | 7→12 | exceção não tratada |
| S1/S2 | `referenciasNulasSaoRejeitadas` | `PedidoService` | `null` | `NullPointerException` | 1→2, 3→4 | guarda |
| S3 | `clienteBloqueadoRetornaZeros` | `PedidoService` | bloqueado, stub espião | `BLOQUEADO`, zeros, 0 cobranças | 5→6 | retorno antecipado |
| S4 | `pedidoSemItensAtivosEhRejeitado` | `PedidoService` | quantidade 0 | `IllegalArgumentException` | 7→8 | subtotal zero |
| S5 | `faltaDeEstoqueRetornaZeros` | `PedidoService` | quantidade 5, estoque 1 | `SEM_ESTOQUE`, 0 cobranças | 9→10 | estoque |
| S6 | `clienteNovoComExpressoVaiParaRevisao` | `PedidoService` | 0 compras, expresso | `REVISAO`, 0 cobranças | 12→13 | risco pendente |
| S7 | `deveFecharPedidoDeClienteComum…` | `PedidoService` | stub aprova | `PAGO`, 1 cobrança de 11.200 | 14→15 | pagamento |
| S8 | `pagamentoRecusadoMantemValores` | `PedidoService` | stub recusa | `PAGAMENTO_RECUSADO` | 14→16 | pagamento |

---

## Evolução da cobertura

| Etapa | Testes executados | Instruções | Branches | Linhas | Métodos | Classes | Lacunas e justificativas |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Inicial (só o exemplo da aula) | 1 | parcial | parcial | parcial | parcial | 8 de 9 | `PagamentoService` só no caminho feliz; `ResultadoPedido` não instanciado por teste próprio |
| Records validados (`Cliente`, `ItemPedido`, `Pedido`) | 73 | — | — | — | — | 9 de 9 | faltavam desconto, frete, risco e pagamento |
| Regras de negócio (desconto, frete, risco) | 150 | — | — | — | — | 9 de 9 | faltavam repetições e exceções do pagamento |
| Final | **187** | **637/637 (100%)** | **116/116 (100%)** | **108/108 (100%)** | **21/21 (100%)** | **9/9 (100%)** | nenhuma lacuna de branch alcançável |

Números finais extraídos de `target/site/jacoco/jacoco.csv` após `mvn clean test`.

---

## Alteração proposital (teste de mutação manual)

Alteração aplicada em `PoliticaDesconto.calcular`:

```diff
- } else if (subtotal >= 50_000) {
+ } else if (subtotal > 50_000) {
```

Resultado de `mvn test -Dtest=PoliticaDescontoTest`:

```
[ERROR] Tests run: 33, Failures: 2
[ERROR] PoliticaDescontoTest.clienteComumNaFronteiraDosCincoPorCento[2] expected: <2500> but was: <0>
[ERROR] PoliticaDescontoTest.extra10SomaAoDescontoBase                   expected: <7500> but was: <5000>
```

O caso de fronteira `subtotal = 50.000` é exatamente o que detecta a troca de `>=` por `>`.
Um teste com 50.001 sozinho **não** pegaria a mutação. **A alteração foi desfeita** e a suíte
voltou a 187 testes verdes antes da entrega.

---

## Análise crítica

**Quais combinações faltavam mesmo com os ramos cobertos?**
`CalculadoraFrete` tem quatro decisões independentes ao final (gratuidade, VIP, expresso,
frágil) → 2⁴ = 16 combinações, mas `V(G)` só exige 11 caminhos na base. Cobrir cada ramo
uma vez deixaria combinações como "VIP + expresso + frágil sobre base zerada" sem executar.
Foram acrescentados testes explícitos para isso (`combinacaoCompleta`,
`adicionaisIncidemSobreBaseZerada`). O mesmo vale para o laço de peso: um único teste com
uma iteração cobriria os dois branches do `while`, mas não distinguiria "1 kg exato" de
"1 kg + fração" — daí os casos 2.001, 3.000, 3.001, 5.000 e 5.001.

**Quais condições não foram avaliadas devido ao curto-circuito?**
- `ItemPedido`: com `sku == null`, `sku.isBlank()` nunca é avaliado (e não poderia ser).
- `Pedido`: com `uf == null`, a expressão regular não chega a rodar.
- `AnaliseRisco`: com `total > 100_000` verdadeiro, `expresso` não é lido; com
  `total > 500_000` falso, `!vip` não é lido.
- `PoliticaDesconto`: com `cupom == null`, `isBlank()` não roda; com
  `comprasAnteriores != 0`, `subtotal >= 10_000` não roda.
- `CalculadoraFrete`: com `liquido < 30_000`, `!expresso` não é avaliado.
Cada um desses pares tem um teste que força o operando esquerdo a decidir sozinho e outro
que obriga o direito a ser avaliado.

**Quais caminhos são inviáveis no serviço, mas viáveis na unidade?**
`AnaliseRisco.avaliar` devolve `RECUSADO` quando o cliente está bloqueado (caminho R2),
mas `PedidoService.fechar` já retornou `BLOQUEADO` na linha anterior — o `avaliar` nunca é
chamado com cliente bloqueado. O teste `recusadoDaAnaliseEhInalcancavelPeloServico`
demonstra os dois lados: `RECUSADO` na unidade e `BLOQUEADO` na colaboração.
Pelo mesmo motivo, `PedidoService` nunca produz um resultado com status `RECUSADO`.

**Como foram testadas exceções e quantidades de iterações?**
Exceções: `assertThrows` com verificação da mensagem, e o contador do stub provando que o
processador **não** foi chamado quando a execução terminou antes do pagamento
(`guardaDeEntradaNaoChamaOProcessador`, `clienteBloqueadoRetornaZeros`,
`faltaDeEstoqueRetornaZeros`).
Iterações: o `for` de `Pedido` foi exercitado com 0, 1 e várias linhas, com linha inativa e
com falta de estoque no início (`break` imediato) e no fim (laço completo); o `while` do
frete com 0, 1, 2, 3 e 4 iterações; o `do/while` do pagamento com 1, 2 e 3 tentativas,
usando um stub roteirizado que devolve um resultado diferente por chamada.

**Cobertura de ramos não demonstra cobertura de caminhos.**
`Participacao`-style: dois testes ("todos os ramos verdadeiros" e "todos falsos") fecham
100% de branches em `CalculadoraFrete`, mas deixam 14 das 16 combinações das quatro
decisões finais sem executar. Os percentuais do JaCoCo teriam mostrado 100% com uma suíte
estritamente pior. Cobertura diz o que **foi executado**; as asserções dizem se a resposta
estava **certa** — a mutação `>= → >` mostra que só a segunda parte pega o defeito.

**Exceção não representada no contador de branches.**
`PagamentoService.pagar` tem `V(G) = 7` no nosso modelo e complexidade **5** no JaCoCo: as
arestas `try → catch` e `try → propagação` não são branches para a ferramenta. O caminho
P7 (`IllegalArgumentException` propagando sem repetir) está testado e é invisível no
percentual de branches — se dependêssemos só do relatório, ele passaria despercebido.
