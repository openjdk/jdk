/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8278784
 * @summary C2: Refactor PhaseIdealLoop::remix_address_expressions() so it operates on longs
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestRemixAddressExpressions
 */

public class TestRemixAddressExpressions {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float invPlusVarLshiftInt(int inv, int scale) {
        float res = 0;
        for (int i = 1; i < 100; i *= 11) {
            res += (i + inv) << scale;
        }
        return res;
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "1", IRNode.LSHIFT_L, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float invPlusVarLshiftLong(long inv, int scale) {
        float res = 0;
        for (long i = 1; i < 100; i *= 11) {
            res += (i + inv) << scale;
        }
        return res;
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.SUB_I, "1", IRNode.LSHIFT_I, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float invMinusVarLshiftInt(int inv, int scale) {
        float res = 0;
        for (int i = 1; i < 100; i *= 11) {
            res += (inv - i) << scale;
        }
        return res;
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "1", IRNode.SUB_L, "1", IRNode.LSHIFT_L, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float invMinusVarLshiftLong(long inv, int scale) {
        float res = 0;
        for (long i = 1; i < 100; i *= 11) {
            res += (inv - i) << scale;
        }
        return res;
    }

    @Test
    @IR(counts = { IRNode.ADD_I, "1", IRNode.SUB_I, "1", IRNode.LSHIFT_I, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float varMinusInvLshiftInt(int inv, int scale) {
        float res = 0;
        for (int i = 1; i < 100; i *= 11) {
            res += (i - inv) << scale;
        }
        return res;
    }

    @Test
    @IR(counts = { IRNode.ADD_L, "1", IRNode.SUB_L, "1", IRNode.LSHIFT_L, "2" })
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static float varMinusInvLshiftLong(long inv, int scale) {
        float res = 0;
        for (long i = 1; i < 100; i *= 11) {
            res += (i - inv) << scale;
        }
        return res;
    }
}
