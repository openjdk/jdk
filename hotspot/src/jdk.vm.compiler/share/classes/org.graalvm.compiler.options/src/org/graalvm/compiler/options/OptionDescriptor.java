/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Describes the attributes of a static field {@linkplain Option option} and provides access to its
 * {@linkplain OptionValue value}.
 */
public final class OptionDescriptor {

    protected final String name;
    protected final Class<?> type;
    protected final String help;
    protected final OptionValue<?> option;
    protected final Class<?> declaringClass;
    protected final String fieldName;

    public static OptionDescriptor create(String name, Class<?> type, String help, Class<?> declaringClass, String fieldName, OptionValue<?> option) {
        OptionDescriptor result = option.getDescriptor();
        if (result == null) {
            result = new OptionDescriptor(name, type, help, declaringClass, fieldName, option);
            option.setDescriptor(result);
        }
        assert result.name.equals(name) && result.type == type && result.declaringClass == declaringClass && result.fieldName.equals(fieldName) && result.option == option;
        return result;
    }

    private OptionDescriptor(String name, Class<?> type, String help, Class<?> declaringClass, String fieldName, OptionValue<?> option) {
        this.name = name;
        this.type = type;
        this.help = help;
        this.option = option;
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        assert !type.isPrimitive() : "must used boxed type instead of " + type;
    }

    /**
     * Gets the type of values stored in the option. This will be the boxed type for a primitive
     * option.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Gets a descriptive help message for the option.
     */
    public String getHelp() {
        return help;
    }

    /**
     * Gets the name of the option. It's up to the client of this object how to use the name to get
     * a user specified value for the option from the environment.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the boxed option value.
     */
    public OptionValue<?> getOptionValue() {
        return option;
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
