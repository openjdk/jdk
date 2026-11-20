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
 * @bug 8329260
 * @summary Inlining a known-null unloaded argument must preserve nested call resolution errors.
 * @requires vm.compiler2.enabled & vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver compiler.c2.unloaded.TestInlineUnloadedLoaderConstraints
 */

package compiler.c2.unloaded;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

import jdk.test.lib.Asserts;
import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestInlineUnloadedLoaderConstraints {
    private static final String PREFIX = TestInlineUnloadedLoaderConstraints.class.getName() + "$";

    public static void main(String[] args) throws Exception {
        new OutputAnalyzer(ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xbootclasspath/a:.", "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                "-XX:-TieredCompilation", "-Xbatch", "-XX:+PrintInlining",
                "-XX:CompileCommand=quiet",
                "-XX:CompileCommand=compileonly," + PREFIX + "Caller::getAsInt",
                "-XX:CompileCommand=inline," + PREFIX + "Middle::gated",
                Runner.class.getName()).start())
                .shouldHaveExitValue(0)
                .shouldMatch("Middle::gated \\(\\d+ bytes\\)\\h+force inline by CompileCommand");
    }

    private static ByteCodeLoader loader(ClassLoader parent, String... names) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        for (String name : names) {
            String className = PREFIX + name;
            try (var in = TestInlineUnloadedLoaderConstraints.class.getResourceAsStream(
                    "/" + className.replace('.', '/') + ".class")) {
                Asserts.assertNotNull(in, "Missing class bytes for " + className);
                classes.put(className, in.readAllBytes());
            }
        }
        return new ByteCodeLoader(classes, parent);
    }

    public static class Runner {
        public static void main(String[] args) throws Exception {
            WhiteBox wb = WhiteBox.getWhiteBox();
            ClassLoader target = loader(ClassLoader.getPlatformClassLoader(), "Target", "V");
            ClassLoader middle = loader(target, "Middle", "Caller", "U", "V");
            Class.forName(PREFIX + "Target", true, target);
            Class.forName(PREFIX + "Target", true, middle);
            Class<?> holder = Class.forName(PREFIX + "Middle", true, middle);
            Class<?> caller = middle.loadClass(PREFIX + "Caller");
            IntSupplier action = (IntSupplier) caller.getConstructor().newInstance();

            // The nested call has incompatible signature types, but gated's U stays unloaded.
            Asserts.assertTrue(target.loadClass(PREFIX + "V") != middle.loadClass(PREFIX + "V"));
            Asserts.assertFalse(wb.isClassAlive(PREFIX + "U"), "U loaded during setup");

            var method = caller.getDeclaredMethod("getAsInt");
            Asserts.assertTrue(wb.enqueueMethodForCompilation(method, 4), "Compilation not queued");
            Asserts.assertEQ(wb.getMethodCompilationLevel(method), 4, "Caller not C2 compiled");
            Asserts.assertFalse(wb.isClassAlive(PREFIX + "U"), "C2 loaded U");
            Asserts.assertEQ(action.getAsInt(), 7, "Untaken branch must not throw a resolution error");

            holder.getField("enabled").setBoolean(null, true);
            try {
                action.getAsInt();
                throw new AssertionError("Conflicting nested call was accepted");
            } catch (LinkageError expected) {
                Asserts.assertTrue(expected.getMessage().contains("loader constraint violation"),
                                   "Unexpected linkage error: " + expected);
            }
        }
    }

    public interface U {}
    public static class V {}

    public static class Target {
        public static int accept(V value) { return 9; }
    }

    public static class Middle {
        public static volatile boolean enabled;

        public static int gated(U value) {
            return enabled ? Target.accept(null) : 7;
        }
    }

    public static class Caller implements IntSupplier {
        public int getAsInt() { return Middle.gated(null); }
    }
}
