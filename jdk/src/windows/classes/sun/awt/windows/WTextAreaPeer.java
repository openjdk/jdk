/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.peer.*;
import java.awt.im.InputMethodRequests;


class WTextAreaPeer extends WTextComponentPeer implements TextAreaPeer {

    // WComponentPeer overrides

    public Dimension getMinimumSize() {
        return getMinimumSize(10, 60);
    }

    // TextAreaPeer implementation

    /* This should eventually be a direct native method. */
    public void insert(String txt, int pos) {
        insertText(txt, pos);
    }

    /* This should eventually be a direct native method. */
    public void replaceRange(String txt, int start, int end) {
        replaceText(txt, start, end);
    }

    public Dimension getPreferredSize(int rows, int cols) {
        return getMinimumSize(rows, cols);
    }
    public Dimension getMinimumSize(int rows, int cols) {
        FontMetrics fm = getFontMetrics(((TextArea)target).getFont());
        return new Dimension(fm.charWidth('0') * cols + 20, fm.getHeight() * rows + 20);
    }

    public InputMethodRequests getInputMethodRequests() {
           return null;
    }

    // Toolkit & peer internals

    WTextAreaPeer(TextArea target) {
        super(target);
    }

    native void create(WComponentPeer parent);

    // native callbacks


    // deprecated methods

    /**
     * DEPRECATED but, for now, still called by insert(String, int).
     */
    public native void insertText(String txt, int pos);

    /**
     * DEPRECATED but, for now, still called by replaceRange(String, int, int).
     */
    public native void replaceText(String txt, int start, int end);

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize(int rows, int cols) {
        return getMinimumSize(rows, cols);
    }

    /**
     * DEPRECATED
     */
    public Dimension preferredSize(int rows, int cols) {
        return getPreferredSize(rows, cols);
    }

}
