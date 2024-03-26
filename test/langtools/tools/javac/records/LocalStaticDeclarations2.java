/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282714
 * @summary synthetic arguments are being added to the constructors of static local classes
 * @library /lib/combo /tools/lib /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 * @run junit/othervm LocalStaticDeclarations2
 */

import org.junit.jupiter.api.Test;
import tools.javac.combo.CompilationTestCase;

class LocalStaticDeclarations2 extends CompilationTestCase {
    @Test
    void testLocalStatic() {
        assertOK(
                """
                class Test {
                    class Inner {
                        Inner() { enum E { A } }
                    }
                }
                """);
        assertOK(
                """
                class Test {
                    class Inner {
                        Inner() {
                            record R(Object o) {
                                static R create() { return new R("hi"); }
                            }
                        }
                    }
                }
                """);
        assertOK(
                """
                class Test {
                    class Inner {
                        Inner() {
                            record R(Object o) {
                                static R create(Object obj) { return new R(obj); }
                            }
                        }
                    }
                }
                """);
    }
}
