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

/*
 * @test
 * @bug 8160821
 * @summary Ensures a polymorphic call site with non-exact invocation won't
 *          cause class loader leaks
 * @library /test/lib
 * @run junit PolymorphicCallSiteLeakTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;

import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PolymorphicCallSiteLeakTest {
    @Test
    void test() throws Throwable {
        var dummyClass = ByteCodeLoader.load("Dummy", ClassFile.of().build(ClassDesc.of("Dummy"), clb -> clb
                .withField("staticField", CD_long, ACC_PUBLIC | ACC_STATIC)
                .withField("instanceField", CD_long, ACC_PUBLIC)
                .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> cob
                        .aload(0).invokespecial(CD_Object, INIT_NAME, MTD_void).return_())));

        var loaderRef = new WeakReference<>(dummyClass.getClassLoader());
        var lookup = MethodHandles.publicLookup();
        var instanceVar = lookup.findVarHandle(dummyClass, "instanceField", long.class);
        var staticVar = lookup.findStaticVarHandle(dummyClass, "staticField", long.class);
        var obj = dummyClass.getConstructor().newInstance();

        // Non-exact invocations call sites - cache are strongly linked by this class like lambdas
        assertEquals(0L, (long) instanceVar.getAndAdd(obj, 1));
        assertEquals(0L, (long) staticVar.getAndAdd(2));

        dummyClass = null;
        instanceVar = null;
        staticVar = null;
        obj = null;

        if (!ForceGC.wait(() -> loaderRef.refersTo(null))) {
            fail("Loader leak through VH polymorphism cache");
        }
    }
}
