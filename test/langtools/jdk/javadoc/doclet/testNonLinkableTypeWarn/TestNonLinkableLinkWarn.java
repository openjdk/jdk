/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8139744 8356549
 * @summary Verify warning emitted for non-linkable {@link} targets
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestNonLinkableLinkWarn
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestNonLinkableLinkWarn extends JavadocTester {

    public static void main(String... args) throws Exception {
        new TestNonLinkableLinkWarn().runTests();
    }

    private final ToolBox tb = new ToolBox();
    private final Path src = Path.of("src");

    public TestNonLinkableLinkWarn() throws IOException {
        tb.writeJavaFiles(src, """
            package p;
            public class A {
                /**
                 * This should warn, because {@link #privateField} is private.
                 * This should warn, because {@link #protectedField} is protected.
                 */
                public void foo() { }

                private int privateField;
                protected int protectedField;
            }
        """);
    }

    @Test
    public void testWarnPrivateField(Path base) {
        javadoc(
                "-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "p"
        );
        checkExit(Exit.OK);

        checkOutput(
                Output.OUT, true, """
                        warning: the specified link will not be shown because the referenced element has access-level "private" (see -private)
                                 * This should warn, because {@link #privateField} is private.
                                                             ^"""
        );
    }

    @Test
    public void testWarnProtectedField(Path base) {
        javadoc(
                "-d", base.resolve("out").toString(),
                "-public",
                "-sourcepath", src.toString(),
                "p"
        );
        checkExit(Exit.OK);

        checkOutput(
                Output.OUT,
                true, """
                        warning: the specified link will not be shown because the referenced element has access-level "private" (see -private)
                                 * This should warn, because {@link #privateField} is private.
                                                             ^""", """
                        warning: the specified link will not be shown because the referenced element has access-level "protected" (see -protected)
                                 * This should warn, because {@link #protectedField} is protected.
                                                             ^"""
        );
    }
}
