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

    @Test
    @DisplayName("Debe verificar la solución analítica de Verhulst para una partida típica de 3 jugadores")
    void testThreePlayersCampusGameEquation() {
        double k = 3.0;
        double p0 = 1.0;
        double r = 0.04;

        // 1. Constante de integración en t=0: A = (3 - 1) / 1 = 2.0
        double a = mathService.calculateInitialA(k, p0);
        assertEquals(2.0, a, 0.0001);

        // 2. Condición inicial: P(0) == 1.0
        double pAt0 = mathService.solveLogistic(k, a, r, 0.0);
        assertEquals(1.0, pAt0, 0.0001);

        // 3. Punto de inflexión analítico: d²P/dt² = 0 <=> P(t) = k / 2 = 1.5
        // En la solución P(t) = k / (A*e^(-rt) + 1) = k/2  <=>  A*e^(-rt) = 1  <=>  t = ln(A) / r
        double tInflection = Math.log(a) / r; // ln(2.0) / 0.04 = 17.32867 s
        double pAtInflection = mathService.solveLogistic(k, a, r, tInflection);
        assertEquals(1.5, pAtInflection, 0.0001, "En t_inflexion, P(t) debe ser exactamente k/2 = 1.5");
        assertTrue(mathService.isInflectionPointReached(pAtInflection, k));

        // 4. Crecimiento monótono continuo: P(t1) < P(t2)
        double pAt30 = mathService.solveLogistic(k, a, r, 30.0);
        double pAt60 = mathService.solveLogistic(k, a, r, 60.0);
        double pAt120 = mathService.solveLogistic(k, a, r, 120.0);

        assertTrue(pAt0 < pAtInflection, "P(0) debe ser menor a P(t_inflexion)");
        assertTrue(pAtInflection < pAt30, "P(t_inflexion) debe ser menor a P(30s)");
        assertTrue(pAt30 < pAt60, "P(30s) debe ser menor a P(60s)");
        assertTrue(pAt60 < pAt120, "P(60s) debe ser menor a P(120s)");

        // 5. Asíntota poblacional: P(t) tiende asintóticamente a k=3.0 sin sobrepasarlo
        assertTrue(pAt120 < k, "P(120s) debe acercarse a k pero sin sobrepasarlo");
        double pAtFarFuture = mathService.solveLogistic(k, a, r, 1000.0);
        assertEquals(3.0, pAtFarFuture, 0.0001, "Para t grande, P(t) converge asintóticamente a k=3.0");
    }
}
