/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.*;
import java.beans.PropertyVetoException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicDesktopPaneUI;

public final class AquaInternalFramePaneUI extends BasicDesktopPaneUI implements MouseListener {

    JComponent fDock;
    DockLayoutManager fLayoutMgr;

    public static ComponentUI createUI(final JComponent c) {
        return new AquaInternalFramePaneUI();
    }

    @Override
    public void update(final Graphics g, final JComponent c) {
        if (c.isOpaque()) {
            super.update(g, c);
            return;
        }
        paint(g, c);
    }

    @Override
    public void installUI(final JComponent c) {
        super.installUI(c);
        fLayoutMgr = new DockLayoutManager();
        c.setLayout(fLayoutMgr);

        c.addMouseListener(this);
    }

    @Override
    public void uninstallUI(final JComponent c) {
        c.removeMouseListener(this);

        if (fDock != null) {
            c.remove(fDock);
            fDock = null;
        }
        if (fLayoutMgr != null) {
            c.setLayout(null);
            fLayoutMgr = null;
        }
        super.uninstallUI(c);
    }

    // Our superclass hardcodes DefaultDesktopManager - how rude!
    @Override
    protected void installDesktopManager() {
        if (desktop.getDesktopManager() == null) {
            desktopManager = new AquaDockingDesktopManager();
            desktop.setDesktopManager(desktopManager);
        }
    }

    @Override
    protected void uninstallDesktopManager() {
        final DesktopManager manager = desktop.getDesktopManager();
        if (manager instanceof AquaDockingDesktopManager) {
            desktop.setDesktopManager(null);
        }
    }

    JComponent getDock() {
        if (fDock == null) {
            fDock = new Dock(desktop);
            desktop.add(fDock, Integer.valueOf(399)); // Just below the DRAG_LAYER
        }
        return fDock;
    }

    final class DockLayoutManager implements LayoutManager {
        @Override
        public void addLayoutComponent(final String name, final Component comp) {
        }

        @Override
        public void removeLayoutComponent(final Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(final Container parent) {
            return parent.getSize();
        }

        @Override
        public Dimension minimumLayoutSize(final Container parent) {
            return parent.getSize();
        }

        @Override
        public void layoutContainer(final Container parent) {
            if (fDock != null) ((Dock)fDock).updateSize();
        }
    }

    @SuppressWarnings("serial") // Superclass is not serializable across versions
    final class Dock extends JComponent implements Border {
        static final int DOCK_EDGE_SLACK = 8;

        Dock(final JComponent parent) {
            setBorder(this);
            setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
            setVisible(false);
        }

        @Override
        public void removeNotify() {
            fDock = null;
            super.removeNotify();
        }

        void updateSize() {
            final Dimension d = getPreferredSize();
            setBounds((getParent().getWidth() - d.width) / 2, getParent().getHeight() - d.height, d.width, d.height);
        }

        @Override
        public Component add(final Component c) {
            super.add(c);
            if (!isVisible()) {
                setVisible(true);
            }

            updateSize();
            validate();
            return c;
        }

        @Override
        public void remove(final Component c) {
            super.remove(c);
            if (getComponentCount() == 0) {
                setVisible(false);
            } else {
                updateSize();
                validate();
            }
        }

        @Override
        public Insets getBorderInsets(final Component c) {
            return new Insets(DOCK_EDGE_SLACK / 4, DOCK_EDGE_SLACK, 0, DOCK_EDGE_SLACK);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int w, final int h) {
            if (!(g instanceof Graphics2D)) return;
            final Graphics2D g2d = (Graphics2D)g;

            final int height = getHeight();
            final int width = getWidth();

            final Object priorAA = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(UIManager.getColor("DesktopIcon.borderColor"));
            g2d.fillRoundRect(4, 4, width - 9, height + DOCK_EDGE_SLACK, DOCK_EDGE_SLACK, DOCK_EDGE_SLACK);

            g2d.setColor(UIManager.getColor("DesktopIcon.borderRimColor"));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawRoundRect(4, 4, width - 9, height + DOCK_EDGE_SLACK, DOCK_EDGE_SLACK, DOCK_EDGE_SLACK);

            if (priorAA != null) g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, priorAA);
        }
    }

    @SuppressWarnings("serial") // JDK implementation class
    static final class AquaDockingDesktopManager extends AquaInternalFrameManager {
        @Override
        public void openFrame(final JInternalFrame f) {
            final JInternalFrame.JDesktopIcon desktopIcon = f.getDesktopIcon();
            final Container dock = desktopIcon.getParent();
            if (dock == null) return;

            if (dock.getParent() != null) dock.getParent().add(f);
            removeIconFor(f);
        }

        @Override
        public void deiconifyFrame(final JInternalFrame f) {
            final JInternalFrame.JDesktopIcon desktopIcon = f.getDesktopIcon();
            final Container dock = desktopIcon.getParent();
            if (dock == null) return;

            if (dock.getParent() != null) dock.getParent().add(f);
            removeIconFor(f);
            // <rdar://problem/3712485> removed f.show(). show() is now deprecated and
            // it wasn't sending our frame to front nor selecting it. Now, we move it
            // to front and select it manually. (vm)
            f.moveToFront();
            try {
                f.setSelected(true);
            } catch(final PropertyVetoException pve) { /* do nothing */ }
        }

        @Override
        public void iconifyFrame(final JInternalFrame f) {
            final JInternalFrame.JDesktopIcon desktopIcon = f.getDesktopIcon();
            // paint the frame onto the icon before hiding the frame, else the contents won't show
            ((AquaInternalFrameDockIconUI)desktopIcon.getUI()).updateIcon();
            super.iconifyFrame(f);
        }

        @Override
        void addIcon(final Container c, final JInternalFrame.JDesktopIcon desktopIcon) {
            final DesktopPaneUI ui = ((JDesktopPane)c).getUI();
            ((AquaInternalFramePaneUI)ui).getDock().add(desktopIcon);
        }
    }

    @Override
    public void mousePressed(final MouseEvent e) {
        JInternalFrame selectedFrame = desktop.getSelectedFrame();
        if (selectedFrame != null) {
            try {
                selectedFrame.setSelected(false);
            } catch (PropertyVetoException ex) {}
            desktop.getDesktopManager().deactivateFrame(selectedFrame);
        }
    }

    @Override
    public void mouseReleased(final MouseEvent e) { }
    @Override
    public void mouseClicked(final MouseEvent e) { }
    @Override
    public void mouseEntered(final MouseEvent e) { }
    @Override
    public void mouseExited(final MouseEvent e) { }
}
