/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

/**
 * This interface provides error, warning and notice printing.
 *
 * @since 1.2
 * @author Robert Field
 */
public interface DocErrorReporter {

    /**
     * Print error message and increment error count.
     *
     * @param msg message to print
     */
    void printError(String msg);

    /**
     * Print an error message and increment error count.
     *
     * @param pos the position item where the error occurs
     * @param msg message to print
     * @since 1.4
     */
    void printError(SourcePosition pos, String msg);

    /**
     * Print warning message and increment warning count.
     *
     * @param msg message to print
     */
    void printWarning(String msg);

    /**
     * Print warning message and increment warning count.
     *
     * @param pos the position item where the warning occurs
     * @param msg message to print
     * @since 1.4
     */
    void printWarning(SourcePosition pos, String msg);

    /**
     * Print a message.
     *
     * @param msg message to print
     */
    void printNotice(String msg);

    /**
     * Print a message.
     *
     * @param pos the position item where the message occurs
     * @param msg message to print
     * @since 1.4
     */
    void printNotice(SourcePosition pos, String msg);
}
