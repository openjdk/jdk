/*
 * Copyright (c) 2010, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.misc
 *
 * @summary converted from VM Testbase vm/mlvm/anonloader/func/isGarbageCollected.
 * VM Testbase keywords: [feature_mlvm]
 * VM Testbase readme:
 * DESCRIPTION
 *     Load an anonymous class, drop all references to it and verify that it is collected
 *     by GC afterwards (call System.gc() just in case). PhantomReference is used to
 *     check that the anonymous class is gone.
 *
 * @library /vmTestbase
 *          /test/lib
 *
 * @comment build test class and indify classes
 * @build vm.mlvm.anonloader.func.isGarbageCollected.Test
 * @run driver vm.mlvm.share.IndifiedClassesBuilder
 *
 * @run main/othervm vm.mlvm.anonloader.func.isGarbageCollected.Test
 */

package vm.mlvm.anonloader.func.isGarbageCollected;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import vm.mlvm.anonloader.share.AnonkTestee01;
import vm.mlvm.share.MlvmTest;
import vm.share.FileUtils;
import vm.share.UnsafeAccess;

public class Test extends MlvmTest {
    private static final Class<?> PARENT = AnonkTestee01.class;

    public boolean run() throws Exception {
        ReferenceQueue<Class<?>> refQueue = new ReferenceQueue<Class<?>>();
        PhantomReference<Class<?>> anonClassRef = createClass(refQueue);
        System.gc();
        Reference<? extends Class<?>> deletedObject = refQueue.remove();
        return anonClassRef.equals(deletedObject);
    }

    // a private method is great to keep anonClass reference local to make it GCed on the next cycle
    private PhantomReference<Class<?>> createClass(ReferenceQueue<Class<?>> refQueue) throws Exception {
        byte[] classBytes = FileUtils.readClass(PARENT.getName());
        Class<?> anonClass = UnsafeAccess.unsafe.defineAnonymousClass(PARENT,
                classBytes, null);
        return new PhantomReference<Class<?>>(anonClass, refQueue);
    }

    public static void main(String[] args) { MlvmTest.launch(args); }
}
