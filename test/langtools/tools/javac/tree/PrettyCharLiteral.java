/*
 * Copyright (c) 2024, Google LLC. All rights reserved.
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

/*
 * @test
 * @bug 8340568
 * @summary Incorrect escaping of single quotes when pretty-printing character literals
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 */

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;

import java.io.IOException;
import java.io.StringWriter;

public class PrettyCharLiteral {
    public static void main(String... args) throws Exception {
        new PrettyCharLiteral().run();
    }

    private final TreeMaker make;

    private PrettyCharLiteral() {
        Context ctx = new Context();
        JavacFileManager.preRegister(ctx);
        this.make = TreeMaker.instance(ctx);
    }

    void run() throws Exception {
        assertEquals(
                prettyPrintLiteral('\''),
                """
                '\\''
                """.trim());
        assertEquals(
                prettyPrintLiteral('"'),
                """
                '"'
                """.trim());
        assertEquals(
                prettyPrintLiteral("'"),
                """
                "'"
                """.trim());
        assertEquals(
                prettyPrintLiteral("\""),
                """
                "\\""
                """.trim());
    }

    private void assertEquals(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new AssertionError("expected: " + expected + ", actual: " + actual);
        }
    }

    private String prettyPrintLiteral(Object value) throws IOException {
        StringWriter sw = new StringWriter();
        new Pretty(sw, true).printExpr(make.Literal(value));
        return sw.toString();
    }
}
