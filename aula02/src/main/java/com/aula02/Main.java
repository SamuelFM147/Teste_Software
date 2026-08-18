package com.aula02;

public class Main {
    public static void main(String[] args) {
        CalculadoraFrete calculadora = new CalculadoraFrete();
        double frete = calculadora.calcular(200, true);
        System.out.println("Frete: " + frete);

        
    }
}