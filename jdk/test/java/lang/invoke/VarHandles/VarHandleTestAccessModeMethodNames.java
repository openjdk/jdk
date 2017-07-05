/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng VarHandleTestAccessModeMethodNames
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

public class VarHandleTestAccessModeMethodNames {

    @DataProvider
    public static Object[][] accessModesProvider() {
        return Stream.of(VarHandle.AccessMode.values()).
                map(am -> new Object[]{am}).
                toArray(Object[][]::new);
    }


    @Test(dataProvider = "accessModesProvider")
    public void testMethodName(VarHandle.AccessMode am) {
        assertEquals(am.methodName(), toMethodName(am.name()));
    }

    private static String toMethodName(String name) {
        StringBuilder s = new StringBuilder(name.toLowerCase());
        int i;
        while ((i = s.indexOf("_")) !=  -1) {
            s.deleteCharAt(i);
            s.setCharAt(i, Character.toUpperCase(s.charAt(i)));
        }
        return s.toString();
    }
}
