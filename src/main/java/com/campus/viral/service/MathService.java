package com.campus.viral.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MathService {

    private static final Logger log = LoggerFactory.getLogger(MathService.class);

    private final double baseGrowthRate;
    private final double maxGrowthRate;
    private final double densityCoefficient;

    public MathService(
        @Value("${app.simulation.base-growth-rate:0.04}") double baseGrowthRate,
        @Value("${app.simulation.max-growth-rate:0.50}") double maxGrowthRate,
        @Value("${app.simulation.density-coefficient:0.08}") double densityCoefficient
    ) {
        this.baseGrowthRate = baseGrowthRate;
        this.maxGrowthRate = maxGrowthRate;
        this.densityCoefficient = densityCoefficient;
    }

    /**
     * Calcula la constante de integración 'A' en t=0 evaluando P(0) y k.
     * P(0) = k / (A + 1)  =>  A = (k - P(0)) / P(0)
     */
    public double calculateInitialA(double k, double p0) {
        if (p0 <= 0.0) {
            throw new IllegalArgumentException("La condición inicial P(0) debe ser estrictamente positiva.");
        }
        if (k <= p0) {
            // Guarda para evitar asíntotas nulas o negativas si la población activa es muy baja
            return 0.01;
        }
        return (k - p0) / p0;
    }

    /**
     * Recalcula la constante 'A' de forma suave si 'k' cambia bruscamente en t > 0.
     * Mantiene la continuidad de la curva en el canvas de Vico:
     * A_new = ((k_safe - I) / I) * exp(r * t)
     */
    public double recalculateSmoothedA(double newK, double currentInfected, double r, double tElapsedSeconds) {
        double safeInfected = Math.max(currentInfected, 1.0);
        // Si usuarios salen abruptamente y k cae por debajo de los infectados actuales:
        double safeK = Math.max(newK, safeInfected + 1.0);

        double factor = (safeK - safeInfected) / safeInfected;
        double adjustedA = factor * Math.exp(r * tElapsedSeconds);

        return Math.max(adjustedA, 0.001);
    }

    /**
     * Calcula dinámicamente la tasa de crecimiento 'r' basada en la densidad Bluetooth reportada.
     */
    public double calculateDynamicR(double k, long bleInteractionsCount) {
        if (k <= 0.0) {
            return baseGrowthRate;
        }
        double densityRatio = (double) bleInteractionsCount / k;
        double dynamicR = baseGrowthRate + (densityRatio * densityCoefficient);

        return Math.clamp(dynamicR, baseGrowthRate, maxGrowthRate);
    }

    /**
     * Resuelve el modelo logístico en el instante t:
     * P(t) = k / (A * e^(-rt) + 1)
     */
    public double solveLogistic(double k, double a, double r, double t) {
        double denominator = (a * Math.exp(-r * t)) + 1.0;
        return k / denominator;
    }

    /**
     * Evalúa si la población infectada alcanzó o superó el punto de inflexión (k / 2),
     * donde la velocidad de propagación es máxima (d²P/dt² = 0).
     */
    public boolean isInflectionPointReached(double infectedCount, double k) {
        if (k <= 0.0) {
            return false;
        }
        return infectedCount >= (k / 2.0);
    }
}
