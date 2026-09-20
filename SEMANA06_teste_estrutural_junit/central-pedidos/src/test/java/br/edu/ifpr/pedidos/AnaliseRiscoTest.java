package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnaliseRisco.avaliar — retornos antecipados e as duas expressões de
 * curto-circuito (|| para cliente novo, && para cliente recorrente).
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class AnaliseRiscoTest {

    private AnaliseRisco risco;

    @BeforeEach
    void preparar() {
        risco = new AnaliseRisco();
    }

    // ------------------------------------------------------------------
    // Guarda de entrada e retorno antecipado por bloqueio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("total negativo é rejeitado antes de qualquer regra")
    void deveRejeitarTotalNegativo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> risco.avaliar(new Cliente(false, false, 1), -1, false));

        assertEquals("Total negativo", erro.getMessage());
    }

    @Test
    @DisplayName("total zero é aceito")
    void totalZeroEhAceito() {
        assertEquals("APROVADO", risco.avaliar(new Cliente(false, false, 1), 0, false));
    }

    @Test
    @DisplayName("cliente bloqueado é recusado antes de avaliar histórico e total")
    void clienteBloqueadoEhRecusado() {
        Cliente bloqueado = new Cliente(false, true, 0);

        assertAll(
                () -> assertEquals("RECUSADO", risco.avaliar(bloqueado, 0, false)),
                () -> assertEquals("RECUSADO", risco.avaliar(bloqueado, 999_999, true)),
                // VIP bloqueado continua recusado.
                () -> assertEquals("RECUSADO", risco.avaliar(new Cliente(true, true, 10), 100, false))
        );
    }

    // ------------------------------------------------------------------
    // Cliente sem compras anteriores: total > R$ 1.000,00 OU expresso
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "cliente novo, total {0}, expresso={1} -> {2}")
    @CsvSource({
            "99999,  false, APROVADO",   // limite - 1, entrega normal
            "100000, false, APROVADO",   // limite exato ainda aprova
            "100001, false, REVISAO",    // limite + 1 aciona o primeiro operando do ||
            "0,      true,  REVISAO",    // primeiro operando falso, segundo verdadeiro
            "100001, true,  REVISAO"     // primeiro operando já verdadeiro (curto-circuito)
    })
    void clienteNovoNaFronteiraDeMilReais(long total, boolean expresso, String esperado) {
        assertEquals(esperado, risco.avaliar(new Cliente(false, false, 0), total, expresso));
    }

    @Test
    @DisplayName("cliente novo e VIP segue a mesma regra dos clientes novos")
    void clienteNovoVipUsaARegraDeClienteNovo() {
        // O teste de VIP só existe no ramo dos clientes recorrentes.
        assertEquals("REVISAO", risco.avaliar(new Cliente(true, false, 0), 100_001, false));
    }

    // ------------------------------------------------------------------
    // Cliente com histórico: total > R$ 5.000,00 E não VIP
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "cliente recorrente, total {0}, vip={1} -> {2}")
    @CsvSource({
            "499999, false, APROVADO",   // limite - 1
            "500000, false, APROVADO",   // limite exato ainda aprova
            "500001, false, REVISAO",    // ambos os operandos do && verdadeiros
            "500001, true,  APROVADO",   // segundo operando falso: VIP escapa da revisão
            "499999, true,  APROVADO"    // primeiro operando falso (curto-circuito)
    })
    void clienteRecorrenteNaFronteiraDeCincoMilReais(long total, boolean vip, String esperado) {
        // A entrega expressa é irrelevante no ramo dos clientes recorrentes.
        assertEquals(esperado, risco.avaliar(new Cliente(vip, false, 5), total, false));
    }

    @Test
    @DisplayName("entrega expressa não aciona revisão para cliente com histórico")
    void expressoNaoAfetaClienteRecorrente() {
        assertEquals("APROVADO", risco.avaliar(new Cliente(false, false, 1), 10_000, true));
    }

    @Test
    @DisplayName("uma única compra anterior já tira o cliente da regra de cliente novo")
    void umaCompraAnteriorMudaDeRegra() {
        Cliente novo = new Cliente(false, false, 0);
        Cliente recorrente = new Cliente(false, false, 1);

        assertAll(
                () -> assertEquals("REVISAO", risco.avaliar(novo, 100_001, false)),
                () -> assertEquals("APROVADO", risco.avaliar(recorrente, 100_001, false))
        );
    }

}
