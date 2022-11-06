package com.sun.tools.javac.util;

public record Help(
        JCDiagnostic.Fragment message,
        List<SuggestedChange> suggestedChanges
) {

    public Help(JCDiagnostic.Fragment message) {
        this(message, List.nil());
    }
}
