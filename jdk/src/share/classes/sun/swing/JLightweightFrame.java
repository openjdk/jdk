/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.AccessController;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import sun.awt.LightweightFrame;
import sun.security.action.GetPropertyAction;

/**
 * The frame serves as a lightweight container which paints its content
 * to an offscreen image and provides access to the image's data via the
 * {@link LightweightContent} interface. Note, that it may not be shown
 * as a standalone toplevel frame. Its purpose is to provide functionality
 * for lightweight embedding.
 *
 * @author Artem Ananiev
 * @author Anton Tarasov
 */
public final class JLightweightFrame extends LightweightFrame implements RootPaneContainer {

    private final JRootPane rootPane = new JRootPane();

    private LightweightContent content;

    private Component component;
    private JPanel contentPane;

    private BufferedImage bbImage;

    /**
     * {@code copyBufferEnabled}, true by default, defines the following strategy.
     * A duplicating (copy) buffer is created for the original pixel buffer.
     * The copy buffer is synchronized with the original buffer every time the
     * latter changes. {@code JLightweightFrame} passes the copy buffer array
     * to the {@link LightweightContent#imageBufferReset} method. The code spot
     * which synchronizes two buffers becomes the only critical section guarded
     * by the lock (managed with the {@link LightweightContent#paintLock()},
     * {@link LightweightContent#paintUnlock()} methods).
     */
    private boolean copyBufferEnabled;
    private int[] copyBuffer;

    private PropertyChangeListener layoutSizeListener;

    static {
        SwingAccessor.setJLightweightFrameAccessor(new SwingAccessor.JLightweightFrameAccessor() {
            @Override
            public void updateCursor(JLightweightFrame frame) {
                frame.updateClientCursor();
            }
        });
    }

