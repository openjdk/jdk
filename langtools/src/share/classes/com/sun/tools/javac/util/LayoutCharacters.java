/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

/** An interface containing layout character constants used in Java
 *  programs.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public interface LayoutCharacters {

    /** Tabulator column increment.
     */
    final static int TabInc = 8;

    /** Standard indentation for subdiagnostics
     */
    final static int DiagInc = 4;

    /** Standard indentation for additional diagnostic lines
     */
    final static int DetailsInc = 2;

    /** Tabulator character.
     */
    final static byte TAB   = 0x9;

    /** Line feed character.
     */
    final static byte LF    = 0xA;

    /** Form feed character.
     */
    final static byte FF    = 0xC;

    /** Carriage return character.
     */
    final static byte CR    = 0xD;

    /** End of input character.  Used as a sentinel to denote the
     *  character one beyond the last defined character in a
     *  source file.
     */
    final static byte EOI   = 0x1A;
}
