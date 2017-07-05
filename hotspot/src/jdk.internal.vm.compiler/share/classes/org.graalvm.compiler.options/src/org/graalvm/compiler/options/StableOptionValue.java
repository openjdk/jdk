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
 * An option that always returns the same {@linkplain #getValue() value}.
 */
public class StableOptionValue<T> extends OptionValue<T> {

    /**
     * Creates a stable option value.
     */
    public StableOptionValue(T value) {
        super(value);
    }

    /**
     * Used to assert the invariant for stability. Without using locks, this check is not safe
     * against races and so it's only an assertion.
     */
    private boolean getValueCalled;

    /**
     * Creates an uninitialized stable option value for a subclass that initializes itself
     * {@link #defaultValue() lazily}.
     */
    public StableOptionValue() {
    }

    /**
     * Gets the value of this option.
     */
    @Override
    public final T getValue() {
        T result = super.getValue();
        assert initGetValueCalled();
        return result;
    }

    private boolean initGetValueCalled() {
        getValueCalled = true;
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This must only be called if {@link #getValue()} has never been called.
     */
    @Override
    public final void setValue(Object v) {
        assert !getValueCalled;
        super.setValue(v);
    }
}
