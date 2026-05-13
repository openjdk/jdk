/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandleHelper;
import jdk.internal.vm.annotation.ForceInline;

/*
 * @test
 * @bug 8166110
 * @library /test/lib / patches
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 *
 * @build java.base/java.lang.invoke.MethodHandleHelper
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch -XX:-TieredCompilation
 *                                 compiler.jsr292.InvokerSignatureMismatch
 */
public class InvokerSignatureMismatch {

    static final MethodHandle INT_MH;

    static {
        MethodHandle mhI = null;
        try {
           mhI = MethodHandles.lookup().findStatic(InvokerSignatureMismatch.class, "bodyI", MethodType.methodType(void.class, int.class));
        } catch (Throwable e) {
        }
        INT_MH = mhI;
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 50_000; i++) { // OSR
            mainLink(i);
            mainInvoke(i);
        }
    }

    static void mainLink(int i) throws Throwable {
        Object name = MethodHandleHelper.internalMemberName(INT_MH);
        MethodHandleHelper.linkToStatic(name, (float) i);
    }

    static void mainInvoke(int i) throws Throwable {
        MethodHandleHelper.invokeBasicV(INT_MH, (float) i);
    }

    static int cnt = 0;
    static void bodyI(int x) {
        if ((x & 1023) == 0) { // already optimized x % 1024 == 0
            ++cnt;
        }
    }

}
