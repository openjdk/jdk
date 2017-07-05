/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.beans.PropertyChangeListener;
import java.util.Locale;
import java.util.Vector;

import javax.accessibility.*;

/**
 * This class is inserted in between cell renderers and the components that
 * use them.  It just exists to thwart the repaint() and invalidate() methods
 * which would otherwise propagate up the tree when the renderer was configured.
 * It's used by the implementations of JTable, JTree, and JList.  For example,
 * here's how CellRendererPane is used in the code the paints each row
 * in a JList:
 * <pre>
 *   cellRendererPane = new CellRendererPane();
 *   ...
 *   Component rendererComponent = renderer.getListCellRendererComponent();
 *   renderer.configureListCellRenderer(dataModel.getElementAt(row), row);
 *   cellRendererPane.paintComponent(g, rendererComponent, this, x, y, w, h);
 * </pre>
 * <p>
 * A renderer component must override isShowing() and unconditionally return
 * true to work correctly because the Swing paint does nothing for components
 * with isShowing false.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans&trade;
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author Hans Muller
 * @since 1.2
 */
@SuppressWarnings("serial") // Same-version serialization only
public class CellRendererPane extends Container implements Accessible
{
    /**
     * Construct a CellRendererPane object.
     */
    public CellRendererPane() {
        super();
        setLayout(null);
        setVisible(false);
    }

    /**
     * Overridden to avoid propagating a invalidate up the tree when the
     * cell renderer child is configured.
     */
    public void invalidate() { }


    /**
     * Shouldn't be called.
     */
    public void paint(Graphics g) { }


    /**
     * Shouldn't be called.
     */
    public void update(Graphics g) { }


    /**
     * If the specified component is already a child of this then we don't
     * bother doing anything - stacking order doesn't matter for cell
     * renderer components (CellRendererPane doesn't paint anyway).
     */
    protected void addImpl(Component x, Object constraints, int index) {
        if (x.getParent() == this) {
            return;
        }
        else {
            super.addImpl(x, constraints, index);
        }
    }


    /**
     * Paint a cell renderer component c on graphics object g.  Before the component
     * is drawn it's reparented to this (if that's necessary), it's bounds
     * are set to w,h and the graphics object is (effectively) translated to x,y.
     * If it's a JComponent, double buffering is temporarily turned off. After
     * the component is painted it's bounds are reset to -w, -h, 0, 0 so that, if
     * it's the last renderer component painted, it will not start consuming input.
     * The Container p is the component we're actually drawing on, typically it's
     * equal to this.getParent(). If shouldValidate is true the component c will be
     * validated before painted.
     */
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        if (c == null) {
            if (p != null) {
                Color oldColor = g.getColor();
                g.setColor(p.getBackground());
                g.fillRect(x, y, w, h);
                g.setColor(oldColor);
            }
            return;
        }

        if (c.getParent() != this) {
            this.add(c);
        }

        c.setBounds(x, y, w, h);

        if(shouldValidate) {
            c.validate();
        }

        boolean wasDoubleBuffered = false;
        if ((c instanceof JComponent) && ((JComponent)c).isDoubleBuffered()) {
            wasDoubleBuffered = true;
            ((JComponent)c).setDoubleBuffered(false);
        }

        Graphics cg = g.create(x, y, w, h);
        try {
            c.paint(cg);
        }
        finally {
            cg.dispose();
        }

        if (wasDoubleBuffered && (c instanceof JComponent)) {
            ((JComponent)c).setDoubleBuffered(true);
        }

        c.setBounds(-w, -h, 0, 0);
    }


    /**
     * Calls this.paintComponent(g, c, p, x, y, w, h, false).
     */
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h) {
        paintComponent(g, c, p, x, y, w, h, false);
    }


    /**
     * Calls this.paintComponent() with the rectangles x,y,width,height fields.
     */
    public void paintComponent(Graphics g, Component c, Container p, Rectangle r) {
        paintComponent(g, c, p, r.x, r.y, r.width, r.height);
    }


    private void writeObject(ObjectOutputStream s) throws IOException {
        removeAll();
        s.defaultWriteObject();
    }


/////////////////
// Accessibility support
////////////////

    protected AccessibleContext accessibleContext = null;

    /**
     * Gets the AccessibleContext associated with this CellRendererPane.
     * For CellRendererPanes, the AccessibleContext takes the form of an
     * AccessibleCellRendererPane.
     * A new AccessibleCellRendererPane instance is created if necessary.
     *
     * @return an AccessibleCellRendererPane that serves as the
     *         AccessibleContext of this CellRendererPane
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleCellRendererPane();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>CellRendererPane</code> class.
     */
    protected class AccessibleCellRendererPane extends AccessibleAWTContainer {
        // AccessibleContext methods
        //
        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PANEL;
        }
    } // inner class AccessibleCellRendererPane
}
