package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PedidoService.fechar — testes de colaboração.
 *
 * A ordem do contrato é verificada explicitamente:
 * validar referências -> bloqueio -> subtotal -> estoque -> desconto ->
 * frete -> risco -> pagamento.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class PedidoServiceTest {

    /** Registra cada cobrança tentada, para provar quando o pagamento NÃO foi acionado. */
    private static final class ProcessadorEspiao implements ProcessadorPagamento {
        private final List<Long> cobrancas = new ArrayList<>();
        private final boolean aprovar;

        private ProcessadorEspiao(boolean aprovar) {
            this.aprovar = aprovar;
        }

        @Override
        public boolean autorizar(long totalCentavos) {
            cobrancas.add(totalCentavos);
            return aprovar;
        }
    }

    private static ItemPedido item(String sku, long preco, int quantidade,
                                   int estoque, int peso, boolean fragil) {
        return new ItemPedido(sku, preco, quantidade, estoque, peso, fragil);
    }

    // ------------------------------------------------------------------
    // Caminho feliz (exemplo original da aula)
    // ------------------------------------------------------------------

    @Test
    void deveFecharPedidoDeClienteComumComFreteDoParanaEPagamentoAprovado() {
        // 1. Preparar: cliente comum, uma compra anterior e item disponível de R$ 100,00.
        Cliente cliente = new Cliente(false, false, 1);
        ItemPedido item = new ItemPedido("LIVRO-JAVA", 10_000, 1, 5, 1_000, false);
        Pedido pedido = new Pedido(List.of(item), "PR", false, null);

        // Simula o pagamento e registra as cobranças, sem banco ou serviço externo.
        List<Long> cobrancas = new ArrayList<>();
        PedidoService service = new PedidoService(total -> {
            cobrancas.add(total);
            return true;
        });

        // 2. Executar: percorrer um caminho completo do fechamento.
        ResultadoPedido resultado = service.fechar(pedido, cliente);

        // 3. Verificar: sem desconto; frete de R$ 12,00; total de R$ 112,00.
        assertAll(
            () -> assertEquals("PAGO", resultado.status()),
            () -> assertEquals(10_000L, resultado.subtotalCentavos()),
            () -> assertEquals(0L, resultado.descontoCentavos()),
            () -> assertEquals(1_200L, resultado.freteCentavos()),
            () -> assertEquals(11_200L, resultado.totalCentavos()),
            // A lista comprova uma única cobrança, com o valor correto.
            () -> assertEquals(List.of(11_200L), cobrancas)
        );
    }

    // ------------------------------------------------------------------
    // Referências obrigatórias
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pedido nulo e cliente nulo causam NullPointerException")
    void referenciasNulasSaoRejeitadas() {
        PedidoService service = new PedidoService(total -> true);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 100, false)), "PR", false, null);
        Cliente cliente = new Cliente(false, false, 1);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> service.fechar(null, cliente)),
                () -> assertThrows(NullPointerException.class, () -> service.fechar(pedido, null))
        );
    }

    @Test
    @DisplayName("processador nulo é rejeitado já na construção do serviço")
    void processadorNuloEhRejeitado() {
        assertThrows(NullPointerException.class, () -> new PedidoService(null));
    }

    // ------------------------------------------------------------------
    // Cliente bloqueado: primeiro retorno antecipado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente bloqueado retorna BLOQUEADO com todos os valores zerados e sem cobrança")
    void clienteBloqueadoRetornaZeros() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(item("SKU", 50_000, 2, 5, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, true, 3));

        assertAll(
                () -> assertEquals("BLOQUEADO", resultado.status()),
                () -> assertEquals(0L, resultado.subtotalCentavos()),
                () -> assertEquals(0L, resultado.descontoCentavos()),
                () -> assertEquals(0L, resultado.freteCentavos()),
                () -> assertEquals(0L, resultado.totalCentavos()),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    @Test
    @DisplayName("o bloqueio é avaliado antes dos itens: nem a falta de estoque nem o cupom inválido são alcançados")
    void bloqueioPrecedeItensECupom() {
        PedidoService service = new PedidoService(total -> true);
        // Item indisponível e cupom desconhecido: ambos lançariam ou mudariam o status.
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 5, 1, 1_000, false)),
                "PR", false, "CUPOM-QUE-NAO-EXISTE");

        assertEquals("BLOQUEADO", service.fechar(pedido, new Cliente(false, true, 0)).status());
    }

    // ------------------------------------------------------------------
    // Subtotal zero
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pedido sem itens ativos é rejeitado")
    void pedidoSemItensAtivosEhRejeitado() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido soInativos = new Pedido(List.of(item("SKU", 10_000, 0, 5, 1_000, false)), "PR", false, null);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.fechar(soInativos, new Cliente(false, false, 1)));

        assertAll(
                () -> assertEquals("Pedido sem itens ativos", erro.getMessage()),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    @Test
    @DisplayName("pedido com lista vazia também é rejeitado por subtotal zero")
    void pedidoComListaVaziaEhRejeitado() {
        PedidoService service = new PedidoService(total -> true);
        Pedido vazio = new Pedido(List.of(), "PR", false, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.fechar(vazio, new Cliente(false, false, 1)));
    }

    // ------------------------------------------------------------------
    // Falta de estoque
    // ------------------------------------------------------------------

    @Test
    @DisplayName("falta de estoque retorna SEM_ESTOQUE com valores zerados e sem cobrança")
    void faltaDeEstoqueRetornaZeros() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 5, 1, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("SEM_ESTOQUE", resultado.status()),
                () -> assertEquals(0L, resultado.subtotalCentavos()),
                () -> assertEquals(0L, resultado.totalCentavos()),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    @Test
    @DisplayName("o estoque é avaliado antes do cupom: cupom desconhecido não chega a lançar")
    void estoquePrecedeOCupom() {
        PedidoService service = new PedidoService(total -> true);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 5, 1, 1_000, false)),
                "PR", false, "CUPOM-QUE-NAO-EXISTE");

        assertEquals("SEM_ESTOQUE", service.fechar(pedido, new Cliente(false, false, 1)).status());
    }

    @Test
    @DisplayName("cupom desconhecido propaga quando o estoque é suficiente")
    void cupomDesconhecidoPropagaComEstoqueOk() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)),
                "PR", false, "CUPOM-QUE-NAO-EXISTE");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.fechar(pedido, new Cliente(false, false, 1))),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    // ------------------------------------------------------------------
    // Risco pendente: REVISAO
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente novo com entrega expressa vai para REVISAO, com valores calculados e sem cobrança")
    void clienteNovoComExpressoVaiParaRevisao() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", true, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 0));

        // Subtotal 10.000; sem desconto; frete 1.200 + 1.500 de expresso = 2.700.
        assertAll(
                () -> assertEquals("REVISAO", resultado.status()),
                () -> assertEquals(10_000L, resultado.subtotalCentavos()),
                () -> assertEquals(0L, resultado.descontoCentavos()),
                () -> assertEquals(2_700L, resultado.freteCentavos()),
                () -> assertEquals(12_700L, resultado.totalCentavos()),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    @Test
    @DisplayName("cliente novo com total acima de R$ 1.000,00 também vai para REVISAO")
    void clienteNovoComTotalAltoVaiParaRevisao() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        // Subtotal 200.000; comum: 5% = 10.000; líquido 190.000; frete zerado pela gratuidade.
        Pedido pedido = new Pedido(List.of(item("SKU", 100_000, 2, 5, 500, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 0));

        assertAll(
                () -> assertEquals("REVISAO", resultado.status()),
                () -> assertEquals(200_000L, resultado.subtotalCentavos()),
                () -> assertEquals(10_000L, resultado.descontoCentavos()),
                () -> assertEquals(0L, resultado.freteCentavos()),
                () -> assertEquals(190_000L, resultado.totalCentavos()),
                () -> assertTrue(espiao.cobrancas.isEmpty())
        );
    }

    // ------------------------------------------------------------------
    // Pagamento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pagamento recusado mantém os valores calculados")
    void pagamentoRecusadoMantemValores() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(false);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("PAGAMENTO_RECUSADO", resultado.status()),
                () -> assertEquals(10_000L, resultado.subtotalCentavos()),
                () -> assertEquals(1_200L, resultado.freteCentavos()),
                () -> assertEquals(11_200L, resultado.totalCentavos()),
                // Recusa definitiva não é repetida.
                () -> assertEquals(List.of(11_200L), espiao.cobrancas)
        );
    }

    @Test
    @DisplayName("o serviço usa três tentativas: duas indisponibilidades ainda terminam em PAGO")
    void indisponibilidadeTemporariaEhRepetida() {
        List<Long> cobrancas = new ArrayList<>();
        PedidoService service = new PedidoService(total -> {
            cobrancas.add(total);
            if (cobrancas.size() < 3) {
                throw new IllegalStateException("indisponível");
            }
            return true;
        });
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("PAGO", resultado.status()),
                () -> assertEquals(List.of(11_200L, 11_200L, 11_200L), cobrancas)
        );
    }

    @Test
    @DisplayName("indisponibilidade permanente esgota as tentativas e recusa o pagamento")
    void indisponibilidadePermanenteRecusa() {
        List<Long> cobrancas = new ArrayList<>();
        PedidoService service = new PedidoService(total -> {
            cobrancas.add(total);
            throw new IllegalStateException("indisponível");
        });
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("PAGAMENTO_RECUSADO", resultado.status()),
                () -> assertEquals(3, cobrancas.size())
        );
    }

    @Test
    @DisplayName("exceção não tratada do processador interrompe o fechamento")
    void excecaoNaoTratadaInterrompeOFechamento() {
        PedidoService service = new PedidoService(total -> {
            throw new IllegalArgumentException("cartão inválido");
        });
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.fechar(pedido, new Cliente(false, false, 1)));
    }

    // ------------------------------------------------------------------
    // Colaboração completa: desconto + frete + risco + pagamento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente comum com subtotal alto: 5% de desconto e frete grátis")
    void clienteComumComDescontoEFreteGratis() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        // Subtotal 60.000; desconto 5% = 3.000; líquido 57.000 >= 30.000 e normal -> frete 0.
        Pedido pedido = new Pedido(List.of(item("SKU", 30_000, 2, 5, 1_000, false)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("PAGO", resultado.status()),
                () -> assertEquals(60_000L, resultado.subtotalCentavos()),
                () -> assertEquals(3_000L, resultado.descontoCentavos()),
                () -> assertEquals(0L, resultado.freteCentavos()),
                () -> assertEquals(57_000L, resultado.totalCentavos()),
                () -> assertEquals(List.of(57_000L), espiao.cobrancas)
        );
    }

    @Test
    @DisplayName("cliente VIP com cupom EXTRA10, frete pela metade, item frágil e peso excedente")
    void clienteVipComCupomFragilEPesoExcedente() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        // Subtotal: 10.000 x 2 = 20.000.
        // Desconto: VIP 10% = 2.000; EXTRA10 (subtotal >= 20.000) + 10% = 2.000 -> 4.000.
        //           Teto: 20% de 20.000 = 4.000 -> exatamente no teto.
        // Líquido: 16.000 (< 30.000, sem gratuidade).
        // Frete: SP 2.000; peso 1.500 x 2 = 3.000 g -> 1 iteração = +300 -> 2.300;
        //        VIP: 1.150; entrega normal; frágil: +500 -> 1.650.
        // Total: 16.000 + 1.650 = 17.650.
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 2, 5, 1_500, true)), "SP", false, "extra10");

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(true, false, 4));

        assertAll(
                () -> assertEquals("PAGO", resultado.status()),
                () -> assertEquals(20_000L, resultado.subtotalCentavos()),
                () -> assertEquals(4_000L, resultado.descontoCentavos()),
                () -> assertEquals(1_650L, resultado.freteCentavos()),
                () -> assertEquals(17_650L, resultado.totalCentavos()),
                () -> assertEquals(List.of(17_650L), espiao.cobrancas)
        );
    }

    @Test
    @DisplayName("cupom BEMVINDO aplicado no fechamento de um cliente sem histórico")
    void cupomBemvindoNoFechamento() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        // Subtotal 10.000; base 0 (comum, < 50.000); BEMVINDO +2.000; teto 2.000 -> 2.000.
        // Líquido 8.000; frete PR 1.200; total 9.200.
        // Risco: cliente novo, total 9.200 <= 100.000 e entrega normal -> APROVADO.
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, " BemVindo ");

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 0));

        assertAll(
                () -> assertEquals("PAGO", resultado.status()),
                () -> assertEquals(2_000L, resultado.descontoCentavos()),
                () -> assertEquals(1_200L, resultado.freteCentavos()),
                () -> assertEquals(9_200L, resultado.totalCentavos())
        );
    }

    @Test
    @DisplayName("linha inativa no meio do pedido não soma valor nem peso, mas não impede o fechamento")
    void linhaInativaNaoImpedeOFechamento() {
        ProcessadorEspiao espiao = new ProcessadorEspiao(true);
        PedidoService service = new PedidoService(espiao);
        Pedido pedido = new Pedido(List.of(
                item("ATIVO", 10_000, 1, 5, 1_000, false),
                item("INATIVO", 999_999, 0, 0, 100_000, true)), "PR", false, null);

        ResultadoPedido resultado = service.fechar(pedido, new Cliente(false, false, 1));

        assertAll(
                () -> assertEquals("PAGO", resultado.status()),
                () -> assertEquals(10_000L, resultado.subtotalCentavos()),
                // A linha inativa é frágil, mas não aciona o adicional de R$ 5,00.
                () -> assertEquals(1_200L, resultado.freteCentavos())
        );
    }

    @Test
    @DisplayName("RECUSADO da análise de risco é inalcançável pelo fechamento: o bloqueio retorna antes")
    void recusadoDaAnaliseEhInalcancavelPeloServico() {
        Cliente bloqueado = new Cliente(false, true, 1);
        PedidoService service = new PedidoService(total -> true);
        Pedido pedido = new Pedido(List.of(item("SKU", 10_000, 1, 5, 1_000, false)), "PR", false, null);

        // Na unidade, AnaliseRisco devolve RECUSADO para o mesmo cliente...
        assertEquals("RECUSADO", new AnaliseRisco().avaliar(bloqueado, 11_200, false));
        // ...mas no serviço o retorno antecipado por bloqueio produz BLOQUEADO.
        assertEquals("BLOQUEADO", service.fechar(pedido, bloqueado).status());
    }
}
