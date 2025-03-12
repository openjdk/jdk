/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jsr292;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/*
 * @test
 * @bug 8336042
 * @library /test/lib /
 *
 * @run main/bootclasspath/othervm -Xbatch -XX:-TieredCompilation compiler.jsr292.MHDeoptTest
 *
 */
public class MHDeoptTest {

    static int xx = 0;

    public static void main(String[] args) throws Throwable {
        MethodHandle mh1 = MethodHandles.lookup().findStatic(MHDeoptTest.class, "body1", MethodType.methodType(int.class));
        MethodHandle mh2 = MethodHandles.lookup().findStatic(MHDeoptTest.class, "body2", MethodType.methodType(int.class));
        MethodHandle[] arr = new MethodHandle[] {mh2, mh1};

        for (MethodHandle mh : arr) {
            for (int i = 1; i < 50_000; i++) {
                xx = i;
                mainLink(mh);
            }
        }

    }

    static int mainLink(MethodHandle mh) throws Throwable {
        return (int)mh.invokeExact();
    }

    static int cnt = 1000;

    static int body1() {
        int limit = 0x7fff;
        // uncommon trap
        if (xx == limit) {
            // OSR
            for (int i = 0; i < 50_000; i++) {
            }
            ++cnt;
            ++xx;
        }
        if (xx == limit + 1) {
            return cnt + 1;
        }
        return cnt;
    }

    static int body2() {
        int limit = 0x7fff;
        int dummy = 0;
        // uncommon trap
        if (xx == limit) {
            // OSR
            for (int i = 0; i < 50_000; i++) {
            }
            ++cnt;
            ++xx;
        }
        if (xx == limit + 1) {
            return cnt + 1;
        }
        return cnt;
    }

}
