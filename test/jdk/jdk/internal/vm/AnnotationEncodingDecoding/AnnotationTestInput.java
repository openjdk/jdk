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
package jdk.internal.vm.test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AnnotationTestInput {

    enum Mood {
        HAPPY,
        SAD,
        CONFUSED;
    }

    private class PrivateClass {}

    @Single(string = "a",
            stringArray = {"a", "b"},
            classValue = String.class,
            classArray = {String.class, Exception.class},
            byteValue = 1,
            byteArray = {1, 2, Byte.MIN_VALUE, Byte.MAX_VALUE},
            charValue = 'a',
            charArray = {'a', 'b',
                         Character.MIN_VALUE, Character.MAX_VALUE,
                         '\b', '\f', '\n', '\r', '\t', '\\', '\'', '\"', '\u012A'},
            doubleValue = 3.3D,
            doubleArray = {3.3D, 4.4D,
                           Double.MIN_VALUE, Double.MAX_VALUE,
                           Double.NaN,
                           Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
            floatValue = 4.4F,
            floatArray = {4.4F, 5.5F,
                          Float.MIN_VALUE, Float.MAX_VALUE,
                          Float.NaN,
                          Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY},
            intValue = 5,
            intArray = {5, 6, Integer.MIN_VALUE, Integer.MAX_VALUE},
            longValue = 6L,
            longArray = {6L, 7L, Long.MIN_VALUE, Long.MAX_VALUE},
            shortValue = 7,
            shortArray = {7, 8, Short.MIN_VALUE, Short.MAX_VALUE},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.SAD,
            moodArray = {Mood.CONFUSED, Mood.HAPPY},
            nested = @NestedAnno("nested1"),
            nestedArray = {@NestedAnno("nested2"), @NestedAnno("nested3")})
    @Single(string = "A",
            stringArray = {"A", "B"},
            classValue = Thread.class,
            classArray = {Thread.class, PrivateClass.class},
            byteValue = -1,
            byteArray = {-1, -2},
            charValue = 'A',
            charArray = {'a', 'b'},
            doubleValue = -3.3D,
            doubleArray = {3.3D, 4.4D},
            floatValue = -4.4F,
            floatArray = {4.4F, 5.5F},
            intValue = -5,
            intArray = {5, 6},
            longValue = -6L,
            longArray = {6L, 7L},
            shortValue = -7,
            shortArray = {7, 8},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.CONFUSED,
            moodArray = {Mood.SAD, Mood.CONFUSED},
            nested = @NestedAnno("nested4"),
            nestedArray = {@NestedAnno("nested5"), @NestedAnno("nested6")})
    @SingleWithDefaults
    @Deprecated
    @SuppressWarnings("unchecked")
    public void annotatedMethod() {
    }

    @Named("Super1")
    public static class Super1 {}
    @Named("Super2")
    public static class Super2 extends Super1 {}
    public static class Super3 extends Super1 {}

    @Named("NonInheritedValue")
    public static class OwnName extends Super1 {}

    public static class InheritedName1 extends Super1 {}
    public static class InheritedName2 extends Super2 {}
    public static class InheritedName3 extends Super3 {}

    @Named("AnnotatedClass")
    @Single(string = "a",
            stringArray = {"a", "b"},
            classValue = String.class,
            classArray = {String.class, Exception.class},
            byteValue = 1,
            byteArray = {1, 2, Byte.MIN_VALUE, Byte.MAX_VALUE},
            charValue = 'a',
            charArray = {'a', 'b',
                    Character.MIN_VALUE, Character.MAX_VALUE,
                    '\b', '\f', '\n', '\r', '\t', '\\', '\'', '\"', '\u012A'},
            doubleValue = 3.3D,
            doubleArray = {3.3D, 4.4D,
                    Double.MIN_VALUE, Double.MAX_VALUE,
                    Double.NaN,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
            floatValue = 4.4F,
            floatArray = {4.4F, 5.5F,
                    Float.MIN_VALUE, Float.MAX_VALUE,
                    Float.NaN,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY},
            intValue = 5,
            intArray = {5, 6, Integer.MIN_VALUE, Integer.MAX_VALUE},
            longValue = 6L,
            longArray = {6L, 7L, Long.MIN_VALUE, Long.MAX_VALUE},
            shortValue = 7,
            shortArray = {7, 8, Short.MIN_VALUE, Short.MAX_VALUE},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.SAD,
            moodArray = {Mood.CONFUSED, Mood.HAPPY},
            nested = @NestedAnno("nested7"),
            nestedArray = {@NestedAnno("nested8"), @NestedAnno("nested9")})
    @Single(string = "A",
            stringArray = {"A", "B"},
            classValue = Thread.class,
            classArray = {Thread.class, PrivateClass.class},
            byteValue = -1,
            byteArray = {-1, -2},
            charValue = 'A',
            charArray = {'a', 'b'},
            doubleValue = -3.3D,
            doubleArray = {3.3D, 4.4D},
            floatValue = -4.4F,
            floatArray = {4.4F, 5.5F},
            intValue = -5,
            intArray = {5, 6},
            longValue = -6L,
            longArray = {6L, 7L},
            shortValue = -7,
            shortArray = {7, 8},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.CONFUSED,
            moodArray = {Mood.SAD, Mood.CONFUSED},
            nested = @NestedAnno("nested10"),
            nestedArray = {@NestedAnno("nested11"), @NestedAnno("nested12")})
    @Deprecated
    @SuppressWarnings({"rawtypes", "all"})
    public static class AnnotatedClass {}

    @Single(string = "a",
            stringArray = {"a", "b"},
            classValue = String.class,
            classArray = {String.class, Exception.class},
            byteValue = 1,
            byteArray = {1, 2, Byte.MIN_VALUE, Byte.MAX_VALUE},
            charValue = 'a',
            charArray = {'a', 'b',
                    Character.MIN_VALUE, Character.MAX_VALUE,
                    '\b', '\f', '\n', '\r', '\t', '\\', '\'', '\"', '\u012A'},
            doubleValue = 3.3D,
            doubleArray = {3.3D, 4.4D,
                    Double.MIN_VALUE, Double.MAX_VALUE,
                    Double.NaN,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
            floatValue = 4.4F,
            floatArray = {4.4F, 5.5F,
                    Float.MIN_VALUE, Float.MAX_VALUE,
                    Float.NaN,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY},
            intValue = 5,
            intArray = {5, 6, Integer.MIN_VALUE, Integer.MAX_VALUE},
            longValue = 6L,
            longArray = {6L, 7L, Long.MIN_VALUE, Long.MAX_VALUE},
            shortValue = 7,
            shortArray = {7, 8, Short.MIN_VALUE, Short.MAX_VALUE},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.SAD,
            moodArray = {Mood.CONFUSED, Mood.HAPPY},
            nested = @NestedAnno("nested12"),
            nestedArray = {@NestedAnno("nested13"), @NestedAnno("nested14")})
    @Single(string = "A",
            stringArray = {"A", "B"},
            classValue = Thread.class,
            classArray = {Thread.class, PrivateClass.class},
            byteValue = -1,
            byteArray = {-1, -2},
            charValue = 'A',
            charArray = {'a', 'b'},
            doubleValue = -3.3D,
            doubleArray = {3.3D, 4.4D},
            floatValue = -4.4F,
            floatArray = {4.4F, 5.5F},
            intValue = -5,
            intArray = {5, 6},
            longValue = -6L,
            longArray = {6L, 7L},
            shortValue = -7,
            shortArray = {7, 8},
            booleanValue = true,
            booleanArray = {true, false},
            mood = Mood.CONFUSED,
            moodArray = {Mood.SAD, Mood.CONFUSED},
            nested = @NestedAnno("nested15"),
            nestedArray = {@NestedAnno("nested16"), @NestedAnno("nested17")})
    private static final int annotatedField = 45;

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnno {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Named {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(SingleList.class)
    public @interface Single {
        Class<?> classValue();
        Class<?>[] classArray();

        String string();
        String[] stringArray();

        byte byteValue();
        byte[] byteArray();

        char charValue();
        char[] charArray();

        double doubleValue();
        double[] doubleArray();

        float floatValue();
        float[] floatArray();

        int intValue();
        int[] intArray();

        long longValue();
        long[] longArray();

        short shortValue();
        short[] shortArray();

        boolean booleanValue();
        boolean[] booleanArray();

        Mood mood();
        Mood[] moodArray();

        NestedAnno nested();
        NestedAnno[] nestedArray();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface SingleWithDefaults {
        Class<?> classValue() default SingleWithDefaults.class;
        Class<?>[] classArray() default {};

        String string() default "anonymous";
        String[] stringArray() default {};

        byte byteValue() default 101;
        byte[] byteArray() default {};

        char charValue() default 'Z';
        char[] charArray() default {};

        double doubleValue() default 102.102D;
        double[] doubleArray() default {};

        float floatValue() default 103.103F;
        float[] floatArray() default {};

        int intValue() default 104;
        int[] intArray() default {};

        long longValue() default 105L;
        long[] longArray() default {};

        short shortValue() default 105;
        short[] shortArray() default {};

        boolean booleanValue() default true;
        boolean[] booleanArray() default {};

        Mood mood() default Mood.HAPPY;
        Mood[] moodArray() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SingleList {
        Single[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Missing {}

    @Retention(RetentionPolicy.RUNTIME)
    public @interface MissingWrapper {
        Missing value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface MissingContainer {
        Class<?> value();
    }

    /**
     * Method with a directly missing annotation.
     */
    @Missing
    public void missingAnnotation() {}

    /**
     * Method with an indirectly missing nested annotation.
     */
    @MissingWrapper(@Missing)
    public void missingNestedAnnotation() {}

    /**
     * Method with an annotation that has a Class member
     * that cannot be resolved.
     */
    @MissingContainer(Missing.class)
    public void missingTypeOfClassMember() {}

    /**
     * Method with an annotation that has a member
     * that is deleted in a newer version of the annotation.
     */
    @MemberDeleted(value = "evolving", retained = -34, deleted = 56)
    public void missingMember() {}

    /**
     * Method with an annotation that has a member named "any"
     * whose type is chnaged from int to String in a newer version
     * of the annotation.
     */
    @MemberTypeChanged(value = "evolving", retained = -34, any = 56)
    public void changeTypeOfMember() {}

}

