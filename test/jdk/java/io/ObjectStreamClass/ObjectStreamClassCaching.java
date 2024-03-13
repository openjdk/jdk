/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=G1
 * @requires vm.gc.G1
 * @bug 8277072
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive (G1 GC)
 * @run testng/othervm -Xmx64m -XX:+UseG1GC ObjectStreamClassCaching
 */

/*
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @bug 8277072
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive (Parallel GC)
 * @run testng/othervm -Xmx64m -XX:+UseParallelGC ObjectStreamClassCaching
 */

/*
 * @test id=ZGenerational
 * @requires vm.gc.ZGenerational
 * @bug 8277072 8327180
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive (ZGC)
 * @run testng/othervm -Xmx64m -XX:+UseZGC -XX:+ZGenerational ObjectStreamClassCaching
 */

/*
 * @test id=Shenandoah
 * @requires vm.gc.Shenandoah
 * @bug 8277072
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive (Shenandoah GC)
 * @run testng/othervm -Xmx64m -XX:+UseShenandoahGC ObjectStreamClassCaching
 */

/*
 * @test id=Serial
 * @requires vm.gc.Serial
 * @bug 8277072 8327180
 * @library /test/lib/
 * @summary ObjectStreamClass caches keep ClassLoaders alive (Serial GC)
 * @run testng/othervm -Xmx64m -XX:+UseSerialGC ObjectStreamClassCaching
 */
public class ObjectStreamClassCaching {

    @Test
    public void test2CacheReleaseUnderMemoryPressure() throws Exception {
        var list = new ArrayList<>();
        var ref = lookupObjectStreamClass(TestClass2.class);
        try {
            while (!ref.refersTo(null)) {
                list.add(new byte[1024 * 1024 * 4]); // 4 MiB chunks
                System.out.println("4MiB allocated...");
            }
        } catch (OutOfMemoryError e) {
            // release
            list = null;
        }
        System.gc();
        Thread.sleep(100L);
        assertTrue(ref.refersTo(null),
                   "Cache still has entry although memory was pressed hard");
    }

    // separate method so that the looked-up ObjectStreamClass is not kept on stack
    private static Reference<?> lookupObjectStreamClass(Class<?> cl) {
        return new WeakReference<>(ObjectStreamClass.lookup(cl));
    }

    // separate method so that the new Object() is not kept on stack
    private static Reference<?> newWeakRef() {
        return new WeakReference<>(new Object());
    }

    static class TestClass1 implements Serializable {
    }

    static class TestClass2 implements Serializable {
    }
}
