package br.edu.ifpr.boletim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes da classe Boletim.
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 *
 * Tolerância usada nas comparações de double: 0.0001.
 */
class BoletimTest {

    private static final double TOLERANCIA = 0.0001;

    private Boletim boletim;

    @BeforeEach
    void preparar() {
        boletim = new Boletim();
    }

    // ------------------------------------------------------------------
    // calcularMedia — média aritmética de duas notas, sem arredondamento
    // ------------------------------------------------------------------

    @Test
    void deveCalcularMediaIgualCinco() {
        assertEquals(5, boletim.calcularMedia(5, 5), TOLERANCIA);
    }

    @Test
    @DisplayName("calcularMedia mantém a parte decimal: (7 + 8) / 2 = 7.5")
    void deveCalcularMediaComParteDecimal() {
        assertEquals(7.5, boletim.calcularMedia(7, 8), TOLERANCIA);
    }

    @Test
    @DisplayName("calcularMedia nos limites do intervalo de notas (0 a 10)")
    void deveCalcularMediaDosLimitesDoIntervalo() {
        assertEquals(0, boletim.calcularMedia(0, 0), TOLERANCIA);
        assertEquals(10, boletim.calcularMedia(10, 10), TOLERANCIA);
        assertEquals(5, boletim.calcularMedia(0, 10), TOLERANCIA);
    }

    @Test
    @DisplayName("calcularMedia com notas fracionadas: (6.3 + 7.4) / 2 = 6.85")
    void deveManterAsCasasDecimaisDasNotas() {
        assertEquals(6.85, boletim.calcularMedia(6.3, 7.4), TOLERANCIA);
    }

    // ------------------------------------------------------------------
    // verificarSituacao — os três caminhos da classificação
    // ------------------------------------------------------------------

    @Test
    void deveAprovarAlunoComMediaOito() {
        // Preparar: o objeto já foi criado no @BeforeEach.
        // Executar: chamar um único método com uma entrada conhecida.
        String resultado = boletim.verificarSituacao(8);

        // Verificar: comparar o resultado esperado com o resultado obtido.
        assertEquals("APROVADO", resultado);
    }

    @Test
    void deveRecuperarNotaAlunoComMediaQuatro() {
        assertEquals("RECUPERACAO", boletim.verificarSituacao(4));
    }

    @Test
    void deveReprovarAlunoComMediaDois() {
        assertEquals("REPROVADO", boletim.verificarSituacao(2));
    }

    /**
     * Valores-limite das fronteiras 4 e 7: imediatamente abaixo, igual e acima.
     */
    @ParameterizedTest(name = "verificarSituacao({0}) deve retornar {1}")
    @CsvSource({
            "0.0,  REPROVADO",
            "3.9,  REPROVADO",
            "4.0,  RECUPERACAO",
            "4.1,  RECUPERACAO",
            "6.9,  RECUPERACAO",
            "7.0,  APROVADO",
            "7.1,  APROVADO",
            "10.0, APROVADO"
    })
    void deveClassificarNosValoresLimite(double media, String esperado) {
        assertEquals(esperado, boletim.verificarSituacao(media));
    }

    // ------------------------------------------------------------------
    // contarAprovados — laço com zero, uma e várias iterações
    // ------------------------------------------------------------------

    @Test
    @DisplayName("contarAprovados com array vazio: zero iterações do for")
    void arrayVazioDeveRetornarZero() {
        assertEquals(0, boletim.contarAprovados(new double[] {}));
    }

    @Test
    @DisplayName("contarAprovados com uma iteração e média aprovada")
    void umaIteracaoComAprovado() {
        assertEquals(1, boletim.contarAprovados(new double[] { 7 }));
    }

    @Test
    @DisplayName("contarAprovados com uma iteração e média reprovada (ramo falso do if)")
    void umaIteracaoSemAprovado() {
        assertEquals(0, boletim.contarAprovados(new double[] { 6.9 }));
    }

    @Test
    @DisplayName("contarAprovados com várias iterações misturando aprovados e não aprovados")
    void variasIteracoesMisturandoAprovadosEReprovados() {
        assertEquals(2, boletim.contarAprovados(new double[] { 8, 5, 7 }));
    }

    @Test
    void variasIteracoesTodasAprovadas() {
        assertEquals(4, boletim.contarAprovados(new double[] { 7, 8, 9, 10 }));
    }

    @Test
    void variasIteracoesNenhumaAprovada() {
        assertEquals(0, boletim.contarAprovados(new double[] { 1, 3.5, 6.99 }));
    }

    // ------------------------------------------------------------------
    // uso combinado dos métodos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a média calculada alimenta a classificação: (6 + 8) / 2 = 7 -> APROVADO")
    void mediaCalculadaAlimentaAClassificacao() {
        double media = boletim.calcularMedia(6, 8);

        assertEquals(7, media, TOLERANCIA);
        assertEquals("APROVADO", boletim.verificarSituacao(media));
    }

    @Test
    @DisplayName("as médias da turma alimentam a contagem de aprovados")
    void mediasDaTurmaAlimentamAContagem() {
        double[] medias = {
                boletim.calcularMedia(10, 10),  // 10.0 -> aprovado
                boletim.calcularMedia(6, 7),    // 6.5  -> recuperação
                boletim.calcularMedia(2, 2)     // 2.0  -> reprovado
        };

        assertEquals(1, boletim.contarAprovados(medias));
    }
}
