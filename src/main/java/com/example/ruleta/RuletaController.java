package com.example.ruleta;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
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
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RuletaController {

    // Componentes de la interfaz
    @FXML
    private HBox lineaDeNumeros;

    @FXML
    private Label cuentaAtrasLabel;

    @FXML
    private Label budgetLabel;

    @FXML
    private VBox apuestasContainer;

    @FXML
    private TextField apuestasTextField;

    @FXML
    private Label apuestasLabel;

    // Variables de estado
    private List<Text> numerosText;
    private Rectangle mascara;
    private boolean animacionEnProgreso;
    private boolean fondoAnimacionEnProgreso;

    private static final int NUMEROS_INICIALES = 24;

    private Thread cuentaAtrasThread;
    private Thread fondoAnimacionThread;
    private Thread detenerAnimacionThread;
    private Thread trackingThread;

    private Timeline fondoAnimacionTimeline;

    private double budget = 10;
    private int contadorApuestas = 0;

    // Objeto para sincronización de presupuesto
    private final Object budgetLock = new Object();

    // Objeto para sincronización de la ruleta
    private final Object ruletaLock = new Object();

    @FXML
    public void initialize() {
        // Crear una lista de Text con los números
        actualizarPresupuesto();

        //Empieza la ruleta
        empezarRuleta();

        // Inicializa el VBox de las apuestas
        apuestasContainer.getChildren().clear();
        contadorApuestas = 0;
    }

    // Método para actualizar la etiqueta de presupuesto
    private void actualizarPresupuesto() {
        Platform.runLater(() -> budgetLabel.setText(budget + " €"));
    }

    // Método para inicializar la HBox con números y espacios
    private void inicializarHBox() {
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
        // Limpiar todos los números actuales antes de iniciar la cuenta atrás
        Platform.runLater(() -> {
            lineaDeNumeros.getChildren().clear();
            inicializarHBox();
        });

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

    // Metodo para detener la animacion despues de un tiempo aleatorio
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
        animacionEnProgreso = false;
        // Detener la animación
        detenerRuleta();
    }

    // Método para detener la animación de la ruleta
    @FXML
    public void detenerRuleta() {
        synchronized (ruletaLock) {
            animacionEnProgreso = false;

            // Detener el temporizador de fondo de animación
            if (fondoAnimacionTimeline != null) {
                fondoAnimacionTimeline.stop();
            }

            // Detener el hilo de detener animación
            if (detenerAnimacionThread != null && detenerAnimacionThread.isAlive()) {
                detenerAnimacionThread.interrupt();
            }

            // Obtener el número ganador cuando se detiene la animación
            int numeroGanador = obtenerNumeroGanador();
            System.out.println("Número ganador: " + numeroGanador);

            // Limpiar apuestas después de obtener el número ganador
            Platform.runLater(() -> limpiarApuestas());

            // Verificar apuestas y ajustar presupuesto
            verificarApuestas(numeroGanador);

            // Espera para mostrar resultado
            esperaDeResultado();
        }
    }

    private void esperaDeResultado() {
        Thread esperaThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // Pausa de 5 segundos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Iniciar un nuevo ciclo
            Platform.runLater(() -> empezarRuleta());
        });

        esperaThread.setDaemon(true);
        esperaThread.start();
    }

    // Método para obtener el número ganador en función de la posición
    private int obtenerNumeroGanador() {
        double zonaGanadoraX = 248.0;
        double zonaGanadoraY = 228.0;
        double zonaGanadoraWidth = 22.0;
        double zonaGanadoraHeight = 22.0;

        double distanciaMinima = Double.MAX_VALUE;
        int numeroGanador = -1;

        // Obtener el número de la escena en lugar de usar localToScene
        ObservableList<Node> children = lineaDeNumeros.getChildren();
        for (int i = 0; i < numerosText.size(); i++) {
            Text numeroText = numerosText.get(i);

            // Obtener las coordenadas globales del número actual
            Bounds bounds = numeroText.localToScene(numeroText.getBoundsInLocal());

            // Verificar si el área del número intersecta con el área de la zona ganadora
            if (bounds.intersects(zonaGanadoraX, zonaGanadoraY, zonaGanadoraWidth, zonaGanadoraHeight)) {
                // Calcular la distancia al centro de la zona ganadora
                double centroX = bounds.getMinX() + bounds.getWidth() / 2.0;
                double centroY = bounds.getMinY() + bounds.getHeight() / 2.0;
                double distancia = Math.sqrt(Math.pow(zonaGanadoraX + zonaGanadoraWidth / 2.0 - centroX, 2) + Math.pow(zonaGanadoraY + zonaGanadoraHeight / 2.0 - centroY, 2));

                // Actualizar el número ganador y la distancia mínima si esta distancia es menor
                if (distancia < distanciaMinima) {
                    distanciaMinima = distancia;
                    numeroGanador = i;
                }
            }
        }

        System.out.println("Número ganador: " + numeroGanador);

        return numeroGanador;
    }

    // Método para la cuenta atrás antes de iniciar la animación
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
            synchronized (ruletaLock) {
                animacionEnProgreso = true;
                int[] iteraciones = {0};
                Thread animationThread = new Thread(() -> animarNumeros(iteraciones));
                animationThread.setDaemon(true);
                animationThread.start();
            }
        });
    }

    // Método para la animación de fondo
    private void fondoAnimacion() {
        Random random = new Random();

        // Establecer una duración aleatoria entre 4 y 9.6 segundos
        int duracionAleatoria = (int) ((random.nextDouble() * (9.6 - 4) + 4) * 1000);

        fondoAnimacionTimeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(duracionAleatoria), (ActionEvent event) -> {
            Platform.runLater(() -> {

            });

            // Reiniciar la animación
            fondoAnimacion();
        }));

        // Iniciar el temporizador
        fondoAnimacionTimeline.play();
    }

    // Método para animar los números en la ruleta
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

    // Método para mover los números en la ruleta
    private void moverNumeros() {
        // Mover cada Text y espacio a la izquierda
        for (int i = 0; i < lineaDeNumeros.getChildren().size(); i += 2) {
            Text numeroText = (Text) lineaDeNumeros.getChildren().get(i);
            Region espacio = (Region) lineaDeNumeros.getChildren().get(i + 1);

            numeroText.setTranslateX(numeroText.getTranslateX() - 5); // Ajusta la distancia del desplazamiento
            espacio.setTranslateX(espacio.getTranslateX() - 5);
        }
    }

    // Método llamado al hacer clic en un botón de apuesta
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

            actualizarPresupuesto();
        }
    }

    // Método para obtener la descripción de la apuesta
    private String obtenerDescripcionApuesta(int numero, double cantidadApostada) {
        String color = (numero % 2 == 0) ? "rojo" : "negro";
        String paridad = (numero % 2 == 0) ? "par" : "impar";

        // Actualiza el texto de las apuestas
        return numero + " " + color + " " + paridad + " - " + cantidadApostada + "€";
    }

    // Método para mostrar la apuesta en la interfaz
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
        labelApuesta.setTextFill(Color.WHITE);
        labelApuesta.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        apuestasContainer.getChildren().add(labelApuesta);
        contadorApuestas++;
    }

    // Método para restar la cantidad apostada del presupuesto
    private void restarMoneda(double cantidad) {
        synchronized (budgetLock) {
            budget = Math.max(budget - cantidad, 0.0); // Asegurarse de que el presupuesto no sea negativo
        }
    }

    // Método para limpiar las apuestas
    private void limpiarApuestas() {
        apuestasContainer.getChildren().clear();
        contadorApuestas = 0;
    }

    // Método para verificar las apuestas y ajustar el presupuesto
    private void verificarApuestas(int numeroGanador) {
        synchronized (budgetLock) {
            ObservableList<Node> children = apuestasContainer.getChildren();
            List<Node> copiaApuestas = new ArrayList<>(children);  // Crear una copia de la lista

            for (Node node : copiaApuestas) {
                if (node instanceof Label) {
                    Label label = (Label) node;
                    String apuesta = label.getText();
                    int numeroApostado = Integer.parseInt(apuesta.split(" ")[0]);
                    System.out.println(apuesta);
                    System.out.println(numeroApostado);
                    // Verificar si el número ganador está contenido en la apuesta
                    if (apuesta.contains(String.valueOf(numeroGanador))) {
                        // El usuario apostó a un número que incluye al número ganador
                        double cantidadApostada = Double.parseDouble(apuesta.substring(apuesta.lastIndexOf(" ") + 1, apuesta.indexOf("€")));
                        double ganancia = cantidadApostada * 35;

                        // Ajustar el presupuesto con la ganancia
                        budget += cantidadApostada + ganancia;

                        // Actualizar la etiqueta de presupuesto
                        actualizarPresupuesto();
                    }
                }
            }
        }
    }
}
