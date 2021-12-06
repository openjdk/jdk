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

import compiler.lib.ir_framework.TestFramework;
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
    private static final List<VectorShape> SHAPE_LIST = List.of(
            VectorShape.S_64_BIT, VectorShape.S_128_BIT, VectorShape.S_256_BIT, VectorShape.S_512_BIT
    );
    private static final List<Class<?>> ETYPE_LIST = List.of(
            byte.class, short.class, int.class, long.class, float.class, double.class
    );

    public static void main(String[] args) {
        var expandShrink = new TestFramework(TestVectorExpandShrink.class);
        expandShrink.setDefaultWarmup(1);
        expandShrink.addHelperClasses(VectorReshapeHelper.class);
        expandShrink.addFlags("--add-modules=jdk.incubator.vector");
        var expandShrinkTests = String.join(",", SHAPE_LIST.stream()
                .flatMap(s -> SHAPE_LIST.stream()
                        .map(t -> VectorSpeciesPair.makePair(VectorSpecies.of(byte.class, s), VectorSpecies.of(byte.class, t))))
                .filter(p -> !p.isp().equals(p.osp()))
                .filter(p -> Math.max(p.isp().vectorBitSize(), p.osp().vectorBitSize()) <= VectorShape.preferredShape().vectorBitSize())
                .map(VectorSpeciesPair::format)
                .toList());
        expandShrink.addFlags("-DTest=" + expandShrinkTests);
        expandShrink.start();

        var doubleExpandShrink = new TestFramework(TestVectorDoubleExpandShrink.class);
        doubleExpandShrink.setDefaultWarmup(1);
        doubleExpandShrink.addHelperClasses(VectorReshapeHelper.class);
        doubleExpandShrink.addFlags("--add-modules=jdk.incubator.vector");
        doubleExpandShrink.addFlags("-DTest=" + expandShrinkTests);
        doubleExpandShrink.start();

        var rebracket = new TestFramework(TestVectorRebracket.class);
        rebracket.setDefaultWarmup(1);
        rebracket.addHelperClasses(VectorReshapeHelper.class);
        rebracket.addFlags("--add-modules=jdk.incubator.vector", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        var rebracketTests = String.join(",", SHAPE_LIST.stream()
                .filter(s -> s.vectorBitSize() <= VectorShape.preferredShape().vectorBitSize())
                .flatMap(shape -> ETYPE_LIST.stream()
                        .flatMap(etype -> ETYPE_LIST.stream()
                                .map(ftype -> VectorSpeciesPair.makePair(VectorSpecies.of(etype, shape), VectorSpecies.of(ftype, shape)))))
                .filter(p -> p.isp().length() > 1 && p.osp().length() > 1)
                .filter(p -> p.isp().elementType() != p.osp().elementType())
                .map(VectorSpeciesPair::format)
                .toList());
        rebracket.addFlags("-DTest=" + rebracketTests);
        rebracket.start();
    }
}
