/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8071693
 * @summary Verify that the Introspector finds default methods inherited from interfaces
 */

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashSet;

public class DefaultMethodBeanPropertyTest {

    public interface IfaceA {
        default int getValue() {
            return 0;
        }
        default Object getObj() {
            return null;
        }

        public static int getStaticValue() {
            return 0;
        }
    }

    public interface IfaceB extends IfaceA {
    }

    public interface IfaceC extends IfaceA {
        Number getFoo();
    }

    public class ClassB implements IfaceC {
        @Override
        public Integer getFoo() {
            return null;
        }
        @Override
        public Float getObj() {
            return null;
        }
    }

    public static void findProperty(Class<?> type, String name) {
        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(type, name);
        if (pd == null) {
            throw new Error("property \"" + name + "\" not found in " + type);
        }
    }

    public static void main(String[] args) throws Exception {
        findProperty(ClassB.class, "foo");

        // Expected properties
        final HashSet<PropertyDescriptor> expected = new HashSet<>();
        expected.add(new PropertyDescriptor("class", ClassB.class, "getClass", null));  // inherited method
        expected.add(new PropertyDescriptor("value", ClassB.class, "getValue", null));  // inherited default method
        expected.add(new PropertyDescriptor("foo", ClassB.class, "getFoo", null));      // overridden interface method
        expected.add(new PropertyDescriptor("obj", ClassB.class, "getObj", null));      // overridden default method

        // Actual properties
        final HashSet<PropertyDescriptor> actual = new HashSet<>(
          Arrays.asList(BeanUtils.getPropertyDescriptors(ClassB.class)));

        // Verify they are the same
        if (!actual.equals(expected)) {
            throw new Error("mismatch:\n    actual: " + actual + "\n  expected: " + expected);
        }
    }
}
