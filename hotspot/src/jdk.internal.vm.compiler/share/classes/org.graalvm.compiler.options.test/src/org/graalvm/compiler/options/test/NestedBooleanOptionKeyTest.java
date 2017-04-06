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
 * @run junit jdk.vm.ci.options.test.NestedBooleanOptionKeyTest
 */

package org.graalvm.compiler.options.test;

import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.Master0;
import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.Master1;
import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.Master2;
import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption0;
import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption1;
import static org.graalvm.compiler.options.test.NestedBooleanOptionKeyTest.Options.NestedOption2;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

public class NestedBooleanOptionKeyTest {

    public static class Options {
        public static final OptionKey<Boolean> Master0 = new OptionKey<>(true);
        public static final OptionKey<Boolean> NestedOption0 = new NestedBooleanOptionKey(Master0, true);
        public static final OptionKey<Boolean> Master1 = new OptionKey<>(true);
        public static final OptionKey<Boolean> NestedOption1 = new NestedBooleanOptionKey(Master1, true);
        public static final OptionKey<Boolean> Master2 = new OptionKey<>(true);
        public static final OptionKey<Boolean> NestedOption2 = new NestedBooleanOptionKey(Master2, false);
    }

    static final OptionDescriptor master0 = OptionDescriptor.create("Master0", Boolean.class, "", Options.class, "Master0", Master0);
    static final OptionDescriptor nestedOption0 = OptionDescriptor.create("NestedOption0", Boolean.class, "", Options.class, "NestedOption0", NestedOption0);
    static final OptionDescriptor master1 = OptionDescriptor.create("Master1", Boolean.class, "", Options.class, "Master1", Master1);
    static final OptionDescriptor nestedOption1 = OptionDescriptor.create("NestedOption1", Boolean.class, "", Options.class, "NestedOption1", NestedOption1);
    static final OptionDescriptor master2 = OptionDescriptor.create("Master2", Boolean.class, "", Options.class, "Master2", Master2);
    static final OptionDescriptor nestedOption2 = OptionDescriptor.create("NestedOption2", Boolean.class, "", Options.class, "NestedOption2", NestedOption2);

    @Test
    public void runDefaultTrue() {
        OptionValues options = new OptionValues(null, Master1, true);
        assertTrue(Master1.getValue(options));
        assertTrue(NestedOption1.getValue(options));

        // nested value unset
        options = new OptionValues(null, Master1, false);
        assertFalse(Master1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        // set false
        options = new OptionValues(null, Master1, false, NestedOption1, false);
        assertFalse(Master1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        options = new OptionValues(null, Master1, true, NestedOption1, false);
        assertTrue(Master1.getValue(options));
        assertFalse(NestedOption1.getValue(options));

        // set true
        options = new OptionValues(null, Master1, false, NestedOption1, true);
        assertFalse(Master1.getValue(options));
        assertTrue(NestedOption1.getValue(options));

        options = new OptionValues(null, Master1, true, NestedOption1, true);
        assertTrue(Master1.getValue(options));
        assertTrue(NestedOption1.getValue(options));
    }

    @Test
    public void runDefaultFalse() {
        OptionValues options = new OptionValues(null, Master2, true);
        assertTrue(Master2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // nested value unset
        options = new OptionValues(null, Master2, false);
        assertFalse(Master2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // set false
        options = new OptionValues(null, Master2, false, NestedOption2, false);
        assertFalse(Master2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        options = new OptionValues(null, Master2, true, NestedOption2, false);
        assertTrue(Master2.getValue(options));
        assertFalse(NestedOption2.getValue(options));

        // set true
        options = new OptionValues(null, Master2, false, NestedOption2, true);
        assertFalse(Master2.getValue(options));
        assertTrue(NestedOption2.getValue(options));

        options = new OptionValues(null, Master2, true, NestedOption2, true);
        assertTrue(Master2.getValue(options));
        assertTrue(NestedOption2.getValue(options));
    }
}
