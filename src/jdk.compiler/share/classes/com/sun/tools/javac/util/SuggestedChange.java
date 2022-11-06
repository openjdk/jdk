package com.sun.tools.javac.util;

public record SuggestedChange(
        Object position,
        String replacement,
        Applicability applicability,
        DiagnosticSource source
) {
}
