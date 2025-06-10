/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8343245
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build AOTLinkedVarHandles
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTLinkedVarHandlesApp AOTLinkedVarHandlesApp$Data
 * @run driver AOTLinkedVarHandles
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTLinkedVarHandles {
    static final String classList = "AOTLinkedVarHandles.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTLinkedVarHandlesApp.class.getName();

    public static void main(String[] args) throws Exception {
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Hello AOTLinkedVarHandlesApp");
            });

        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-XX:+AOTClassLinking",
                       "-Xlog:aot+resolve=trace",
                       "-Xlog:cds+class=debug",
                       "-cp", appJar);

        String s = "archived method CP entry.* AOTLinkedVarHandlesApp ";
        OutputAnalyzer dumpOut = CDSTestUtils.createArchiveAndCheck(opts);
        dumpOut.shouldMatch(s + "java/lang/invoke/VarHandle.compareAndExchangeAcquire:\\(\\[DIDI\\)D =>");
        dumpOut.shouldMatch(s + "java/lang/invoke/VarHandle.get:\\(\\[DI\\)D => ");

        CDSOptions runOpts = (new CDSOptions())
            .setUseVersion(false)
            .addPrefix("-Xlog:cds",
                       "-esa",
                       "-cp", appJar)
            .addSuffix(mainClass);

        CDSTestUtils.run(runOpts)
            .assertNormalExit("Hello AOTLinkedVarHandlesApp");
    }
}

class AOTLinkedVarHandlesApp {
    static final VarHandle initialized;
    static final VarHandle lazy;
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

    static class Data {
        static long longField = seed;
    }

    public static void main(String args[]) {
        seed = 1234;
        System.out.println("Hello AOTLinkedVarHandlesApp");
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
        double[] array = new double[] {1.0};
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
}
