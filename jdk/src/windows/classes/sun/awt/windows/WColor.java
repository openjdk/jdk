/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.windows;

import java.awt.Color;

/*
 * This helper class maps Windows system colors to AWT Color objects.
 */
final class WColor {

    static final int WINDOW_BKGND = 1;  // COLOR_WINDOW
    static final int WINDOW_TEXT  = 2;  // COLOR_WINDOWTEXT
    static final int FRAME        = 3;  // COLOR_WINDOWFRAME
    static final int SCROLLBAR    = 4;  // COLOR_SCROLLBAR
    static final int MENU_BKGND   = 5;  // COLOR_MENU
    static final int MENU_TEXT    = 6;  // COLOR MENUTEXT
    static final int BUTTON_BKGND = 7;  // COLOR_3DFACE or COLOR_BTNFACE
    static final int BUTTON_TEXT  = 8;  // COLOR_BTNTEXT
    static final int HIGHLIGHT    = 9;  // COLOR_HIGHLIGHT

    static native Color getDefaultColor(int index);
}
