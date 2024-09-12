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
 */

/*
 * @test
 * @bug 8337622
 * @summary (reflect) java.lang.Class componentType field not found.
 * @library /test/lib
 * @modules java.base/java.lang:open
 * @run main ComponentTypeFieldTest
 */

import java.lang.reflect.Field;
import static jdk.test.lib.Asserts.*;

public class ComponentTypeFieldTest {

    public static void main(String[] args) throws Exception {
        Field f = Class.class.getDeclaredField("componentType");
        f.setAccessible(true);
        Object val = f.get(Runnable.class);
        assertTrue(val == null);
        System.out.println("val is " + val);

        Object arrayVal = f.get(Integer[].class);
        System.out.println("val is " + arrayVal);
        String arrayValString = arrayVal.toString();
        assertTrue(arrayValString.equals("class java.lang.Integer"));
    }
}
