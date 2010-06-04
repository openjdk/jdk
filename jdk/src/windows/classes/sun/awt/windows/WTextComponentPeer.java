/*
 * Copyright (c) 1996, 2001, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.TextEvent;

abstract
class WTextComponentPeer extends WComponentPeer implements TextComponentPeer {

    static {
        initIDs();
    }

    // TextComponentPeer implementation

    public void setEditable(boolean editable) {
        enableEditing(editable);
        setBackground(((TextComponent)target).getBackground());
    }
    public native String getText();
    public native void setText(String txt);
    public native int getSelectionStart();
    public native int getSelectionEnd();
    public native void select(int selStart, int selEnd);

    // Toolkit & peer internals

    WTextComponentPeer(TextComponent target) {
        super(target);
    }

    void initialize() {
        TextComponent tc = (TextComponent)target;
        String text = tc.getText();

        if (text != null) {
            setText(text);
        }
        select(tc.getSelectionStart(), tc.getSelectionEnd());
        setEditable(tc.isEditable());

//      oldSelectionStart = -1; // accessibility support
//      oldSelectionEnd = -1;   // accessibility support

        super.initialize();
    }

    native void enableEditing(boolean e);

    public boolean isFocusable() {
        return true;
    }

    /*
     * Set the caret position by doing an empty selection. This
     * unfortunately resets the selection, but seems to be the
     * only way to get this to work.
     */
    public void setCaretPosition(int pos) {
        select(pos,pos);
    }

    /*
     * Get the caret position by looking up the end of the current
     * selection.
     */
    public int getCaretPosition() {
        return getSelectionStart();
    }

    /*
     * Post a new TextEvent when the value of a text component changes.
     */
    public void valueChanged() {
        postEvent(new TextEvent(target, TextEvent.TEXT_VALUE_CHANGED));
    }

    /**
     * Initialize JNI field and method IDs
     */
    private static native void initIDs();

    // stub functions: to be fully implemented in a future release
    public int getIndexAtPoint(int x, int y) { return -1; }
    public Rectangle getCharacterBounds(int i) { return null; }
    public long filterEvents(long mask) { return 0; }

    public boolean shouldClearRectBeforePaint() {
        return false;
    }

//
// Accessibility support
//

/*  To be fully implemented in a future release

    int oldSelectionStart;
    int oldSelectionEnd;

    public native int getIndexAtPoint(int x, int y);
    public native Rectangle getCharacterBounds(int i);
    public native long filterEvents(long mask);

    /**
     * Handle a change in the text selection endpoints
     * (Note: could be simply a change in the caret location)
     *
    public void selectionValuesChanged(int start, int end) {
        return;  // Need to write implementation of this.
    }
*/
}
