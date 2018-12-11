/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.tiers.Suites;

public class EnumSwitchTest extends GraalCompilerTest {

    enum E {
        E0,
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E8,
        E9,
        E10,
        E11,
        E12,
        E13,
        E14,
        E15,
        E16,
        E17,
        E18,
        E19,
        E20
    }

    public int test1Snippet(E e) {
        switch (e) {
            case E0:
                return 0;
            case E1:
                return 1;
            case E2:
                return 2;
            case E3:
                return 3;
            case E4:
                return 4;
            case E5:
                return 5;
            case E6:
                return 6;
            case E7:
                return 7;
            case E8:
                return 8;
            case E9:
                return 9;
            case E10:
                return 10;
            case E11:
                return 11;
            case E12:
                return 12;
            case E13:
                return 13;
            case E14:
                return 14;
            case E15:
                return 15;
            case E16:
                return 16;
            case E17:
                return 17;
            case E18:
                return 18;
            case E19:
                return 19;
            case E20:
                return 20;
            default:
                return -1;
        }
    }

    @Test
    public void test1() {
        for (E e : E.values()) {
            test("test1Snippet", e);
        }
        test("test1Snippet", new Object[]{null});
    }

    public int test2Snippet(E e) {
        switch (e) {
            case E5:
            case E19:
            case E20:
                return 1;
            case E8:
            case E9:
            case E10:
                return 2;
        }
        return -1;
    }

    @Test
    public void test2() {
        for (E e : E.values()) {
            test("test2Snippet", e);
        }
        test("test2Snippet", new Object[]{null});
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites ret = super.createSuites(options);
        ret.getHighTier().prependPhase(new Phase() {
            @Override
            protected void run(StructuredGraph graph) {
                /* Array load from the enum switch map. */
                assertTrue(graph.getNodes().filter(LoadIndexedNode.class).count() == 1);
                /* The actual switch. */
                assertTrue(graph.getNodes().filter(IntegerSwitchNode.class).count() == 1);
            }

            @Override
            protected CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        ret.getHighTier().findPhase(RemoveValueProxyPhase.class).add(new Phase() {
            @Override
            protected void run(StructuredGraph graph) {
                /* Re-writing of the switch cases eliminates the array load. */
                assertTrue(graph.getNodes().filter(LoadIndexedNode.class).count() == 0);
                /* The switch is still there. */
                assertTrue(graph.getNodes().filter(IntegerSwitchNode.class).count() == 1);
            }

            @Override
            protected CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        return ret;
    }
}
