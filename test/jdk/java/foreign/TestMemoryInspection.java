
/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestMemoryInspection
 */

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.foreign.MemoryInspection;
import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.*;
import static java.util.stream.Collectors.joining;
import static org.testng.Assert.*;
import static java.util.Objects.requireNonNull;

@Test
public class TestMemoryInspection {

    private static final String EXPECT_ADDRESS = "0x" + "00".repeat((int) ValueLayout.ADDRESS.byteSize());

    @Test
    public void valueLayouts() {

        record TestInput(ValueLayout layout, String stringValue) {
        }

        List.of(
                new TestInput(ValueLayout.JAVA_BYTE, "0"),
                new TestInput(ValueLayout.JAVA_SHORT, "0"),
                new TestInput(ValueLayout.JAVA_INT, "0"),
                new TestInput(ValueLayout.JAVA_LONG, "0"),
                new TestInput(ValueLayout.JAVA_FLOAT, "0.0"),
                new TestInput(ValueLayout.JAVA_DOUBLE, "0.0"),
                new TestInput(ValueLayout.JAVA_CHAR, "" + (char) 0),
                new TestInput(JAVA_BOOLEAN, "false"),
                new TestInput(ValueLayout.ADDRESS, EXPECT_ADDRESS)
        ).forEach(ti -> {
            var expect = ti.layout() + "=" + ti.stringValue();
            var actual = testWithFreshMemorySegment(ti.layout().byteSize(), s -> jdk.internal.foreign.MemoryInspection.inspect(s, ti.layout(), jdk.internal.foreign.MemoryInspection.standardRenderer()))
                    .collect(joining(System.lineSeparator()));
            assertEquals(actual, expect);
        });
    }

    @Test
    public void point() {

        var expect = platformLineSeparated("""
                Point {
                    x=1,
                    y=2
                }""");

        var actual = testWithFreshMemorySegment(Integer.BYTES * 2, segment -> {
            final Point point = new Point(segment);
            point.x(1);
            point.y(2);
            return jdk.internal.foreign.MemoryInspection.inspect(segment, Point.LAYOUT, jdk.internal.foreign.MemoryInspection.standardRenderer())
                    .collect(joining(System.lineSeparator()));
        });

        assertEquals(actual, expect);
    }

    @Test
    public void pointCustomRenderer() {

        var expect = platformLineSeparated("""
                Point {
                    x=0x0001,
                    y=0x0002
                }""");

        var actual = testWithFreshMemorySegment(Integer.BYTES * 2, segment -> {
            final Point point = new Point(segment);
            point.x(1);
            point.y(2);
            return MemoryInspection.inspect(segment, Point.LAYOUT, new BiFunction<ValueLayout, Object, String>() {

                        @Override
                        public String apply(ValueLayout layout, Object o) {
                            return String.format("0x%04x", (int)o);
                        }
                    })
                    .collect(joining(System.lineSeparator()));
        });

        assertEquals(actual, expect);
    }

    @Test
    public void standardCustomRenderer() {

        MemoryLayout layout = MemoryLayout.structLayout(
                // These are in bit alignment order (descending) for all platforms
                // in order for each element to be aligned to its type's bit alignment.
                Stream.of(
                                JAVA_LONG,
                                JAVA_DOUBLE,
                                ADDRESS,
                                JAVA_INT,
                                JAVA_FLOAT,
                                JAVA_SHORT,
                                JAVA_CHAR,
                                JAVA_BOOLEAN,
                                JAVA_BYTE
                        )
                        .map(vl -> vl.withName(vl.carrier().getSimpleName()))
                        .toArray(MemoryLayout[]::new)
        ).withName("struct");

        System.out.println("layout = " + layout);
        var expect = platformLineSeparated("""
                struct {
                    long=0,
                    double=0.0,
                    MemorySegment=$1,
                    int=0,
                    float=0.0,
                    short=0,
                    char=\u0000,
                    boolean=false,
                    byte=0
                }""").replace("$1", EXPECT_ADDRESS);


        var actual = testWithFreshMemorySegment(layout.byteSize(), segment ->
                jdk.internal.foreign.MemoryInspection.inspect(segment, layout, jdk.internal.foreign.MemoryInspection.standardRenderer()))
                .collect(joining(System.lineSeparator()));

        assertEquals(actual, expect);
    }


