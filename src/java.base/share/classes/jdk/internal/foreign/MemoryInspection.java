/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */
package jdk.internal.foreign;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.Buffer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static jdk.internal.foreign.MemoryInspectionUtil.*;

/**
 * Class that supports inspection of MemorySegments through MemoryLayouts.
 * <p>
 * Memory abstractions such as ByteBuffers and byte arrays can be inspected via wrapping methods
 * such as {@link MemorySegment#ofArray(byte[])} and {@link MemorySegment#ofBuffer(Buffer)}.
 *
 * @since 20
 */
public final class MemoryInspection {

    // Suppresses default constructor, ensuring non-instantiability.
    private MemoryInspection() {
    }

    /**
     * Returns a human-readable view of the provided {@linkplain MemorySegment memory} viewed
     * through the provided {@linkplain MemoryLayout layout} using the provided {@code renderer}.
     * <p>
     * The exact format of the returned view is unspecified and should not
     * be acted upon programmatically.
     * <p>
     * As an example, a MemorySegment viewed though the following memory layout
     * {@snippet lang = java:
     * var layout = MemoryLayout.structLayout(
     *         ValueLayout.JAVA_INT.withName("x"),
     *         ValueLayout.JAVA_INT.withName("y")
     * ).withName("Point");
     *
     * MemoryInspection.inspect(segment, layout, ValueLayoutRenderer.standard())
     *     .forEach(System.out::println);
     *
     *}
     * might be rendered to something like this:
     * {@snippet lang = text:
     * Point {
     *   x=1,
     *   y=2
     * }
     *}
     * <p>
     * This method is intended to view memory segments through small and medium-sized memory layouts.
     *
     * @param segment  to be viewed
     * @param layout   to use as a layout when viewing the memory segment
     * @param renderer to apply when rendering value layouts
     * @return a view of the memory abstraction viewed through the memory layout
     */
    public static Stream<String> inspect(MemorySegment segment,
                                         MemoryLayout layout,
                                         BiFunction<ValueLayout, Object, String> renderer) {
        requireNonNull(segment);
        requireNonNull(layout);
        requireNonNull(renderer);
        return MemoryInspectionUtil.inspect(segment, layout, renderer);
    }

    /**
     * {@return a standard value layout renderer that will render numeric values into decimal form and where
     * other value types are rendered to a reasonable "natural" form}
     * <p>
     * More specifically, values types are rendered as follows:
     * <ul>
     *     <li>Numeric values are rendered in decimal form (e.g 1 or 1.2).</li>
     *     <li>Boolean values are rendered as {@code true} or {@code false}.</li>
     *     <li>Character values are rendered as {@code char}.</li>
     *     <li>Address values are rendered in hexadecimal form e.g. {@code 0x0000000000000000} (on 64-bit platforms) or
     *     {@code 0x00000000} (on 32-bit platforms)</li>
     * </ul>
     */
    public static BiFunction<ValueLayout, Object, String> standardRenderer() {
        return STANDARD_VALUE_LAYOUT_RENDERER;
    }

}
