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

/*
 * @test
 * @bug 8389452
 * @summary Test that contended annotations are ignored on value class layouts
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java ContendedValueClassInheritanceTest.java
 * @run main runtime.valhalla.inlinetypes.field_layout.ContendedValueClassInheritanceTest
 */

package runtime.valhalla.inlinetypes.field_layout;

import jdk.internal.vm.annotation.Contended;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

public class ContendedValueClassInheritanceTest {
    private static final int CONTENDED_PADDING_WIDTH = 128;

    static abstract value class Base {
        long base = 0;
    }

    @Contended
    static abstract value class ContendedBase {
        long base = 0;
    }

    static value class Value extends ContendedBase {
        int value = 0;
    }

    @Contended
    static value class ContendedValue extends Base {
        int value = 0;
    }

    static class Identity extends ContendedBase {
        int value;
    }

    @Contended
    static class ContendedIdentity extends Base {
        int value;
    }

    static class TestRunner {
        public static void main(String[] args) {
            new Value();
            new ContendedValue();
            new Identity();
            new ContendedIdentity();
        }
    }

    private static FieldLayoutAnalyzer.ClassLayout getLayout(FieldLayoutAnalyzer fla, Class<?> type) {
        String className = type.getName().replace('.', '/');
        FieldLayoutAnalyzer.ClassLayout layout = fla.getClassLayoutFromName(className);
        Asserts.assertNotNull(layout, "Missing layout for " + type.getName());
        return layout;
    }

    private static int contendedPaddingBlocks(FieldLayoutAnalyzer.ClassLayout layout) {
        int count = 0;
        for (FieldLayoutAnalyzer.FieldBlock block : layout.nonStaticFields) {
            if (block.type() == FieldLayoutAnalyzer.BlockType.PADDING && block.size() == CONTENDED_PADDING_WIDTH) {
                count++;
            }
        }
        return count;
    }

    private static void assertSameFieldLayout(FieldLayoutAnalyzer.ClassLayout first,
                                              FieldLayoutAnalyzer.ClassLayout second,
                                              String fieldName) {
        FieldLayoutAnalyzer.FieldBlock firstField = first.getFieldFromName(fieldName, false);
        FieldLayoutAnalyzer.FieldBlock secondField = second.getFieldFromName(fieldName, false);
        Asserts.assertEquals(firstField.offset(), secondField.offset(), fieldName + " offset");
        Asserts.assertEquals(firstField.size(), secondField.size(), fieldName + " size");
        Asserts.assertEquals(firstField.alignment(), secondField.alignment(), fieldName + " alignment");
    }

    private static void checkLayouts(FieldLayoutAnalyzer fla) {
        // Contended annotations should not affect value base classes.
        var base = getLayout(fla, Base.class);
        var contendedBase = getLayout(fla, ContendedBase.class);

        Asserts.assertEquals(base.instanceSize, contendedBase.instanceSize);
        assertSameFieldLayout(base, contendedBase, "base");
        Asserts.assertEquals(contendedPaddingBlocks(contendedBase), 0);

        // Neither inherited nor declared contended annotations should affect value subclasses.
        var value = getLayout(fla, Value.class);
        var contendedValue = getLayout(fla, ContendedValue.class);

        Asserts.assertEquals(value.instanceSize, contendedValue.instanceSize);
        Asserts.assertEquals(value.payloadSize, contendedValue.payloadSize);

        assertSameFieldLayout(value, contendedValue, "base");
        assertSameFieldLayout(value, contendedValue, "value");

        Asserts.assertEquals(contendedPaddingBlocks(value), 0);
        Asserts.assertEquals(contendedPaddingBlocks(contendedValue), 0);

        // Only the contended identity subclass gets leading and trailing padding.
        var identity = getLayout(fla, Identity.class);
        var contendedIdentity = getLayout(fla, ContendedIdentity.class);
        var identityField = identity.getFieldFromName("value", false);
        var contendedIdentityField = contendedIdentity.getFieldFromName("value", false);

        assertSameFieldLayout(identity, contendedIdentity, "base");

        Asserts.assertEquals(contendedIdentityField.offset(), identityField.offset() + CONTENDED_PADDING_WIDTH);
        Asserts.assertEquals(contendedIdentity.instanceSize, identity.instanceSize + 2 * CONTENDED_PADDING_WIDTH);

        Asserts.assertEquals(contendedPaddingBlocks(identity), 0);
        Asserts.assertEquals(contendedPaddingBlocks(contendedIdentity), 2);
    }

    public static void main(String[] args) throws Exception {
        var out = ProcessTools.executeTestJava(
                "--enable-preview",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintFieldLayout",
                "-XX:-RestrictContended",
                "-XX:ContendedPaddingWidth=" + CONTENDED_PADDING_WIDTH,
                "-Xshare:off",
                "-cp", System.getProperty("java.class.path"),
                TestRunner.class.getName());
        out.shouldHaveExitValue(0);

        FieldLayoutAnalyzer.LogOutput log = new FieldLayoutAnalyzer.LogOutput(out.asLines());
        FieldLayoutAnalyzer fla = FieldLayoutAnalyzer.createFieldLayoutAnalyzer(log);
        try {
            checkLayouts(fla);
            fla.check();
        } catch (Throwable t) {
            System.out.print(out.getOutput());
            throw t;
        }
    }
}