    @Test
    public void sequence() {
        final int arraySize = 4;
        var sequenceLayout = MemoryLayout.sequenceLayout(arraySize,
                MemoryLayout.structLayout(
                        ValueLayout.JAVA_INT.withName("x"),
                        ValueLayout.JAVA_INT.withName("y")
                ).withName("Point")
        ).withName("PointArrayOfElements");

        var xh = sequenceLayout.varHandle(PathElement.sequenceElement(), PathElement.groupElement("x"));
        var yh = sequenceLayout.varHandle(PathElement.sequenceElement(), PathElement.groupElement("y"));

        var expect = platformLineSeparated("""
                PointArrayOfElements [
                    Point {
                        x=1,
                        y=0
                    },
                    Point {
                        x=1,
                        y=1
                    },
                    Point {
                        x=1,
                        y=2
                    },
                    Point {
                        x=1,
                        y=3
                    }
                ]""");
        var actual = testWithFreshMemorySegment(Integer.BYTES * 2 * arraySize, segment -> {
            for (long i = 0; i < sequenceLayout.elementCount(); i++) {
                xh.set(segment, i, 1);
                yh.set(segment, i, (int) i);
            }

            return jdk.internal.foreign.MemoryInspection.inspect(segment, sequenceLayout, jdk.internal.foreign.MemoryInspection.standardRenderer())
                .collect(joining(System.lineSeparator()));}
        );
        assertEquals(actual, expect);
    }


    @Test
    public void union() {
        var u0 = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("x"),
                ValueLayout.JAVA_INT.withName("y"),
                MemoryLayout.paddingLayout(Integer.SIZE)
        ).withName("Point");

        var u1 = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("x"),
                ValueLayout.JAVA_INT.withName("y"),
                ValueLayout.JAVA_INT.withName("z")
        ).withName("3D-Point");

        var union = MemoryLayout.unionLayout(u0, u1).withName("Union");

        var expect = platformLineSeparated("""
                Union {
                    Point {
                        x=1,
                        y=2,
                        32 padding bits
                    }|
                    3D-Point {
                        x=1,
                        y=2,
                        z=3
                    }
                }""");
        var actual = testWithFreshMemorySegment(Integer.BYTES * 3, segment -> {
            u0.varHandle(PathElement.groupElement("x")).set(segment, 1);
            u1.varHandle(PathElement.groupElement("y")).set(segment, 2);
            u1.varHandle(PathElement.groupElement("z")).set(segment, 3);
            return jdk.internal.foreign.MemoryInspection.inspect(segment, union, jdk.internal.foreign.MemoryInspection.standardRenderer())
                    .collect(joining(System.lineSeparator()));
        });

        assertEquals(actual, expect);
    }

    static final class Point {

        static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("x"),
                ValueLayout.JAVA_INT.withName("y")
        ).withName("Point");

        static final VarHandle xVH = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
        static final VarHandle yVH = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

        private final MemorySegment memorySegment;

        Point(MemorySegment memorySegment) {
            this.memorySegment = requireNonNull(memorySegment);
        }

        int x() {
            return (int) xVH.get(memorySegment);
        }

        int y() {
            return (int) yVH.get(memorySegment);
        }

        void x(int x) {
            xVH.set(memorySegment, x);
        }

        void y(int y) {
            yVH.set(memorySegment, y);
        }

        @Override
        public String toString() {
            return "Point {x=" + x() + ", y=" + y() + "}";
        }
    }

    private static String platformLineSeparated(String s) {
        return s.lines()
                .collect(joining(System.lineSeparator()));
    }

    private static <T> T testWithFreshMemorySegment(long size,
                                                    Function<MemorySegment, T> mapper) {
        try (final Arena arena = Arena.ofConfined()) {
            var segment = arena.allocate(size);
            return mapper.apply(segment);
        }
    }

}
