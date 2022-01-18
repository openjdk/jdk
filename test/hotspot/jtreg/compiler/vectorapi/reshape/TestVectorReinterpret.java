/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi.reshape;

import compiler.vectorapi.reshape.tests.TestVectorDoubleExpandShrink;
import compiler.vectorapi.reshape.tests.TestVectorExpandShrink;
import compiler.vectorapi.reshape.tests.TestVectorRebracket;
import compiler.vectorapi.reshape.utils.VectorReshapeHelper;
import compiler.vectorapi.reshape.utils.VectorSpeciesPair;
import java.util.List;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8259610
 * @modules jdk.incubator.vector
 * @modules java.base/jdk.internal.misc
 * @summary Test that vector reinterpret intrinsics work as intended.
 * @library /test/lib /
 * @run driver compiler.vectorapi.reshape.TestVectorReinterpret
 */
public class TestVectorReinterpret {
    private static final List<VectorShape> SHAPE_LIST = List.of(VectorShape.values());
    private static final List<Class<?>> ETYPE_LIST = List.of(
            byte.class, short.class, int.class, long.class, float.class, double.class
    );

    public static void main(String[] args) {
        VectorReshapeHelper.runMainHelper(
                TestVectorExpandShrink.class,
                SHAPE_LIST.stream()
                        .flatMap(s -> SHAPE_LIST.stream()
                                .filter(t -> t.vectorBitSize() != s.vectorBitSize())
                                .map(t -> VectorSpeciesPair.makePair(VectorSpecies.of(byte.class, s),
                                        VectorSpecies.of(byte.class, t))))
        );

        VectorReshapeHelper.runMainHelper(
                TestVectorDoubleExpandShrink.class,
                SHAPE_LIST.stream()
                        .flatMap(s -> SHAPE_LIST.stream()
                                .filter(t -> t.vectorBitSize() != s.vectorBitSize())
                                .map(t -> VectorSpeciesPair.makePair(VectorSpecies.of(byte.class, s),
                                        VectorSpecies.of(byte.class, t))))
        );

        VectorReshapeHelper.runMainHelper(
                TestVectorRebracket.class,
                SHAPE_LIST.stream()
                        .flatMap(shape -> ETYPE_LIST.stream()
                                .flatMap(etype -> ETYPE_LIST.stream()
                                        .filter(ftype -> ftype != etype)
                                        .map(ftype -> VectorSpeciesPair.makePair(VectorSpecies.of(etype, shape),
                                                VectorSpecies.of(ftype, shape)))))
                        .filter(p -> p.isp().length() > 1 && p.osp().length() > 1)
        );
    }
}
