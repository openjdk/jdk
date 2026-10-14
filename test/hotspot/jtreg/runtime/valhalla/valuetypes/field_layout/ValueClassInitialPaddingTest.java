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
 * @summary Verify that value subclasses preserve initial padding and identity subclasses reuse it
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @enablePreview
 * @library /test/lib
 * @run driver runtime.valhalla.valuetypes.field_layout.ValueClassInitialPaddingTest
 */

package runtime.valhalla.valuetypes.field_layout;

import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ValueClassInitialPaddingTest {
    static abstract value class AbstractValue {
        byte b = 0;
    }

    static abstract value class AbstractMiddle extends AbstractValue {}

    static value class ConcreteValue extends AbstractMiddle {
        int i = 0;
    }

    static class IdentityMiddle extends AbstractValue {}

    static class IdentityLeaf extends IdentityMiddle {
        int i;
    }

    static class IdentityMiddleWithField extends AbstractValue {
        int middleField;
    }

    static class IdentityLeafWithField extends IdentityMiddleWithField {}

    static class TestMain {
        public static void main(String[] args) {
            new ConcreteValue();
            new IdentityLeaf();
            new IdentityLeafWithField();
        }
    }

    static void checkInitialLayout(OutputAnalyzer output, Class<?> klass, String initialBlock) {
        String className = klass.getName().replace('.', '/');
        output.shouldMatch("Layout of class " + Pattern.quote(className)
                + "@.*\\RInstance fields:"
                + "\\R @0 RESERVED 12/-"
                + "\\R @12 " + Pattern.quote(initialBlock)
                + "\\R @16 INHERITED 1/1 \"b\" B");
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder process = ProcessTools.createLimitedTestJavaProcessBuilder(
                "--enable-preview",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintFieldLayout",
                "-XX:-UseCompactObjectHeaders",
                TestMain.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(process.start());
        output.shouldHaveExitValue(0);

        checkInitialLayout(output, AbstractMiddle.class, "PADDING 4/1");
        checkInitialLayout(output, ConcreteValue.class, "PADDING 4/1");
        checkInitialLayout(output, IdentityMiddle.class, "EMPTY 4/1");
        checkInitialLayout(output, IdentityLeaf.class, "REGULAR 4/4 \"i\" I");
        checkInitialLayout(output, IdentityMiddleWithField.class, "REGULAR 4/4 \"middleField\" I");
        checkInitialLayout(output, IdentityLeafWithField.class, "INHERITED 4/4 \"middleField\" I");
    }
}