    /**
     * Constructs a new, initially invisible {@code JLightweightFrame}
     * instance.
     */
    public JLightweightFrame() {
        super();
        copyBufferEnabled = "true".equals(AccessController.
            doPrivileged(new GetPropertyAction("swing.jlf.copyBufferEnabled", "true")));

        add(rootPane, BorderLayout.CENTER);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        if (getGraphicsConfiguration().isTranslucencyCapable()) {
            setBackground(new Color(0, 0, 0, 0));
        }

        layoutSizeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                Dimension d = (Dimension)e.getNewValue();

                if ("preferredSize".equals(e.getPropertyName())) {
                    content.preferredSizeChanged(d.width, d.height);

                } else if ("maximumSize".equals(e.getPropertyName())) {
                    content.maximumSizeChanged(d.width, d.height);

                } else if ("minimumSize".equals(e.getPropertyName())) {
                    content.minimumSizeChanged(d.width, d.height);
                }
            }
        };
    }

    /**
     * Sets the {@link LightweightContent} instance for this frame.
     * The {@code JComponent} object returned by the
     * {@link LightweightContent#getComponent()} method is immediately
     * added to the frame's content pane.
     *
     * @param content the {@link LightweightContent} instance
     */
    public void setContent(final LightweightContent content) {
        if (content == null) {
            System.err.println("JLightweightFrame.setContent: content may not be null!");
            return;
        }
        this.content = content;
        this.component = content.getComponent();

        Dimension d = this.component.getPreferredSize();
        content.preferredSizeChanged(d.width, d.height);

        d = this.component.getMaximumSize();
        content.maximumSizeChanged(d.width, d.height);

        d = this.component.getMinimumSize();
        content.minimumSizeChanged(d.width, d.height);

        initInterior();
    }

    @Override
    public Graphics getGraphics() {
        if (bbImage == null) return null;

        Graphics2D g = bbImage.createGraphics();
        g.setBackground(getBackground());
        g.setColor(getForeground());
        g.setFont(getFont());
        return g;
    }

    /**
     * {@inheritDoc}
     *
     * @see LightweightContent#focusGrabbed()
     */
    @Override
    public void grabFocus() {
        if (content != null) content.focusGrabbed();
    }

    /**
     * {@inheritDoc}
     *
     * @see LightweightContent#focusUngrabbed()
     */
    @Override
    public void ungrabFocus() {
        if (content != null) content.focusUngrabbed();
    }

    private void syncCopyBuffer(boolean reset, int x, int y, int w, int h) {
        content.paintLock();
        try {
            int[] srcBuffer = ((DataBufferInt)bbImage.getRaster().getDataBuffer()).getData();
            if (reset) {
                copyBuffer = new int[srcBuffer.length];
            }
            int linestride = bbImage.getWidth();

            for (int i=0; i<h; i++) {
                int from = (y + i) * linestride + x;
                System.arraycopy(srcBuffer, from, copyBuffer, from, w);
            }
        } finally {
            content.paintUnlock();
        }
    }

    private void initInterior() {
        contentPane = new JPanel() {
            @Override
            public void paint(Graphics g) {
                if (!copyBufferEnabled) {
                    content.paintLock();
                }
                try {
                    super.paint(g);

                    final Rectangle clip = g.getClipBounds() != null ?
                            g.getClipBounds() :
                            new Rectangle(0, 0, contentPane.getWidth(), contentPane.getHeight());

                    clip.x = Math.max(0, clip.x);
                    clip.y = Math.max(0, clip.y);
                    clip.width = Math.min(contentPane.getWidth(), clip.width);
                    clip.height = Math.min(contentPane.getHeight(), clip.height);

                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (copyBufferEnabled) {
                                syncCopyBuffer(false, clip.x, clip.y, clip.width, clip.height);
                            }
                            content.imageUpdated(clip.x, clip.y, clip.width, clip.height);
                        }
                    });
                } finally {
                    if (!copyBufferEnabled) {
                        content.paintUnlock();
                    }
                }
            }
            @Override
            protected boolean isPaintingOrigin() {
                return true;
            }
        };
        contentPane.setLayout(new BorderLayout());
        contentPane.add(component);
        if ("true".equals(AccessController.
            doPrivileged(new GetPropertyAction("swing.jlf.contentPaneTransparent", "false"))))
        {
            contentPane.setOpaque(false);
        }
        setContentPane(contentPane);

        contentPane.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                Component c = JLightweightFrame.this.component;
                if (e.getChild() == c) {
                    c.addPropertyChangeListener("preferredSize", layoutSizeListener);
                    c.addPropertyChangeListener("maximumSize", layoutSizeListener);
                    c.addPropertyChangeListener("minimumSize", layoutSizeListener);
                }
            }
            @Override
            public void componentRemoved(ContainerEvent e) {
                Component c = JLightweightFrame.this.component;
                if (e.getChild() == c) {
                    c.removePropertyChangeListener(layoutSizeListener);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override public void reshape(int x, int y, int width, int height) {
        super.reshape(x, y, width, height);

        if (width == 0 || height == 0) {
            return;
        }
        if (!copyBufferEnabled) {
            content.paintLock();
        }
        try {
            if ((bbImage == null) || (width != bbImage.getWidth()) || (height != bbImage.getHeight())) {
                boolean createBB = true;
                int newW = width;
                int newH = height;
                if (bbImage != null) {
                    int oldW = bbImage.getWidth();
                    int oldH = bbImage.getHeight();
                    if ((oldW >= newW) && (oldH >= newH)) {
                        createBB = false;
                    } else {
                        if (oldW >= newW) {
                            newW = oldW;
                        } else {
                            newW = Math.max((int)(oldW * 1.2), width);
                        }
                        if (oldH >= newH) {
                            newH = oldH;
                        } else {
                            newH = Math.max((int)(oldH * 1.2), height);
                        }
                    }
                }
                if (createBB) {
                    BufferedImage oldBB = bbImage;
                    bbImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB_PRE);
                    if (oldBB != null) {
                        Graphics g = bbImage.getGraphics();
                        try {
                            g.drawImage(oldBB, 0, 0, newW, newH, null);
                        } finally {
                            g.dispose();
                            oldBB.flush();
                        }
                    }
                    int[] pixels = ((DataBufferInt)bbImage.getRaster().getDataBuffer()).getData();
                    if (copyBufferEnabled) {
                        syncCopyBuffer(true, 0, 0, width, height);
                        pixels = copyBuffer;
                    }
                    content.imageBufferReset(pixels, 0, 0, width, height, bbImage.getWidth());
                    return;
                }
            }
            content.imageReshaped(0, 0, width, height);

        } finally {
            if (!copyBufferEnabled) {
                content.paintUnlock();
            }
        }
    }

    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    @Override
    public void setContentPane(Container contentPane) {
        getRootPane().setContentPane(contentPane);
    }

    @Override
    public Container getContentPane() {
        return getRootPane().getContentPane();
    }

    @Override
    public void setLayeredPane(JLayeredPane layeredPane) {
        getRootPane().setLayeredPane(layeredPane);
    }

    @Override
    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    @Override
    public void setGlassPane(Component glassPane) {
        getRootPane().setGlassPane(glassPane);
    }

    @Override
    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }


    /*
     * Notifies client toolkit that it should change a cursor.
     *
     * Called from the peer via SwingAccessor, because the
     * Component.updateCursorImmediately method is final
     * and could not be overridden.
     */
    private void updateClientCursor() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(p, this);
        Component target = SwingUtilities.getDeepestComponentAt(this, p.x, p.y);
        if (target != null) {
            content.setCursor(target.getCursor());
        }
    }
}
