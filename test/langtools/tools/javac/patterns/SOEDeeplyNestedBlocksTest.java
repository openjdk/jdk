/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8322992 8331030
 * @summary Javac fails with StackOverflowError when compiling deeply nested synchronized blocks
 * @run main SOEDeeplyNestedBlocksTest
 */

import java.net.*;
import java.util.*;
import javax.tools.*;

public class SOEDeeplyNestedBlocksTest {

    static final int NESTING_DEPTH = 1000;

    public static void main(String... args) {
        var lines = new ArrayList<String>();
        lines.add("class Test {");
        lines.add("  static { ");
        for (int i = 0; i < NESTING_DEPTH; i++) lines.add("    synchronized (Test.class) {");
        for (int i = 0; i < NESTING_DEPTH; i++) lines.add("    }");
        lines.add("  }");
        lines.add("}");

        var source = SimpleJavaFileObject.forSource(URI.create("mem://Test.java"), String.join("\n", lines));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var task = compiler.getTask(null, null, noErrors, null, null, List.of(source));
        task.call();
    }

    static DiagnosticListener<? super JavaFileObject> noErrors = d -> {
        System.out.println(d);
        if (d.getKind() == Diagnostic.Kind.ERROR) {
            throw new AssertionError(d.getMessage(null));
        }
    };
}
