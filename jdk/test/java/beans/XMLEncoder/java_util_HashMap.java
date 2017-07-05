/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4631471 4921212
 * @summary Tests HashMap encoding
 * @author Sergey Malenkov
 */

import java.util.HashMap;
import java.util.Map;

public final class java_util_HashMap extends AbstractTest<Map<String, String>> {
    public static void main(String[] args) {
        new java_util_HashMap().test(true);
    }

    protected Map<String, String> getObject() {
        return new HashMap<String, String>();
    }

    protected Map<String, String> getAnotherObject() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(null, "null-value");
        map.put("key", "value");
        map.put("key-null", null);
        return map;
    }

    protected void validate(Map<String, String> before, Map<String, String> after) {
        super.validate(before, after);
        validate(before);
        validate(after);
    }

    private static void validate(Map<String, String> map) {
        if (!map.isEmpty()) {
            validate(map, null, "null-value");
            validate(map, "key", "value");
            validate(map, "key-null", null);
        }
    }

    private static void validate(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key))
            throw new Error("There are no key: " + key);

        if (!map.containsValue(value))
            throw new Error("There are no value: " + value);

        if (!isEqual(value, map.get(key)))
            throw new Error("There are no entry: " + key + ", " + value);
    }

    private static boolean isEqual(String str1, String str2) {
        return (str1 == null)
                ? str2 == null
                : str1.equals(str2);
    }
}
