package com.campus.viral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ViralApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ViralApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ViralApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("""
            
            ========================================================================================
            🦠 SERVIDOR "SIMULACIÓN VIRAL POR CERCANÍA" INICIADO CON ÉXITO
            ========================================================================================
            🟢 ESTADO: EN LÍNEA Y ESCUCHANDO CONEXIONES (HTTP y WebSockets)
            📡 Puerto: 8080
            🌐 Health Check: http://localhost:8080/api/v1/sessions/health
            💾 Consola H2 DB: http://localhost:8080/h2-console
            
            ℹ️ NOTA: El servidor permanece corriendo en segundo plano esperando conexiones de la
               aplicación Android (Virus) y no debe detenerse.
            ========================================================================================
            """);
    }
}
