package br.edu.ifpr.pedidos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pedido: validação da lista e da UF, cópia defensiva e os quatro métodos
 * de agregação (subtotal, peso, fragilidade e estoque).
 *
 * Os laços são exercitados com zero, uma e várias linhas, incluindo linhas
 * inativas (quantidade zero) e falta de estoque no início e no fim da lista.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class PedidoTest {

    private static ItemPedido item(String sku, long preco, int quantidade,
                                   int estoque, int peso, boolean fragil) {
        return new ItemPedido(sku, preco, quantidade, estoque, peso, fragil);
    }

    private static Pedido pedidoCom(ItemPedido... itens) {
        return new Pedido(List.of(itens), "PR", false, null);
    }

    // ------------------------------------------------------------------
    // Validação da lista
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lista nula é rejeitada")
    void deveRejeitarListaNula() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Pedido(null, "PR", false, null));

        assertEquals("Lista inválida", erro.getMessage());
    }

    @Test
    @DisplayName("lista vazia é válida na construção")
    void deveAceitarListaVazia() {
        Pedido pedido = new Pedido(List.of(), "PR", false, null);

        assertTrue(pedido.itens().isEmpty());
    }

    @Test
    @DisplayName("aceita exatamente 100 linhas (limite superior)")
    void deveAceitarCemLinhas() {
        List<ItemPedido> itens = IntStream.range(0, 100)
                .mapToObj(i -> item("SKU-" + i, 100, 1, 1, 10, false))
                .toList();

        assertEquals(100, new Pedido(itens, "PR", false, null).itens().size());
    }

    @Test
    @DisplayName("rejeita 101 linhas (limite superior + 1)")
    void deveRejeitarCentoEUmaLinhas() {
        List<ItemPedido> itens = IntStream.range(0, 101)
                .mapToObj(i -> item("SKU-" + i, 100, 1, 1, 10, false))
                .toList();

        assertThrows(IllegalArgumentException.class, () -> new Pedido(itens, "PR", false, null));
    }

    @Test
    @DisplayName("elemento nulo na lista causa NullPointerException (List.copyOf)")
    void deveLancarNpeComElementoNulo() {
        List<ItemPedido> comNulo = Arrays.asList(item("SKU", 100, 1, 1, 10, false), null);

        assertThrows(NullPointerException.class, () -> new Pedido(comNulo, "PR", false, null));
    }

    @Test
    @DisplayName("a cópia acontece antes da validação da UF: elemento nulo com UF inválida ainda é NPE")
    void copiaDefensivaPrecedeValidacaoDaUf() {
        List<ItemPedido> comNulo = Arrays.asList((ItemPedido) null);

        assertThrows(NullPointerException.class, () -> new Pedido(comNulo, "xx", false, null));
    }

    @Test
    @DisplayName("a lista original pode mudar sem afetar o pedido (cópia defensiva)")
    void deveCopiarAListaDefensivamente() {
        List<ItemPedido> original = new ArrayList<>();
        original.add(item("SKU-1", 10_000, 1, 5, 100, false));
        Pedido pedido = new Pedido(original, "PR", false, null);

        original.add(item("SKU-2", 50_000, 1, 5, 100, false));

        assertAll(
                () -> assertEquals(1, pedido.itens().size()),
                () -> assertEquals(10_000L, pedido.subtotalCentavos()),
                () -> assertEquals(2, original.size())
        );
    }

    @Test
    @DisplayName("a lista exposta pelo pedido é imutável")
    void listaExpostaEhImutavel() {
        Pedido pedido = pedidoCom(item("SKU", 100, 1, 1, 10, false));

        assertThrows(UnsupportedOperationException.class,
                () -> pedido.itens().add(item("OUTRO", 100, 1, 1, 10, false)));
    }

    // ------------------------------------------------------------------
    // Validação da UF
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "UF \"{0}\" é aceita")
    @ValueSource(strings = { "PR", "SP", "RJ", "MG", "AC", "ZZ" })
    @DisplayName("qualquer sigla com duas letras maiúsculas é aceita")
    void deveAceitarQualquerUfComDuasMaiusculas(String uf) {
        assertEquals(uf, new Pedido(List.of(), uf, false, null).uf());
    }

    @ParameterizedTest(name = "UF \"{0}\" é rejeitada")
    @ValueSource(strings = { "", "P", "PRR", "pr", "Pr", "P1", "1 ", "P-" })
    @DisplayName("siglas fora do formato de duas letras maiúsculas são rejeitadas")
    void deveRejeitarUfForaDoFormato(String uf) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Pedido(List.of(), uf, false, null));

        assertEquals("UF inválida", erro.getMessage());
    }

    @Test
    @DisplayName("UF nula é rejeitada sem avaliar a expressão regular (curto-circuito do ||)")
    void deveRejeitarUfNula() {
        assertThrows(IllegalArgumentException.class, () -> new Pedido(List.of(), null, false, null));
    }

    @Test
    @DisplayName("cupom nulo e cupom branco são aceitos na construção")
    void deveAceitarCupomNuloOuBranco() {
        assertAll(
                () -> assertNull(new Pedido(List.of(), "PR", false, null).cupom()),
                () -> assertEquals("   ", new Pedido(List.of(), "PR", false, "   ").cupom())
        );
    }

    // ------------------------------------------------------------------
    // subtotalCentavos — laço com continue nas linhas inativas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("subtotal de lista vazia é zero (zero iterações)")
    void subtotalDeListaVaziaEhZero() {
        assertEquals(0L, new Pedido(List.of(), "PR", false, null).subtotalCentavos());
    }

    @Test
    @DisplayName("subtotal com uma linha ativa")
    void subtotalComUmaLinha() {
        assertEquals(30_000L, pedidoCom(item("SKU", 10_000, 3, 5, 100, false)).subtotalCentavos());
    }

    @Test
    @DisplayName("subtotal com várias linhas ativas")
    void subtotalComVariasLinhas() {
        Pedido pedido = pedidoCom(
                item("A", 10_000, 1, 5, 100, false),
                item("B", 5_000, 2, 5, 100, false),
                item("C", 1_000, 3, 5, 100, false));

        assertEquals(23_000L, pedido.subtotalCentavos());
    }

    @Test
    @DisplayName("linha inativa (quantidade zero) é pulada pelo continue e não soma valor")
    void subtotalIgnoraLinhaInativa() {
        Pedido pedido = pedidoCom(
                item("ATIVO", 10_000, 1, 5, 100, false),
                item("INATIVO", 999_999, 0, 5, 100, false));

        assertEquals(10_000L, pedido.subtotalCentavos());
    }

    @Test
    @DisplayName("somente linhas inativas: subtotal zero, ainda que a lista não esteja vazia")
    void subtotalDeLinhasInativasEhZero() {
        Pedido pedido = pedidoCom(
                item("A", 999_999, 0, 5, 100, false),
                item("B", 999_999, 0, 5, 100, false));

        assertEquals(0L, pedido.subtotalCentavos());
    }

    // ------------------------------------------------------------------
    // pesoGramas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("peso de lista vazia é zero")
    void pesoDeListaVaziaEhZero() {
        assertEquals(0, new Pedido(List.of(), "PR", false, null).pesoGramas());
    }

    @Test
    @DisplayName("peso é o peso unitário multiplicado pela quantidade, somado por linha")
    void pesoSomaPesoUnitarioVezesQuantidade() {
        Pedido pedido = pedidoCom(
                item("A", 100, 3, 5, 500, false),   // 1500 g
                item("B", 100, 2, 5, 250, false));  //  500 g

        assertEquals(2_000, pedido.pesoGramas());
    }

    @Test
    @DisplayName("linha inativa não contribui com peso (quantidade zero)")
    void pesoIgnoraLinhaInativa() {
        Pedido pedido = pedidoCom(
                item("ATIVO", 100, 1, 5, 800, false),
                item("INATIVO", 100, 0, 5, 100_000, false));

        assertEquals(800, pedido.pesoGramas());
    }

    // ------------------------------------------------------------------
    // temFragil — retorno antecipado no primeiro frágil ativo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lista vazia não tem item frágil")
    void listaVaziaNaoTemFragil() {
        assertFalse(new Pedido(List.of(), "PR", false, null).temFragil());
    }

    @Test
    @DisplayName("nenhum item frágil")
    void semItemFragil() {
        assertFalse(pedidoCom(
                item("A", 100, 1, 5, 100, false),
                item("B", 100, 1, 5, 100, false)).temFragil());
    }

    @Test
    @DisplayName("item frágil ativo na primeira posição encerra o laço imediatamente")
    void fragilNaPrimeiraPosicao() {
        assertTrue(pedidoCom(
                item("FRAGIL", 100, 1, 5, 100, true),
                item("COMUM", 100, 1, 5, 100, false)).temFragil());
    }

    @Test
    @DisplayName("item frágil ativo na última posição também é detectado")
    void fragilNaUltimaPosicao() {
        assertTrue(pedidoCom(
                item("COMUM", 100, 1, 5, 100, false),
                item("FRAGIL", 100, 1, 5, 100, true)).temFragil());
    }

    @Test
    @DisplayName("item frágil inativo não conta (o && exige quantidade maior que zero)")
    void fragilInativoNaoConta() {
        assertFalse(pedidoCom(item("FRAGIL", 100, 0, 5, 100, true)).temFragil());
    }

    // ------------------------------------------------------------------
    // estoqueSuficiente — laço com break
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lista vazia tem estoque suficiente (zero iterações)")
    void listaVaziaTemEstoqueSuficiente() {
        assertTrue(new Pedido(List.of(), "PR", false, null).estoqueSuficiente());
    }

    @Test
    @DisplayName("todas as linhas disponíveis")
    void todasAsLinhasDisponiveis() {
        assertTrue(pedidoCom(
                item("A", 100, 1, 1, 100, false),
                item("B", 100, 2, 5, 100, false)).estoqueSuficiente());
    }

    @Test
    @DisplayName("falta de estoque na primeira linha aciona o break logo na primeira iteração")
    void faltaDeEstoqueNaPrimeiraLinha() {
        assertFalse(pedidoCom(
                item("SEM", 100, 2, 1, 100, false),
                item("COM", 100, 1, 5, 100, false)).estoqueSuficiente());
    }

    @Test
    @DisplayName("falta de estoque na última linha exige percorrer o laço inteiro")
    void faltaDeEstoqueNaUltimaLinha() {
        assertFalse(pedidoCom(
                item("COM", 100, 1, 5, 100, false),
                item("SEM", 100, 2, 1, 100, false)).estoqueSuficiente());
    }

    @Test
    @DisplayName("estoque é avaliado por linha, mesmo com SKUs repetidos")
    void estoqueEhAvaliadoPorLinha() {
        // Duas linhas do mesmo SKU, cada uma com estoque 1: ambas passam isoladamente.
        assertTrue(pedidoCom(
                item("SKU", 100, 1, 1, 100, false),
                item("SKU", 100, 1, 1, 100, false)).estoqueSuficiente());
    }
}
