/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.UIManager;

public final class MnemonicHandler {

    private static boolean isMnemonicHidden;

    private MnemonicHandler() {}

    /**
     * Gets the state of the hide mnemonic flag.
     * This only has meaning if this feature is supported by the underlying OS.
     *
     * @return true if mnemonics are hidden, otherwise, false
     */
    public static boolean isMnemonicHidden() {
        if (UIManager.getBoolean("Button.showMnemonics")) {
            // Do not hide mnemonics if the UI defaults do not support this
            isMnemonicHidden = false;
        }
        return isMnemonicHidden;
    }

    /**
     * Sets the state of the hide mnemonic flag. This flag is used by the
     * component UI delegates to determine if the mnemonic should be rendered.
     * This method is a non operation if the underlying operating system
     * does not support the mnemonic hiding feature.
     *
     * @param hide true if mnemonics should be hidden
     */
    public static void setMnemonicHidden(final boolean hide) {
        if (UIManager.getBoolean("Button.showMnemonics")) {
            // Do not hide mnemonics if the UI defaults do not support this
            isMnemonicHidden = false;
        } else {
            isMnemonicHidden = hide;
        }
    }

    /**
     * Repaints all the components with the mnemonics in the given window and all its owned windows.
     */
    public static void repaintMnemonicsInWindow(final Window w) {
        if (w == null || !w.isShowing()) {
            return;
        }

        final Window[] ownedWindows = w.getOwnedWindows();
        for (final Window element : ownedWindows) {
            repaintMnemonicsInWindow(element);
        }

        repaintMnemonicsInContainer(w);
    }

    /**
     * Repaints all the components with the mnemonics in container.
     * Recursively searches for all the subcomponents.
     */
    private static void repaintMnemonicsInContainer(final Container cont) {
        final Component[] elements = cont.getComponents();
        for (final Component c : elements) {
            if (c == null || !c.isVisible()) {
                continue;
            }

            if ((c instanceof AbstractButton b && b.getMnemonic() != '\0')
                || (c instanceof JLabel l && l.getDisplayedMnemonic() != '\0')) {
                c.repaint();
            } else if (c instanceof Container) {
                repaintMnemonicsInContainer((Container) c);
            }
        }
    }
}
