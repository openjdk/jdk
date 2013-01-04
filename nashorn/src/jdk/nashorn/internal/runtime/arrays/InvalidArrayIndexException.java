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

package jdk.nashorn.internal.runtime.arrays;

/**
 * Mechanism for communicating that something isn't a plain
 * numeric integer array index. This enables things like
 * array getters for the fast case in a try, basically
 * just consisting of an "array[index]" access without
 * any checks of boundary conditions that rarely happen
 */
@SuppressWarnings("serial")
class InvalidArrayIndexException extends Exception {

    private final Object index;

    InvalidArrayIndexException(final Object index) {
        super(index == null ? "null" : index.toString());
        this.index = index;
    }

    InvalidArrayIndexException(final int index) {
        this(Integer.valueOf(index));
    }

    InvalidArrayIndexException(final long index) {
        this(Long.valueOf(index));
    }

    InvalidArrayIndexException(final double index) {
        this(Double.valueOf(index));
    }

    @Override
    public String toString() {
        return index.toString();
    }

    Object getIndex() {
        return index;
    }

}
