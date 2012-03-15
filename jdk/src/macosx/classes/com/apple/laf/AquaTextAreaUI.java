/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.laf;

import java.awt.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;

public class AquaTextAreaUI extends BasicTextAreaUI {
    public static ComponentUI createUI(final JComponent c) {
        return new AquaTextAreaUI();
    }

    public AquaTextAreaUI() {
        super();
    }

    AquaFocusHandler handler;
    protected void installListeners() {
        super.installListeners();

        final JTextComponent c = getComponent();
        handler = new AquaFocusHandler();
        c.addFocusListener(handler);
        c.addPropertyChangeListener(handler);

        AquaUtilControlSize.addSizePropertyListener(c);
    }

    protected void uninstallListeners() {
        final JTextComponent c = getComponent();

        AquaUtilControlSize.removeSizePropertyListener(c);

        c.removeFocusListener(handler);
        c.removePropertyChangeListener(handler);
        handler = null;

        super.uninstallListeners();
    }

    boolean oldDragState = false;
    protected void installDefaults() {
        if (!GraphicsEnvironment.isHeadless()) {
            oldDragState = getComponent().getDragEnabled();
            getComponent().setDragEnabled(true);
        }
        super.installDefaults();
    }

    protected void uninstallDefaults() {
        if (!GraphicsEnvironment.isHeadless()) {
            getComponent().setDragEnabled(oldDragState);
        }
        super.uninstallDefaults();
    }

    // Install a default keypress action which handles Cmd and Option keys properly
    protected void installKeyboardActions() {
        super.installKeyboardActions();
        AquaKeyBindings bindings = AquaKeyBindings.instance();
        bindings.setDefaultAction(getKeymapName());
        final JTextComponent c = getComponent();
        bindings.installAquaUpDownActions(c);
    }

    protected Caret createCaret() {
        final JTextComponent c = getComponent();
        final Window owningWindow = SwingUtilities.getWindowAncestor(c);
        final AquaCaret returnValue = new AquaCaret(owningWindow, c);
        return returnValue;
    }

    protected Highlighter createHighlighter() {
        return new AquaHighlighter();
    }
}
