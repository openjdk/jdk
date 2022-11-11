package com.sun.tools.javac.util;

import com.sun.tools.javac.util.JCDiagnostic.Fragment;

public record Help(
        Fragment message,
        List<SuggestedChange> suggestedChanges
) {

    public Help(final Fragment message) {
        this(message, List.nil());
    }

    public Help(final Fragment message, final List<SuggestedChange> suggestedChanges) {
        this.message = message;
        this.suggestedChanges = suggestedChanges;
    }
}
