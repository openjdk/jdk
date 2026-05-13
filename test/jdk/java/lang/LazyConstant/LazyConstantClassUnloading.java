/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary LazyConstant should not retain throwable classes after failed computation
 * @enablePreview
 * @modules java.base/java.lang.ref:open
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @requires vm.opt.final.ClassUnloading
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI
 *      LazyConstantClassUnloading
 */

import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

import java.io.ByteArrayOutputStream;
import java.lang.LazyConstant;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class LazyConstantClassUnloading {

    private static final String THROWABLE_NAME = "test.lazyconstant.GeneratedProblem";
    private static final String SUPPLIER_NAME = "test.lazyconstant.ThrowingSupplier";

    private static WhiteBox wb;

    public static void main(String[] args) throws Exception {
        TestState state = createFailedLazyConstant();

        if (!waitFor(() -> state.loaderRef().refersTo(null), Utils.adjustTimeout(4_000L))) {
            throw new AssertionError("The throwing supplier class loader was not unloaded");
        }

        assertLazyAccessFails(state.lazyConstant(), THROWABLE_NAME);
    }

    private static TestState createFailedLazyConstant() throws Exception {
        Path sourceDir = Files.createTempDirectory("lazy-constant-throwables-src");
        Path classesDir = Files.createTempDirectory("lazy-constant-throwables-classes");

        writeSource(sourceDir, "GeneratedProblem.java", """
                package test.lazyconstant;

                public class GeneratedProblem extends RuntimeException {
                }
                """);
        writeSource(sourceDir, "ThrowingSupplier.java", """
                package test.lazyconstant;

                import java.util.function.Supplier;

                public class ThrowingSupplier implements Supplier<String> {
                    @Override
                    public String get() {
                        throw new GeneratedProblem();
                    }
                }
                """);
        compile(sourceDir, classesDir);

        URLClassLoader loader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() },
                LazyConstantClassUnloading.class.getClassLoader());
        WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);

        Class<?> supplierClass = Class.forName(SUPPLIER_NAME, true, loader);
        @SuppressWarnings("unchecked")
        Supplier<String> supplier =
                (Supplier<String>) supplierClass.getConstructor().newInstance();

        LazyConstant<?> lazyConstant = LazyConstant.of(supplier);
        assertLazyAccessFails(lazyConstant, THROWABLE_NAME);

        supplier = null;
        supplierClass = null;
        loader.close();
        loader = null;

        return new TestState(lazyConstant, loaderRef);
    }

    private static void writeSource(Path sourceDir, String fileName, String source) throws Exception {
        Path file = sourceDir.resolve("test/lazyconstant").resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private static void compile(Path sourceDir, Path classesDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("No system Java compiler available");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, output, output,
                "-d", classesDir.toString(),
                sourceDir.resolve("test/lazyconstant/GeneratedProblem.java").toString(),
                sourceDir.resolve("test/lazyconstant/ThrowingSupplier.java").toString());
        if (exitCode != 0) {
            throw new AssertionError("Compilation failed: " + output);
        }
    }

    private static void assertLazyAccessFails(LazyConstant<?> lazyConstant, String throwableName) {
        var x = assertThrows(NoSuchElementException.class, () -> lazyConstant.get());
        var message = x.getMessage();
        assertTrue(message.contains(throwableName), "Missing throwable name in message: " + message);
    }

    private static boolean waitFor(BooleanSupplier condition, long timeoutMillis) {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        wb = WhiteBox.getWhiteBox();
        wb.fullGC();
        boolean refProResult;
        boolean conditionValue;
        try {
            do {
                refProResult = wb.waitForReferenceProcessing();
                conditionValue = condition.getAsBoolean();
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Timed out waiting for reference");
                }
            } while (refProResult || !conditionValue);
            return conditionValue;
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    static <X> X assertThrows(Class<X> type, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (t.getClass().equals(type)) {
                return (X) t;
            }
            throw new AssertionError("Expected " + type + " to be thrown", t);
        }
        throw new AssertionError("Expected " + type + " to be thrown but nothing was thrown.");
    }

    static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record TestState(LazyConstant<?> lazyConstant, WeakReference<ClassLoader> loaderRef) { }
}
