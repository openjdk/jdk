/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8015436
 * @summary the IK _initial_method_idnum value must be adjusted if overpass methods are added
 * @run main Test8015436
 *
 */

/*
 * The test checks that a MemberName for the defaultMethod() is cached in
 * the class MemberNameTable without a crash in the VM fastdebug mode.
 * The original issue was that the InstanceKlass _initial_method_idnum was
 * not adjusted properly when the overpass methods are added to the class.
 * The expected/correct behavior: The test does not crash nor throw any exceptions.
 * All the invocations of the defaultMethod() must be completed successfully.
 */

import java.lang.invoke.*;

interface InterfaceWithDefaultMethod {
    public void someMethod();

    default public void defaultMethod(String str){
        System.out.println("defaultMethod() " + str);
    }
}

public class Test8015436 implements InterfaceWithDefaultMethod {
    @Override
    public void someMethod() {
        System.out.println("someMethod() invoked");
    }

    public static void main(String[] args) throws Throwable {
        Test8015436 testObj = new Test8015436();
        testObj.someMethod();
        testObj.defaultMethod("invoked directly");

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType   mt = MethodType.methodType(void.class, String.class);
        MethodHandle mh = lookup.findVirtual(Test8015436.class, "defaultMethod", mt);
        mh.invokeExact(testObj, "invoked via a MethodHandle");
    }
}

/*
 * A successful execution gives the output:
 *   someMethod() invoked
 *   defaultMethod() invoked directly
 *   defaultMethod() invoked via a MethodHandle
 */
