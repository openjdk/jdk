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
 * @bug 8304585
 * @run junit CallerSensitiveMethodInvoke
 * @summary Test Method::invoke that wraps exception in InvocationTargetException properly
 */

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CallerSensitiveMethodInvoke {
    private int value = 10;

    private void m() throws Exception {
        throw new InvocationTargetException(new IllegalArgumentException("testing"));
    }

    /**
     * Tests the Field::get method being invoked via Method::invoke which
     * should throw InvocationTargetException thrown by Field::get.
     */
    @Test
    public void csMethodInvoke() throws ReflectiveOperationException {
        Field f = CallerSensitiveMethodInvoke.class.getDeclaredField("value");
        try {
            // Field::get throws IAE
            Method m = Field.class.getDeclaredMethod("get", Object.class);
            m.invoke(f, new Object());  // illegal receiver type. IAE thrown
            fail("should not reach here");
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (!(t instanceof IllegalArgumentException)) {
                throw new RuntimeException("Unexpected cause of InvocationTargetException: " + t);
            }
        }
    }

    /**
     * Tests the method being invoked throws InvocationTargetException which
     * will be wrapped with another InvocationTargetException by Method::invoke.
     */
    @Test
    public void methodInvoke() throws ReflectiveOperationException {
        try {
            // m() throws InvocationTargetException which will be wrapped by Method::invoke
            Method m = CallerSensitiveMethodInvoke.class.getDeclaredMethod("m");
            m.invoke(new CallerSensitiveMethodInvoke());
            fail("should not reach here");
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (!(t instanceof InvocationTargetException)) {
                throw new RuntimeException("Unexpected cause of InvocationTargetException: " + t);
            }
        }
    }
}
