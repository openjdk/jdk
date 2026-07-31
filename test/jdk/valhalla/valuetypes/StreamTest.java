/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic stream tests to iterate on nullable and null-restricted values
 * @enablePreview
 * @run junit/othervm StreamTest
 */

import jdk.internal.vm.annotation.NullRestricted;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StreamTest {

    static value class X {
        int x;
        X(int x) {
            this.x = x;
        }
        int x() {
            return x;
        }
    }

    static value class Point {
        public int x;
        public int y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static value class Value {
        int i;
        @NullRestricted
        Point p;
        Point nullable;
        List<X> list;
        Value(int i, Point/* Point! */ p, Point np, List<X> list) {
            this.i = i;
            this.p = p;
            this.nullable = np;
            this.list = list;
        }

        Point point() {
            return p;
        }

        Point nullablePoint() {
            return nullable;
        }

        int getI() { return i; }

        List<X> list() { return list; }
    }

    final Value[] values = init();
    private Value[] init() {
        Value[] values = new Value[10];
        for (int i = 0; i < 10; i++) {
            values[i] = new Value(i,
                                  new Point(i,i*2),
                                  (i%2) == 0 ? null : new Point(i*10, i*20),
                                  List.of(new X(i), new X(i*10)));
        }
        return values;
    }

    @Test
    public void testValues() {
        Arrays.stream(values)
              .filter(v -> (v.i % 2) == 0)
              .forEach(System.out::println);
    }

    @Test
    public void testNullRestrictedType() {
        Arrays.stream(values)
                .map(Value::point)
                .filter(p -> p.x >= 5)
                .forEach(System.out::println);

    }

    @Test
    public void testNullableValueType() {
        Arrays.stream(values)
                .map(Value::nullablePoint)
                .filter(p -> p != null)
                .forEach(System.out::println);
    }

    @Test
    public void mapToInt() {
        Stream<Point> stream = Arrays.stream(values)
                                     .filter(v -> (v.getI() % 2) == 0)
                                     .map(Value::point);
        stream.forEach(p -> assertTrue((p.x % 2) == 0));
    }

    @Test
    public void testListOfValues() {
        long count = Arrays.stream(values)
                           .map(Value::list)
                           .flatMap(List::stream)
                           .map(X::x)
                           .filter(x -> x >= 10)
                           .count();
        assertEquals(count, values.length-1);
    }
}
