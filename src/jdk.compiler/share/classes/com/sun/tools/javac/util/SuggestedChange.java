package com.sun.tools.javac.util;

import com.sun.tools.javac.util.JCDiagnostic.RangeDiagnosticPosition;

public record SuggestedChange(
        DiagnosticSource source,
        RangeDiagnosticPosition position,
        String replacement,
        Applicability applicability
) {
    public SuggestedChange {
        // TODO(fancy-diags)
        if (source == null) {
            throw new IllegalArgumentException();
        }
        if (position.getPreferredPosition() == Position.NOPOS) {
            throw new IllegalArgumentException();
        }
        if (replacement == null || replacement.isBlank()) {
            throw new IllegalArgumentException();
        }
    }
}
