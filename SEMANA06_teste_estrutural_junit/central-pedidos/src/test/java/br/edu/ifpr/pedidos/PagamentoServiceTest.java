package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PagamentoService.pagar — do/while com try/catch.
 *
 * Só IllegalStateException (indisponibilidade temporária) permite repetir.
 * Recusa definitiva (false) encerra na primeira tentativa e qualquer outra
 * exceção propaga. O stub abaixo registra a ordem, a quantidade de chamadas
 * e o valor recebido, para verificar efeitos observáveis e não apenas o retorno.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class PagamentoServiceTest {

    /**
     * Stub roteirizado: consome um resultado por chamada, na ordem informada.
     * Cada elemento é Boolean (retorno) ou RuntimeException (a ser lançada).
     */
    private static final class ProcessadorStub implements ProcessadorPagamento {
        private final List<Object> roteiro;
        private final List<Long> recebidos = new ArrayList<>();
        private int chamadas;

        private ProcessadorStub(Object... roteiro) {
            this.roteiro = List.of(roteiro);
        }

        @Override
        public boolean autorizar(long totalCentavos) {
            recebidos.add(totalCentavos);
            Object passo = roteiro.get(Math.min(chamadas, roteiro.size() - 1));
            chamadas++;
            if (passo instanceof RuntimeException excecao) {
                throw excecao;
            }
            return (boolean) passo;
        }
    }

    // ------------------------------------------------------------------
    // Construção e guardas de entrada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("processador nulo é rejeitado na construção")
    void deveRejeitarProcessadorNulo() {
        assertThrows(NullPointerException.class, () -> new PagamentoService(null));
    }

    @ParameterizedTest(name = "total {0} é rejeitado")
    @ValueSource(longs = { 0L, -1L, -100L })
    @DisplayName("total precisa ser positivo")
    void deveRejeitarTotalNaoPositivo(long total) {
        PagamentoService servico = new PagamentoService(t -> true);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.pagar(total, 1));

        assertEquals("Total deve ser positivo", erro.getMessage());
    }

    @ParameterizedTest(name = "limite de {0} tentativas é rejeitado")
    @ValueSource(ints = { 0, -1, 4 })
    @DisplayName("o limite de tentativas precisa estar entre 1 e 3")
    void deveRejeitarLimiteForaDoIntervalo(int limite) {
        PagamentoService servico = new PagamentoService(t -> true);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.pagar(100, limite));

        assertEquals("Use 1 a 3 tentativas", erro.getMessage());
    }

    @Test
    @DisplayName("os limites 1 e 3 são aceitos")
    void deveAceitarOsLimitesDoIntervalo() {
        PagamentoService servico = new PagamentoService(t -> true);

        assertAll(
                () -> assertTrue(servico.pagar(100, 1)),
                () -> assertTrue(servico.pagar(100, 3))
        );
    }

    @Test
    @DisplayName("o processador não é chamado quando a guarda de entrada falha")
    void guardaDeEntradaNaoChamaOProcessador() {
        ProcessadorStub stub = new ProcessadorStub(true);
        PagamentoService servico = new PagamentoService(stub);

        assertThrows(IllegalArgumentException.class, () -> servico.pagar(0, 1));

        assertEquals(0, stub.chamadas);
    }

    // ------------------------------------------------------------------
    // Uma única iteração do do/while
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aprovação na primeira tentativa: uma chamada, com o valor correto")
    void aprovacaoNaPrimeiraTentativa() {
        ProcessadorStub stub = new ProcessadorStub(true);
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertTrue(servico.pagar(11_200, 3)),
                () -> assertEquals(1, stub.chamadas),
                () -> assertEquals(List.of(11_200L), stub.recebidos)
        );
    }

    @Test
    @DisplayName("recusa definitiva encerra na primeira tentativa, sem repetir")
    void recusaDefinitivaNaoRepete() {
        ProcessadorStub stub = new ProcessadorStub(false);
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertFalse(servico.pagar(11_200, 3)),
                () -> assertEquals(1, stub.chamadas)
        );
    }

    // ------------------------------------------------------------------
    // Várias iterações: indisponibilidade temporária
    // ------------------------------------------------------------------

    @Test
    @DisplayName("indisponibilidade seguida de aprovação usa duas tentativas")
    void indisponibilidadeSeguidaDeAprovacao() {
        ProcessadorStub stub = new ProcessadorStub(
                new IllegalStateException("fora do ar"), true);
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertTrue(servico.pagar(5_000, 3)),
                () -> assertEquals(2, stub.chamadas),
                () -> assertEquals(List.of(5_000L, 5_000L), stub.recebidos)
        );
    }

    @Test
    @DisplayName("duas indisponibilidades seguidas de aprovação usam as três tentativas")
    void aprovacaoNaUltimaTentativa() {
        ProcessadorStub stub = new ProcessadorStub(
                new IllegalStateException("1"), new IllegalStateException("2"), true);
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertTrue(servico.pagar(5_000, 3)),
                () -> assertEquals(3, stub.chamadas)
        );
    }

    @Test
    @DisplayName("esgotar as três tentativas retorna false")
    void esgotarTentativasRetornaFalse() {
        ProcessadorStub stub = new ProcessadorStub(new IllegalStateException("sempre fora"));
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertFalse(servico.pagar(5_000, 3)),
                () -> assertEquals(3, stub.chamadas)
        );
    }

    @Test
    @DisplayName("com limite de 1 tentativa, a indisponibilidade não é repetida")
    void limiteDeUmaTentativaNaoRepete() {
        ProcessadorStub stub = new ProcessadorStub(new IllegalStateException("fora do ar"));
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertFalse(servico.pagar(5_000, 1)),
                () -> assertEquals(1, stub.chamadas)
        );
    }

    @Test
    @DisplayName("recusa definitiva após uma indisponibilidade encerra na segunda chamada")
    void indisponibilidadeSeguidaDeRecusa() {
        ProcessadorStub stub = new ProcessadorStub(
                new IllegalStateException("fora do ar"), false);
        PagamentoService servico = new PagamentoService(stub);

        assertAll(
                () -> assertFalse(servico.pagar(5_000, 3)),
                () -> assertEquals(2, stub.chamadas)
        );
    }

    // ------------------------------------------------------------------
    // Exceções não tratadas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exceção diferente de IllegalStateException propaga sem repetir")
    void outraExcecaoPropaga() {
        ProcessadorStub stub = new ProcessadorStub(
                new IllegalArgumentException("cartão inválido"), true);
        PagamentoService servico = new PagamentoService(stub);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.pagar(5_000, 3));

        assertAll(
                () -> assertEquals("cartão inválido", erro.getMessage()),
                () -> assertEquals(1, stub.chamadas)
        );
    }
}
