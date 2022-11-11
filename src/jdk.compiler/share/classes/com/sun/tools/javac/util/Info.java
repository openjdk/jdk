package com.sun.tools.javac.util;

import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

public record Info(
        String message,
        Object sourceFile,
        List<DiagnosticPosition> positions
) {
}
