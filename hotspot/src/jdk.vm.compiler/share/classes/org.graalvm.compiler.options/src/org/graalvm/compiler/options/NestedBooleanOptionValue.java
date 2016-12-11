/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

/**
 * A nested Boolean {@link OptionValue} that can be overridden by a {@link #masterOption master
 * option}.
 * <p>
 * <li>If the option is present on the command line the specified value is used.
 * <li>Otherwise {@link #getValue()} depends on the {@link #masterOption} and evaluates as follows:
 * <ul>
 * <li>If {@link #masterOption} is set, this value equals to {@link #initialValue}.
 * <li>Otherwise, if {@link #masterOption} is {@code false}, this option is {@code false}.
 */
public class NestedBooleanOptionValue extends OptionValue<Boolean> {
    private final OptionValue<Boolean> masterOption;
    private final Boolean initialValue;

    public NestedBooleanOptionValue(OptionValue<Boolean> masterOption, Boolean initialValue) {
        super(null);
        this.masterOption = masterOption;
        this.initialValue = initialValue;
    }

    public OptionValue<Boolean> getMasterOption() {
        return masterOption;
    }

    @Override
    public Boolean getValue() {
        Boolean v = super.getValue();
        if (v == null) {
            return initialValue && masterOption.getValue();
        }
        return v;
    }

}
