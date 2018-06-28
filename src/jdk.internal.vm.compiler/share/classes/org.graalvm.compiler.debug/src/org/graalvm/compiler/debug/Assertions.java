/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

/**
 * Utility for query whether assertions are enabled.
 */
public class Assertions {
    /**
     * Determines if assertions are enabled. Strictly speaking, this may only be true for the
     * {@link Assertions} class but we assume assertions are enabled/disabled for Graal as a whole.
     */
    public static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true;
        return enabled;
    }

    /**
     * Determines if detailed assertions are enabled. This requires that the normal assertions are
     * also enabled.
     *
     * @param values the current OptionValues that might define a value for DetailAsserts.
     */
    public static boolean detailedAssertionsEnabled(OptionValues values) {
        return assertionsEnabled() && Options.DetailedAsserts.getValue(values);
    }

    // @formatter:off
    public static class Options {

        @Option(help = "Enable expensive assertions if normal assertions (i.e. -ea or -esa) are enabled.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DetailedAsserts = new OptionKey<>(false);

    }
    // @formatter:on
}
