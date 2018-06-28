/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;

import jdk.vm.ci.meta.JavaKind;

@RunWith(Parameterized.class)
public class ReinterpretStampFloatToIntTest extends ReinterpretStampTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();

        for (int x : interestingInts) {
            float lowerBound = Float.intBitsToFloat(x);
            if (Float.isNaN(lowerBound)) {
                continue;
            }

            for (int y : interestingInts) {
                float upperBound = Float.intBitsToFloat(y);
                if (Float.isNaN(upperBound)) {
                    continue;
                }

                if (Float.compare(lowerBound, upperBound) <= 0) {
                    ret.add(new Object[]{new FloatStamp(Float.SIZE, lowerBound, upperBound, true)});
                    ret.add(new Object[]{new FloatStamp(Float.SIZE, lowerBound, upperBound, false)});
                }
            }
        }

        ret.add(new Object[]{new FloatStamp(Float.SIZE, Float.NaN, Float.NaN, false)});

        return ret;
    }

    @Parameter(value = 0) public FloatStamp inputStamp;

    @Test
    public void run() {
        ParameterNode param = new ParameterNode(0, StampPair.createSingle(inputStamp));
        ValueNode reinterpret = ReinterpretNode.create(JavaKind.Int, param, NodeView.DEFAULT);
        reinterpret.inferStamp();

        IntegerStamp resultStamp = (IntegerStamp) reinterpret.stamp(NodeView.DEFAULT);
        Assert.assertEquals(Integer.SIZE, resultStamp.getBits());

        for (int result : interestingInts) {
            float input = Float.intBitsToFloat(result);

            if (inputStamp.contains(input) && !resultStamp.contains(result)) {
                Assert.fail(String.format("value %f (0x%x) is in input stamp, but not in result stamp (%s)", input, result, resultStamp));
            }
        }
    }
}
