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
package compiler.valhalla.inlinetypes;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8370914
 * @summary Test VarHandle access on an array that is a merged of different layouts.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=1 ${test.main.class}
 */
public class TestArrayVarHandleDifferentLayouts {
    private static final int INDEX = 8;
    private static final VarHandle HANDLE = MethodHandles.arrayElementVarHandle(Integer[].class);
    private static final Integer[] FLAT = new Integer[64];
    private static final Integer[] REFERENCE = (Integer[]) ValueClass.newReferenceArray(Integer.class, 64);

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testGet((i & 1) == 0);
        }
    }

    static Integer testGet(boolean useFlat) {
        Integer[] array = useFlat ? FLAT : REFERENCE;
        return (Integer)HANDLE.getVolatile(array, INDEX);
    }
}
