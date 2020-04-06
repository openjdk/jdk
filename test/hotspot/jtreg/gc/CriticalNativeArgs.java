/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. and/or its affiliates.
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

package gc;

/*
 * @test CriticalNativeStressEpsilon
 * @key gc
 * @bug 8199868
 * @library /
 * @requires (os.arch =="x86_64" | os.arch == "amd64" | os.arch=="x86" | os.arch=="i386") & vm.gc.Epsilon & !vm.graal.enabled
 * @summary test argument unpacking nmethod wrapper of critical native method
 * @run main/othervm/native -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xcomp -Xmx256M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 */

/*
 * @test CriticalNativeStressShenandoah
 * @key gc
 * @bug 8199868
 * @library /
 * @requires (os.arch =="x86_64" | os.arch == "amd64" | os.arch=="x86" | os.arch=="i386") & vm.gc.Shenandoah & !vm.graal.enabled
 * @summary test argument unpacking nmethod wrapper of critical native method
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive    -XX:+ShenandoahDegeneratedGC -Xcomp -Xmx512M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive    -XX:-ShenandoahDegeneratedGC -Xcomp -Xmx512M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive -Xcomp -Xmx512M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC                                                                        -Xcomp -Xmx256M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=iu        -Xcomp -Xmx512M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=iu -XX:ShenandoahGCHeuristics=aggressive -Xcomp -Xmx512M -XX:+CriticalJNINatives gc.CriticalNativeArgs
 */
public class CriticalNativeArgs {
    public static void main(String[] args) {
        int[] arr = new int[2];

        if (CriticalNative.isNull(arr)) {
            throw new RuntimeException("Should not be null");
        }

        if (!CriticalNative.isNull(null)) {
            throw new RuntimeException("Should be null");
        }
    }
}
