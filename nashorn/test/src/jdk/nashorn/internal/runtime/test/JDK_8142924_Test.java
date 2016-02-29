/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.runtime.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import jdk.nashorn.internal.objects.NativeSymbol;
import jdk.nashorn.internal.runtime.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @bug 8142924
 * @summary ES6 symbols created with Symbol.for should deserialize to canonical instances
 */
@SuppressWarnings("javadoc")
public class JDK_8142924_Test {
    @Test
    public static void testNonGlobal() throws Exception {
        final String name = "testNonGlobal";
        final Symbol symbol1 = (Symbol)NativeSymbol.constructor(false, null, name);
        final Symbol symbol2 = serializeRoundTrip(symbol1);
        Assert.assertNotSame(symbol1, symbol2);
        Assert.assertEquals(symbol2.getName(), name);
        Assert.assertNotSame(symbol1, NativeSymbol._for(null, name));
    }

    @Test
    public static void testGlobal() throws Exception {
        final String name = "testGlobal";
        final Symbol symbol1 = (Symbol)NativeSymbol._for(null, name);
        final Symbol symbol2 = serializeRoundTrip(symbol1);
        Assert.assertSame(symbol1, symbol2);
    }

    private static Symbol serializeRoundTrip(final Symbol symbol) throws Exception {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(symbol);
        out.close();
        return (Symbol) new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray())).readObject();
    }
}
