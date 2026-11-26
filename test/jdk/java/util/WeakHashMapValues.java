/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @summary Check WeakHashMap throws IdentityException when Value Objects are put
 * @enablePreview
 * @run junit WeakHashMapValues
 */
public class WeakHashMapValues {

    /*
     * Check that any kind of put with a value class as a key throws IdentityException
     */
    @Test
    void checkThrowsIdentityException() {
        WeakHashMap<Object, Object> whm = new WeakHashMap<>();
        Object key = new Foo(1);
        assertThrows(IdentityException.class, () -> whm.put(key, "1"));
        assertThrows(IdentityException.class, () -> whm.putIfAbsent(key, "2"));
        assertThrows(IdentityException.class, () -> whm.compute(key, (_, _) -> "3"));
        assertThrows(IdentityException.class, () -> whm.computeIfAbsent(key, (_) -> "4"));

        HashMap<Object, String> hmap = new HashMap<>();
        hmap.put(key, "6");
        assertThrows(IdentityException.class, () -> whm.putAll(hmap));
    }

    /*
     * Check that any kind of put with Integer as a value class as a key throws IdentityException
     */
    @Test
    void checkIntegerThrowsIdentityException() {
        WeakHashMap<Object, Object> whm = new WeakHashMap<>();
        Object key = 1;
        assertThrows(IdentityException.class, () -> whm.put(key, "1"));
        assertThrows(IdentityException.class, () -> whm.putIfAbsent(key, "2"));
        assertThrows(IdentityException.class, () -> whm.compute(key, (_, _) -> "3"));
        assertThrows(IdentityException.class, () -> whm.computeIfAbsent(key, (_) -> "4"));

        HashMap<Object, String> hmap = new HashMap<>();
        hmap.put(key, "6");
        assertThrows(IdentityException.class, () -> whm.putAll(hmap));

    }

    /**
     * Check that queries with a value object return false or null.
     */
    @Test
    void checkValueObjectGet() {
        WeakHashMap<Object, Object> whm = new WeakHashMap<>();
        Object key = "X";
        Object v = new Foo(1);
        assertEquals(whm.get(v), null, "Get of value object should return null");
        assertEquals(whm.containsKey(v), false, "containsKey should return false");
    }

    /**
     * Check WeakHashMap.putAll from a source map containing a value object as a key throws.
     */
    @Test
    void checkValueObjectPutAll() {
        // src is mix of identity and value objects (Integer is value class with --enable-preview)
        HashMap<Object, Object> srcMap = new LinkedHashMap<>();
        srcMap.put("abc", "Vabc");
        srcMap.put(1, "V1");
        srcMap.put("xyz", "Vxyz");
        WeakHashMap<Object, Object> whm = new WeakHashMap<>();
        assertThrows(IdentityException.class, () -> whm.putAll(srcMap));
        assertTrue(whm.containsKey("abc"), "Identity key should have been copied");
        assertFalse(whm.containsKey(1), "Value object key should not have been copied");
        assertEquals(1, whm.size(), "Result map size");
    }
}

value class Foo {
    int x;
    Foo(int x) {
        this.x = x;
    }
}
