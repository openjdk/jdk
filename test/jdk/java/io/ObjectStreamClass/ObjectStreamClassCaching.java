/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/* @test
 * @bug 8277072
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive
 * @run testng/othervm -Xmx10m -XX:SoftRefLRUPolicyMSPerMB=1 ObjectStreamClassCaching
 */
public class ObjectStreamClassCaching {

    @Test
    public void testCachingEffectiveness() throws Exception {
        var ref = lookupObjectStreamClass(TestClass.class);
        System.gc();
        Thread.sleep(100L);
        // to trigger any ReferenceQueue processing...
        lookupObjectStreamClass(AnotherTestClass.class);
        assertFalse(ref.refersTo(null),
                    "Cache lost entry although memory was not under pressure");
    }

    @Test
    public void testCacheReleaseUnderMemoryPressure() throws Exception {
        var ref = lookupObjectStreamClass(TestClass.class);
        pressMemoryHard(ref);
        System.gc();
        Thread.sleep(100L);
        assertTrue(ref.refersTo(null),
                   "Cache still has entry although memory was pressed hard");
    }

    // separate method so that the looked-up ObjectStreamClass is not kept on stack
    private static WeakReference<?> lookupObjectStreamClass(Class<?> cl) {
        return new WeakReference<>(ObjectStreamClass.lookup(cl));
    }

    private static void pressMemoryHard(Reference<?> ref) {
        try {
            var list = new ArrayList<>();
            while (!ref.refersTo(null)) {
                list.add(new byte[1024 * 1024 * 64]); // 64 MiB chunks
            }
        } catch (OutOfMemoryError e) {
            // release
        }
    }
}

class TestClass implements Serializable {
}

class AnotherTestClass implements Serializable {
}
