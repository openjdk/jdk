/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import static org.testng.Assert.*;

/*
 * Test MethodHandleProxies::asInterfaceInstance with an inaccessible interface
 */
public class Unnamed {
    public static void main(String... args) throws Throwable {
        MethodHandle target = MethodHandles.constant(String.class, "test");
        Class<?> intf = Class.forName("p2.TestIntf");
        Object t = MethodHandleProxies.asInterfaceInstance(intf, target);

        // verify that the caller has no access to the proxy created on an
        // inaccessible interface
        Method m = intf.getMethod("test", Object[].class);
        assertFalse(m.canAccess(null));
    }
}
