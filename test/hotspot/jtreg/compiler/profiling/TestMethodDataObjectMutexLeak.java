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
 */

/*
 * @test
 * @bug 8390874
 * @summary Test that method unloading doesn't cause leaks of C heap allocated Mutexes in MethodData instances.
 *
 * @run main/othervm/timeout=600 -Xbatch
        -XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:MallocLimit=synchronization:8m -XX:-CreateCoredumpOnCrash
        compiler.profiling.TestMethodDataObjectMutexLeak
 */

package compiler.profiling;

import java.io.IOException;
import java.io.InputStream;

public class TestMethodDataObjectMutexLeak {
    static final String CLASS_NAME = Burn.class.getName();
    static final byte[] BYTES;

    static {
        try (InputStream in = TestMethodDataObjectMutexLeak.class.getResourceAsStream("/" + CLASS_NAME.replace('.', '/') + ".class")) {
            BYTES = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class Burn implements Runnable {
        int x;
        public void run() {
            for (int i = 0; i < 300; i++) {
                work01(i);
            }
        }

        public void work01(int i) { work02(i); }
        public void work02(int i) { work03(i); }
        public void work03(int i) { work04(i); }
        public void work04(int i) { work05(i); }
        public void work05(int i) { work06(i); }
        public void work06(int i) { work07(i); }
        public void work07(int i) { work08(i); }
        public void work08(int i) { work09(i); }
        public void work09(int i) { work10(i); }
        public void work10(int i) { work11(i); }
        public void work11(int i) { work12(i); }
        public void work12(int i) { work13(i); }
        public void work13(int i) { work14(i); }
        public void work14(int i) { work15(i); }
        public void work15(int i) { work16(i); }
        public void work16(int i) { work17(i); }
        public void work17(int i) { work18(i); }
        public void work18(int i) { work19(i); }
        public void work19(int i) { work20(i); }
        public void work20(int i) { work21(i); }
        public void work21(int i) { work22(i); }
        public void work22(int i) { work23(i); }
        public void work23(int i) { work24(i); }
        public void work24(int i) { work25(i); }
        public void work25(int i) { work26(i); }
        public void work26(int i) { work27(i); }
        public void work27(int i) { work28(i); }
        public void work28(int i) { work29(i); }
        public void work29(int i) { work30(i); }
        public void work30(int i) { work31(i); }
        public void work31(int i) { work32(i); }
        public void work32(int i) { work33(i); }
        public void work33(int i) { work34(i); }
        public void work34(int i) { work35(i); }
        public void work35(int i) { work36(i); }
        public void work36(int i) { work37(i); }
        public void work37(int i) { work38(i); }
        public void work38(int i) { work39(i); }
        public void work39(int i) { work40(i); }
        public void work40(int i) { work41(i); }
        public void work41(int i) { work42(i); }
        public void work42(int i) { work43(i); }
        public void work43(int i) { work44(i); }
        public void work44(int i) { work45(i); }
        public void work45(int i) { work46(i); }
        public void work46(int i) { work47(i); }
        public void work47(int i) { work48(i); }
        public void work48(int i) { work49(i); }
        public void work49(int i) { work50(i); }
        public void work50(int i) { work51(i); }
        public void work51(int i) { work52(i); }
        public void work52(int i) { work53(i); }
        public void work53(int i) { work54(i); }
        public void work54(int i) { work55(i); }
        public void work55(int i) { work56(i); }
        public void work56(int i) { work57(i); }
        public void work57(int i) { work58(i); }
        public void work58(int i) { work59(i); }
        public void work59(int i) { work60(i); }
        public void work60(int i) { work61(i); }
        public void work61(int i) { work62(i); }
        public void work62(int i) { work63(i); }
        public void work63(int i) { work64(i); }
        public void work64(int i) { work65(i); }
        public void work65(int i) { work66(i); }
        public void work66(int i) { work67(i); }
        public void work67(int i) { work68(i); }
        public void work68(int i) { work69(i); }
        public void work69(int i) { work70(i); }
        public void work70(int i) { work71(i); }
        public void work71(int i) { work72(i); }
        public void work72(int i) { work73(i); }
        public void work73(int i) { work74(i); }
        public void work74(int i) { work75(i); }
        public void work75(int i) { work76(i); }
        public void work76(int i) { work77(i); }
        public void work77(int i) { work78(i); }
        public void work78(int i) { work79(i); }
        public void work79(int i) { work80(i); }
        public void work80(int i) { work81(i); }
        public void work81(int i) { work82(i); }
        public void work82(int i) { work83(i); }
        public void work83(int i) { work84(i); }
        public void work84(int i) { work85(i); }
        public void work85(int i) { work86(i); }
        public void work86(int i) { work87(i); }
        public void work87(int i) { work88(i); }
        public void work88(int i) { work89(i); }
        public void work89(int i) { work90(i); }
        public void work90(int i) { work91(i); }
        public void work91(int i) { work92(i); }
        public void work92(int i) { work93(i); }
        public void work93(int i) { work94(i); }
        public void work94(int i) { work95(i); }
        public void work95(int i) { work96(i); }
        public void work96(int i) { work97(i); }
        public void work97(int i) { work98(i); }
        public void work98(int i) { work99(i); }
        public void work99(int i) {
            x += i * x + 42;
        }
    }

    static class MyCL extends ClassLoader {
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(CLASS_NAME)) {
                Class<?> c = defineClass(name, BYTES, 0, BYTES.length);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(name, resolve);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int t = 0; t < 30; t++) {
            System.gc();
            System.out.println("Epoch " + t);
            for (int i = 0; i < 100; i++) {
                Class<?> c = Class.forName(CLASS_NAME, true, new MyCL());
                Runnable r = (Runnable) c.getDeclaredConstructor().newInstance();
                r.run();
            }
        }
        System.out.println("Done.");
    }
}
