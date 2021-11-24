/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277398
 * @summary COMPAT charset is required
 * @modules java.base/sun.nio.cs:+open java.base/java.nio.charset:+open
 * @run main/othervm TestCompatCharset
 */

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;
import sun.nio.cs.StandardCharsets;

public class TestCompatCharset {
    public static void main(String[] args) throws Exception {
        // Get sun.nio.cs.StandardCharsets instance
        Field standardProviderFid = Charset.class.getDeclaredField("standardProvider");
        standardProviderFid.setAccessible(true);
        StandardCharsets standardProvider = (StandardCharsets) standardProviderFid.get(null);
        // Get Map classMap on StandardCharsets
        Method classMapMid = StandardCharsets.class.getDeclaredMethod("classMap");
        classMapMid.setAccessible(true);
        Map classMap = (Map)classMapMid.invoke(standardProvider);
        Method cacheMid = StandardCharsets.class.getDeclaredMethod("cache");
        // Get Map cache on StandardCharsets
        cacheMid.setAccessible(true);
        // Check initial setting
        Map cache = (Map)cacheMid.invoke(standardProvider);
        String cl1 = (String)classMap.get("compat");
        if (!"COMPAT".equals(cl1)) throw new RuntimeException("Dummy class name should be COMPAT");
        Charset cs1 = (Charset)cache.get("compat");
        if (cs1 != null) throw new RuntimeException("compat should not be in cache");
        // Set compat charset
        Charset compat = Charset.forName("compat");
        // Check compat charset on cache
        Charset cs2 = (Charset)cache.get("compat");
        if (cs2 == null) throw new RuntimeException("compat should be in cache");
        if (compat != cs2) throw new RuntimeException("compat and cache entry should be same");
        String cl2 = (String)classMap.get("compat");
        if (cl2 != null) throw new RuntimeException("Dummy class name should be null");
        Charset cs = Charset.forName(System.getProperty("native.encoding"));
        if (compat != cs) throw new RuntimeException("compat and native.encoding should be same");
    }
}
