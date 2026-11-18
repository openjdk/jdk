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
 * @summary Excluded training data must not remain reachable through dependencies or CI records
 * @requires vm.cds.supports.aot.class.linking & vm.compiler2.enabled & vm.jvmti
 * @library /test/lib
 * @run driver RedefineClassHelper
 * @build TrainingDataCleanup jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             TrainingDataCleanupApp TrainingDataCleanupTarget
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar custom.jar TrainingDataCleanupCustom
 * @run driver TrainingDataCleanup
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class TrainingDataCleanup {
    public static void main(String[] args) throws Exception {
        SimpleCDSAppTester.of("TrainingDataCleanup")
            .addVmArgs("-javaagent:redefineagent.jar",
                       "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                       "-Xbootclasspath/a:WhiteBox.jar", "-Xbatch", "-XX:-TieredCompilation",
                       "-XX:CompileCommand=compileonly,TrainingDataCleanup*::callee",
                       "-XX:CompileCommand=compileonly,TrainingDataCleanupApp::*Caller",
                       "-Xlog:aot+training=debug",
                       "-Xlog:aot+map=debug:file=training-data-cleanup.map:tags:filesize=0")
            .classpath("app.jar")
            .appCommandLine("TrainingDataCleanupApp")
            .setTrainingChecker(out -> {
                out.shouldContain("Cleanup KTD TrainingDataCleanupTarget");
                out.shouldContain("Cleanup KTD TrainingDataCleanupCustom");
                checkArchive();
            })
            .setAssemblyChecker(out -> checkArchive())
            .setProductionChecker(out -> out.shouldContain("Training data cleanup test passed"))
            .runAOTWorkflow();
    }

    private static void checkArchive() {
        String entry = ".*@@\\s+(Klass|Method|Compile)TrainingData\\s+\\d+";
        String trainingData;
        try {
            // Heap-string details can contain non-UTF-8 bytes; these checks use ASCII entries.
            trainingData = Files.readString(Path.of("training-data-cleanup.map"), StandardCharsets.ISO_8859_1)
                                .lines()
                                .filter(line -> line.contains("TrainingData"))
                                .filter(line -> line.matches(entry + ".*"))
                                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        OutputAnalyzer out = new OutputAnalyzer(trainingData, "");
        for (String kind : new String[] { "Klass", "Method", "Compile" }) {
            out.shouldMatch(".*@@\\s+" + kind + "TrainingData\\s+\\d+.*TrainingDataCleanupApp");
        }
        // A holderless entry has no class or method name after its size.
        out.shouldNotMatch(entry + "\\s*$");
        out.shouldNotMatch(entry + ".*TrainingDataCleanup(Target|Custom)");
    }
}

class TrainingDataCleanupApp {
    static final MethodHandle CUSTOM = customTarget();

    static MethodHandle customTarget() {
        try {
            URL jar = new File("custom.jar").toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[] { jar }, null);
            Class<?> target = Class.forName("TrainingDataCleanupCustom", true, loader);
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup())
                                .findStatic(target, "callee", MethodType.methodType(int.class, int.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int redefinedCaller(int x) {
        return TrainingDataCleanupTarget.callee(x);
    }

    static int customCaller(int x) throws Throwable {
        return (int) CUSTOM.invokeExact(x);
    }

    static void compile(Class<?> holder, String name) throws Exception {
        Method method = holder.getDeclaredMethod(name, int.class);
        WhiteBox wb = WhiteBox.getWhiteBox();
        if (!wb.enqueueMethodForCompilation(method, 4) || wb.getMethodCompilationLevel(method) != 4) {
            throw new RuntimeException("C2 did not compile " + method);
        }
    }

    public static void main(String[] args) throws Throwable {
        if (redefinedCaller(1) != 2 || customCaller(1) != 2) {
            throw new RuntimeException("Unexpected result");
        }
        // Compile the callee first so that compiling its caller records a CI
        // query about the callee's inline instruction size.
        compile(TrainingDataCleanupTarget.class, "callee");
        compile(TrainingDataCleanupApp.class, "redefinedCaller");
        compile(MethodHandles.reflectAs(Method.class, CUSTOM).getDeclaringClass(), "callee");
        compile(TrainingDataCleanupApp.class, "customCaller");

        // Redefinition invalidates training data while dependency lists and CI
        // records are still backed by growable arrays.
        try (InputStream in = TrainingDataCleanupTarget.class.getResourceAsStream(
                 "TrainingDataCleanupTarget.class")) {
            RedefineClassHelper.redefineClass(TrainingDataCleanupTarget.class, in.readAllBytes());
        }

        // The custom target is excluded at dump time, before dependency lists
        // and CI records are materialized into archiveable arrays.
        if (redefinedCaller(2) != 3 || customCaller(2) != 3) {
            throw new RuntimeException("Unexpected result after redefinition");
        }
        System.out.println("Training data cleanup test passed");
    }
}

class TrainingDataCleanupTarget {
    static int callee(int x) {
        return Math.abs(x) + 1;
    }
}

class TrainingDataCleanupCustom {
    public static int callee(int x) {
        return Math.abs(x) + 1;
    }
}
