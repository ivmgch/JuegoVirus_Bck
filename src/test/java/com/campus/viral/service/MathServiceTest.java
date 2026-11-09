package com.campus.viral.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathServiceTest {

    private MathService mathService;

    @BeforeEach
    void setUp() {
        mathService = new MathService(0.04, 0.50, 0.08);
    }

    @Test
    @DisplayName("Debe calcular correctamente la constante 'A' en t=0")
    void testCalculateInitialA() {
        double k = 100.0;
        double p0 = 5.0;

        // A = (100 - 5) / 5 = 19.0
        double a = mathService.calculateInitialA(k, p0);
        assertEquals(19.0, a, 0.001);

        // En t=0, P(0) debe ser exactamente 5.0
        double pAtZero = mathService.solveLogistic(k, a, 0.04, 0.0);
        assertEquals(5.0, pAtZero, 0.001);
    }

    @Test
    @DisplayName("Debe recalcular 'A' de forma suave preservando la continuidad en t > 0")
    void testRecalculateSmoothedA() {
        double currentInfected = 25.0;
        double newK = 150.0; // k aumentó porque entraron más estudiantes
        double r = 0.05;
        double tElapsed = 120.0; // 2 minutos transcurridos

        double smoothedA = mathService.recalculateSmoothedA(newK, currentInfected, r, tElapsed);

        // Al evaluar P(tElapsed) con newK y smoothedA, debe devolver exactamente currentInfected
        double predictedP = mathService.solveLogistic(newK, smoothedA, r, tElapsed);
        assertEquals(currentInfected, predictedP, 0.01);
    }

    @Test
    @DisplayName("Debe incrementar 'r' dinámicamente con la densidad de contactos BLE")
    void testDynamicR() {
        double k = 100.0;

        // Sin contactos BLE reportados: r = r_base = 0.04
        double rBase = mathService.calculateDynamicR(k, 0);
        assertEquals(0.04, rBase, 0.001);

        // Con 50 contactos BLE: r = 0.04 + (50/100)*0.08 = 0.04 + 0.04 = 0.08
        double rMedium = mathService.calculateDynamicR(k, 50);
        assertEquals(0.08, rMedium, 0.001);

        // Con densidad extrema: debe estar acotado en maxGrowthRate = 0.50
        double rExtreme = mathService.calculateDynamicR(k, 10000);
        assertEquals(0.50, rExtreme, 0.001);
    }

    @Test
    @DisplayName("Debe evaluar correctamente el punto de inflexión k / 2")
    void testInflectionPoint() {
        double k = 80.0;

        assertFalse(mathService.isInflectionPointReached(39.0, k), "39 infectados no debe alcanzar 80/2 = 40");
        assertTrue(mathService.isInflectionPointReached(40.0, k), "40 infectados debe alcanzar 80/2 = 40 exactamente");
        assertTrue(mathService.isInflectionPointReached(45.0, k), "45 infectados debe superar 80/2 = 40");
    }
}
