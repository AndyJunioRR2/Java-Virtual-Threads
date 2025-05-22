package org.example;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String archivoEntrada = "urls_parcial1.txt";
        String archivoSalida = "resultados.csv";

        try {
            List<String> urls = Files.readAllLines(Paths.get(archivoEntrada));
            List<URLProcessor> procesadores = new ArrayList<>();
            List<Thread> hilos = new ArrayList<>();

            for (String url : urls) {
                URLProcessor processor = new URLProcessor(url);
                Thread hilo = new Thread(processor);
                procesadores.add(processor);
                hilos.add(hilo);
                hilo.start();
            }

            for (Thread hilo : hilos) {
                hilo.join();
            }

            // Imprimir resumen en consola
            System.out.println("\nResumen final:");
            for (URLProcessor processor : procesadores) {
                System.out.println("URL: " + processor.url + " -> Enlaces internos: " + processor.getResultado());
            }

            // Guardar resultados en archivo CSV
            try (PrintWriter writer = new PrintWriter(archivoSalida)) {
                writer.println("URL,CantidadEnlacesInternos");
                for (URLProcessor processor : procesadores) {
                    writer.printf("\"%s\",%d%n", processor.url, processor.getResultado());
                }
            }

            System.out.println("\nArchivo CSV generado: " + archivoSalida);

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}