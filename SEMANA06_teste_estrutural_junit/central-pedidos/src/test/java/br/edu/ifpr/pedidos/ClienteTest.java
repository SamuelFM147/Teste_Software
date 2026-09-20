package br.edu.ifpr.pedidos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cliente é um record com uma única validação: histórico não negativo.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */
class ClienteTest {

    @Test
    @DisplayName("aceita histórico zero (limite inferior válido)")
    void deveAceitarHistoricoZero() {
        Cliente cliente = new Cliente(false, false, 0);

        assertAll(
                () -> assertFalse(cliente.vip()),
                () -> assertFalse(cliente.bloqueado()),
                () -> assertEquals(0, cliente.comprasAnteriores())
        );
    }

    @Test
    @DisplayName("aceita histórico positivo e preserva as flags")
    void deveAceitarHistoricoPositivo() {
        Cliente cliente = new Cliente(true, true, 42);

        assertAll(
                () -> assertTrue(cliente.vip()),
                () -> assertTrue(cliente.bloqueado()),
                () -> assertEquals(42, cliente.comprasAnteriores())
        );
    }

    @Test
    @DisplayName("histórico negativo (limite inferior - 1) é rejeitado")
    void deveRejeitarHistoricoNegativo() {
        IllegalArgumentException erro =
                assertThrows(IllegalArgumentException.class, () -> new Cliente(false, false, -1));

        assertEquals("Histórico inválido", erro.getMessage());
    }

    @Test
    @DisplayName("dois clientes com os mesmos dados são iguais (semântica de record)")
    void clientesComMesmosDadosSaoIguais() {
        Cliente a = new Cliente(true, false, 3);
        Cliente b = new Cliente(true, false, 3);

        assertAll(
                () -> assertEquals(a, b),
                () -> assertEquals(a.hashCode(), b.hashCode()),
                () -> assertNotEquals(a, new Cliente(false, false, 3))
        );
    }
}
