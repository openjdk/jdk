/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8291065
 * @summary Checks interaction of static field VarHandle with class
 *          initialization mechanism..
 * @run main LazyStaticTest
 */

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LazyStaticTest {
    static Set<Class<?>> initialized = ConcurrentHashMap.newKeySet();

    static class SimpleSample {
        static int apple;

        static {
            initialized.add(SimpleSample.class);
            apple = 5;
        }
    }

    static class ParentSample {
        static int pear;

        static {
            initialized.add(ParentSample.class);
            pear = 3;
        }
    }

    static class ChildSample extends ParentSample {
        static {
            initialized.add(ChildSample.class);
            pear = 6;
        }
    }

    public static void main(String... args) throws Throwable {
        assert initialized.isEmpty() : "Incorrect initial state";

        var lookup = MethodHandles.lookup();

        // SimpleSample: a regular test case
        var simpleSampleAppleVh = lookup.findStaticVarHandle(SimpleSample.class, "apple", int.class);

        assert !initialized.contains(SimpleSample.class) : "SimpleSample class initialized on VH creation";

        assert (int) simpleSampleAppleVh.get() == 5 : "VarHandle incorrectly reads before initialization";

        assert initialized.contains(SimpleSample.class) : "SimpleSample class not initialized after VH use";

        simpleSampleAppleVh.set(42);
        assert SimpleSample.apple == 42 : "The value is not set correctly to SimpleSample.apple";
        assert (int) simpleSampleAppleVh.getAcquire() == 42
                : "The SimpleSample.apple value is not read correctly from the VH after a few uses";

        // ChildSample: ensure only ParentSample (field declarer) is initialized
        var childSamplePearVh = lookup.findStaticVarHandle(ChildSample.class, "pear", int.class);

        assert !initialized.contains(ParentSample.class) : "ParentSample class initialized on VH creation";
        assert !initialized.contains(ChildSample.class) : "ChildSample class initialized on VH creation";

        assert (int) childSamplePearVh.get() == 3 : "ParentSample not correctly initialized before first VH use";

        assert initialized.contains(ParentSample.class) : "ParentSample class not initialized after VH use";
        assert !initialized.contains(ChildSample.class) : "ChildSample class initialized after unrelated VH use";
    }
}
