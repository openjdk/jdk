/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.jdk;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class Unsafe_compareAndSwap extends JTTTest {

    static final long valueOffset;
    static {
        try {
            valueOffset = UNSAFE.objectFieldOffset(Unsafe_compareAndSwap.class.getDeclaredField("value"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    public static String test(Unsafe_compareAndSwap u, Object o, String expected, String newValue) {
        // First arg is not an array - can use a field write barrier
        UNSAFE.compareAndSwapObject(u, valueOffset, expected, newValue);
        // Not known if first arg is an array - different write barrier may be used
        UNSAFE.compareAndSwapObject(o, valueOffset, expected, newValue);

        return instance.value;
    }

    private String value;

    private static final Unsafe_compareAndSwap instance = new Unsafe_compareAndSwap();

    @Override
    protected void before(ResolvedJavaMethod m) {
        instance.value = "a";
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", instance, instance, "a", "b");
    }
}
