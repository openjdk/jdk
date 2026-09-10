/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.annotations;

import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Category;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.ValueDescriptor;
import jdk.test.lib.Asserts;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestGetValues
 */
@MetadataDefinition
@interface Animal {
    String value();
}

@MetadataDefinition
@interface Elephant {
    String color();
    boolean mammal();
}
public class TestGetValues {

    public static void main(String[] args) throws Exception {
        testGetValue();
        testImmutableGetValue();
        testGetValues();
        testImmutableGetValues();
    }

    public static void testGetValue() {
        AnnotationElement animal = new AnnotationElement(Animal.class, "tiger");
        Asserts.assertEquals("tiger", animal.getValue("value"));

        AnnotationElement elephant = new AnnotationElement(Elephant.class, Map.of("color", "gray", "mammal", true));
        Asserts.assertEquals("gray", elephant.getValue("color"));
        Asserts.assertEquals(true, elephant.getValue("mammal"));
    }

    public static void testImmutableGetValue() {
        String[] initial = { "tree", "leaf" };
        AnnotationElement a = new AnnotationElement(Category.class, initial);

        // Mutate array after construction
        initial[0] = "foo";
        String[] s1 = (String[]) a.getValue("value");
        Asserts.assertEquals(s1[0], "tree");

        // Mutate returned array
        s1[0] = "syntax error";
        String[] s2 = (String[]) a.getValue("value");
        Asserts.assertEquals(s2[0], "tree");
    }

    public static void testGetValues() {
        AnnotationElement animal = new AnnotationElement(Animal.class, "tiger");
        Asserts.assertEquals("tiger", animal.getValues().get(0));

        AnnotationElement elephant = new AnnotationElement(Elephant.class, Map.of("color", "gray", "mammal", true));
        Asserts.assertEquals(getValue(elephant, "color"), "gray");
        Asserts.assertEquals(getValue(elephant, "mammal"), true);
    }

    public static void testImmutableGetValues() throws Exception {
        String[] initial = { "tree", "leaf" };
        AnnotationElement a = new AnnotationElement(Category.class, initial);

        // Mutate array after construction
        initial[0] = "foo";
        String[] s1 = (String[]) a.getValues().get(0);
        Asserts.assertEquals(s1[0], "tree");

        // Mutate returned array
        s1[0] = "syntax error";
        String[] s2 = (String[]) a.getValues().get(0);
        Asserts.assertEquals(s2[0], "tree");

        // Mutate returned list
        try {
            a.getValues().add("modify");
            throw new Exception("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException uoe) {
            // as expected
        }
    }

    private static Object getValue(AnnotationElement a, String name) {
        int index = 0;
        List<Object> values = a.getValues();
        for (ValueDescriptor v : a.getValueDescriptors()) {
            if (v.getName().equals(name)) {
                return values.get(index);
            }
            index++;
        }
        throw new IllegalStateException("No annotation value named " + name);
    }
}
