/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6173575
 * @summary Unit tests for appendToBootstrapClassLoaderSearch and
 *   appendToSystemClassLoaderSearch methods.
 *
 * @library /test/lib
 * @build CircularityErrorTest
 * @run driver jdk.test.lib.util.JavaAgentBuilder CircularityErrorTest CircularityErrorTest.jar
 * @run main/othervm/timeout=240 -javaagent:CircularityErrorTest.jar CircularityErrorTest
 */

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;

public class CircularityErrorTest {

    static Instrumentation ins;

    public static void main(String args[]) throws Exception {
        Path srcDir = Path.of("circsrc");
        Path outDir = Path.of(System.getProperty("test.classes"));
        Files.createDirectories(srcDir);

        // Step 1: B extends A (A is standalone)
        Files.writeString(srcDir.resolve("A.java"), "public interface A { }\n");
        Files.writeString(srcDir.resolve("B.java"), "public interface B extends A { }\n");
        CompilerUtils.compile(srcDir, outDir);

        // Jar A.class and save B.class as B.keep
        JarUtils.createJarFile(Path.of("A.jar"), outDir, Path.of("A.class"));
        Files.delete(outDir.resolve("A.class"));
        Files.move(outDir.resolve("B.class"), outDir.resolve("B.keep"));

        // Step 2: A extends B (B is standalone) - creates circularity
        Files.writeString(srcDir.resolve("A.java"), "public interface A extends B { }\n");
        Files.writeString(srcDir.resolve("B.java"), "public interface B { }\n");

        // Also create Resolver that references A.class literal
        Files.writeString(srcDir.resolve("Resolver.java"),
            "public class Resolver {\n" +
            "    public static Class<?> resolve() {\n" +
            "        return A.class;\n" +
            "    }\n" +
            "}\n");

        CompilerUtils.compile(srcDir, outDir);
        Files.delete(outDir.resolve("B.class"));

        // Step 3: Restore B.keep as B.class -> A extends B AND B extends A
        Files.move(outDir.resolve("B.keep"), outDir.resolve("B.class"));

        // First resolve - should get ClassCircularityError
        try {
            Method m = Class.forName("Resolver").getMethod("resolve");
            Class<?> c = (Class<?>) m.invoke(null);
            throw new RuntimeException("Test failed - class A loaded by: " + c.getClassLoader());
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ClassCircularityError) {
                System.err.println(e.getCause());
            } else {
                throw new RuntimeException("Unexpected exception", e.getCause());
            }
        }

        // Add good A.jar to bootstrap
        ins.appendToBootstrapClassLoaderSearch(new JarFile("A.jar"));

        // Second resolve - should STILL get ClassCircularityError
        // because JVM caches the resolution failure for A.class literal
        try {
            Method m = Class.forName("Resolver").getMethod("resolve");
            Class<?> c = (Class<?>) m.invoke(null);
            throw new RuntimeException("Test failed - class A loaded by: " + c.getClassLoader());
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ClassCircularityError) {
                System.err.println(e.getCause());
            } else {
                throw new RuntimeException("Unexpected exception", e.getCause());
            }
        }
    }

    public static void premain(String args, Instrumentation i) {
        ins = i;
    }
}
