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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import java.beans.*;
import java.util.EventListener;
import java.io.Serializable;


/**
 * Basic L&F for a minimized window on a desktop.
 *
 * @author David Kloba
 * @author Steve Wilson
 * @author Rich Schiavi
 */
public class BasicDesktopIconUI extends DesktopIconUI {

    protected JInternalFrame.JDesktopIcon desktopIcon;
    protected JInternalFrame frame;
    private DesktopIconMover desktopIconMover;

    /**
     * The title pane component used in the desktop icon.
     *
     * @since 1.5
     */
    protected JComponent iconPane;
    MouseInputListener mouseInputListener;



    public static ComponentUI createUI(JComponent c)    {
        return new BasicDesktopIconUI();
    }

    public BasicDesktopIconUI() {
    }

    public void installUI(JComponent c)   {
        desktopIcon = (JInternalFrame.JDesktopIcon)c;
        frame = desktopIcon.getInternalFrame();
        installDefaults();
        installComponents();

        // Update icon layout if frame is already iconified
        JInternalFrame f = desktopIcon.getInternalFrame();
        if (f.isIcon() && f.getParent() == null) {
            JDesktopPane desktop = desktopIcon.getDesktopPane();
            if (desktop != null) {
                DesktopManager desktopManager = desktop.getDesktopManager();
                if (desktopManager instanceof DefaultDesktopManager) {
                    desktopManager.iconifyFrame(f);
                }
            }
        }

        installListeners();
        JLayeredPane.putLayer(desktopIcon, JLayeredPane.getLayer(frame));
    }

    public void uninstallUI(JComponent c) {
        uninstallDefaults();
        uninstallComponents();

        // Force future UI to relayout icon
        JInternalFrame f = desktopIcon.getInternalFrame();
        if (f.isIcon()) {
            JDesktopPane desktop = desktopIcon.getDesktopPane();
            if (desktop != null) {
                DesktopManager desktopManager = desktop.getDesktopManager();
                if (desktopManager instanceof DefaultDesktopManager) {
                    // This will cause DefaultDesktopManager to layout the icon
                    f.putClientProperty("wasIconOnce", null);
                    // Move aside to allow fresh layout of all icons
                    desktopIcon.setLocation(Integer.MIN_VALUE, 0);
                }
            }
        }

        uninstallListeners();
        frame = null;
        desktopIcon = null;
    }

    protected void installComponents() {
        iconPane = new BasicInternalFrameTitlePane(frame);
        desktopIcon.setLayout(new BorderLayout());
        desktopIcon.add(iconPane, BorderLayout.CENTER);
    }

    protected void uninstallComponents() {
        desktopIcon.remove(iconPane);
        desktopIcon.setLayout(null);
        iconPane = null;
    }

    protected void installListeners() {
        mouseInputListener = createMouseInputListener();
        desktopIcon.addMouseMotionListener(mouseInputListener);
        desktopIcon.addMouseListener(mouseInputListener);
         getDesktopIconMover().installListeners();
    }

    protected void uninstallListeners() {
        desktopIcon.removeMouseMotionListener(mouseInputListener);
        desktopIcon.removeMouseListener(mouseInputListener);
        mouseInputListener = null;
         getDesktopIconMover().uninstallListeners();
    }

    private DesktopIconMover getDesktopIconMover() {
        if (desktopIconMover == null) {
            desktopIconMover = new DesktopIconMover(desktopIcon);
        }
        return desktopIconMover;
    }

    protected void installDefaults() {
        LookAndFeel.installBorder(desktopIcon, "DesktopIcon.border");
        LookAndFeel.installProperty(desktopIcon, "opaque", Boolean.TRUE);
    }

    protected void uninstallDefaults() {
        LookAndFeel.uninstallBorder(desktopIcon);
    }

    protected MouseInputListener createMouseInputListener() {
        return new MouseInputHandler();
    }

    public Dimension getPreferredSize(JComponent c) {
        return desktopIcon.getLayout().preferredLayoutSize(desktopIcon);
    }

    public Dimension getMinimumSize(JComponent c) {
        Dimension dim = new Dimension(iconPane.getMinimumSize());
        Border border = frame.getBorder();

        if (border != null) {
            dim.height += border.getBorderInsets(frame).bottom +
                          border.getBorderInsets(frame).top;
        }
        return dim;
    }

