/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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


/*
 * Subclass defines its own set of constants
 * It is itself serializable by virtue of extending SuperClassConsts
 *
 */
public class SubClassConsts extends SuperClassConsts {

    private final static int SUB_INT_CONSTANT = 2;
    private final static double SUB_DOUBLE_CONSTANT = 2.25;
    private final static float SUB_FLOAT_CONSTANT = 7.90f;
    private final static boolean SUB_BOOLEAN_CONSTANT = true;

    public SubClassConsts(String p) {
        super(p);
    }
}
