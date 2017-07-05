/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.im.InputMethodRequests;

class WTextFieldPeer extends WTextComponentPeer implements TextFieldPeer {

    // WComponentPeer overrides

    public Dimension getMinimumSize() {
        FontMetrics fm = getFontMetrics(((TextField)target).getFont());
        return new Dimension(fm.stringWidth(getText()) + 24,
                             fm.getHeight() + 8);
    }

    public boolean handleJavaKeyEvent(KeyEvent e) {
        switch (e.getID()) {
           case KeyEvent.KEY_TYPED:
               if ((e.getKeyChar() == '\n') && !e.isAltDown() && !e.isControlDown()) {
                    postEvent(new ActionEvent(target, ActionEvent.ACTION_PERFORMED,
                                              getText(), e.getWhen(), e.getModifiers()));
                    return true;
               }
           break;
        }
        return false;
    }

    // TextFieldPeer implementation


    /* This should eventually be a direct native method. */
    public void setEchoChar(char c) {
        setEchoCharacter(c);
    }

    public Dimension getPreferredSize(int cols) {
        return getMinimumSize(cols);
    }

    public Dimension getMinimumSize(int cols) {
        FontMetrics fm = getFontMetrics(((TextField)target).getFont());
        return new Dimension(fm.charWidth('0') * cols + 24, fm.getHeight() + 8);
    }

    public InputMethodRequests getInputMethodRequests() {
           return null;
    }



    // Toolkit & peer internals

    WTextFieldPeer(TextField target) {
        super(target);
    }

    native void create(WComponentPeer parent);

    void initialize() {
        TextField tf = (TextField)target;
        if (tf.echoCharIsSet()) {
            setEchoChar(tf.getEchoChar());
        }
        super.initialize();
    }

    // deprecated methods

    /**
     * DEPRECATED but, for now, called by setEchoChar(char).
     */
    public native void setEchoCharacter(char c);

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize(int cols) {
        return getMinimumSize(cols);
    }

    /**
     * DEPRECATED
     */
    public Dimension preferredSize(int cols) {
        return getPreferredSize(cols);
    }

}