    /**
     * Desktop icons can not be resized.  Therefore, we should always
     * return the minimum size of the desktop icon.
     *
     * @see #getMinimumSize
     */
    public Dimension getMaximumSize(JComponent c){
        return iconPane.getMaximumSize();
    }

    public Insets getInsets(JComponent c) {
        JInternalFrame iframe = desktopIcon.getInternalFrame();
        Border border = iframe.getBorder();
        if(border != null)
            return border.getBorderInsets(iframe);

        return new Insets(0,0,0,0);
    }

    public void deiconize() {
        try { frame.setIcon(false); } catch (PropertyVetoException e2) { }
    }

    /**
     * Listens for mouse movements and acts on them.
     *
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of <Foo>.
     */
    public class MouseInputHandler extends MouseInputAdapter
    {
        // _x & _y are the mousePressed location in absolute coordinate system
        int _x, _y;
        // __x & __y are the mousePressed location in source view's coordinate system
        int __x, __y;
        Rectangle startingBounds;

        public void mouseReleased(MouseEvent e) {
            _x = 0;
            _y = 0;
            __x = 0;
            __y = 0;
            startingBounds = null;

            JDesktopPane d;
            if((d = desktopIcon.getDesktopPane()) != null) {
                DesktopManager dm = d.getDesktopManager();
                dm.endDraggingFrame(desktopIcon);
            }

        }

        public void mousePressed(MouseEvent e) {
            Point p = SwingUtilities.convertPoint((Component)e.getSource(),
                        e.getX(), e.getY(), null);
            __x = e.getX();
            __y = e.getY();
            _x = p.x;
            _y = p.y;
            startingBounds = desktopIcon.getBounds();

            JDesktopPane d;
            if((d = desktopIcon.getDesktopPane()) != null) {
                DesktopManager dm = d.getDesktopManager();
                dm.beginDraggingFrame(desktopIcon);
            }

            try { frame.setSelected(true); } catch (PropertyVetoException e1) { }
            if(desktopIcon.getParent() instanceof JLayeredPane) {
                ((JLayeredPane)desktopIcon.getParent()).moveToFront(desktopIcon);
            }

            if(e.getClickCount() > 1) {
                if(frame.isIconifiable() && frame.isIcon()) {
                    deiconize();
                }
            }

        }

         public void mouseMoved(MouseEvent e) {}

         public void mouseDragged(MouseEvent e) {
            Point p;
            int newX, newY, newW, newH;
            int deltaX;
            int deltaY;
            Dimension min;
            Dimension max;
            p = SwingUtilities.convertPoint((Component)e.getSource(),
                                        e.getX(), e.getY(), null);

                Insets i = desktopIcon.getInsets();
                int pWidth, pHeight;
                pWidth = ((JComponent)desktopIcon.getParent()).getWidth();
                pHeight = ((JComponent)desktopIcon.getParent()).getHeight();

                if (startingBounds == null) {
                  // (STEVE) Yucky work around for bug ID 4106552
                    return;
                }
                newX = startingBounds.x - (_x - p.x);
                newY = startingBounds.y - (_y - p.y);
                // Make sure we stay in-bounds
                if(newX + i.left <= -__x)
                    newX = -__x - i.left;
                if(newY + i.top <= -__y)
                    newY = -__y - i.top;
                if(newX + __x + i.right > pWidth)
                    newX = pWidth - __x - i.right;
                if(newY + __y + i.bottom > pHeight)
                    newY =  pHeight - __y - i.bottom;

                JDesktopPane d;
                if((d = desktopIcon.getDesktopPane()) != null) {
                    DesktopManager dm = d.getDesktopManager();
                    dm.dragFrame(desktopIcon, newX, newY);
                } else {
                    moveAndRepaint(desktopIcon, newX, newY,
                                desktopIcon.getWidth(), desktopIcon.getHeight());
                }
                return;
        }

        public void moveAndRepaint(JComponent f, int newX, int newY,
                                        int newWidth, int newHeight) {
            Rectangle r = f.getBounds();
            f.setBounds(newX, newY, newWidth, newHeight);
            SwingUtilities.computeUnion(newX, newY, newWidth, newHeight, r);
            f.getParent().repaint(r.x, r.y, r.width, r.height);
        }
    }; /// End MotionListener

}
