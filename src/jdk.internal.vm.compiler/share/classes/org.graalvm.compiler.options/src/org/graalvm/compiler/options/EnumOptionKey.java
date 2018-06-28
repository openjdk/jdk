/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicMap;

public class EnumOptionKey<T extends Enum<T>> extends OptionKey<T> {
    final Class<T> enumClass;

    @SuppressWarnings("unchecked")
    public EnumOptionKey(T value) {
        super(value);
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        this.enumClass = (Class<T>) value.getClass();
    }

    /**
     * @return the set of possible values for this option.
     */
    public EnumSet<T> getAllValues() {
        return EnumSet.allOf(enumClass);
    }

    public Object valueOf(String name) {
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid option for " + getName() + ". Valid values are " + getAllValues());
        }
    }

    @Override
    protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T oldValue, T newValue) {
        assert enumClass.isInstance(newValue) : newValue + " is not a valid value for " + getName();
    }
}
