/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.options;

/**
 * This is an option class that at its most primitive level just wraps a
 * boolean or String. However, it is conceivable that the option, when set
 * should run some initializations (for example, the logger system) or carry
 * some other kind of payload, arrays, Collections, etc. In that case, this
 * should be subclassed
 *
 * @param <T> option value type, for example a boolean or something more complex
 */
public class Option<T> {
    /** the option value */
    protected T value;

    Option(final T value) {
       this.value = value;
    }

    /**
     * Return the value of an option
     * @return the option value
     */
    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue() + " [" + getValue().getClass() + "]";
    }
}
