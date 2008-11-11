/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6336968
 * @summary Test AttributeList.toMap
 * @author Eamonn McManus
 */

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;

public class AttributeListMapTest {

    private static String failure;

    public static void main(String[] args) throws Exception {
        AttributeList attrs = new AttributeList(Arrays.asList(
            new Attribute("Str", "Five"),
            new Attribute("Int", 5),
            new Attribute("Flt", 5.0)));

        Map<String, Object> map = attrs.toMap();
        final Map<String, Object> expectMap = new HashMap<String, Object>();
        for (Attribute attr : attrs.asList())
            expectMap.put(attr.getName(), attr.getValue());
        assertEquals("Initial map", expectMap, map);
        assertEquals("Initial map size", 3, map.size());
        assertEquals("Name set", expectMap.keySet(), map.keySet());
        assertEquals("Values", new HashSet<Object>(expectMap.values()),
                               new HashSet<Object>(map.values()));
        assertEquals("Entry set", expectMap.entrySet(), map.entrySet());

        AttributeList attrs2 = new AttributeList(map);
        assertEquals("AttributeList from Map", attrs, attrs2);
        // This assumes that the Map conserves the order of the attributes,
        // which is not specified but true because we use LinkedHashMap.

        // Check that toMap fails if the list contains non-Attribute elements.
        AttributeList attrs3 = new AttributeList(attrs);
        attrs3.add("Hello");  // allowed but curious
        try {
            map = attrs3.toMap();
            fail("toMap succeeded on list with non-Attribute elements");
        } catch (Exception e) {
            assertEquals("Exception for toMap with non-Atttribute elements",
                    IllegalArgumentException.class, e.getClass());
        }

        // Check that the Map does not reflect changes made to the list after
        // the Map was obtained.
        AttributeList attrs4 = new AttributeList(attrs);
        map = attrs4.toMap();
        attrs4.add(new Attribute("Big", new BigInteger("5")));
        assertEquals("Map after adding element to list", expectMap, map);

        // Check that if there is more than one Attribute with the same name
        // then toMap() chooses the last of them.
        AttributeList attrs5 = new AttributeList(attrs);
        attrs5.add(new Attribute("Str", "Cinq"));
        map = attrs5.toMap();
        assertEquals("Size of Map for list with duplicate attribute name",
                3, map.size());
        Object value = map.get("Str");
        assertEquals("Value of Str in Map for list with two values for it",
                "Cinq", value);

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    private static void assertEquals(String what, Object expect, Object actual) {
        if (eq(expect, actual))
            System.out.println("OK: " + what);
        else
            fail(what + ": expected " + expect + ", got " + actual);
    }

    private static boolean eq(Object x, Object y) {
        return (x == null) ? (y == null) : x.equals(y);
    }

    private static void fail(String why) {
        System.out.println("FAIL: " + why);
        failure = why;
    }
}
