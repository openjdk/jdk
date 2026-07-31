/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.test;

/**
 * This interface provides arguments (and can set fields) for a test method. Different implementations are chosen
 * based on the @Arguments annotation for the @Test method.
 */
interface ArgumentsProvider {
    /**
     * Compute arguments (and possibly set fields) for a test method.
     *
     * @param invocationTarget object on which the test method is called, or null if the test method is static.
     * @param invocationCounter is incremented for every set of arguments to be provided for the test method.
     *                          It can be used to create deterministic inputs, that vary between different
     *                          invocations of the test method.
     * @return Returns the arguments to be passed into the test method.
     */
    Object[] getArguments(Object invocationTarget, int invocationCounter);

}
