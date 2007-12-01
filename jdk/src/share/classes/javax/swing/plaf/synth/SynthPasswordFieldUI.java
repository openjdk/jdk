/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.swing.plaf.synth;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.plaf.*;

/**
 * Provides the Synth look and feel for a password field.
 * The only difference from the standard text field is that
 * the view of the text is simply a string of the echo
 * character as specified in JPasswordField, rather than the
 * real text contained in the field.
 *
 * @author  Shannon Hickey
 */
class SynthPasswordFieldUI extends SynthTextFieldUI {

    /**
     * Creates a UI for a JPasswordField.
     *
     * @param c the JPasswordField
     * @return the UI
     */
    public static ComponentUI createUI(JComponent c) {
        return new SynthPasswordFieldUI();
    }

    /**
     * Fetches the name used as a key to look up properties through the
     * UIManager.  This is used as a prefix to all the standard
     * text properties.
     *
     * @return the name ("PasswordField")
     */
    protected String getPropertyPrefix() {
        return "PasswordField";
    }

    /**
     * Creates a view (PasswordView) for an element.
     *
     * @param elem the element
     * @return the view
     */
    public View create(Element elem) {
        return new PasswordView(elem);
    }

    void paintBackground(SynthContext context, Graphics g, JComponent c) {
        context.getPainter().paintPasswordFieldBackground(context, g, 0, 0,
                                                c.getWidth(), c.getHeight());
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintPasswordFieldBorder(context, g, x, y, w, h);
    }

    protected void installKeyboardActions() {
        super.installKeyboardActions();
        ActionMap map = SwingUtilities.getUIActionMap(getComponent());
        if (map != null && map.get(DefaultEditorKit.selectWordAction) != null) {
            Action a = map.get(DefaultEditorKit.selectLineAction);
            if (a != null) {
                map.put(DefaultEditorKit.selectWordAction, a);
            }
        }
    }
}
