/*
* Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test MonitorEnterTest
* @library /test/lib
* @enablePreview
* @compile MonitorEnterTest.java
* @run main/othervm/native -Xcheck:jni runtime.valhalla.inlinetypes.MonitorEnterTest
*/

package runtime.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class MonitorEnterTest {

    static {
        System.loadLibrary("MonitorEnterTest");
    }

    native static void JNIMonitorEnter(Object o);
    native static void JNIMonitorExit(Object o);

    static void monitorEnter(Object o, boolean expectSuccess, String message) {
        try {
            synchronized(o) {
                Asserts.assertTrue(expectSuccess, "MonitorEnter should not have succeeded on an instance of " + o.getClass().getName());
            }
        } catch (IdentityException e) {
            Asserts.assertFalse(expectSuccess, "Unexpected IdentityException with an instance of " + o.getClass().getName());
            if (message != null) {
                Asserts.assertEQ(e.getMessage(), message, "Exception message mismatch");
            }
        }

        try {
            JNIMonitorEnter(o);
            Asserts.assertTrue(expectSuccess, "JNI MonitorEnter should not have succeeded on an instance of " + o.getClass().getName());
        } catch (IdentityException e) {
            Asserts.assertFalse(expectSuccess, "Unexpected IdentityException with an instance of " + o.getClass().getName());
            if (message != null) {
                Asserts.assertEQ(e.getMessage(), message, "Exception message mismatch");
            }
        }

        try {
            JNIMonitorExit(o);
            Asserts.assertTrue(expectSuccess, "JNI MonitorExit should not have succeeded on an instance of " + o.getClass().getName());
        } catch (IllegalMonitorStateException e) {
            Asserts.assertFalse(expectSuccess, "Unexpected IllegalMonitorStateException with an instance of " + o.getClass().getName());
        }

    }

    static value class MyValue { }

    public static void main(String[] args) {
        // Attempts to lock the instance are repeated many time to ensure that the different paths
        // are used: interpreter, C1, and C2 (which deopt to the interpreter in this case)
        for (int i = 0; i <  20000; i++) {
            monitorEnter(new Object(), true, "");
            monitorEnter(new String(), true, "");
            monitorEnter(new MyValue(), false, "Cannot synchronize on an instance of value class runtime.valhalla.inlinetypes.MonitorEnterTest$MyValue");
            monitorEnter(Integer.valueOf(42), false, "Cannot synchronize on an instance of value class java.lang.Integer");
        }
    }
}
