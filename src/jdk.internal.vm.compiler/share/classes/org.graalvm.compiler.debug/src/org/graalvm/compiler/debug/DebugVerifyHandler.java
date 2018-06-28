/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Performs some kind of verification on an object.
 */
public interface DebugVerifyHandler extends DebugHandler {

    /**
     * Verifies that a given object satisfies some invariants.
     *
     * @param object object to verify
     * @param debug the debug context requesting the dump
     * @param format a format string specifying a title that describes the context of the
     *            verification (e.g., the compiler phase in which request is made)
     * @param arguments arguments referenced by the format specifiers in {@code format}
     */
    void verify(DebugContext debug, Object object, String format, Object... arguments);
}
