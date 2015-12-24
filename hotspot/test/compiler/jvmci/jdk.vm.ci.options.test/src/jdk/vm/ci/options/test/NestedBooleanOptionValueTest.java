/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @run junit jdk.vm.ci.options.test.NestedBooleanOptionValueTest
 */

package jdk.vm.ci.options.test;

import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.Master0;
import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.Master1;
import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.Master2;
import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.NestedOption0;
import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.NestedOption1;
import static jdk.vm.ci.options.test.NestedBooleanOptionValueTest.Options.NestedOption2;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import jdk.vm.ci.options.NestedBooleanOptionValue;
import jdk.vm.ci.options.OptionDescriptor;
import jdk.vm.ci.options.OptionValue;
import jdk.vm.ci.options.OptionValue.OverrideScope;

import org.junit.Test;

public class NestedBooleanOptionValueTest {

    public static class Options {
        public static final OptionValue<Boolean> Master0 = new OptionValue<>(true);
        public static final OptionValue<Boolean> NestedOption0 = new NestedBooleanOptionValue(Master0, true);
        public static final OptionValue<Boolean> Master1 = new OptionValue<>(true);
        public static final OptionValue<Boolean> NestedOption1 = new NestedBooleanOptionValue(Master1, true);
        public static final OptionValue<Boolean> Master2 = new OptionValue<>(true);
        public static final OptionValue<Boolean> NestedOption2 = new NestedBooleanOptionValue(Master2, false);
    }

    static final OptionDescriptor master0 = OptionDescriptor.create("Master0", Boolean.class, "", Options.class, "Master0", Master0);
    static final OptionDescriptor nestedOption0 = OptionDescriptor.create("NestedOption0", Boolean.class, "", Options.class, "NestedOption0", NestedOption0);
    static final OptionDescriptor master1 = OptionDescriptor.create("Master1", Boolean.class, "", Options.class, "Master1", Master1);
    static final OptionDescriptor nestedOption1 = OptionDescriptor.create("NestedOption1", Boolean.class, "", Options.class, "NestedOption1", NestedOption1);
    static final OptionDescriptor master2 = OptionDescriptor.create("Master2", Boolean.class, "", Options.class, "Master2", Master2);
    static final OptionDescriptor nestedOption2 = OptionDescriptor.create("NestedOption2", Boolean.class, "", Options.class, "NestedOption2", NestedOption2);

    @SuppressWarnings("try")
    @Test
    public void runOverrides() {
        assertTrue(Master0.getValue());
        assertTrue(NestedOption0.getValue());
        try (OverrideScope s1 = OptionValue.override(Master0, false)) {
            assertFalse(Master0.getValue());
            assertFalse(NestedOption0.getValue());
            try (OverrideScope s2 = OptionValue.override(NestedOption0, false)) {
                assertFalse(NestedOption0.getValue());
            }
            try (OverrideScope s2 = OptionValue.override(NestedOption0, true)) {
                assertTrue(NestedOption0.getValue());
            }
        }
        assertTrue(Master0.getValue());
        try (OverrideScope s1 = OptionValue.override(NestedOption0, false)) {
            assertFalse(NestedOption0.getValue());
        }
        try (OverrideScope s1 = OptionValue.override(NestedOption0, true)) {
            assertTrue(NestedOption0.getValue());
        }
    }

    @Test
    public void runDefaultTrue() {
        Master1.setValue(true);
        assertTrue(Master1.getValue());
        assertTrue(NestedOption1.getValue());
        // nested value unset
        Master1.setValue(false);
        assertFalse(Master1.getValue());
        assertFalse(NestedOption1.getValue());
        // set false
        Master1.setValue(false);
        NestedOption1.setValue(false);
        assertFalse(Master1.getValue());
        assertFalse(NestedOption1.getValue());
        Master1.setValue(true);
        assertTrue(Master1.getValue());
        assertFalse(NestedOption1.getValue());
        // set true
        Master1.setValue(false);
        NestedOption1.setValue(true);
        assertFalse(Master1.getValue());
        assertTrue(NestedOption1.getValue());
        Master1.setValue(true);
        assertTrue(Master1.getValue());
        assertTrue(NestedOption1.getValue());
    }

    @Test
    public void runDefaultFalse() {
        Master2.setValue(true);
        assertTrue(Master2.getValue());
        assertFalse(NestedOption2.getValue());
        // nested value unset
        Master2.setValue(false);
        assertFalse(Master2.getValue());
        assertFalse(NestedOption2.getValue());
        // set false
        Master2.setValue(false);
        NestedOption2.setValue(false);
        assertFalse(Master2.getValue());
        assertFalse(NestedOption2.getValue());
        Master2.setValue(true);
        assertTrue(Master2.getValue());
        assertFalse(NestedOption2.getValue());
        // set true
        Master2.setValue(false);
        NestedOption2.setValue(true);
        assertFalse(Master2.getValue());
        assertTrue(NestedOption2.getValue());
        Master2.setValue(true);
        assertTrue(Master2.getValue());
        assertTrue(NestedOption2.getValue());
    }

}
