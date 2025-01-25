package jdk.jpackage.internal.pipeline;

import java.util.Objects;
import java.util.Set;

public final class CycleException extends Exception {

    CycleException(Set<?> cycle) {
        Objects.requireNonNull(cycle);
        if (cycle.isEmpty()) {
            throw new IllegalArgumentException("Empty cyclic nodes");
        }

        this.cycle = cycle;
    }

    Set<?> getCycleNodes() {
        return cycle;
    }

    private final Set<?> cycle;

    private static final long serialVersionUID = 1L;
}
