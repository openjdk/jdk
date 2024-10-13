/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003881
 * @library /test/lib/
 * @modules jdk.compiler
 *          jdk.zipfs
 * @compile LambdaAccessControlDoPrivilegedTest.java
 * @run main/othervm -Djava.security.manager=allow LambdaAccessControlDoPrivilegedTest
 * @summary tests DoPrivileged action (implemented as lambda expressions) by
 * inserting them into the BootClassPath.
 */
import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;

import static jdk.test.lib.process.ProcessTools.*;

public class LambdaAccessControlDoPrivilegedTest {
    public static void main(String... args) throws Exception {
        final List<String> scratch = new ArrayList();
        scratch.clear();
        scratch.add("import java.security.*;");
        scratch.add("public class DoPriv {");
        scratch.add("public static void main(String... args) {");
        scratch.add("String prop = AccessController.doPrivileged((PrivilegedAction<String>) () -> {");
        scratch.add("return System.getProperty(\"user.home\");");
        scratch.add("});");
        scratch.add("}");
        scratch.add("}");
        Path doprivJava = Path.of("DoPriv.java");
        Path doprivClass = Path.of("DoPriv.class");
        Files.write(doprivJava, scratch, Charset.defaultCharset());

        scratch.clear();
        scratch.add("public class Bar {");
        scratch.add("public static void main(String... args) {");
        scratch.add("System.setSecurityManager(new SecurityManager());");
        scratch.add("DoPriv.main();");
        scratch.add("}");
        scratch.add("}");

        Path barJava = Path.of("Bar.java");
        Path barClass = Path.of("Bar.class");
        Files.write(barJava, scratch, Charset.defaultCharset());

        compile(barJava.toString(), doprivJava.toString());

        jar("cvf", "foo.jar", doprivClass.toString());
        Files.delete(doprivJava);
        Files.delete(doprivClass);

        ProcessBuilder pb = createTestJavaProcessBuilder(
                                "-Xbootclasspath/a:foo.jar",
                                "-cp", ".",
                                "-Djava.security.manager=allow",
                                "Bar");
        executeProcess(pb).shouldHaveExitValue(0);

        Files.delete(barJava);
        Files.delete(barClass);
        Files.delete(Path.of("foo.jar"));
    }

    static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar").orElseThrow();
    static final ToolProvider JAVAC = ToolProvider.findFirst("javac").orElseThrow();
    static void compile(String... args) throws IOException {
        if (JAVAC.run(System.out, System.err, args) != 0) {
            throw new RuntimeException("compilation fails");
        }
    }

    static void jar(String... args) {
        int rc = JAR_TOOL.run(System.out, System.err, args);
        if (rc != 0){
            throw new RuntimeException("fail to create JAR file");
        }
    }
}
