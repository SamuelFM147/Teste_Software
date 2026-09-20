package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PoliticaDesconto.calcular — desconto base, cupons e teto de 20%.
 *
 * Todos os resultados esperados foram derivados das regras do contrato,
 * nunca chamando a própria implementação para produzir o oráculo.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class PoliticaDescontoTest {

    private static final Cliente COMUM_NOVO = new Cliente(false, false, 0);
    private static final Cliente COMUM_RECORRENTE = new Cliente(false, false, 3);
    private static final Cliente VIP_NOVO = new Cliente(true, false, 0);
    private static final Cliente VIP_RECORRENTE = new Cliente(true, false, 3);

    private PoliticaDesconto politica;

    @BeforeEach
    void preparar() {
        politica = new PoliticaDesconto();
    }

    // ------------------------------------------------------------------
    // Guarda de entrada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("subtotal negativo é rejeitado")
    void deveRejeitarSubtotalNegativo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> politica.calcular(COMUM_RECORRENTE, -1, null));

        assertEquals("Subtotal negativo", erro.getMessage());
    }

    @Test
    @DisplayName("subtotal zero é aceito e não gera desconto")
    void subtotalZeroNaoGeraDesconto() {
        assertEquals(0L, politica.calcular(COMUM_RECORRENTE, 0, null));
    }

    // ------------------------------------------------------------------
    // Desconto base: VIP, cliente comum com subtotal alto, demais
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIP recebe 10% independentemente do subtotal")
    void vipRecebeDezPorCento() {
        assertAll(
                () -> assertEquals(1_000L, politica.calcular(VIP_RECORRENTE, 10_000, null)),
                () -> assertEquals(10_000L, politica.calcular(VIP_RECORRENTE, 100_000, null))
        );
    }

    @ParameterizedTest(name = "cliente comum com subtotal {0} recebe desconto {1}")
    @CsvSource({
            "49999, 0",      // limite - 1: nenhum desconto
            "50000, 2500",   // limite exato: 5%
            "50001, 2500",   // 5% de 50.001 = 2.500,05 truncado para 2.500
            "100000, 5000"
    })
    @DisplayName("cliente comum recebe 5% a partir de R$ 500,00 (fronteira de 50.000 centavos)")
    void clienteComumNaFronteiraDosCincoPorCento(long subtotal, long esperado) {
        assertEquals(esperado, politica.calcular(COMUM_RECORRENTE, subtotal, null));
    }

    @Test
    @DisplayName("divisão percentual é truncada para baixo")
    void percentualEhTruncado() {
        // 10% de 99 centavos = 9,9 -> 9.
        assertEquals(9L, politica.calcular(VIP_RECORRENTE, 99, null));
    }

    // ------------------------------------------------------------------
    // Cupom ausente: retorno antecipado, sem passar pelo teto
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "cupom \"{0}\" mantém apenas o desconto base")
    @ValueSource(strings = { "", " ", "   ", "\t" })
    void cupomBrancoMantemDescontoBase(String cupom) {
        assertEquals(1_000L, politica.calcular(VIP_RECORRENTE, 10_000, cupom));
    }

    @Test
    @DisplayName("cupom nulo mantém apenas o desconto base (curto-circuito do ||)")
    void cupomNuloMantemDescontoBase() {
        assertEquals(1_000L, politica.calcular(VIP_RECORRENTE, 10_000, null));
    }

    // ------------------------------------------------------------------
    // Cupom BEMVINDO
    // ------------------------------------------------------------------

    @Test
    @DisplayName("BEMVINDO soma R$ 20,00 para cliente sem compras anteriores e subtotal >= R$ 100,00")
    void bemvindoElegivel() {
        // Base: comum com subtotal 10.000 (< 50.000) = 0; cupom soma 2.000.
        assertEquals(2_000L, politica.calcular(COMUM_NOVO, 10_000, "BEMVINDO"));
    }

    @Test
    @DisplayName("BEMVINDO não se aplica um centavo abaixo do limite de R$ 100,00")
    void bemvindoAbaixoDoLimite() {
        assertEquals(0L, politica.calcular(COMUM_NOVO, 9_999, "BEMVINDO"));
    }

    @Test
    @DisplayName("BEMVINDO não se aplica a quem já tem compras anteriores (curto-circuito do &&)")
    void bemvindoParaClienteRecorrente() {
        assertEquals(0L, politica.calcular(COMUM_RECORRENTE, 10_000, "BEMVINDO"));
    }

    @ParameterizedTest(name = "cupom \"{0}\" é normalizado para BEMVINDO")
    @ValueSource(strings = { "bemvindo", "  BemVindo  ", "BEMVINDO ", " bemvindo" })
    @DisplayName("o cupom é normalizado com trim e maiúsculas")
    void bemvindoEhNormalizado(String cupom) {
        assertEquals(2_000L, politica.calcular(COMUM_NOVO, 10_000, cupom));
    }

    // ------------------------------------------------------------------
    // Cupom EXTRA10
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "EXTRA10 com subtotal {0} produz desconto {1}")
    @CsvSource({
            "19999, 0",       // limite - 1: cupom conhecido, mas sem elegibilidade
            "20000, 2000",    // limite exato: 10%
            "20001, 2000",    // 10% de 20.001 = 2.000,1 truncado
            "30000, 3000"
    })
    void extra10NaFronteiraDeDuzentosReais(long subtotal, long esperado) {
        assertEquals(esperado, politica.calcular(COMUM_RECORRENTE, subtotal, "EXTRA10"));
    }

    @Test
    @DisplayName("EXTRA10 se soma ao desconto base do cliente comum")
    void extra10SomaAoDescontoBase() {
        // Base: 5% de 50.000 = 2.500; cupom: 10% de 50.000 = 5.000; total 7.500.
        // Teto: 20% de 50.000 = 10.000 — não limita.
        assertEquals(7_500L, politica.calcular(COMUM_RECORRENTE, 50_000, "EXTRA10"));
    }

    // ------------------------------------------------------------------
    // Cupom desconhecido
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "cupom \"{0}\" é desconhecido")
    @ValueSource(strings = { "PROMO", "BEMVINDO10", "EXTRA", "x" })
    void cupomDesconhecidoEhRejeitado(String cupom) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> politica.calcular(COMUM_RECORRENTE, 50_000, cupom));

        assertEquals("Cupom desconhecido", erro.getMessage());
    }

    // ------------------------------------------------------------------
    // Teto de 20%
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIP com EXTRA10 chega exatamente ao teto de 20% sem ser limitado")
    void vipComExtra10AtingeOTetoExato() {
        // 10% + 10% = 20.000; teto = 20% de 100.000 = 20.000.
        assertEquals(20_000L, politica.calcular(VIP_RECORRENTE, 100_000, "EXTRA10"));
    }

    @Test
    @DisplayName("VIP com BEMVINDO ultrapassaria o teto e é limitado a 20% do subtotal")
    void descontoCombinadoEhLimitadoAoTeto() {
        // Base VIP: 10% de 10.000 = 1.000; cupom: +2.000 = 3.000.
        // Teto: 20% de 10.000 = 2.000 -> resultado limitado a 2.000.
        assertEquals(2_000L, politica.calcular(VIP_NOVO, 10_000, "BEMVINDO"));
    }

    @Test
    @DisplayName("cupom conhecido sem elegibilidade também passa pelo teto, sem alterá-lo")
    void cupomSemElegibilidadeAindaPassaPeloTeto() {
        // Base VIP: 10% de 5.000 = 500; BEMVINDO não se aplica (subtotal < 10.000).
        // Teto: 20% de 5.000 = 1.000 -> 500 permanece.
        assertEquals(500L, politica.calcular(VIP_NOVO, 5_000, "BEMVINDO"));
    }

    @Test
    @DisplayName("sem cupom o teto nem chega a ser avaliado, pois o retorno é antecipado")
    void semCupomOTetoNaoEhAvaliado() {
        // O desconto base máximo é 10%, então o teto de 20% nunca seria atingido;
        // o teste documenta que esse caminho retorna antes de calcular o teto.
        assertEquals(10_000L, politica.calcular(VIP_RECORRENTE, 100_000, null));
    }
}
