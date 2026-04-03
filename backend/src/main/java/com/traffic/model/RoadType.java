package com.traffic.model;

/**
 * Classifies road segments by their real-world type.
 * This affects driving behavior (e.g., center-road drift only on RURAL).
 */
public enum RoadType {
    /**
     * National/state highway or expressway — high speed, divided, no center drift
     */
    EXPRESSWAY,
    /** Major urban arterial road (e.g., Palm Beach Road) — divided carriageways */
    ARTERIAL,
    /** Normal urban road with marked lanes */
    URBAN,
    /** Rural road with no lane markings — center-road driving applies */
    RURAL,
    /** Narrow residential/colony road — slow speed, center driving applies */
    RESIDENTIAL
}
