/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8071693 8347826
 * @summary Verify that the Introspector finds default methods inherited
 *          from interfaces
 */

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultMethodBeanPropertyTest {

//////////////////////////////////////
//                                  //
//          SCENARIO 1              //
//                                  //
//////////////////////////////////////

    public interface A1 {
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

    public interface B1 extends A1 {
    }

    public interface C1 extends A1 {
        Number getFoo();
    }

    public class D1 implements C1 {
        @Override
        public Integer getFoo() {
            return null;
        }
        @Override
        public Float getObj() {
            return null;
        }
    }

    public static void testScenario1() {
        verifyMethods(D1.class,
            "public static int DefaultMethodBeanPropertyTest$A1.getStaticValue()",
            "public default int DefaultMethodBeanPropertyTest$A1.getValue()",
            "public java.lang.Integer DefaultMethodBeanPropertyTest$D1.getFoo()",
            "public java.lang.Float DefaultMethodBeanPropertyTest$D1.getObj()"
        );
        verifyProperties(D1.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public default int DefaultMethodBeanPropertyTest$A1.getValue()",
            "public java.lang.Integer DefaultMethodBeanPropertyTest$D1.getFoo()",
            "public java.lang.Float DefaultMethodBeanPropertyTest$D1.getObj()"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 2              //
//                                  //
//////////////////////////////////////

    public interface A2 {
        default Object getFoo() {
            return null;
        }
    }

    public interface B2 extends A2 {
    }

    public interface C2 extends A2 {
    }

    public class D2 implements B2, C2 {
    }

    public static void testScenario2() {
        verifyMethods(D2.class,
            "public default java.lang.Object DefaultMethodBeanPropertyTest$A2.getFoo()"
        );
        verifyProperties(D2.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public default java.lang.Object DefaultMethodBeanPropertyTest$A2.getFoo()"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 3              //
//                                  //
//////////////////////////////////////

    public interface A3 {
        default Object getFoo() {
            return null;
        }
    }

    public interface B3 extends A3 {
        @Override
        Set<?> getFoo();
    }

    public interface C3 extends A3 {
        @Override
        Collection<?> getFoo();
    }

    public class D3 implements B3, C3 {
        @Override
        public NavigableSet<?> getFoo() {
            return null;
        }
    }

    public static void testScenario3() {
        verifyMethods(D3.class,
            "public java.util.NavigableSet DefaultMethodBeanPropertyTest$D3.getFoo()"
        );
        verifyProperties(D3.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public java.util.NavigableSet DefaultMethodBeanPropertyTest$D3.getFoo()"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 4              //
//                                  //
//////////////////////////////////////

    public interface A4 {
        default Object getDefault0() {
            return null;
        }
        default Object getDefault1() {
            return null;
        }
        default Object getDefault2() {
            return null;
        }
        default Object getDefault3() {
            return null;
        }
        Object getNonDefault();
    }

    public class B4 implements A4 {
        @Override
        public Object getDefault1() {
            return new B4();
        }
        @Override
        public String getDefault2() {
            return null;
        }
        @Override
        public Float getDefault3() {
            return null;
        }
        public Long getNonDefault() {
            return null;
        }
    }

    public static void testScenario4() {
        verifyMethods(B4.class,
            "public default java.lang.Object DefaultMethodBeanPropertyTest$A4.getDefault0()",
            "public java.lang.Object DefaultMethodBeanPropertyTest$B4.getDefault1()",
            "public java.lang.String DefaultMethodBeanPropertyTest$B4.getDefault2()",
            "public java.lang.Float DefaultMethodBeanPropertyTest$B4.getDefault3()",
            "public java.lang.Long DefaultMethodBeanPropertyTest$B4.getNonDefault()"
        );
        verifyProperties(B4.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public default java.lang.Object DefaultMethodBeanPropertyTest$A4.getDefault0()",
            "public java.lang.Object DefaultMethodBeanPropertyTest$B4.getDefault1()",
            "public java.lang.String DefaultMethodBeanPropertyTest$B4.getDefault2()",
            "public java.lang.Float DefaultMethodBeanPropertyTest$B4.getDefault3()",
            "public java.lang.Long DefaultMethodBeanPropertyTest$B4.getNonDefault()"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 5              //
//                                  //
//////////////////////////////////////

    public interface A5 {
        public default void setParentFoo(Integer num) {
        }
        public default void setFoo(String num) {
        }
        public static int getStaticValue() {
            return 0;
        }
        private int getPrivateValue() {
            return 0;
        }
    }

    public class B5 implements A5 {
        public void setFoo(Number num) {
        }
        public void setLocalFoo(Long num) {
        }
        public static int getStaticValue() {
            return 0;
        }
    }

    public static void testScenario5() {
        verifyMethods(B5.class,
            "public static int DefaultMethodBeanPropertyTest$B5.getStaticValue()",
            "public default void DefaultMethodBeanPropertyTest$A5.setFoo(java.lang.String)",
            "public default void DefaultMethodBeanPropertyTest$A5.setParentFoo(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$B5.setFoo(java.lang.Number)",
            "public void DefaultMethodBeanPropertyTest$B5.setLocalFoo(java.lang.Long)"
        );
        verifyProperties(B5.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public default void DefaultMethodBeanPropertyTest$A5.setParentFoo(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$B5.setFoo(java.lang.Number)",
            "public void DefaultMethodBeanPropertyTest$B5.setLocalFoo(java.lang.Long)"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 6              //
//                                  //
//////////////////////////////////////

    public class A6 {
        public void setParentFoo(Integer num) {
        }
        public void setFoo(Integer num) {
        }
        public static int getStaticValue() {
            return 0;
        }
        private int getPrivateValue() {
            return 0;
        }
    }

    public class B6 extends A6 {
        public void setFoo(String num) {
        }
        public void setLocalFoo(Long num) {
        }
        public static int getStaticValue() {
            return 0;
        }
    }

    public static void testScenario6() {
        verifyMethods(B6.class,
            "public static int DefaultMethodBeanPropertyTest$B6.getStaticValue()",
            "public void DefaultMethodBeanPropertyTest$A6.setFoo(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$A6.setParentFoo(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$B6.setFoo(java.lang.String)",
            "public void DefaultMethodBeanPropertyTest$B6.setLocalFoo(java.lang.Long)"
        );
        verifyProperties(B6.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public void DefaultMethodBeanPropertyTest$A6.setParentFoo(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$B6.setFoo(java.lang.String)",
            "public void DefaultMethodBeanPropertyTest$B6.setLocalFoo(java.lang.Long)"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 7              //
//                                  //
//////////////////////////////////////

    interface A7<T> {
        T getValue();
    }

    interface B7 {
        Runnable getValue();
    }

    interface AB7 extends B7, A7<Object> {
        Runnable getValue();
    }

    abstract class D7 implements AB7 {
        public void setValue(Runnable value) {
        }
    }

    public static void testScenario7() {
        verifyMethods(D7.class,
            "public void DefaultMethodBeanPropertyTest$D7.setValue(java.lang.Runnable)"
        );
        verifyProperties(D7.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public void DefaultMethodBeanPropertyTest$D7.setValue(java.lang.Runnable)"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 8              //
//                                  //
//////////////////////////////////////

    public interface A8 {
        public default void setFoo(Float num) {
        }
        public default void setFoo2(Integer num) {
        }
    }
    public interface B8 extends A8 {
        public default void setFoo(Integer num) {
        }
        public default void setFoo2(Float num) {
        }
    }

    public class C8 implements B8 {
    }

    public static void testScenario8() {
        verifyMethods(C8.class,
            "public default void DefaultMethodBeanPropertyTest$A8.setFoo(java.lang.Float)",
            "public default void DefaultMethodBeanPropertyTest$A8.setFoo2(java.lang.Integer)",
            "public default void DefaultMethodBeanPropertyTest$B8.setFoo(java.lang.Integer)",
            "public default void DefaultMethodBeanPropertyTest$B8.setFoo2(java.lang.Float)"
        );
        verifyProperties(C8.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public default void DefaultMethodBeanPropertyTest$B8.setFoo(java.lang.Integer)",
            "public default void DefaultMethodBeanPropertyTest$B8.setFoo2(java.lang.Float)"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 9              //
//                                  //
//////////////////////////////////////

    public class A9 {
        public void setFoo(Object value) {
        }
        public void setFoo(String value) {
        }
        public void setFoo2(Object value) {
        }
        public void setFoo2(Integer value) {
        }
        // For the same setters with inconvertible arg types PropertyInfo behavior is undefined.
        // public void setLocalFoo3(Long num) { }
        // public void setLocalFoo3(Float num) { }
    }

    public static void testScenario9() {
        verifyMethods(A9.class,
            "public void DefaultMethodBeanPropertyTest$A9.setFoo(java.lang.String)",
            "public void DefaultMethodBeanPropertyTest$A9.setFoo(java.lang.Object)",
            "public void DefaultMethodBeanPropertyTest$A9.setFoo2(java.lang.Integer)",
            "public void DefaultMethodBeanPropertyTest$A9.setFoo2(java.lang.Object)"
        );
        verifyProperties(A9.class,
            "public final native java.lang.Class java.lang.Object.getClass()",
            "public void DefaultMethodBeanPropertyTest$A9.setFoo(java.lang.String)",
            "public void DefaultMethodBeanPropertyTest$A9.setFoo2(java.lang.Integer)"
        );
    }

//////////////////////////////////////
//                                  //
//          SCENARIO 10              //
//                                  //
//////////////////////////////////////

    public static class A10 {
        public Object getProp() {
            return null;
        }
    }

    public static interface B10 {
        Object getProp();
    }

    public static class C10_1 extends A10 implements B10 {
    }

    public static class C10_2 extends A10 implements B10 {
    }

    public static class A10BeanInfo extends SimpleBeanInfo {
        public MethodDescriptor[] getMethodDescriptors() {
            try {
                Class params[] = {};
                MethodDescriptor md = new MethodDescriptor(A10.class.getDeclaredMethod("getProp", params));
                md.setDisplayName("display name");
                MethodDescriptor res[] = { md };
                return res;
            } catch (Exception exception) {
                throw new Error("unexpected exception", exception);
            }
        }
    }

    public static class C10_1BeanInfo extends SimpleBeanInfo {
        public BeanInfo[] getAdditionalBeanInfo() {
            try {
                BeanInfo res[] = {
                    Introspector.getBeanInfo(A10.class),
                    Introspector.getBeanInfo(B10.class)
                };
                return res;
            } catch (IntrospectionException exception) {
                throw new Error("unexpected exception", exception);
            }
        }
    }

    public static class C10_2BeanInfo extends SimpleBeanInfo {
        public BeanInfo[] getAdditionalBeanInfo() {
            try {
                BeanInfo res[] = {
                    Introspector.getBeanInfo(B10.class),
                    Introspector.getBeanInfo(A10.class)
                };
                return res;
            } catch (IntrospectionException exception) {
                throw new Error("unexpected exception", exception);
            }
        }
    }

    public static void testScenario10() {
        {
            var md = getMethodDescriptor(C10_1.class, A10.class, "getProp");
            assertEquals("display name", md.getDisplayName(), "getDisplayName()");
        }
        {
            var md = getMethodDescriptor(C10_2.class, A10.class, "getProp");
            assertEquals("display name", md.getDisplayName(), "getDisplayName()");
        }
    }

// Helper methods

    private static void verifyEquality(String title, Set<String> expected, Set<String> actual) {
        if (!actual.equals(expected)) {
            throw new Error(title + " mismatch: "
                    + "\nACTUAL:\n  "
                    + actual.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("\n  "))
                    + "\nEXPECTED:\n  "
                    + expected.stream()
                              .map(Object::toString)
                              .collect(Collectors.joining("\n  ")));
        }
    }

    public static void verifyProperties(Class<?> type,  String... methodNames) {
        try {
            final Set<String> expected = new HashSet<>(Arrays.asList(methodNames));
            final Set<String> actual = Arrays
                    .stream(Introspector.getBeanInfo(type)
                                        .getPropertyDescriptors())
                    .flatMap(pd -> Stream.of(pd.getReadMethod(), pd.getWriteMethod()))
                    .filter(Objects::nonNull)
                    .map((Method m) -> m.toString())
                    .collect(Collectors.toSet());
            verifyEquality("properties", expected, actual);
        } catch (IntrospectionException exception) {
            throw new Error("unexpected exception", exception);
        }
    }

    public static void verifyMethods(Class<?> type, String... methodNames) {
        try {
            final Set<String> expected = new HashSet<>(Arrays.asList(methodNames));
            final Set<String> actual = Arrays
                    .stream(Introspector.getBeanInfo(type, Object.class)
                                        .getMethodDescriptors())
                    .map(MethodDescriptor::getMethod)
                    .map(Method::toString)
                    .collect(Collectors.toSet());
            verifyEquality("methods", expected, actual);
        } catch (IntrospectionException exception) {
            throw new Error("unexpected exception", exception);
        }
    }

    private static MethodDescriptor getMethodDescriptor(Class cls, Class stop, String name) {
        try {
            for (var md : Introspector.getBeanInfo(cls, stop).getMethodDescriptors()) {
                if (md.getName().equals(name)) {
                    return md;
                }
            }
            return null;
        } catch (IntrospectionException exception) {
            throw new Error("unexpected exception", exception);
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new Error(msg + ":\nACTUAL: " + actual + "\nEXPECTED: " + expected);
        }
    }

// Main method

    public static void main(String[] args) throws Exception {
        testScenario1();
        testScenario2();
        testScenario3();
        testScenario4();
        testScenario5();
        testScenario6();
        testScenario7();
        testScenario8();
        testScenario9();
        testScenario10();
    }
}
