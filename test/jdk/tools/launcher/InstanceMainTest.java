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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @test
 * @summary test execution priority of main methods
 * @run main InstanceMainTest
 */
public class InstanceMainTest extends TestHelper {

    private static final String[] SOURCES = new String[] {
            // static dominating with args
            """
            class MainClass {
                static void main() {
                    throw new AssertionError();
                }
                static void main(String[] args) {
                }
            }
            """,

            // instance dominating static
            """
            class MainClass {
                void main(String[] args) {
                }
                static void main() {
                    throw new AssertionError();
                }
            }
            """,

            // instance dominating with args
            """
            class MainClass {
                void main() {
                    throw new AssertionError();
                }
                void main(String[] args) {
                }
            }
            """,

            // instance no args
            """
            class MainClass {
                void main() {
                }
            }
            """,

            // unnamed class static dominating with args
            """
            static void main() {
                throw new AssertionError();
            }
            static void main(String[] args) {
            }
            """,

            // main with args dominating main without args
            """
            void main(String[] args) {
            }
            static void main() {
                throw new AssertionError();
            }
            """,

            // unnamed class instance dominating with args
            """
            void main() {
                throw new AssertionError();
            }
            void main(String[] args) {
            }
            """,

            // unnamed class instance main no args
            """
            void main() {
            }
            """,

            // instance main with args dominating super
            """
            class MainClass extends SuperClass {
                void main() {
                    throw new AssertionError();
                }
            }
            class SuperClass {
                void main(String[] args) {
                }
            }
            """,

            // super instance main with args dominating
            """
            public class MainClass extends Super {
            }

            class Super {
                public void main(String... args) {
                }

                public void main() {
                    throw new AssertionError();
                }
            }
            """,

            // ignore super instance main
            """
            public class MainClass extends Super {
                public static void main(String... args) {
                }
            }

            class Super {
                public static void main(String... args) {
                    throw new AssertionError();
                }
            }
            """,

            // enum main
            """
            enum MainClass {
                A;

                public static void main() {
                }
            }
            """,

            // record main
            """
            record MainClass() {
                 static void main() {
                     System.out.println("Done!");
                 }
            }
            """,
            // interface main
            """
            interface MainClass {
                 static void main() {
                     System.out.println("Done!");
                 }
            }
            """
    };

    public static void main(String... args) throws Exception {
        for (String source : SOURCES) {
            Files.writeString(Path.of("MainClass.java"), source);
            var version = System.getProperty("java.specification.version");
            var tr = doExec(javaCmd, "--enable-preview", "--source", version, "MainClass.java");
            if (!tr.isOK()) {
                System.err.println(source);
                System.err.println(tr);
                throw new AssertionError();
            }
        }
    }
}
