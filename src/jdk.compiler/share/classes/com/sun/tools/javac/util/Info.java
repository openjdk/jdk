package com.sun.tools.javac.util;

public record Info(
        String message,
        Object sourceFile,
        List<JCDiagnostic.DiagnosticPosition> positions
) {
}
