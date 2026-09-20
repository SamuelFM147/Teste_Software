package br.edu.ifpr.boletim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes da classe Participacao.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 *
 * calcularPontos tem duas decisões independentes, logo V(G) = 3.
 * Os testes "ambos verdadeiros" e "ambos falsos" já cobrem todos os ramos,
 * mas deixam duas das quatro combinações sem executar — por isso as quatro
 * combinações são testadas explicitamente abaixo.
 */
class ParticipacaoTest {

    private Participacao participacao;

    @BeforeEach
    void preparar() {
        participacao = new Participacao();
    }

    @Test
    @DisplayName("entregou a atividade e participou da aula: 2 + 1 = 3 pontos")
    void deveSomarOsDoisBonus() {
        assertEquals(3, participacao.calcularPontos(true, true));
    }

    @Test
    @DisplayName("entregou a atividade, mas não participou da aula: 2 pontos")
    void deveContarApenasAEntrega() {
        assertEquals(2, participacao.calcularPontos(true, false));
    }

    @Test
    @DisplayName("não entregou a atividade, mas participou da aula: 1 ponto")
    void deveContarApenasAParticipacao() {
        assertEquals(1, participacao.calcularPontos(false, true));
    }

    @Test
    @DisplayName("não entregou nada e não participou: 0 pontos")
    void deveRetornarZeroSemNenhumaContribuicao() {
        assertEquals(0, participacao.calcularPontos(false, false));
    }

    /**
     * A mesma tabela-verdade, agora como tabela de decisão explícita.
     * Serve para mostrar que os pontos se acumulam e são independentes entre si.
     */
    @ParameterizedTest(name = "entregou={0}, participou={1} -> {2} ponto(s)")
    @CsvSource({
            "true,  true,  3",
            "true,  false, 2",
            "false, true,  1",
            "false, false, 0"
    })
    void tabelaDeDecisaoCompleta(boolean entregou, boolean participou, int esperado) {
        assertEquals(esperado, participacao.calcularPontos(entregou, participou));
    }
}
