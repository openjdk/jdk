/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;

/**
 * @test
 * @bug 8391743
 * @summary Test that C2 propagates escape state to value types returned as
 *          fields.
 * @library /test/lib /
 * @enablePreview
 * @run driver ${test.main.class}
 */

class TestValueReturnEscapeState {
    public static void main(String[] args) {
        new TestFramework()
            .addScenarios(new Scenario(0, "--enable-preview", "-XX:+ValueTypeReturnedAsFields"),
                          new Scenario(1, "--enable-preview", "-XX:-ValueTypeReturnedAsFields"))
            .start();
    }

    static class IdentityObject {
        public int val;
    }

    static value class ValueHolder {
        public IdentityObject c;
        ValueHolder(IdentityObject c) {
            this.c = c;
        }
    }

    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static ValueHolder testReturnEscape() {
        IdentityObject obj = new IdentityObject();
        synchronized (obj) {
            obj.val = 42;
        }
        return new ValueHolder(obj);
    }

    @Test
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static ValueHolder testReturnNoEscape() {
        IdentityObject obj = new IdentityObject();
        synchronized (obj) {
            obj.val = 42;
        }
        return new ValueHolder(new IdentityObject());
    }
}
