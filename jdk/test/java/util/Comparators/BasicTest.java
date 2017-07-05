/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8001667 8010279
 * @run testng BasicTest
 */

import java.util.Comparator;
import java.util.Comparators;
import java.util.AbstractMap;
import java.util.Map;
import org.testng.annotations.Test;

import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.ToDoubleFunction;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

/**
 * Unit tests for helper methods in Comparators
 */
@Test(groups = "unit")
public class BasicTest {
    private static class Thing {
        public final int intField;
        public final long longField;
        public final double doubleField;
        public final String stringField;

        private Thing(int intField, long longField, double doubleField, String stringField) {
            this.intField = intField;
            this.longField = longField;
            this.doubleField = doubleField;
            this.stringField = stringField;
        }

        public int getIntField() {
            return intField;
        }

        public long getLongField() {
            return longField;
        }

        public double getDoubleField() {
            return doubleField;
        }

        public String getStringField() {
            return stringField;
        }
    }

    private final int[] intValues = { -2, -2, -1, -1, 0, 0, 1, 1, 2, 2 };
    private final long[] longValues = { -2, -2, -1, -1, 0, 0, 1, 1, 2, 2 };
    private final double[] doubleValues = { -2, -2, -1, -1, 0, 0, 1, 1, 2, 2 };
    private final String[] stringValues = { "a", "a", "b", "b", "c", "c", "d", "d", "e", "e" };
    private final int[] comparisons = { 0, -1, 0, -1, 0, -1, 0, -1, 0 };

    private<T> void assertComparisons(T[] things, Comparator<T> comp, int[] comparisons) {
        for (int i=0; i<comparisons.length; i++) {
            assertEquals(comparisons.length + 1, things.length);
            assertEquals(comparisons[i], comp.compare(things[i], things[i+1]));
            assertEquals(-comparisons[i], comp.compare(things[i+1], things[i]));
        }
    }

    public void testIntComparator() {
        Thing[] things = new Thing[intValues.length];
        for (int i=0; i<intValues.length; i++)
            things[i] = new Thing(intValues[i], 0L, 0.0, null);
        Comparator<Thing> comp = Comparators.comparing(new ToIntFunction<BasicTest.Thing>() {
            @Override
            public int applyAsInt(Thing thing) {
                return thing.getIntField();
            }
        });

        assertComparisons(things, comp, comparisons);
    }

    public void testLongComparator() {
        Thing[] things = new Thing[longValues.length];
        for (int i=0; i<longValues.length; i++)
            things[i] = new Thing(0, longValues[i], 0.0, null);
        Comparator<Thing> comp = Comparators.comparing(new ToLongFunction<BasicTest.Thing>() {
            @Override
            public long applyAsLong(Thing thing) {
                return thing.getLongField();
            }
        });

        assertComparisons(things, comp, comparisons);
    }

    public void testDoubleComparator() {
        Thing[] things = new Thing[doubleValues.length];
        for (int i=0; i<doubleValues.length; i++)
            things[i] = new Thing(0, 0L, doubleValues[i], null);
        Comparator<Thing> comp = Comparators.comparing(new ToDoubleFunction<BasicTest.Thing>() {
            @Override
            public double applyAsDouble(Thing thing) {
                return thing.getDoubleField();
            }
        });

        assertComparisons(things, comp, comparisons);
    }

    public void testComparing() {
        Thing[] things = new Thing[doubleValues.length];
        for (int i=0; i<doubleValues.length; i++)
            things[i] = new Thing(0, 0L, 0.0, stringValues[i]);
        Comparator<Thing> comp = Comparators.comparing(new Function<Thing, String>() {
            @Override
            public String apply(Thing thing) {
                return thing.getStringField();
            }
        });

        assertComparisons(things, comp, comparisons);
    }

    public void testNaturalOrderComparator() {
        Comparator<String> comp = Comparators.naturalOrder();

        assertComparisons(stringValues, comp, comparisons);
    }

    public void testReverseComparator() {
        Comparator<String> cmpr = Comparators.reverseOrder();
        Comparator<String> cmp = cmpr.reverseOrder();

        assertEquals(cmp.reverseOrder(), cmpr);
        assertEquals(0, cmp.compare("a", "a"));
        assertEquals(0, cmpr.compare("a", "a"));
        assertTrue(cmp.compare("a", "b") < 0);
        assertTrue(cmpr.compare("a", "b") > 0);
        assertTrue(cmp.compare("b", "a") > 0);
        assertTrue(cmpr.compare("b", "a") < 0);
    }

