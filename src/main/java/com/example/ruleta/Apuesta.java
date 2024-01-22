package com.example.ruleta;

public class Apuesta {
    private int numero;
    private String color;
    private String paridad;
    private double cantidad;

    public Apuesta(int numero, String color, String paridad, double cantidad) {
        this.numero = numero;
        this.color = color;
        this.paridad = paridad;
        this.cantidad = cantidad;
    }

    // Añade los métodos getters para cada propiedad

    public int getNumero() {
        return numero;
    }

    public String getColor() {
        return color;
    }

    public String getParidad() {
        return paridad;
    }

    public double getCantidad() {
        return cantidad;
    }
}
