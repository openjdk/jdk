/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.sun.tools.javac.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.stream.Collectors.toList;

/**
 * Class for TestWatcher implementation.
 */
public class JavacTemplateDiagnosticPrinter extends JavacTemplateTestBase {

    // After each test method, if the test failed, capture source files and diagnostics and put them in the log
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        suiteErrors.addAll(diags.errorKeys());
        List<Object> list = new ArrayList<>();
        list.add("Test case: " + getTestCaseDescription());
        for (Pair<String, String> e : sourceFiles)
            list.add("Source file " + e.fst + ": " + e.snd);
        if (diags.errorsFound())
            list.add("Compile diagnostics: " + diags.toString());
        System.err.printf(Arrays.toString(list.toArray(new Object[0])));
    }
    // After the suite is done, dump any errors to output
    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (!suiteErrors.isEmpty())
            System.err.println("Errors found in test suite: " + suiteErrors);
    }
}