    public void testReverseComparator2() {
        Comparator<String> cmp = (s1, s2) -> s1.length() - s2.length();
        Comparator<String> cmpr = cmp.reverseOrder();

        assertEquals(cmpr.reverseOrder(), cmp);
        assertEquals(0, cmp.compare("abc", "def"));
        assertEquals(0, cmpr.compare("abc", "def"));
        assertTrue(cmp.compare("abcd", "def") > 0);
        assertTrue(cmpr.compare("abcd", "def") < 0);
        assertTrue(cmp.compare("abc", "defg") < 0);
        assertTrue(cmpr.compare("abc", "defg") > 0);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testReverseComparatorNPE() {
        Comparator<String> cmp = Comparators.reverseOrder(null);
    }

    public void testComposeComparator() {
        // Longer string in front
        Comparator<String> first = (s1, s2) -> s2.length() - s1.length();
        Comparator<String> second = Comparators.naturalOrder();
        Comparator<String> composed = Comparators.compose(first, second);

        assertTrue(composed.compare("abcdefg", "abcdef") < 0);
        assertTrue(composed.compare("abcdef", "abcdefg") > 0);
        assertTrue(composed.compare("abcdef", "abcdef") == 0);
        assertTrue(composed.compare("abcdef", "ghijkl") < 0);
        assertTrue(composed.compare("ghijkl", "abcdefg") > 0);
    }

    private <K, V> void assertPairComparison(K k1, V v1, K k2, V v2,
                                        Comparator<Map.Entry<K, V>> ck,
                                        Comparator<Map.Entry<K, V>> cv) {
        final Map.Entry<K, V> p11 = new AbstractMap.SimpleImmutableEntry<>(k1, v1);
        final Map.Entry<K, V> p12 = new AbstractMap.SimpleImmutableEntry<>(k1, v2);
        final Map.Entry<K, V> p21 = new AbstractMap.SimpleImmutableEntry<>(k2, v1);
        final Map.Entry<K, V> p22 = new AbstractMap.SimpleImmutableEntry<>(k2, v2);

        assertTrue(ck.compare(p11, p11) == 0);
        assertTrue(ck.compare(p12, p11) == 0);
        assertTrue(ck.compare(p11, p12) == 0);
        assertTrue(ck.compare(p12, p22) < 0);
        assertTrue(ck.compare(p12, p21) < 0);
        assertTrue(ck.compare(p21, p11) > 0);
        assertTrue(ck.compare(p21, p12) > 0);

        assertTrue(cv.compare(p11, p11) == 0);
        assertTrue(cv.compare(p12, p11) > 0);
        assertTrue(cv.compare(p11, p12) < 0);
        assertTrue(cv.compare(p12, p22) == 0);
        assertTrue(cv.compare(p12, p21) > 0);
        assertTrue(cv.compare(p21, p11) == 0);
        assertTrue(cv.compare(p21, p12) < 0);

        Comparator<Map.Entry<K, V>> cmp = Comparators.compose(ck, cv);
        assertTrue(cmp.compare(p11, p11) == 0);
        assertTrue(cmp.compare(p12, p11) > 0);
        assertTrue(cmp.compare(p11, p12) < 0);
        assertTrue(cmp.compare(p12, p22) < 0);
        assertTrue(cmp.compare(p12, p21) < 0);
        assertTrue(cmp.compare(p21, p11) > 0);
        assertTrue(cmp.compare(p21, p12) > 0);

        cmp = Comparators.compose(cv, ck);
        assertTrue(cmp.compare(p11, p11) == 0);
        assertTrue(cmp.compare(p12, p11) > 0);
        assertTrue(cmp.compare(p11, p12) < 0);
        assertTrue(cmp.compare(p12, p22) < 0);
        assertTrue(cmp.compare(p12, p21) > 0);
        assertTrue(cmp.compare(p21, p11) > 0);
        assertTrue(cmp.compare(p21, p12) < 0);
    }

    public void testKVComparatorable() {
        assertPairComparison(1, "ABC", 2, "XYZ",
                         Comparators.<Integer, String>naturalOrderKeys(),
                         Comparators.<Integer, String>naturalOrderValues());
    }

    private static class People {
        final String firstName;
        final String lastName;
        final int age;

        People(String first, String last, int age) {
            firstName = first;
            lastName = last;
            this.age = age;
        }

        String getFirstName() { return firstName; }
        String getLastName() { return lastName; }
        int getAge() { return age; }
        long getAgeAsLong() { return (long) age; };
        double getAgeAsDouble() { return (double) age; };
    }

    private final People people[] = {
        new People("John", "Doe", 34),
        new People("Mary", "Doe", 30),
        new People("Maria", "Doe", 14),
        new People("Jonah", "Doe", 10),
        new People("John", "Cook", 54),
        new People("Mary", "Cook", 50),
    };

    public void testKVComparators() {
        // Comparator<People> cmp = Comparators.naturalOrder(); // Should fail to compiler as People is not comparable
        // We can use simple comparator, but those have been tested above.
        // Thus choose to do compose for some level of interation.
        Comparator<People> cmp1 = Comparators.comparing((Function<People, String>) People::getFirstName);
        Comparator<People> cmp2 = Comparators.comparing((Function<People, String>) People::getLastName);
        Comparator<People> cmp = Comparators.compose(cmp1, cmp2);

        assertPairComparison(people[0], people[0], people[1], people[1],
                         Comparators.<People, People>byKey(cmp),
                         Comparators.<People, People>byValue(cmp));

    }

    private <T> void assertComparison(Comparator<T> cmp, T less, T greater) {
        assertTrue(cmp.compare(less, greater) < 0, "less");
        assertTrue(cmp.compare(less, less) == 0, "equal");
        assertTrue(cmp.compare(greater, less) > 0, "greater");
    }

    public void testComparatorDefaultMethods() {
        Comparator<People> cmp = Comparators.comparing((Function<People, String>) People::getFirstName);
        Comparator<People> cmp2 = Comparators.comparing((Function<People, String>) People::getLastName);
        // reverseOrder
        assertComparison(cmp.reverseOrder(), people[1], people[0]);
        // thenComparing(Comparator)
        assertComparison(cmp.thenComparing(cmp2), people[0], people[1]);
        assertComparison(cmp.thenComparing(cmp2), people[4], people[0]);
        // thenComparing(Function)
        assertComparison(cmp.thenComparing(People::getLastName), people[0], people[1]);
        assertComparison(cmp.thenComparing(People::getLastName), people[4], people[0]);
        // thenComparing(ToIntFunction)
        assertComparison(cmp.thenComparing(People::getAge), people[0], people[1]);
        assertComparison(cmp.thenComparing(People::getAge), people[1], people[5]);
        // thenComparing(ToLongFunction)
        assertComparison(cmp.thenComparing(People::getAgeAsLong), people[0], people[1]);
        assertComparison(cmp.thenComparing(People::getAgeAsLong), people[1], people[5]);
        // thenComparing(ToDoubleFunction)
        assertComparison(cmp.thenComparing(People::getAgeAsDouble), people[0], people[1]);
        assertComparison(cmp.thenComparing(People::getAgeAsDouble), people[1], people[5]);
    }

    public void testGreaterOf() {
        // lesser
        assertSame(Comparators.greaterOf(Comparators.comparing(
                                    (Function<People, String>) People::getFirstName))
                              .apply(people[0], people[1]),
                   people[1]);
        // euqal
        assertSame(Comparators.greaterOf(Comparators.comparing(
                                    (Function<People, String>) People::getLastName))
                              .apply(people[0], people[1]),
                   people[0]);
        // greater
        assertSame(Comparators.greaterOf(Comparators.comparing(
                                    (ToIntFunction<People>) People::getAge))
                              .apply(people[0], people[1]),
                   people[0]);
    }

    public void testLesserOf() {
        // lesser
        assertSame(Comparators.lesserOf(Comparators.comparing(
                                    (Function<People, String>) People::getFirstName))
                              .apply(people[0], people[1]),
                   people[0]);
        // euqal
        assertSame(Comparators.lesserOf(Comparators.comparing(
                                    (Function<People, String>) People::getLastName))
                              .apply(people[0], people[1]),
                   people[0]);
        // greater
        assertSame(Comparators.lesserOf(Comparators.comparing(
                                    (ToIntFunction<People>) People::getAge))
                              .apply(people[0], people[1]),
                   people[1]);
    }

    public void testNulls() {
        try {
            Comparators.<String>naturalOrder().compare("abc", (String) null);
            fail("expected NPE with naturalOrder");
        } catch (NullPointerException npe) {}
        try {
            Comparators.<String>naturalOrder().compare((String) null, "abc");
            fail("expected NPE with naturalOrder");
        } catch (NullPointerException npe) {}

        try {
            Comparators.<String>reverseOrder().compare("abc", (String) null);
            fail("expected NPE with naturalOrder");
        } catch (NullPointerException npe) {}
        try {
            Comparators.<String>reverseOrder().compare((String) null, "abc");
            fail("expected NPE with naturalOrder");
        } catch (NullPointerException npe) {}

        try {
            Comparator<Map.Entry<String, String>> cmp = Comparators.byKey(null);
            fail("byKey(null) should throw NPE");
        } catch (NullPointerException npe) {}

        try {
            Comparator<Map.Entry<String, String>> cmp = Comparators.byValue(null);
            fail("byValue(null) should throw NPE");
        } catch (NullPointerException npe) {}

        try {
            Comparator<People> cmp = Comparators.comparing((Function<People, String>) null);
            fail("comparing(null) should throw NPE");
        } catch (NullPointerException npe) {}
        try {
            Comparator<People> cmp = Comparators.comparing((ToIntFunction<People>) null);
            fail("comparing(null) should throw NPE");
        } catch (NullPointerException npe) {}
        try {
            Comparator<People> cmp = Comparators.comparing((ToLongFunction<People>) null);
            fail("comparing(null) should throw NPE");
        } catch (NullPointerException npe) {}
        try {
            Comparator<People> cmp = Comparators.comparing((ToDoubleFunction<People>) null);
            fail("comparing(null) should throw NPE");
        } catch (NullPointerException npe) {}

        try {
            BinaryOperator<String> op = Comparators.lesserOf(null);
            fail("lesserOf(null) should throw NPE");
        } catch (NullPointerException npe) {}

        try {
            BinaryOperator<String> op = Comparators.greaterOf(null);
            fail("lesserOf(null) should throw NPE");
        } catch (NullPointerException npe) {}
    }
}
