/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.basic;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;

/**
 * DesktopIconMover is intended to move desktop icon
 * when parent window is resized.
 */
class DesktopIconMover implements ComponentListener, PropertyChangeListener {
    private Component parent;
    private JInternalFrame frame; // if not null, DesktopIconMover(frame)
                                  // constructor was used
    private JInternalFrame.JDesktopIcon icon;
    private Rectangle parentBounds;
    private boolean componentListenerAdded = false;

    public DesktopIconMover(JInternalFrame frame) {
        if (frame == null) {
            throw new NullPointerException("Frame cannot be null");
        }
        this.frame = frame;
        this.icon = frame.getDesktopIcon();
        if (icon == null) {
            throw new NullPointerException(
                    "frame.getDesktopIcon() cannot be null");
        }
        this.parent = frame.getParent();
        if (this.parent != null) {
            parentBounds = this.parent.getBounds();
        }
    }

    public DesktopIconMover(JInternalFrame.JDesktopIcon icon) {
        if (icon == null) {
            throw new NullPointerException("Icon cannot be null");
        }
        this.icon = icon;
        this.parent = icon.getParent();
        if (this.parent != null) {
            parentBounds = this.parent.getBounds();
        }
    }

    public void installListeners() {
        if (frame != null) {
            frame.addPropertyChangeListener(this);
        } else {
            icon.addPropertyChangeListener(this);
        }
        addComponentListener();
    }

    public void uninstallListeners() {
        if (frame != null) {
            frame.removePropertyChangeListener(this);
        } else {
            icon.removePropertyChangeListener(this);
        }
        removeComponentListener();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();
        if ("ancestor".equals(propName)) {
            Component newAncestor = (Component) evt.getNewValue();

            // Remove component listener if parent is changing
            Component probablyNewParent = getCurrentParent();
            if ((probablyNewParent != null) &&
                    (!probablyNewParent.equals(parent))) {
                removeComponentListener();
                parent = probablyNewParent;
            }

            if (newAncestor == null) {
                removeComponentListener();
            } else {
                addComponentListener();
            }

            // Update parentBounds
            if (parent != null) {
                parentBounds = parent.getBounds();
            } else {
                parentBounds = null;
            }
        } else if (JInternalFrame.IS_CLOSED_PROPERTY.equals(propName)) {
            removeComponentListener();
        }
    }

    private void addComponentListener() {
        if (!componentListenerAdded && (parent != null)) {
            parent.addComponentListener(this);
            componentListenerAdded = true;
        }
    }

    private void removeComponentListener() {
        if ((parent != null) && componentListenerAdded) {
            parent.removeComponentListener(this);
            componentListenerAdded = false;
        }
    }

    private Component getCurrentParent() {
        if (frame != null) {
            return frame.getParent();
        } else {
            return icon.getParent();
        }
    }

    public void componentResized(ComponentEvent e) {
        if ((parent == null) || (parentBounds == null)) {
            return;
        }

        Rectangle parentNewBounds = parent.getBounds();
        if ((parentNewBounds == null) || parentNewBounds.equals(parentBounds)) {
            return;
        }

        // Move desktop icon only in up-down direction
        int newIconY = icon.getLocation().y +
                (parentNewBounds.height - parentBounds.height);
        icon.setLocation(icon.getLocation().x, newIconY);

        parentBounds = parentNewBounds;
    }

    public void componentMoved(ComponentEvent e) {
    }

    public void componentShown(ComponentEvent e) {
    }

    public void componentHidden(ComponentEvent e) {
    }
}
