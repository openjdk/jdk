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

/*
 * @test
 * @bug 8391308
 * @library /test/lib
 * @summary The test verifies heap walking API (FollowReferences) doesn't report classes
 *          when starting from the class loader. This is a change in behavior from 8391308.
 * @run main/othervm/native -agentlib:ClassLoaderTest ClassLoaderTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.ref.Reference;
import jdk.test.lib.Asserts;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

public class ClassLoaderTest {

    static class MyLoader extends ClassLoader {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
            clb.withFlags(ACC_PUBLIC)
               .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob ->
               cob.aload(0)
                  .invokespecial(CD_Object, INIT_NAME, MTD_void)
                  .return_())
        );

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals("Test")) {
               return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    }

    static {
        System.loadLibrary("ClassLoaderTest");
    }

    private static native boolean targetReachedFrom(ClassLoader loader, Class<?> target);

    public static void main(String[] args) {
        MyLoader ldr = new MyLoader();
        Class<?> test;
        try {
            test = ldr.loadClass("Test");
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Test not loaded");
        }

        Asserts.assertTrue(targetReachedFrom(ldr, test),
                           "FollowReferences starting at MyLoader reached Test.class");

        Reference.reachabilityFence(ldr);
        Reference.reachabilityFence(test);
    }
}
