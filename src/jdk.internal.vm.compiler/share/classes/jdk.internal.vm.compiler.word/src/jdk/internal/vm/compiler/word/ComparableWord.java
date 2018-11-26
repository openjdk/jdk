/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


















package jdk.internal.vm.compiler.word;

/**
 * A machine-word-sized value that can be compared for equality.
 *
 * @since 1.0
 */
public interface ComparableWord extends WordBase {

    /**
     * Compares this word with the specified value.
     *
     * @param val value to which this word is to be compared.
     * @return {@code this == val}
     *
     * @since 1.0
     */
    boolean equal(ComparableWord val);

    /**
     * Compares this word with the specified value.
     *
     * @param val value to which this word is to be compared.
     * @return {@code this != val}
     *
     * @since 1.0
     */
    boolean notEqual(ComparableWord val);
}
