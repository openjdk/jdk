package com.sun.hotspot.tools.compiler.timeline;

public record Event(int id, String method, int level, long timeCreated, long timeStarted, long timeFinished) {
}
