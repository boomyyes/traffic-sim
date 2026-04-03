package com.traffic.model;

import com.traffic.model.TrafficSignal.State;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Controls traffic signal phases — only ONE road gets green at a time.
 * After a green phase expires, the controller picks the next road
 * adaptively based on which approach has the most backed-up traffic.
 *
 * Cycle: GREEN → YELLOW → (switch to next road)
 */
public class SignalPhaseController {

    private final List<TrafficSignal> signals;
    private final double greenDuration; // seconds of green per road
    private final double yellowDuration; // seconds of yellow before switching

    private int activeIndex = 0; // which signal is currently green/yellow
    private double phaseTimer = 0; // time elapsed in current phase
    private boolean inYellow = false; // currently in yellow transition?

    // Function to count queued vehicles on a road (injected from SimulationEngine)
    private Function<Integer, Integer> queueCounter;

    public SignalPhaseController(List<TrafficSignal> signals,
            double greenDuration, double yellowDuration) {
        this.signals = new ArrayList<>(signals);
        this.greenDuration = greenDuration;
        this.yellowDuration = yellowDuration;

        // Initialize: first signal is green, all others red
        for (int i = 0; i < signals.size(); i++) {
            signals.get(i).forceState(i == 0 ? State.GREEN : State.RED);
        }
    }

    /**
     * Set the function used to count queued vehicles on a road.
     * Signature: roadId → number of vehicles queued behind signal.
     */
    public void setQueueCounter(Function<Integer, Integer> counter) {
        this.queueCounter = counter;
    }

    /**
     * Advance the controller by dt seconds.
     */
    public void tick(double dt) {
        if (signals.isEmpty())
            return;

        phaseTimer += dt;

        if (!inYellow) {
            // Currently GREEN
            if (phaseTimer >= greenDuration) {
                // Transition to YELLOW
                inYellow = true;
                phaseTimer = 0;
                signals.get(activeIndex).forceState(State.YELLOW);
            }
        } else {
            // Currently YELLOW
            if (phaseTimer >= yellowDuration) {
                // Yellow expired — switch to next road
                signals.get(activeIndex).forceState(State.RED);

                // Pick next road: adaptive (most queued traffic) or round-robin
                activeIndex = pickNextRoad();

                signals.get(activeIndex).forceState(State.GREEN);
                inYellow = false;
                phaseTimer = 0;
            }
        }
    }

    /**
     * Pick the next road to receive green.
     * Adaptive: choose the road (other than current) with the most backed-up
     * vehicles.
     * Falls back to round-robin if no queue counter is set.
     */
    private int pickNextRoad() {
        if (queueCounter == null || signals.size() <= 1) {
            return (activeIndex + 1) % signals.size();
        }

        int bestIndex = -1;
        int bestCount = -1;

        for (int i = 0; i < signals.size(); i++) {
            if (i == activeIndex)
                continue; // Don't re-pick the same road
            int roadId = signals.get(i).getRoadSegmentId();
            int count = queueCounter.apply(roadId);
            if (count > bestCount) {
                bestCount = count;
                bestIndex = i;
            }
        }

        // If all other roads have 0 vehicles, just go round-robin
        return bestIndex >= 0 ? bestIndex : (activeIndex + 1) % signals.size();
    }

    public int getActiveRoadId() {
        if (signals.isEmpty())
            return -1;
        return signals.get(activeIndex).getRoadSegmentId();
    }

    public boolean isInYellow() {
        return inYellow;
    }
}
