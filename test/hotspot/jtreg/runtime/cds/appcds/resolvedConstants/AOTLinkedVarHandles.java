/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary AOT resolution of VarHandle invocation
 * @bug 8343245 8398128
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build AOTLinkedVarHandles
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTLinkedVarHandlesApp AOTLinkedVarHandlesApp$Data
 * @run driver AOTLinkedVarHandles
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

public class AOTLinkedVarHandles {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTLinkedVarHandlesApp.class.getName();

    private static final List<String> methodNames = List.of("plainArray", "arrayVarHandle", "segmentGetNative");
    private static final List<String> codes = List.of("\\(A4\\)", "\\(AP4\\)");

    public static void main(String[] args) throws Exception {
        SimpleCDSAppTester t = SimpleCDSAppTester.of("AOTLinkedVarHandles")
            .classpath(appJar)
            .appCommandLine(mainClass)
            .addVmArgs("-esa",
                "-XX:+AOTClassLinking",
                "-Xlog:aot+resolve=trace",
                "-Xlog:aot+codecache+nmethod=info",
                "-Xlog:cds+class=debug")
            .setTrainingChecker((OutputAnalyzer output) -> {
                output.shouldContain("Hello AOTLinkedVarHandlesApp");
            })
            .setAssemblyChecker((OutputAnalyzer output) -> {
                String s = "archived method CP entry.* AOTLinkedVarHandlesApp ";
                output.shouldMatch(s + "java/lang/invoke/VarHandle.compareAndExchangeAcquire:\\(\\[DIDI\\)D =>");
                output.shouldMatch(s + "java/lang/invoke/VarHandle.get:\\(\\[DI\\)D => ");
                output.shouldNotContain("rejected .* CP entry.*");
                methodNames.forEach(m ->
                    output.shouldNotMatch("'AOTLinkedVarHandlesApp\\.%s\\(.*store to AOT Code Cache failed".formatted(m))
                );
                methodNames.forEach(m -> codes.forEach(c ->
                    output.shouldMatch("%s 'AOTLinkedVarHandlesApp\\.%s\\(.*wrote to AOT Code Cache".formatted(c, m))
                ));

            })
            .runAOTTrainingAndAssemblyWorkflow();

        t.setVmArgs("-Xlog:cds,aot", "-esa")
            .setProductionChecker((OutputAnalyzer output) -> {
                output.shouldContain("Hello AOTLinkedVarHandlesApp");
            })
            .productionRun();
    }

}

class AOTLinkedVarHandlesApp {
    static final VarHandle initialized;
    static final VarHandle lazy;
    static final int N = 1024;
    static final float[] ARRAY = new float[N];
    static final MemorySegment NATIVE = Arena.global().allocate(4L * N, 4);
    static final VarHandle ARRAY_VH = MethodHandles.arrayElementVarHandle(float[].class);
    static long longField = 5678;
    static long seed;

    static {
        try {
            lazy = MethodHandles.lookup().findStaticVarHandle(Data.class, "longField", long.class);
            initialized = MethodHandles.lookup().findStaticVarHandle(AOTLinkedVarHandlesApp.class, "longField", long.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String args[]) {

        seed = 1234;
        System.out.println("Hello AOTLinkedVarHandlesApp");

        double total = 0;
        for (int r = 0; r < 30_000; r++) total += plainArray() + arrayVarHandle() + segmentGetNative();
        System.out.println(total);

        long a = (long) lazy.get();
        long b = (long) initialized.get();
        System.out.println(a);
        System.out.println(b);
        if (a != 1234) {
            throw new RuntimeException("Data class should not be initialized: " + a);
        }
        if (b != 5678) {
            throw new RuntimeException("VarHandle.get() failed: " + b);
        }

        VarHandle vh = MethodHandles.arrayElementVarHandle(double[].class);
        double[] array = new double[]{1.0};
        int index = 0;
        int v = 4;

        // JDK-8343245 -- this generates "java.lang.invoke.LambdaForm$VH/0x80????" hidden class
        double r = (double) vh.compareAndExchangeAcquire(array, index, 1.0, v);
        if (r != 1.0) {
            throw new RuntimeException("Unexpected result: " + r);
        }
        r = (double) vh.get(array, index);
        if (r != 4.0) {
            throw new RuntimeException("Unexpected result: " + r);
        }
    }

    static float plainArray() {
        float[] a = ARRAY;
        float acc = 0f;
        for (int i = 0; i < N; i++) acc += a[i];
        return acc;
    }

    static float arrayVarHandle() {
        float[] a = ARRAY;
        float acc = 0f;
        for (int i = 0; i < N; i++) acc += (float) ARRAY_VH.get(a, i); // explicit VarHandle: not stored
        return acc;
    }

    static float segmentGetNative() {
        MemorySegment s = NATIVE;
        float acc = 0f;
        for (int i = 0; i < N; i++) acc += s.get(JAVA_FLOAT, 4L * i); // VarHandle inside get(): not stored
        return acc;
    }

    static class Data {
        static long longField = seed;
    }
}
