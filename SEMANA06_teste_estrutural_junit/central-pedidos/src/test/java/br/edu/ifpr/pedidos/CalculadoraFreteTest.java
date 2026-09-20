package br.edu.ifpr.pedidos;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalculadoraFrete.calcular — switch de UF, laço de peso excedente e as
 * quatro decisões independentes (gratuidade, VIP, expresso e fragilidade).
 *
 * A ordem das regras é parte do contrato: base -> peso -> gratuidade ->
 * VIP -> expresso -> frágil. Os adicionais de expresso e fragilidade
 * incidem mesmo quando a base foi zerada.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class CalculadoraFreteTest {

    private static final Cliente COMUM = new Cliente(false, false, 1);
    private static final Cliente VIP = new Cliente(true, false, 1);

    private CalculadoraFrete calculadora;

    @BeforeEach
    void preparar() {
        calculadora = new CalculadoraFrete();
    }

    /** Monta um pedido com uma única linha ativa de peso controlado. */
    private static Pedido pedido(String uf, int pesoGramas, boolean expresso, boolean fragil) {
        ItemPedido item = new ItemPedido("SKU", 10_000, 1, 10, pesoGramas, fragil);
        return new Pedido(List.of(item), uf, expresso, null);
    }

    // ------------------------------------------------------------------
    // Guarda de entrada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("valor líquido negativo é rejeitado")
    void deveRejeitarLiquidoNegativo() {
        Pedido pedido = pedido("PR", 1_000, false, false);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcular(pedido, COMUM, -1));

        assertEquals("Valor líquido negativo", erro.getMessage());
    }

    // ------------------------------------------------------------------
    // switch de UF — os três cases e o default
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "UF {0} tem base de {1} centavos")
    @CsvSource({
            "PR, 1200",
            "SP, 2000",
            "RJ, 2000",
            "MG, 3000",   // default
            "ZZ, 3000"    // qualquer outra sigla válida também cai no default
    })
    void baseDoFretePorUf(String uf, long esperado) {
        // Peso abaixo de 2 kg, líquido baixo, cliente comum, entrega normal, sem frágil.
        assertEquals(esperado, calculadora.calcular(pedido(uf, 1_000, false, false), COMUM, 10_000));
    }

    // ------------------------------------------------------------------
    // Laço do peso excedente — zero, uma e várias iterações
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pedido sem itens tem peso zero e nenhuma iteração do laço")
    void pesoZeroNaoEntraNoLaco() {
        Pedido semItens = new Pedido(List.of(), "PR", false, null);

        assertEquals(1_200L, calculadora.calcular(semItens, COMUM, 10_000));
    }

    @ParameterizedTest(name = "peso de {0} g resulta em frete {1} (base PR de 1200)")
    @CsvSource({
            "1999, 1200",   // abaixo do limite: zero iterações
            "2000, 1200",   // limite exato: zero iterações
            "2001, 1500",   // uma fração acima: 1 iteração
            "3000, 1500",   // 1 kg exato de excedente: 1 iteração
            "3001, 1800",   // 1 kg + fração: 2 iterações
            "5000, 2100",   // 3 kg de excedente: 3 iterações
            "5001, 2400"    // 3 kg + fração: 4 iterações
    })
    @DisplayName("cada quilo adicional ou fração acrescenta R$ 3,00")
    void adicionalPorPesoExcedente(int pesoGramas, long esperado) {
        assertEquals(esperado, calculadora.calcular(pedido("PR", pesoGramas, false, false), COMUM, 10_000));
    }

    @Test
    @DisplayName("o peso considerado é o do pedido inteiro: peso unitário x quantidade")
    void pesoConsideraQuantidade() {
        ItemPedido item = new ItemPedido("SKU", 10_000, 3, 10, 1_000, false); // 3 kg
        Pedido pedido = new Pedido(List.of(item), "PR", false, null);

        // Excedente de 1000 g -> 1 iteração -> 1200 + 300.
        assertEquals(1_500L, calculadora.calcular(pedido, COMUM, 10_000));
    }

    // ------------------------------------------------------------------
    // Gratuidade: líquido >= R$ 300,00 e entrega normal
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "líquido de {0} centavos em entrega normal resulta em frete {1}")
    @CsvSource({
            "29999, 1500",   // limite - 1: cobra base + peso
            "30000, 0",      // limite exato: zera base e adicional de peso
            "30001, 0"
    })
    void gratuidadeNaFronteiraDeTrezentosReais(long liquido, long esperado) {
        // PR com 2001 g: base 1200 + 300 de excedente.
        assertEquals(esperado, calculadora.calcular(pedido("PR", 2_001, false, false), COMUM, liquido));
    }

    @Test
    @DisplayName("entrega expressa não recebe gratuidade, mesmo com líquido alto")
    void expressoNaoRecebeGratuidade() {
        // 1200 de base + 1500 de expresso.
        assertEquals(2_700L, calculadora.calcular(pedido("PR", 1_000, true, false), COMUM, 100_000));
    }

    // ------------------------------------------------------------------
    // VIP paga metade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIP paga metade da base somada ao adicional de peso")
    void vipPagaMetade() {
        // 3000 (MG) + 300 (excedente) = 3300 -> 1650.
        assertEquals(1_650L, calculadora.calcular(pedido("MG", 3_000, false, false), VIP, 10_000));
    }

    @Test
    @DisplayName("metade de zero continua zero quando a gratuidade já se aplicou")
    void vipComFreteJaZerado() {
        assertEquals(0L, calculadora.calcular(pedido("PR", 1_000, false, false), VIP, 30_000));
    }

    // ------------------------------------------------------------------
    // Adicionais: expresso e fragilidade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("entrega expressa acrescenta R$ 15,00")
    void expressoAcrescentaQuinzeReais() {
        assertEquals(2_700L, calculadora.calcular(pedido("PR", 1_000, true, false), COMUM, 10_000));
    }

    @Test
    @DisplayName("item frágil ativo acrescenta R$ 5,00")
    void fragilAcrescentaCincoReais() {
        assertEquals(1_700L, calculadora.calcular(pedido("PR", 1_000, false, true), COMUM, 10_000));
    }

    @Test
    @DisplayName("o adicional de fragilidade incide uma única vez, com dois itens frágeis")
    void fragilidadeIncideUmaUnicaVez() {
        Pedido pedido = new Pedido(List.of(
                new ItemPedido("A", 10_000, 1, 10, 500, true),
                new ItemPedido("B", 10_000, 1, 10, 500, true)), "PR", false, null);

        assertEquals(1_700L, calculadora.calcular(pedido, COMUM, 10_000));
    }

    @Test
    @DisplayName("item frágil inativo não aciona o adicional")
    void fragilInativoNaoAcionaAdicional() {
        Pedido pedido = new Pedido(List.of(
                new ItemPedido("ATIVO", 10_000, 1, 10, 500, false),
                new ItemPedido("FRAGIL", 10_000, 0, 10, 500, true)), "PR", false, null);

        assertEquals(1_200L, calculadora.calcular(pedido, COMUM, 10_000));
    }

    @Test
    @DisplayName("expresso e fragilidade incidem mesmo depois de a base ser zerada")
    void adicionaisIncidemSobreBaseZerada() {
        // Gratuidade só se aplica em entrega normal, então usamos normal + frágil.
        // 1200 -> 0 pela gratuidade; VIP: 0/2 = 0; frágil: +500.
        assertEquals(500L, calculadora.calcular(pedido("PR", 1_000, false, true), VIP, 30_000));
    }

    // ------------------------------------------------------------------
    // Combinação das quatro decisões independentes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIP, expresso, frágil e peso excedente na mesma cobrança")
    void combinacaoCompleta() {
        // MG: 3000 + 900 (3 iterações) = 3900; VIP: 1950; expresso: +1500; frágil: +500.
        assertEquals(3_950L, calculadora.calcular(pedido("MG", 5_000, true, true), VIP, 0));
    }

    @Test
    @DisplayName("cliente comum, sem nenhuma das quatro condições, paga apenas a base")
    void nenhumaDecisaoAtiva() {
        assertEquals(2_000L, calculadora.calcular(pedido("SP", 1_000, false, false), COMUM, 0));
    }
}
