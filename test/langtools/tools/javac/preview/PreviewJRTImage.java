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
 * @summary Verify javac handles preview files in JRT FS reasonably.
 * @library /tools/lib
 * @modules
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.util:+open
 * @run junit/othervm PreviewJRTImage
 * @run junit/othervm --enable-preview PreviewJRTImage
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.Task.Mode;
import toolbox.ToolBox;

import static org.junit.Assert.*;

public class PreviewJRTImage {

    private final ToolBox tb = new ToolBox();
    private final String specificationVersion = System.getProperty("java.specification.version");

    @Test
    public void testVersionInDependency() throws Exception {
        Path root = Paths.get(".");
        Path src = root.resolve("src");

        tb.writeJavaFiles(src,
                """
                import sun.misc.Unsafe;
                public class Test {
                    void test() {
                        Boolean b = true;
                        synchronized (b) {
                        }
                        Unsafe u;
                    }
                }
                """);

        Path classes = root.resolve("classes");
        Files.createDirectories(classes);

        List<String> log;
        List<String> expected;

        for (String option : new String[]{"--source", "--release"}) {
            for (Mode mode : new Mode[]{Mode.API, Mode.CMDLINE}) {
                // Without preview:
                log = new JavacTask(tb, mode)
                        .outdir(classes)
                        .options(option, specificationVersion, "-XDrawDiagnostics")
                        .files(tb.findJavaFiles(src))
                        .run()
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

                expected = List.of(
                        "Test.java:1:16: compiler.warn.sun.proprietary: sun.misc.Unsafe",
                        "Test.java:7:9: compiler.warn.sun.proprietary: sun.misc.Unsafe",
                        "Test.java:5:9: compiler.warn.attempt.to.synchronize.on.instance.of.value.based.class",
                        "3 warnings"
                );

                assertEquals(expected, log);

                // With preview:
                log = new JavacTask(tb, mode)
                        .outdir(classes)
                        .options(option, specificationVersion, "--enable-preview", "-XDrawDiagnostics")
                        .files(tb.findJavaFiles(src))
                        .run(Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

                expected = List.of(
                        "Test.java:1:16: compiler.warn.sun.proprietary: sun.misc.Unsafe",
                        "Test.java:5:9: compiler.err.type.found.req: java.lang.Boolean, (compiler.misc.type.req.identity)",
                        "Test.java:7:9: compiler.warn.sun.proprietary: sun.misc.Unsafe",
                        "1 error",
                        "2 warnings"
                );

                assertEquals(expected, log);
            }
        }
    }
}
