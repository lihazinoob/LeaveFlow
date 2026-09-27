package com.misl.leaveflow.dashboard.dto;

/**
 * One bar/slice of a chart: a label (leave type, department, month...), an optional colour and a total.
 * The total is a Long because SQL SUM() returns a Long.
 */
public record LabelTotal(String label, String color, Long total) {

    /** For charts without per-item colours (e.g. departments). */
    public LabelTotal(String label, Long total) {
        this(label, null, total);
    }
}
