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

/* @test 8299183
 * @run testng WrongMethodTypeTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;

import static java.lang.invoke.MethodType.methodType;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.*;

public class WrongMethodTypeTest {
    static final Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void checkExactType() throws Throwable {
        String expectedMessage = "handle's method type (int)int but found ()boolean";
        try {
            MethodHandle mh = LOOKUP.findStatic(WrongMethodTypeTest.class, "m", methodType(int.class, int.class));
            boolean b = (boolean)mh.invokeExact();
            fail("Expected WrongMethodTypeException");
        } catch (WrongMethodTypeException ex) {
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    @Test
    public void checkAccessModeInvokeExact() throws Throwable {
        String expectedMessage = "handle's method type ()int but found ()Void";
        VarHandle vh = LOOKUP.findStaticVarHandle(WrongMethodTypeTest.class, "x", int.class)
                             .withInvokeExactBehavior();
        try {
            Void o = (Void) vh.get();
        } catch (WrongMethodTypeException ex) {
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    @Test
    public void checkVarHandleInvokeExact() throws Throwable {
        String expectedMessage = "handle's method type (WrongMethodTypeTest)boolean but found (WrongMethodTypeTest)int";
        VarHandle vh = LOOKUP.findVarHandle(WrongMethodTypeTest.class, "y", boolean.class)
                             .withInvokeExactBehavior();
        try {
            int o = (int) vh.get(new WrongMethodTypeTest());
        } catch (WrongMethodTypeException ex) {
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    static int m(int x) {
        return x;
    }

    static int x = 200;
    boolean y = false;
}
