/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary unit tests for java.lang.invoke.MethodHandles
 * @run junit/othervm -ea -esa test.java.lang.invoke.VarArgsTest
 */
package test.java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.List;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VarArgsTest {

    @Test
    public void testWithVarargs() throws Throwable {
        MethodHandle deepToString = publicLookup()
            .findStatic(Arrays.class, "deepToString", methodType(String.class, Object[].class));
        assertFalse(deepToString.isVarargsCollector());
        MethodHandle ts = deepToString.withVarargs(false);
        assertFalse(ts.isVarargsCollector());
        MethodHandle ts1 = deepToString.withVarargs(true);
        assertTrue(ts1.isVarargsCollector());
        assertEquals("[won]", (String) ts1.invokeExact(new Object[]{"won"}));
        assertEquals("[won]", (String) ts1.invoke(new Object[]{"won"}));
        assertEquals("[won]", (String) ts1.invoke("won"));
        assertEquals("[won, won]", (String) ts1.invoke("won", "won"));
        assertEquals("[won, won]", (String) ts1.invoke(new Object[]{"won", "won"}));
        assertEquals("[[won]]", (String) ts1.invoke((Object) new Object[]{"won"}));
    }

    @Test
    public void testWithVarargs2() throws Throwable {
        MethodHandle asList = publicLookup()
            .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class));
        MethodHandle asListWithVarargs = asList.withVarargs(asList.isVarargsCollector());
        assertTrue(asListWithVarargs.isVarargsCollector());
        assertEquals("[]", asListWithVarargs.invoke().toString());
        assertEquals("[1]", asListWithVarargs.invoke(1).toString());
        assertEquals("[two, too]", asListWithVarargs.invoke("two", "too").toString());
    }

    @Test
    public void testWithVarargsIAE() throws Throwable {
        MethodHandle lenMH = publicLookup()
                .findVirtual(String.class, "length", methodType(int.class));
        assertThrows(IllegalArgumentException.class, () -> lenMH.withVarargs(true));
    }

}
