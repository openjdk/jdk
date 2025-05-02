/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;

import javax.swing.JPanel;

/**
 * @test
 * @bug 8308152
 * @summary PropertyDescriptor should work with overridden generic getter method
 */
public class OverriddenGetter {

    static class Parent<T> {
        private T value;
        public T getValue() {return value;}
        public final void setValue(T value) {this.value = value;}
    }

    static class ChildO extends Parent<Object> {
        public ChildO() {}
        @Override
        public Object getValue() {return super.getValue();}
    }

    static class ChildA extends Parent<ArithmeticException> {
        public ChildA() {}
        @Override
        public ArithmeticException getValue() {return super.getValue();}
    }

    static class ChildS extends Parent<String> {
        public ChildS() {}
        @Override
        public String getValue() {return super.getValue();}
    }

    public static void main(String[] args) throws Exception {
        test("UI", JPanel.class, "getUI", "setUI");
        test("value", ChildO.class, "getValue", "setValue");
        test("value", ChildA.class, "getValue", "setValue");
        test("value", ChildS.class, "getValue", "setValue");
    }

    private static void test(String name, Class<?> beanClass,
                             String read, String write) throws Exception
    {
        var gold = new PropertyDescriptor(name, beanClass, read, write);
        BeanInfo beanInfo = Introspector.getBeanInfo(beanClass);
        PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals(gold.getName())) {
                if (pd.getReadMethod() != gold.getReadMethod()) {
                    System.err.println("Expected: " + gold.getReadMethod());
                    System.err.println("Actual: " + pd.getReadMethod());
                    throw new RuntimeException("Wrong read method");
                }
                if (pd.getWriteMethod() != gold.getWriteMethod()) {
                    System.err.println("Expected: " + gold.getWriteMethod());
                    System.err.println("Actual: " + pd.getWriteMethod());
                    throw new RuntimeException("Wrong write method");
                }
                if (pd.getPropertyType() != gold.getPropertyType()) {
                    System.err.println("Expected: " + gold.getPropertyType());
                    System.err.println("Actual: " + pd.getPropertyType());
                    throw new RuntimeException("Wrong property type");
                }
                return;
            }
        }
        throw new RuntimeException("The PropertyDescriptor is not found");
    }
}
