/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package gc.jni;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.gc.GC;

/* @test
 * @summary Test that we are catching bugs when leaving JNI critical region into unsafe code
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox gc.jni.JNICriticalSupport
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      gc.jni.TestGCInJNICritical
 */
public class TestGCInJNICritical {

    public static void main(String[] args) throws Throwable {
        if (GC.Epsilon.isSupported()) {
            // Supports JNI Critical by not moving anything and never tracking thread status
            shouldPass("-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC");
        }
        if (GC.G1.isSupported()) {
            // Supports JNI Critical by pinning regions (JEP 423) and never tracking thread status
            shouldPass("-XX:+UseG1GC");
        }
        if (GC.Parallel.isSupported()) {
            // Supports JNI Critical with GCLocker, tracks thread status
            shouldFailCheck("-XX:+UseParallelGC");
        }
        if (GC.Serial.isSupported()) {
            // Supports JNI Critical with GCLocker, tracks thread status
            shouldFailCheck("-XX:+UseSerialGC");
        }
        if (GC.Shenandoah.isSupported()) {
            // Supports JNI Critical by pinning regions, and never tracking thread status
            shouldPass("-XX:+UseShenandoahGC");
        }
        if (GC.Z.isSupported()) {
            // Supports JNI Critical by GCLocker-like mechanism, tracks thread status
            shouldFailCheck("-XX:+UseZGC");
        }
    }

    public static OutputAnalyzer runWith(String... gcArgs) throws Throwable {
        List<String> args = new ArrayList<>();
        args.add("-Xmx256m");
        args.add("-Xcheck:jni");
        args.add("--enable-native-access=ALL-UNNAMED");
        args.addAll(Arrays.asList(gcArgs));
        args.add(TestGCInJNICritical.Main.class.getName());
        return ProcessTools.executeLimitedTestJava(args);
    }

    public static void shouldFailCheck(String... gcArgs) throws Throwable {
        OutputAnalyzer oa = runWith(gcArgs);
        oa.shouldNotHaveExitValue(0);
        oa.stdoutShouldContain("Leaving native code while in JNI critical section, potential deadlock");
    }

    public static void shouldPass(String... gcArgs) throws Throwable {
        OutputAnalyzer oa = runWith(gcArgs);
        oa.shouldHaveExitValue(0);
        oa.stderrShouldBeEmptyIgnoreVMWarnings();
        oa.stdoutShouldBeEmpty();
    }

    static class Main {
        public static void main(String[] args) {
            int[] cog = new int[10];
            JNICriticalSupport.get(cog);
            // Do anything that could trigger a safepoint while we are in JNI critical section, really.
            // Asking for GC also shows the opportunity to deadlock with GCLocker.
            System.gc();
            JNICriticalSupport.release(cog);
        }
    }
}
