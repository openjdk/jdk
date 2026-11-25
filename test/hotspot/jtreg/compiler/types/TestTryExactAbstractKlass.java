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
package compiler.types;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8394225
 * @summary Multiple cases where a TypeInstPtr is constructed with an abstract instance_klass and
 *          klass_is_exact.
 * @library /test/lib /
 * @enablePreview
 * @run driver ${test.main.class}
 */
public class TestTryExactAbstractKlass {
    private static abstract value class A {}

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("--enable-preview");
        framework.start();
    }

    @Test
    @IR(counts = {IRNode.IF, "2"})
    private static A testMethodSignature(A[] array) {
        // Should know that A[] must not be flat
        return array[0];
    }

    @Run(test = "testMethodSignature")
    public void runMethodSignature() {
        Asserts.assertNull(testMethodSignature(new A[1]));
    }

    @Test
    private static A testFromKlass(Object o) {
        return (A) o;
    }

    @Run(test = "testFromKlass")
    public void runFromKlass() {
        Asserts.assertNull(testFromKlass(null));
    }

    @Test
    private static A testNewInstance(A obj) throws ReflectiveOperationException {
        if (obj == null) {
            return null;
        }

        return obj.getClass().getConstructor().newInstance();
    }

    @Run(test = "testNewInstance")
    public void runNewInstance() throws ReflectiveOperationException {
        Asserts.assertNull(testNewInstance(null));
    }
}
