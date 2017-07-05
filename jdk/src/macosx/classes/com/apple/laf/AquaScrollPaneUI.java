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

import java.awt.event.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

public class AquaScrollPaneUI extends javax.swing.plaf.basic.BasicScrollPaneUI {
    public static ComponentUI createUI(final JComponent x) {
        return new AquaScrollPaneUI();
    }

    protected MouseWheelListener createMouseWheelListener() {
        return new XYMouseWheelHandler();
    }

    // This is a grody hack to trick BasicScrollPaneUI into scrolling horizontally
    // when we notice that the shift key is down. This should be removed when AWT/Swing
    // becomes aware of of multi-axis scroll wheels.
    protected class XYMouseWheelHandler extends javax.swing.plaf.basic.BasicScrollPaneUI.MouseWheelHandler {
        public void mouseWheelMoved(final MouseWheelEvent e) {
            JScrollBar vScrollBar = null;
            boolean wasVisible = false;

            if (e.isShiftDown()) {
                vScrollBar = scrollpane.getVerticalScrollBar();
                if (vScrollBar != null) {
                    wasVisible = vScrollBar.isVisible();
                    vScrollBar.setVisible(false);
                }
            }

            super.mouseWheelMoved(e);

            if (wasVisible) {
                vScrollBar.setVisible(true);
            }
        }
    }
}
