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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;

import jdk.vm.ci.meta.JavaKind;

@RunWith(Parameterized.class)
public class ReinterpretStampLongToDoubleTest extends ReinterpretStampTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();

        for (long lowerBound : interestingLongs) {
            for (long upperBound : interestingLongs) {
                if (lowerBound <= upperBound) {
                    ret.add(new Object[]{StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound)});
                }
                if ((lowerBound & ~upperBound) == 0) {
                    ret.add(new Object[]{IntegerStamp.stampForMask(Long.SIZE, lowerBound, upperBound)});
                }
            }
        }

        return ret;
    }

    @Parameter(value = 0) public IntegerStamp inputStamp;

    @Test
    public void run() {
        ParameterNode param = new ParameterNode(0, StampPair.createSingle(inputStamp));
        ReinterpretNode reinterpret = new ReinterpretNode(JavaKind.Double, param);
        reinterpret.inferStamp();

        FloatStamp resultStamp = (FloatStamp) reinterpret.stamp();
        Assert.assertEquals(Double.SIZE, resultStamp.getBits());

        for (long input : interestingLongs) {
            double result = Double.longBitsToDouble(input);

            if (inputStamp.contains(input) && !resultStamp.contains(result)) {
                Assert.fail(String.format("value 0x%x (%f) is in input stamp, but not in result stamp (%s)", input, result, resultStamp));
            }
        }
    }
}
