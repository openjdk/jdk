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


package org.graalvm.compiler.options;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes the attributes of a static field {@linkplain Option option} and provides access to its
 * {@linkplain OptionKey value}.
 */
public final class OptionDescriptor {

    private final String name;
    private final OptionType optionType;
    private final Class<?> optionValueType;
    private final String help;
    private final List<String> extraHelp;
    private final OptionKey<?> optionKey;
    private final Class<?> declaringClass;
    private final String fieldName;

    private static final String[] NO_EXTRA_HELP = {};

    public static OptionDescriptor create(String name, OptionType optionType, Class<?> optionValueType, String help, Class<?> declaringClass, String fieldName, OptionKey<?> option) {
        return create(name, optionType, optionValueType, help, NO_EXTRA_HELP, declaringClass, fieldName, option);
    }

    public static OptionDescriptor create(String name, OptionType optionType, Class<?> optionValueType, String help, String[] extraHelp, Class<?> declaringClass, String fieldName,
                    OptionKey<?> option) {
        assert option != null : declaringClass + "." + fieldName;
        OptionDescriptor result = option.getDescriptor();
        if (result == null) {
            List<String> extraHelpList = extraHelp == null || extraHelp.length == 0 ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(extraHelp));
            result = new OptionDescriptor(name, optionType, optionValueType, help, extraHelpList, declaringClass, fieldName, option);
            option.setDescriptor(result);
        }
        assert result.name.equals(name) && result.optionValueType == optionValueType && result.declaringClass == declaringClass && result.fieldName.equals(fieldName) && result.optionKey == option;
        return result;
    }

    private OptionDescriptor(String name, OptionType optionType, Class<?> optionValueType, String help, List<String> extraHelp, Class<?> declaringClass, String fieldName, OptionKey<?> optionKey) {
        this.name = name;
        this.optionType = optionType;
        this.optionValueType = optionValueType;
        this.help = help;
        this.extraHelp = extraHelp;
        this.optionKey = optionKey;
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        assert !optionValueType.isPrimitive() : "must used boxed optionValueType instead of " + optionValueType;
    }

    /**
     * Gets the type of values stored in the option. This will be the boxed type for a primitive
     * option.
     */
    public Class<?> getOptionValueType() {
        return optionValueType;
    }

    /**
     * Gets a descriptive help message for the option. This message should be self contained without
     * relying on {@link #getExtraHelp() extra help lines}.
     *
     * @see Option#help()
     */
    public String getHelp() {
        return help;
    }

    /**
     * Gets extra lines of help text. These lines should not be subject to any line wrapping or
     * formatting apart from indentation.
     */
    public List<String> getExtraHelp() {
        return extraHelp;
    }

    /**
     * Gets the name of the option. It's up to the client of this object how to use the name to get
     * a user specified value for the option from the environment.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the type of the option.
     */
    public OptionType getOptionType() {
        return optionType;
    }

    /**
     * Gets the boxed option value.
     */
    public OptionKey<?> getOptionKey() {
        return optionKey;
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Gets a description of the location where this option is stored.
     */
    public String getLocation() {
        return getDeclaringClass().getName() + "." + getFieldName();
    }
}
