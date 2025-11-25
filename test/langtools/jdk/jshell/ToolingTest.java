/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8306560 8365878
 * @summary Tests for snippets and methods defined in TOOLING.jsh
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @build KullaTesting TestingInputStream
 * @run junit ToolingTest
 */

import org.junit.jupiter.api.Test;

public class ToolingTest extends ReplToolTesting {
    @Test
    public void testListToolingSnippets() {
        test(
                a -> assertCommand(a, "/open TOOLING",
                        ""),
                a -> assertCommandOutputContains(a, "/list",
                        // Tool methods
                        "void jar(String... args)",
                        // ...
                        "void jpackage(String... args)",
                        // Utility methods
                        "void javap(Class<?> type) throws Exception",
                        "void run(String name, String... args)",
                        "void tools()")
        );
    }

    @Test
    public void testDisassembleJavaLangObjectClass() {
        test(
                a -> assertCommand(a, "/open TOOLING",
                        ""),
                a -> assertCommandUserOutputContains(a, "javap(Object.class)",
                        "Classfile jrt:/java.base/java/lang/Object.class",
                        "SourceFile: \"Object.java\"")
        );
    }

    @Test
    public void testDisassembleNewRecordClass() {
        test(
                a -> assertCommand(a, "record Point(int x, int y) {}",
                        "|  created record Point"),
                a -> assertCommand(a, "/open TOOLING",
                        ""),
                a -> assertCommandUserOutputContains(a, "javap(Point.class)",
                        "Classfile ", // Classfile /.../TOOLING-13366652659767559204.class
                        "Point extends java.lang.Record", // public final class REPL.$JShell$11$Point extends java.lang.Record
                        "SourceFile: \"$JShell$" // SourceFile: "$JShell$11.java"
                )
        );
    }

    @Test
    public void testDisassembleBuiltinInnerClass() {
        test(
            a -> assertCommand(a, "/open TOOLING",
                        ""),
            a -> assertCommandUserOutputContains(a, "javap(Base64.Decoder.class)",
                        "Classfile jrt:/java.base/java/util/Base64$Decoder.class",
                        "class java.util.Base64$Decoder",
                        "SourceFile: \"Base64.java\"")
        );
    }

    @Test
    public void testDisassembleAnonymousClass() {
        test(
            a -> assertCommand(a, "Object o() {return new ArrayList<>(){ };}", // must be in a method or it won't be anonymous
                        "|  created method o()"),
            a -> assertCommand(a, "/open TOOLING",
                        ""),
            a -> assertCommandUserOutputContains(a, "javap(o().getClass())",
                        "Classfile ", // Classfile /.../TOOLING-16063368030094702464.class
                        " extends java.util.ArrayList<java.lang.Object>", // class REPL.$JShell$22$1 extends java.util.ArrayList<java.lang.Object>
                        "SourceFile: \"$JShell$" // SourceFile: "$JShell$22.java"
            )
        );
    }
}
