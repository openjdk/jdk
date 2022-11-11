package com.sun.tools.javac.util;

import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.Fragment;

public record Info(
        Fragment message,
        DiagnosticSource sourceFile,
        List<DiagnosticPosition> positions
) {
}
