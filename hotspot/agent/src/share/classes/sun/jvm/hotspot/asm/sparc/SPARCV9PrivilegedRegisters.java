/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.sparc;

public interface SPARCV9PrivilegedRegisters {
    public static final int TPC         = 0;
    public static final int TNPC        = 1;
    public static final int TSTATE      = 2;
    public static final int TT          = 3;
    public static final int TICK        = 4;
    public static final int TBA         = 5;
    public static final int PSTATE      = 6;
    public static final int TL          = 7;
    public static final int PIL         = 8;
    public static final int CWP         = 9;
    public static final int CANSAVE     = 10;
    public static final int CANRESTORE  = 11;
    public static final int CLEANWIN    = 12;
    public static final int OTHERWIN    = 13;
    public static final int WSTATE      = 14;
    public static final int FQ          = 15;
    public static final int VER         = 31;
}
