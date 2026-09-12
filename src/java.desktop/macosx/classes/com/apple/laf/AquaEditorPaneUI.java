/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.FocusListener;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicEditorPaneUI;
import javax.swing.text.*;

import sun.swing.SwingAccessor;

public final class AquaEditorPaneUI extends BasicEditorPaneUI {
    public static ComponentUI createUI(final JComponent c){
        return new AquaEditorPaneUI();
    }

    private boolean oldDragState;

    @Override
    protected void installDefaults(){
        oldDragState = getComponent().getDragEnabled();
        super.installDefaults();
        if (!GraphicsEnvironment.isHeadless()) {
            LookAndFeel.installProperty(getComponent(), "dragEnabled", true);
        }
    }

    @Override
    protected void uninstallDefaults() {
        if (!SwingAccessor.getJTextComponentAccessor()
                          .isDragEnabledSet(getComponent())) {
            LookAndFeel.installProperty(getComponent(), "dragEnabled",
                                        oldDragState);
        }
        super.uninstallDefaults();
    }

    FocusListener focusListener;
    @Override
    protected void installListeners(){
        super.installListeners();
        focusListener = createFocusListener();
        getComponent().addFocusListener(focusListener);
    }

    @Override
    protected void installKeyboardActions() {
        super.installKeyboardActions();
        AquaKeyBindings bindings = AquaKeyBindings.instance();
        bindings.setDefaultAction(getKeymapName());
        final JTextComponent c = getComponent();
        bindings.installAquaUpDownActions(c);
    }

    @Override
    protected void uninstallListeners(){
        getComponent().removeFocusListener(focusListener);
        super.uninstallListeners();
    }

    protected FocusListener createFocusListener(){
        return new AquaFocusHandler();
    }

    @Override
    protected Caret createCaret() {
        return new AquaCaret();
    }

    @Override
    protected Highlighter createHighlighter(){
        return new AquaHighlighter();
    }
}
