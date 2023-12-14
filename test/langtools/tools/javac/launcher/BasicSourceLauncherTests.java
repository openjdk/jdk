/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * @test
 * @enablePreview
 * @bug 8304400
 * @summary Test basic features of javac's source-code launcher
 * @modules jdk.compiler/com.sun.tools.javac.launcher
 * @run junit BasicSourceLauncherTests
 */
class BasicSourceLauncherTests {
    @Test
    void launchHelloClassInHelloJavaUnit(@TempDir Path base) throws Exception {
        var hello = Files.writeString(base.resolve("Hello.java"),
                """
                public class Hello {
                  public static void main(String... args) {
                    System.out.println("Hi");
                  }
                }
                """);

        var run = Run.of(hello);
        var result = run.result();
        assertAll("# " + run,
                () -> assertLinesMatch(
                        """
                        Hi
                        """.lines(), run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()),
                () -> assertEquals(Set.of("Hello"), result.classNames()),
                () -> assertNotNull(result.programClass().getResource("Hello.java")),
                () -> assertNotNull(result.programClass().getResource("Hello.class")));
    }

    @Test
    void launchHelloClassInHalloJavaUnit(@TempDir Path base) throws Exception {
        var hallo = Files.writeString(base.resolve("Hallo.java"),
                """
                public class Hello {
                  public static void main(String... args) {
                    System.out.println("Hi!");
                  }
                }
                """);

        var run = Run.of(hallo);
        var result = run.result();
        assertAll("# " + run,
                () -> assertLinesMatch(
                        """
                        Hi!
                        """.lines(), run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()),
                () -> assertEquals(Set.of("Hello"), result.classNames()),
                () -> assertNotNull(result.programClass().getResource("Hallo.java")),
                () -> assertNotNull(result.programClass().getResource("Hello.class")));
    }

    @Test
    void launchMinifiedJavaProgram(@TempDir Path base) throws Exception {
        var hi = Files.writeString(base.resolve("Hi.java"),
                """
                void main() {
                  System.out.println("Hi!");
                }
                """);

        // Replace with plain Run.of(hi) once implict classes are out of preview
        System.setProperty("jdk.internal.javac.source", String.valueOf(Runtime.version().feature()));
        var run = Run.of(hi, List.of("--enable-preview"), List.of());
        System.clearProperty("jdk.internal.javac.source");

        assertAll("# " + run,
                () -> assertLinesMatch(
                        """
                        Hi!
                        """.lines(), run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()));
    }
}
