package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ItemPedido: validações do construtor compacto e os dois métodos derivados.
 *
 * Domínio declarado no contrato:
 *   sku       — não nulo e não branco
 *   preço     — 1 a 1.000.000 centavos
 *   quantidade— 0 a 100 (zero representa linha inativa)
 *   estoque   — não negativo
 *   peso      — 1 a 100.000 gramas
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class ItemPedidoTest {

    /** Item de referência, usado quando o campo em teste não é o foco. */
    private static ItemPedido item(String sku, long preco, int quantidade,
                                   int estoque, int peso, boolean fragil) {
        return new ItemPedido(sku, preco, quantidade, estoque, peso, fragil);
    }

    // ------------------------------------------------------------------
    // Construção válida e limites aceitos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aceita um item típico e preserva todos os componentes")
    void deveAceitarItemTipico() {
        ItemPedido item = item("LIVRO-JAVA", 10_000, 2, 5, 1_000, true);

        assertAll(
                () -> assertEquals("LIVRO-JAVA", item.sku()),
                () -> assertEquals(10_000L, item.precoCentavos()),
                () -> assertEquals(2, item.quantidade()),
                () -> assertEquals(5, item.estoque()),
                () -> assertEquals(1_000, item.pesoGramas()),
                () -> assertTrue(item.fragil())
        );
    }

    @Test
    @DisplayName("aceita os limites inferiores válidos: preço 1, quantidade 0, estoque 0, peso 1")
    void deveAceitarLimitesInferiores() {
        ItemPedido item = item("A", 1, 0, 0, 1, false);

        assertAll(
                () -> assertEquals(0L, item.totalCentavos()),
                () -> assertTrue(item.disponivel())
        );
    }

    @Test
    @DisplayName("aceita os limites superiores válidos: preço 1.000.000, quantidade 100, peso 100.000")
    void deveAceitarLimitesSuperiores() {
        ItemPedido item = item("SKU", 1_000_000, 100, 100, 100_000, false);

        assertEquals(100_000_000L, item.totalCentavos());
    }

    // ------------------------------------------------------------------
    // SKU inválido (curto-circuito do ||)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SKU nulo é rejeitado sem avaliar isBlank (curto-circuito do ||)")
    void deveRejeitarSkuNulo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> item(null, 100, 1, 1, 100, false));

        assertEquals("SKU obrigatório", erro.getMessage());
    }

    @ParameterizedTest(name = "SKU \"{0}\" é rejeitado")
    @ValueSource(strings = { "", " ", "   ", "\t" })
    @DisplayName("SKU vazio ou só com espaços é rejeitado (operando direito do || avaliado)")
    void deveRejeitarSkuBranco(String sku) {
        assertThrows(IllegalArgumentException.class, () -> item(sku, 100, 1, 1, 100, false));
    }

    // ------------------------------------------------------------------
    // Demais validações: abaixo, no limite e acima
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "preço {0} centavos é rejeitado")
    @CsvSource({ "0", "-1", "1000001" })
    void deveRejeitarPrecoForaDoDominio(long preco) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> item("SKU", preco, 1, 1, 100, false));

        assertEquals("Preço inválido", erro.getMessage());
    }

    @ParameterizedTest(name = "quantidade {0} é rejeitada")
    @CsvSource({ "-1", "101" })
    void deveRejeitarQuantidadeForaDoDominio(int quantidade) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> item("SKU", 100, quantidade, 200, 100, false));

        assertEquals("Quantidade inválida", erro.getMessage());
    }

    @Test
    @DisplayName("estoque negativo é rejeitado")
    void deveRejeitarEstoqueNegativo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> item("SKU", 100, 1, -1, 100, false));

        assertEquals("Estoque inválido", erro.getMessage());
    }

    @ParameterizedTest(name = "peso {0} gramas é rejeitado")
    @CsvSource({ "0", "-1", "100001" })
    void deveRejeitarPesoForaDoDominio(int peso) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> item("SKU", 100, 1, 1, peso, false));

        assertEquals("Peso inválido", erro.getMessage());
    }

    // ------------------------------------------------------------------
    // totalCentavos
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} centavos x {1} unidades = {2} centavos")
    @CsvSource({
            "10000, 0, 0",
            "10000, 1, 10000",
            "10000, 3, 30000",
            "1,     100, 100"
    })
    void deveMultiplicarPrecoPelaQuantidade(long preco, int quantidade, long esperado) {
        assertEquals(esperado, item("SKU", preco, quantidade, 100, 100, false).totalCentavos());
    }

    // ------------------------------------------------------------------
    // disponivel — fronteira quantidade x estoque
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "quantidade {0} com estoque {1} -> disponível={2}")
    @CsvSource({
            "0, 0, true",    // linha inativa sempre disponível
            "1, 0, false",   // limite: falta exatamente uma unidade
            "1, 1, true",    // limite: estoque exato
            "2, 1, false",   // acima do estoque
            "1, 2, true"     // estoque sobrando
    })
    void deveCompararQuantidadeComEstoque(int quantidade, int estoque, boolean esperado) {
        assertEquals(esperado, item("SKU", 100, quantidade, estoque, 100, false).disponivel());
    }
}
