/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358772
 * @summary Demonstrate the use of PrimitiveTypes form the Template Library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.examples.TestPrimitiveTypes
 */

package template_framework.examples;

import compiler.lib.template_framework.library.PrimitiveType;

public class TestPrimitiveTypes {
    public static void main(String[] args) {
        var l = PrimitiveType.PRIMITIVE_TYPES;
    }

    // TODO: write tests
    //
    // - use all functions and lists of types.
    // - use DataNames for sampling
    // - generate random constants with con
    // - cast to boxed types and back
    // - Use byteSize with MemorySegment -> check if correct via strides.
    // - isFloating -> check for rounding or something?
    // - boolean -> no size??
}
