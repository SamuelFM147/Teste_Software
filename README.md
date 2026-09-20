# Teste_Software

Repositório da disciplina **Projeto, Implementação e Teste de Software — 2026**.

**Integrantes**

- Samuel Fuentes Michels — RA 24011114-2
- Felipe de Almeida Matrone — RA 24051005-2

Repositório da disciplina: <https://github.com/JoaoChoma/teste_software_2026>

---

## Atividades da Avaliação Prática — Prova 01

| # | Atividade | Pasta | Estado |
| --- | --- | --- | --- |
| 1 | Artefatos de Plano de Teste e Casos de Teste | [`Trabalho/`](Trabalho) | Concluída |
| 2 | Teste funcional com Playwright | [`SEMANA04_teste_funcional_playwright/`](SEMANA04_teste_funcional_playwright) | Concluída — 60 testes |
| 3 | Teste estrutural / unitário com JUnit | [`SEMANA06_teste_estrutural_junit/`](SEMANA06_teste_estrutural_junit) | Concluída — 218 testes, 100% de cobertura |
| 4 | Exercícios sobre Grafo de Fluxo de Controle (GFC) | [`SEMANA07_GFC/`](SEMANA07_GFC) | Concluída |

---

### 1. Artefatos de Plano de Teste e Casos de Teste

Sistema escolhido: **Sistema de Reserva de Salas**.

- `Trabalho/Plano_de_Teste_Sistema_Reserva_Salas.docx` (e `.pdf`)
- `Trabalho/Casos_de_Teste_Sistema_Reserva_Salas.docx` (e `.pdf`)
- `Trabalho/Matriz_de_Rastreabilidade_Sistema_Reserva_Salas.docx`

---

### 2. Teste funcional com Playwright

Projeto de `aulas/SEMANA04/exemplo-playwright_win` com os testes das interfaces de
**frete** e **senha**, que eram o que faltava entregar.

Arquivos escritos por nós:

- [`tests/frete.spec.ts`](SEMANA04_teste_funcional_playwright/tests/frete.spec.ts) — 27 testes
- [`tests/senha.spec.ts`](SEMANA04_teste_funcional_playwright/tests/senha.spec.ts) — 22 testes

Os specs `idade.spec.ts` e `login.spec.ts` são os gabaritos originais da aula e foram mantidos.

Cobertura de cada tela, por classe de equivalência e valor-limite:

| Tela | Caminhos válidos | Classes inválidas | Valores-limite |
| --- | --- | --- | --- |
| `/frete` | CEP faixa 8 (R$ 15,00), demais CEPs (R$ 25,00), frete grátis, separador `,` e `.`, `trim` | CEP com 7/9 dígitos, com letra, com hífen, vazio; valor 0, negativo, com 3 casas, não numérico, vazio | `199,99` x `200,00` x `200,01`; `0,01`; CEP de 7/8/9 dígitos |
| `/senha` | 8 a 20 caracteres, com maiúscula, minúscula e número | 7 e 21 caracteres, sem maiúscula, sem minúscula, sem número, com espaço, vazia, confirmação divergente | `7` x `8` x `9` e `19` x `20` x `21` caracteres |

Também são verificados o atributo `role` (`status` x `alert`), a classe `success`, o
`reset` do formulário após o cadastro e a precedência da validação de formato sobre a
comparação com a confirmação.

**Como executar**

```bash
cd SEMANA04_teste_funcional_playwright && npm install && npx playwright install chromium && npm test
```

Resultado da última execução: **60 testes, 60 aprovados** (Chromium).

---

### 3. Teste estrutural / unitário com JUnit

Dois projetos Maven com JUnit 5 e JaCoCo.

#### 3.1 `boletim-simples` — 31 testes

Testes dos quatro métodos (`calcularMedia`, `verificarSituacao`, `contarAprovados`,
`calcularPontos`), com as fronteiras 4 e 7 da classificação, o laço com zero/uma/várias
iterações e as quatro combinações da tabela de decisão de `calcularPontos`.

```bash
cd SEMANA06_teste_estrutural_junit/boletim-simples && mvn clean test
```

Cobertura: **100%** de instruções, branches, linhas, métodos e classes.

#### 3.2 `central-pedidos` — 187 testes

Laboratório completo de teste estrutural: nove classes, sem alterar nenhuma regra de
produção. Inclui CFGs, cálculo de McCabe, base de caminhos independentes e matriz de
testes em [`RELATORIO.md`](SEMANA06_teste_estrutural_junit/central-pedidos/RELATORIO.md).

```bash
cd SEMANA06_teste_estrutural_junit/central-pedidos && mvn clean test
```

| Classe de teste | Testes |
| --- | --- |
| `PedidoTest` | 42 |
| `PoliticaDescontoTest` | 33 |
| `CalculadoraFreteTest` | 28 |
| `ItemPedidoTest` | 26 |
| `PedidoServiceTest` | 21 |
| `PagamentoServiceTest` | 17 |
| `AnaliseRiscoTest` | 16 |
| `ClienteTest` | 4 |

Cobertura JaCoCo: **637/637 instruções · 116/116 branches · 108/108 linhas · 21/21 métodos ·
9/9 classes — 100%**.

Resumo dos grafos (modelo e caminhos detalhados no `RELATORIO.md`):

| Método | N | E | V(G) |
| --- | --- | --- | --- |
| `PoliticaDesconto.calcular` | 21 | 31 | 12 |
| `CalculadoraFrete.calcular` | 19 | 28 | 11 |
| `AnaliseRisco.avaliar` | 13 | 19 | 8 |
| `PedidoService.fechar` | 18 | 24 | 8 |
| `PagamentoService.pagar` | 13 | 18 | 7 |

O relatório traz ainda a alteração proposital exigida pelo roteiro (`>=` trocado por `>`
em `PoliticaDesconto`), os dois testes que a detectaram e a confirmação de que a alteração
foi desfeita.

---

### 4. Exercícios sobre Grafo de Fluxo de Controle

[`SEMANA07_GFC/respostas_exercicios_gfc.md`](SEMANA07_GFC/respostas_exercicios_gfc.md)

Os dois exercícios resolvidos com blocos básicos, decisões, grafo (diagrama Mermaid e
versão em texto), contagem de nós e arestas, complexidade ciclomática pelas duas fórmulas,
base de caminhos independentes com dados de teste e resultados esperados, além das
questões para discussão.

| Exercício | N | E | V(G) = E − N + 2 | decisões + 1 |
| --- | --- | --- | --- | --- |
| 1 — `classificarPedido` | 8 | 10 | 4 | 4 |
| 2 — `contarAlertas` | 9 | 11 | 4 | 4 |

Os resultados esperados de cada caminho foram conferidos executando os dois métodos.

---

## Aula 02

`aula02/` — primeiro exercício de teste automatizado da disciplina (`CalculadoraFrete` com JUnit).
