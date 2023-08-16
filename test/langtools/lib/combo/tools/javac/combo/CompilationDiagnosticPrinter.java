/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package tools.javac.combo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.stream.Collectors.toList;

/**
 * Class for TestWatcher implementation.
 */
public class CompilationDiagnosticPrinter extends JavacTemplateTestBase {
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // Make sure offending template ends up in log file on failure
        System.err.printf("Diagnostics: %s%nTemplate: %s%n", diags.errorKeys(),
                sourceFiles.stream().map(p -> p.snd).collect(toList()));
    }

    // After the suite is done, dump any errors to output
    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (!suiteErrors.isEmpty())
            System.err.println("Errors found in test suite: " + suiteErrors);
    }
}