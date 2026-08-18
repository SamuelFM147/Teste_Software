import com.aula02.CalculadoraFrete;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalculadoraFreteTest {
    
    CalculadoraFrete calculadora;  
    @BeforeEach
    void preparar() {
        calculadora = new CalculadoraFrete();
    }

    @Test
    public void deveCobrarFrete(){

        assertEquals(20.0,calculadora.calcular(100, false));
    }
    @Test
    public void naoDeveCobrarFrete(){
        assertEquals(0,calculadora.calcular(200, false));
        assertEquals(0,calculadora.calcular(10, true));
    }
}
