/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit org.graalvm.compiler.options.test.TestOptionKey
 */



package org.graalvm.compiler.options.test;

import static org.graalvm.compiler.options.OptionValues.asMap;
import static org.graalvm.compiler.options.test.TestOptionKey.Options.MyOption;
import static org.graalvm.compiler.options.test.TestOptionKey.Options.MyOtherOption;
import static org.junit.Assert.assertEquals;

import org.graalvm.compiler.options.ModifiableOptionValues;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@SuppressWarnings("try")
public class TestOptionKey {

    public static class Options {
        public static final OptionKey<String> MyOption = new OptionKey<>("original");
        public static final OptionKey<String> MyOtherOption = new OptionKey<>("original");
    }

    @Test
    public void toStringTest() {
        OptionDescriptor.create("MyOption", OptionType.Debug, String.class, "", Options.class, "MyOption", MyOption);
        assertEquals("MyOption", MyOption.toString());
    }

    @Test
    public void missingDescriptorTest() {
        OptionValues options = new OptionValues(OptionValues.newOptionMap());
        Assume.assumeTrue(OptionValues.class.desiredAssertionStatus() == true);
        boolean sawAssertionError = false;
        try {
            MyOtherOption.getValue(options);
        } catch (AssertionError e) {
            sawAssertionError = true;
        }
        Assert.assertTrue(sawAssertionError);
    }

    /**
     * Tests that initial values are properly copied.
     */
    @Test
    public void testDerived() {
        OptionValues initialOptions = new ModifiableOptionValues(asMap(MyOption, "new value 1"));
        OptionValues derivedOptions = new OptionValues(initialOptions, MyOtherOption, "ignore");
        Assert.assertEquals("new value 1", MyOption.getValue(derivedOptions));

        initialOptions = new OptionValues(asMap(MyOption, "new value 1"));
        derivedOptions = new OptionValues(initialOptions, MyOtherOption, "ignore");
        Assert.assertEquals("new value 1", MyOption.getValue(derivedOptions));

    }

}
