package com.example.ruleta;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RuletaController {

    @FXML
    private HBox lineaDeNumeros;

    @FXML
    private Label cuentaAtrasLabel;

    @FXML
    private Label budgetLabel;

    private List<Text> numerosText;
    private Rectangle mascara;
    private boolean animacionEnProgreso;

    private static final int NUMEROS_INICIALES = 24;

    private Thread cuentaAtrasThread;
    private Thread fondoAnimacionThread;

    private boolean fondoAnimacionEnProgreso;

    private Thread detenerAnimacionThread;

    private Timeline fondoAnimacionTimeline;

    private double budget = 10;

    private int contadorApuestas = 0;

    @FXML
    private VBox apuestasContainer;

    @FXML
    private TextField apuestasTextField;

    @FXML
    private Label apuestasLabel;

    private final Object budgetLock = new Object();


    @FXML
    public void initialize() {
        // Crear una lista de Text con los números
        actualizarPresupuesto();
        numerosText = new ArrayList<>();
        for (int i = 0; i <= 36; i++) {
            Text numeroText = new Text(String.valueOf(i));
            numeroText.setFill((i == 0) ? Color.GREEN : (i % 2 == 0) ? Color.RED : Color.BLACK);
            numeroText.setFont(new Font(40)); // Ajusta el tamaño de la fuente
            numerosText.add(numeroText);
        }

        // Agregar espacio inicial de 8 números
        for (int i = 0; i < NUMEROS_INICIALES; i++) {
            Text espacioInicial = new Text("");
            Region espacio = new Region();
            espacio.setMinWidth(20); // Ajusta la distancia entre los números
            lineaDeNumeros.getChildren().addAll(espacioInicial, espacio);
        }

        // Mezclar la lista de Texts inicialmente
        Collections.shuffle(numerosText);

        // Inicialmente, agrega los Texts y espacios a la HBox
        inicializarHBox();

        // Inicializa el VBox de las apuestas
        apuestasContainer.getChildren().clear();
        contadorApuestas = 0;
    }

    private void actualizarPresupuesto() {
        budgetLabel.setText(budget + " €");
    }

    private void inicializarHBox() {
        for (Text numeroText : numerosText) {
            Region espacio = new Region();
            espacio.setMinWidth(20); // Ajusta la distancia entre los números
            lineaDeNumeros.getChildren().addAll(numeroText, espacio);
        }
        // Establecer la propiedad de clip para ocultar los elementos fuera del HBox
        Rectangle clip = new Rectangle(lineaDeNumeros.getPrefWidth(), lineaDeNumeros.getPrefHeight());
        lineaDeNumeros.setClip(clip);
    }

    @FXML
    public void empezarRuleta() {
        // Iniciar la cuenta atrás en un hilo separado
        cuentaAtrasThread = new Thread(() -> cuentaAtras());
        cuentaAtrasThread.setDaemon(true);
        cuentaAtrasThread.start();

        // Iniciar el hilo de fondo de animación
        fondoAnimacion();
        fondoAnimacionEnProgreso = true;
        fondoAnimacionThread = new Thread(() -> fondoAnimacion());
        fondoAnimacionThread.setDaemon(true);
        fondoAnimacionThread.start();

        // Iniciar el hilo para detener la animación después de un tiempo aleatorio
        detenerAnimacionThread = new Thread(() -> detenerAnimacionDespuesDeTiempo());
        detenerAnimacionThread.setDaemon(true);
        detenerAnimacionThread.start();
    }

    private void detenerAnimacionDespuesDeTiempo() {
        Random random = new Random();
        int tiempoAleatorio = (int) ((random.nextDouble() * (29.6 - 24) + 24) * 1000);

        try {
            // Esperar el tiempo aleatorio antes de detener la animación
            Thread.sleep(tiempoAleatorio);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("AHORA SE DETIENE LA RULETA");
        // Detener la animación
        detenerRuleta();
    }

    @FXML
    public void detenerRuleta() {
        animacionEnProgreso = false;
        // Obtener el número ganador cuando se detiene la animación
        int numeroGanador = obtenerNumeroGanador();

        // Verificar si el número ganador está dentro de la zona ganadora
        if (esNumeroEnZonaGanadora(numeroGanador)) {
            System.out.println("¡Has ganado!");
            // Realizar acciones adicionales si el número está en la zona ganadora
        } else {
            System.out.println("Número ganador: " + numeroGanador);
            // Realizar acciones adicionales si el número no está en la zona ganadora
        }
        // Detener el temporizador de fondo de animación
        if (fondoAnimacionTimeline != null) {
            fondoAnimacionTimeline.stop();
        }

        // Detener el hilo de detener animación
        if (detenerAnimacionThread != null && detenerAnimacionThread.isAlive()) {
            detenerAnimacionThread.interrupt();
        }
    }

    private int obtenerNumeroGanador() {
        double zonaGanadoraX = 248.0;
        double zonaGanadoraY = 228.0;
        double zonaGanadoraWidth = 42.0;
        double zonaGanadoraHeight = 42.0;

        for (int i = 0; i < numerosText.size(); i++) {
            // Utilizar localToParent para obtener las coordenadas en la líneaDeNumeros
            double numeroCenterXInLineaDeNumeros = numerosText.get(i).localToParent(numerosText.get(i).getBoundsInLocal().getCenterX(),
                    numerosText.get(i).getBoundsInLocal().getCenterY()).getX();
            double numeroCenterYInLineaDeNumeros = numerosText.get(i).localToParent(numerosText.get(i).getBoundsInLocal().getCenterX(),
                    numerosText.get(i).getBoundsInLocal().getCenterY()).getY();

            // Imprimir las coordenadas
            System.out.println("Número " + i + ": " + numeroCenterXInLineaDeNumeros + ", " + numeroCenterYInLineaDeNumeros);

            // Verificar si el área del número intersecta con el área de la zona ganadora
            if (numerosText.get(i).getBoundsInParent().intersects(
                    zonaGanadoraX, zonaGanadoraY, zonaGanadoraWidth, zonaGanadoraHeight)) {
                return i;
            }
        }
        return -1;
    }

    private boolean esNumeroEnZonaGanadora(int numero) {
        double zonaGanadoraX = 248.0;
        double zonaGanadoraY = 228.0;
        double zonaGanadoraWidth = 42.0;
        double zonaGanadoraHeight = 42.0;

        // Verificar si el área del número intersecta con el área de la zona ganadora
        return numerosText.get(numero).getBoundsInParent().intersects(
                zonaGanadoraX, zonaGanadoraY, zonaGanadoraWidth, zonaGanadoraHeight);
    }

    private void cuentaAtras() {
        int[] segundos = {20};

        while (segundos[0] > 0 && !Thread.interrupted()) {
            try {
                Thread.sleep(1000); // Esperar 1 segundo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Platform.runLater(() -> cuentaAtrasLabel.setText(String.valueOf(segundos[0])));
            segundos[0]--;
        }

        // Al finalizar la cuenta atrás, iniciar la animación
        Platform.runLater(() -> {
            animacionEnProgreso = true;
            int[] iteraciones = {0};
            Thread animationThread = new Thread(() -> animarNumeros(iteraciones));
            animationThread.setDaemon(true);
            animationThread.start();
        });
    }

    private void fondoAnimacion() {
        Random random = new Random();

        // Establecer una duración aleatoria entre 4 y 9.6 segundos
        int duracionAleatoria = (int) ((random.nextDouble() * (9.6 - 4) + 4) * 1000);

        fondoAnimacionTimeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(duracionAleatoria), (ActionEvent event) -> {
            Platform.runLater(() -> {
                // Lógica para el fondo de animación (puedes adaptar esto según tus necesidades)
                // ...
            });

            // Reiniciar la animación
            fondoAnimacion();
        }));

        // Iniciar el temporizador
        fondoAnimacionTimeline.play();
    }

    private void animarNumeros(int[] iteraciones) {
        while (animacionEnProgreso) {
            try {
                Thread.sleep(20); // Ajusta la velocidad de la animación
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Platform.runLater(() -> {
                if (iteraciones[0] < NUMEROS_INICIALES) {
                    moverNumeros();
                    iteraciones[0]++;
                } else {
                    moverNumeros();
                }
            });
        }
    }

    private void moverNumeros() {
        // Mover cada Text y espacio a la izquierda
        for (int i = 0; i < lineaDeNumeros.getChildren().size(); i += 2) {
            Text numeroText = (Text) lineaDeNumeros.getChildren().get(i);
            Region espacio = (Region) lineaDeNumeros.getChildren().get(i + 1);

            numeroText.setTranslateX(numeroText.getTranslateX() - 5); // Ajusta la distancia del desplazamiento
            espacio.setTranslateX(espacio.getTranslateX() - 5);
        }
    }

    @FXML
    private void onButtonClick(ActionEvent event) {
        if (budget >= 0.10 && !animacionEnProgreso && contadorApuestas < 10) {
            double cantidadApostada = Math.min(budget, 1.0); // Obtener la cantidad apostada

            // Restar el dinero de la cantidad apostada al presupuesto
            restarMoneda(cantidadApostada);

            Button clickedButton = (Button) event.getSource();
            int buttonNumber = Integer.parseInt(clickedButton.getId().substring(6)); // Obtener el número del botón

            // Lógica adicional según el botón presionado
            String apuesta = obtenerDescripcionApuesta(buttonNumber, cantidadApostada);
            mostrarApuesta(apuesta);

            // No es necesario reducir la cantidad apostada del presupuesto aquí, ya que se hizo en restarMoneda
            // budget -= cantidadApostada;

            actualizarPresupuesto();
        }
    }


    private String obtenerDescripcionApuesta(int numero, double cantidadApostada) {
        String color = (numero % 2 == 0) ? "rojo" : "negro";
        String paridad = (numero % 2 == 0) ? "par" : "impar";

        // Actualiza el texto de las apuestas
        return numero + " " + color + " " + paridad + " - " + cantidadApostada + "€";
    }

    private void mostrarApuesta(String apuesta) {
        // Verificar si ya hay una apuesta para el mismo número
        for (Node node : apuestasContainer.getChildren()) {
            if (node instanceof Label) {
                Label existingLabel = (Label) node;
                if (existingLabel.getText().startsWith(apuesta.substring(0, apuesta.indexOf(" - ")))) {
                    // Si ya hay una apuesta para el mismo número, actualizar la cantidad apostada
                    String[] parts = existingLabel.getText().split(" - ");
                    double existingAmount = Double.parseDouble(parts[1].replace("€", "").trim());
                    double newAmount = Double.parseDouble(apuesta.substring(apuesta.indexOf(" - ") + 3, apuesta.indexOf("€")).trim());
                    double totalAmount = existingAmount + newAmount;

                    // Actualizar la cantidad apostada en el texto del Label
                    existingLabel.setText(apuesta.substring(0, apuesta.indexOf(" - ")) + " - " + totalAmount + "€");
                    return;
                }
            }
        }

        // Si no hay una apuesta existente para el mismo número, agregar una nueva entrada
        Label labelApuesta = new Label(apuesta);
        apuestasContainer.getChildren().add(labelApuesta);
        contadorApuestas++;
    }

    private void restarMoneda(double cantidad) {
        synchronized (budgetLock) {
            budget = Math.max(budget - cantidad, 0.0); // Asegurarse de que el presupuesto no sea negativo
        }
    }

}

