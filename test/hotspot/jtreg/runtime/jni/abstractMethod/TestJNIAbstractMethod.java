/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8323243
 * @summary Test that invocation of an abstract method from JNI works correctly
 * @compile AbstractMethodClass.jasm
 * @run main/othervm/native TestJNIAbstractMethod
 */

/**
 * We are testing invocation of an abstract method from JNI - which should
 * simply result in throwning AbstractMethodError. To invoke an abstract method
 * we must have an instance method (as abstract static methods are illegal),
 * but instantiating an abstract class is also illegal at the Java language
 * level, so we have to use a custom jasm class that contains an abstract method
 * declaration, but which is not itself declared as an abstract class.
 */
public class TestJNIAbstractMethod {

    // Invokes an abstract method from JNI and throws AbstractMethodError.
    private static native void invokeAbstractM(Class<?> AMclass,
                                               AbstractMethodClass receiver);

    static {
        System.loadLibrary("JNIAbstractMethod");
    }

    public static void main(String[] args) {
        AbstractMethodClass obj = new AbstractMethodClass();
        try {
            System.out.println("Attempting direct invocation via Java");
            obj.abstractM();
            throw new RuntimeException("Did not get AbstractMethodError from Java!");
        } catch (AbstractMethodError expected) {
            System.out.println("ok - got expected exception: " + expected);
        }
        try {
            System.out.println("Attempting direct invocation via JNI");
            invokeAbstractM(obj.getClass(), obj);
            throw new RuntimeException("Did not get AbstractMethodError from JNI!");
        } catch (AbstractMethodError expected) {
            System.out.println("ok - got expected exception: " + expected);
        }
    }
}
