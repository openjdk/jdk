/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing;

import javax.swing.plaf.LayerUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * {@code JLayer} is a universal decorator for Swing components
 * which enables you to implement various advanced painting effects as well as
 * receive notifications of all {@code AWTEvent}s generated within its borders.
 * <p/>
 * {@code JLayer} delegates the handling of painting and input events to a
 * {@link javax.swing.plaf.LayerUI} object, which performs the actual decoration.
 * <p/>
 * The custom painting implemented in the {@code LayerUI} and events notification
 * work for the JLayer itself and all its subcomponents.
 * This combination enables you to enrich existing components
 * by adding new advanced functionality such as temporary locking of a hierarchy,
 * data tips for compound components, enhanced mouse scrolling etc and so on.
 * <p/>
 * {@code JLayer} is a good solution if you only need to do custom painting
 * over compound component or catch input events from its subcomponents.
 * <pre>
 *         // create a component to be decorated with the layer
 *        JPanel panel = new JPanel();
 *        panel.add(new JButton("JButton"));
 *        // This custom layerUI will fill the layer with translucent green
 *        // and print out all mouseMotion events generated within its borders
 *        LayerUI&lt;JPanel&gt; layerUI = new LayerUI&lt;JPanel&gt;() {
 *            public void paint(Graphics g, JCompo  nent c) {
 *                // paint the layer as is
 *                super.paint(g, c);
 *                // fill it with the translucent green
 *                g.setColor(new Color(0, 128, 0, 128));
 *                g.fillRect(0, 0, c.getWidth(), c.getHeight());
 *            }
 *            // overridden method which catches MouseMotion events
 *            public void eventDispatched(AWTEvent e, JLayer&lt;JPanel&gt; l) {
 *                System.out.println("AWTEvent detected: " + e);
 *            }
 *        };
 *        // create the layer for the panel using our custom layerUI
 *        JLayer&lt;JPanel&gt; layer = new JLayer&lt;JPanel&gt;(panel, layerUI);
 *        // work with the layer as with any other Swing component
 *        frame.add(layer);
 * </pre>
 *
 * <b>Note:</b> {@code JLayer} doesn't support the following methods:
 * <ul>
 * <li>{@link Container#add(java.awt.Component)}</li>
 * <li>{@link Container#add(String, java.awt.Component)}</li>
 * <li>{@link Container#add(java.awt.Component, int)}</li>
 * <li>{@link Container#add(java.awt.Component, Object)}</li>
 * <li>{@link Container#add(java.awt.Component, Object, int)}</li>
 * </ul>
 * using any of of them will cause {@code UnsupportedOperationException} to be thrown,
 * to add a component to {@code JLayer}
 * use {@link #setView(Component)} or {@link #setGlassPane(JPanel)}.
 *
 * @param <V> the type of {@code JLayer}'s view component
 *
 * @see #JLayer(Component)
 * @see #setView(Component)
 * @see #getView()
 * @see javax.swing.plaf.LayerUI
 * @see #JLayer(Component, LayerUI)
 * @see #setUI(javax.swing.plaf.LayerUI)
 * @see #getUI()
 * @since 1.7
 *
 * @author Alexander Potochkin
 */
public final class JLayer<V extends Component>
        extends JComponent
        implements Scrollable, PropertyChangeListener {
    private V view;
    // this field is necessary because JComponent.ui is transient
    // when layerUI is serializable
    private LayerUI<? super V> layerUI;
    private JPanel glassPane;
    private boolean isPainting;
    private static final DefaultLayerLayout sharedLayoutInstance =
            new DefaultLayerLayout();
    private long eventMask;

    private static final LayerEventController eventController =
            new LayerEventController();

    private static final long ACCEPTED_EVENTS =
            AWTEvent.COMPONENT_EVENT_MASK |
                    AWTEvent.CONTAINER_EVENT_MASK |
                    AWTEvent.FOCUS_EVENT_MASK |
                    AWTEvent.KEY_EVENT_MASK |
                    AWTEvent.MOUSE_WHEEL_EVENT_MASK |
                    AWTEvent.MOUSE_MOTION_EVENT_MASK |
                    AWTEvent.MOUSE_EVENT_MASK |
                    AWTEvent.INPUT_METHOD_EVENT_MASK |
                    AWTEvent.HIERARCHY_EVENT_MASK |
                    AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK;

    /**
     * Creates a new {@code JLayer} object with a {@code null} view component
     * and {@code null} {@link javax.swing.plaf.LayerUI}.
     *
     * @see #setView
     * @see #setUI
     */
    public JLayer() {
        this(null);
    }

    /**
     * Creates a new {@code JLayer} object
     * with {@code null} {@link javax.swing.plaf.LayerUI}.
     *
     * @param view the component to be decorated by this {@code JLayer}
     *
     * @see #setUI
     */
    public JLayer(V view) {
        this(view, null);
    }

    /**
     * Creates a new {@code JLayer} object with the specified view component
     * and {@link javax.swing.plaf.LayerUI} object.
     *
     * @param view the component to be decorated
     * @param ui the {@link javax.swing.plaf.LayerUI} delegate
     * to be used by this {@code JLayer}
     */
    public JLayer(V view, LayerUI<V> ui) {
        setLayout(sharedLayoutInstance);
        setGlassPane(createGlassPane());
        setView(view);
        setUI(ui);
    }

    /**
     * Returns the {@code JLayer}'s view component or {@code null}.
     * <br/>This is a bound property.
     *
     * @return the {@code JLayer}'s view component
     *         or {@code null} if none exists
     *
     * @see #setView(V)
     */
    public V getView() {
        return view;
    }

    /**
     * Sets the {@code JLayer}'s view component, which can be {@code null}.
     * <br/>This is a bound property.
     *
     * @param view the view component for this {@code JLayer}
     *
     * @see #getView()
     */
    public void setView(V view) {
        Component oldView = getView();
        if (oldView != null) {
            super.remove(oldView);
        }
        if (view != null) {
            super.addImpl(view, null, getComponentCount());
        }
        this.view = view;
        firePropertyChange("view", oldView, view);
        revalidate();
        repaint();
    }

    /**
     * Sets the {@link javax.swing.plaf.LayerUI} which will perform painting
     * and receive input events for this {@code JLayer}.
     *
     * @param ui the {@link javax.swing.plaf.LayerUI} for this {@code JLayer}
     */
    public void setUI(LayerUI<? super V> ui) {
        this.layerUI = ui;
        super.setUI(ui);
    }

    /**
     * Returns the {@link javax.swing.plaf.LayerUI} for this {@code JLayer}.
     *
     * @return the {@code LayerUI} for this {@code JLayer}
     */
    public LayerUI<? super V> getUI() {
        return layerUI;
    }

    /**
     * Returns the {@code JLayer}'s glassPane component or {@code null}.
     * <br/>This is a bound property.
     *
     * @return the {@code JLayer}'s glassPane component
     *         or {@code null} if none exists
     *
     * @see #setGlassPane(JPanel)
     */
    public JPanel getGlassPane() {
        return glassPane;
    }

    /**
     * Sets the {@code JLayer}'s glassPane component, which can be {@code null}.
     * <br/>This is a bound property.
     *
     * @param glassPane the glassPane component of this {@code JLayer}
     *
     * @see #getGlassPane()
     */
    public void setGlassPane(JPanel glassPane) {
        Component oldGlassPane = getGlassPane();
        if (oldGlassPane != null) {
            super.remove(oldGlassPane);
        }
        if (glassPane != null) {
            super.addImpl(glassPane, null, 0);
        }
        this.glassPane = glassPane;
        firePropertyChange("glassPane", oldGlassPane, glassPane);
        revalidate();
        repaint();
    }

    /**
     * Called by the constructor methods to create a default {@code glassPane}.
     * By default this method creates a new JPanel with visibility set to true
     * and opacity set to false.
     *
     * @return the default {@code glassPane}
     */
    public JPanel createGlassPane() {
        return new DefaultLayerGlassPane();
    }

    /**
     * This method is not supported by {@code JLayer}
     * and always throws {@code UnsupportedOperationException}
     *
     * @throws UnsupportedOperationException this method is not supported
     *
     * @see #setView(Component)
     * @see #setGlassPane(Component)
     */
    protected void addImpl(Component comp, Object constraints, int index) {
        throw new UnsupportedOperationException(
                "Adding components to JLayer is not supported, " +
                        "use setView() or setGlassPane() instead");
    }

    /**
     * {@inheritDoc}
     */
    public void remove(Component comp) {
        if (comp == getView()) {
            setView(null);
        } else if (comp == getGlassPane()) {
            setGlassPane(null);
        } else {
            super.remove(comp);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeAll() {
        setView(null);
        setGlassPane(null);
    }

    /**
     * Delegates all painting to the {@link javax.swing.plaf.LayerUI} object.
     *
     * @param g the {@code Graphics} to render to
     */
    public void paint(Graphics g) {
        if (!isPainting) {
            isPainting = true;
            super.paintComponent(g);
            isPainting = false;
        } else {
            super.paint(g);
        }
    }

    /**
     * This method is empty, because all painting is done by
     * {@link #paint(Graphics)} and
     * {@link javax.swing.plaf.LayerUI#update(Graphics, JComponent)} methods
     */
    protected void paintComponent(Graphics g) {
    }

    /**
     * To enable the correct painting of the {@code glassPane} and view component,
     * the {@code JLayer} overrides the default implementation of
     * this method to return {@code false} when the {@code glassPane} is visible.
     *
     * @return false if {@code JLayer}'s {@code glassPane} is visible
     */
    public boolean isOptimizedDrawingEnabled() {
        return !glassPane.isVisible();
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if (getUI() != null) {
            getUI().applyPropertyChange(evt, this);
        }
    }

    /**
     * Sets the bitmask of event types to receive by this {@code JLayer}.
     * Here is the list of the supported event types:
     * <ul>
     * <li>AWTEvent.COMPONENT_EVENT_MASK</li>
     * <li>AWTEvent.CONTAINER_EVENT_MASK</li>
     * <li>AWTEvent.FOCUS_EVENT_MASK</li>
     * <li>AWTEvent.KEY_EVENT_MASK</li>
     * <li>AWTEvent.MOUSE_WHEEL_EVENT_MASK</li>
     * <li>AWTEvent.MOUSE_MOTION_EVENT_MASK</li>
     * <li>AWTEvent.MOUSE_EVENT_MASK</li>
     * <li>AWTEvent.INPUT_METHOD_EVENT_MASK</li>
     * <li>AWTEvent.HIERARCHY_EVENT_MASK</li>
     * <li>AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK</li>
     * </ul>
     * <p/>
     * If {@code LayerUI} is installed,
     * {@link javax.swing.plaf.LayerUI#eventDispatched(AWTEvent, JLayer)} method
     * will only receive events that match the event mask.
     * <p/>
     * The following example shows how to correclty use this method
     * in the {@code LayerUI} implementations:
     * <pre>
     *    public void installUI(JComponent c) {
     *       super.installUI(c);
     *       JLayer l = (JLayer) c;
     *       // this LayerUI will receive only key and focus events
     *       l.setLayerEventMask(AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK);
     *    }
     *
     *    public void uninstallUI(JComponent c) {
     *       super.uninstallUI(c);
     *       JLayer l = (JLayer) c;
     *       // JLayer must be returned to its initial state
     *       l.setLayerEventMask(0);
     *    }
     * </pre>
     *
     * By default {@code JLayer} receives no events.
     *
     * @param layerEventMask the bitmask of event types to receive
     *
     * @throws IllegalArgumentException if the {@code layerEventMask} parameter
     * contains unsupported event types
     * @see #getLayerEventMask()
     */
    public void setLayerEventMask(long layerEventMask) {
        if (layerEventMask != (layerEventMask & ACCEPTED_EVENTS)) {
            throw new IllegalArgumentException(
                    "The event bitmask contains unsupported event types");
        }
        long oldEventMask = getLayerEventMask();
        this.eventMask = layerEventMask;
        firePropertyChange("layerEventMask", oldEventMask, layerEventMask);
        if (layerEventMask != oldEventMask) {
            disableEvents(oldEventMask);
            enableEvents(eventMask);
            if (isDisplayable()) {
                eventController.updateAWTEventListener(
                        oldEventMask, layerEventMask);
            }
        }
    }

    /**
     * Returns the bitmap of event mask to receive by this {@code JLayer}
     * and its {@code LayerUI}.
     * <p/>
     * It means that {@link javax.swing.plaf.LayerUI#eventDispatched(AWTEvent, JLayer)} method
     * will only receive events that match the event mask.
     * <p/>
     * By default {@code JLayer} receives no events.
     *
     * @return the bitmask of event types to receive for this {@code JLayer}
     */
    public long getLayerEventMask() {
        return eventMask;
    }

    /**
     * Delegates its functionality to the {@link javax.swing.plaf.LayerUI#updateUI(JLayer)} method,
     * if {@code LayerUI} is set.
     */
    public void updateUI() {
        if (getUI() != null) {
            getUI().updateUI(this);
        }
    }

    /**
     * Returns the preferred size of the viewport for a view component.
     * <p/>
     * If the ui delegate of this layer is not {@code null}, this method delegates its
     * implementation to the {@code LayerUI.getPreferredScrollableViewportSize(JLayer)}
     *
     * @return the preferred size of the viewport for a view component
     *
     * @see Scrollable
     * @see LayerUI#getPreferredScrollableViewportSize(JLayer)
     */
    public Dimension getPreferredScrollableViewportSize() {
        if (getUI() != null) {
            return getUI().getPreferredScrollableViewportSize(this);
        }
        return getPreferredSize();
    }

    /**
     * Returns a scroll increment, which is required for components
     * that display logical rows or columns in order to completely expose
     * one block of rows or columns, depending on the value of orientation.
     * <p/>
     * If the ui delegate of this layer is not {@code null}, this method delegates its
     * implementation to the {@code LayerUI.getScrollableBlockIncrement(JLayer,Rectangle,int,int)}
     *
     * @return the "block" increment for scrolling in the specified direction
     *
     * @see Scrollable
     * @see LayerUI#getScrollableBlockIncrement(JLayer, Rectangle, int, int)
     */
    public int getScrollableBlockIncrement(Rectangle visibleRect,
                                           int orientation, int direction) {
        if (getUI() != null) {
            return getUI().getScrollableBlockIncrement(this, visibleRect,
                    orientation, direction);
        }
        return (orientation == SwingConstants.VERTICAL) ? visibleRect.height :
                visibleRect.width;
    }

    /**
     * Returns {@code false} to indicate that the height of the viewport does not
     * determine the height of the layer, unless the preferred height
     * of the layer is smaller than the height of the viewport.
     * <p/>
     * If the ui delegate of this layer is not null, this method delegates its
     * implementation to the {@code LayerUI.getScrollableTracksViewportHeight(JLayer)}
     *
     * @return whether the layer should track the height of the viewport
     *
     * @see Scrollable
     * @see LayerUI#getScrollableTracksViewportHeight(JLayer)
     */
    public boolean getScrollableTracksViewportHeight() {
        if (getUI() != null) {
            return getUI().getScrollableTracksViewportHeight(this);
        }
        return false;
    }

    /**
     * Returns {@code false} to indicate that the width of the viewport does not
     * determine the width of the layer, unless the preferred width
     * of the layer is smaller than the width of the viewport.
     * <p/>
     * If the ui delegate of this layer is not null, this method delegates its
     * implementation to the {@code LayerUI.getScrollableTracksViewportWidth(JLayer)}
     *
     * @return whether the layer should track the width of the viewport
     *
     * @see Scrollable
     * @see LayerUI#getScrollableTracksViewportWidth(JLayer)
     */
    public boolean getScrollableTracksViewportWidth() {
        if (getUI() != null) {
            return getUI().getScrollableTracksViewportWidth(this);
        }
        return false;
    }

    /**
     * Returns a scroll increment, which is required for components
     * that display logical rows or columns in order to completely expose
     * one new row or column, depending on the value of orientation.
     * Ideally, components should handle a partially exposed row or column
     * by returning the distance required to completely expose the item.
     * <p/>
     * Scrolling containers, like {@code JScrollPane}, will use this method
     * each time the user requests a unit scroll.
     * <p/>
     * If the ui delegate of this layer is not {@code null}, this method delegates its
     * implementation to the {@code LayerUI.getScrollableUnitIncrement(JLayer,Rectangle,int,int)}
     *
     * @return The "unit" increment for scrolling in the specified direction.
     *         This value should always be positive.
     *
     * @see Scrollable
     * @see LayerUI#getScrollableUnitIncrement(JLayer, Rectangle, int, int)
     */
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation,
                                          int direction) {
        if (getUI() != null) {
            return getUI().getScrollableUnitIncrement(
                    this, visibleRect, orientation, direction);
        }
        return 1;
    }

    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (layerUI != null) {
            setUI(layerUI);
        }
        if (eventMask != 0) {
            eventController.updateAWTEventListener(0, eventMask);
        }
    }

    public void addNotify() {
        eventController.updateAWTEventListener(0, eventMask);
        super.addNotify();
    }

    public void removeNotify() {
        eventController.updateAWTEventListener(eventMask, 0);
        super.removeNotify();
    }

    /**
     * static AWTEventListener to be shared with all AbstractLayerUIs
     */
    private static class LayerEventController implements AWTEventListener {
        private ArrayList<Long> layerMaskList =
                new ArrayList<Long>();

        private long currentEventMask;

        @SuppressWarnings("unchecked")
        public void eventDispatched(AWTEvent event) {
            Object source = event.getSource();
            if (source instanceof Component) {
                Component component = (Component) source;
                while (component != null) {
                    if (component instanceof JLayer) {
                        JLayer l = (JLayer) component;
                        LayerUI ui = l.getUI();
                        if (ui != null &&
                                isEventEnabled(l.getLayerEventMask(),
                                        event.getID())) {
                            ui.eventDispatched(event, l);
                        }
                    }
                    component = component.getParent();
                }
            }
        }

        private void updateAWTEventListener(long oldEventMask, long newEventMask) {
            if (oldEventMask != 0) {
                layerMaskList.remove(oldEventMask);
            }
            if (newEventMask != 0) {
                layerMaskList.add(newEventMask);
            }
            long combinedMask = 0;
            for (Long mask : layerMaskList) {
                combinedMask |= mask;
            }
            if (combinedMask == 0) {
                removeAWTEventListener();
            } else if (getCurrentEventMask() != combinedMask) {
                removeAWTEventListener();
                addAWTEventListener(combinedMask);
            }
            currentEventMask = combinedMask;
        }

        private long getCurrentEventMask() {
            return currentEventMask;
        }

        private void addAWTEventListener(final long eventMask) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    Toolkit.getDefaultToolkit().
                            addAWTEventListener(LayerEventController.this, eventMask);
                    return null;
                }
            });

        }

        private void removeAWTEventListener() {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    Toolkit.getDefaultToolkit().
                            removeAWTEventListener(LayerEventController.this);
                    return null;
                }
            });
        }

        private boolean isEventEnabled(long eventMask, int id) {
            return (((eventMask & AWTEvent.COMPONENT_EVENT_MASK) != 0 &&
                    id >= ComponentEvent.COMPONENT_FIRST &&
                    id <= ComponentEvent.COMPONENT_LAST)
                    || ((eventMask & AWTEvent.CONTAINER_EVENT_MASK) != 0 &&
                    id >= ContainerEvent.CONTAINER_FIRST &&
                    id <= ContainerEvent.CONTAINER_LAST)
                    || ((eventMask & AWTEvent.FOCUS_EVENT_MASK) != 0 &&
                    id >= FocusEvent.FOCUS_FIRST &&
                    id <= FocusEvent.FOCUS_LAST)
                    || ((eventMask & AWTEvent.KEY_EVENT_MASK) != 0 &&
                    id >= KeyEvent.KEY_FIRST &&
                    id <= KeyEvent.KEY_LAST)
                    || ((eventMask & AWTEvent.MOUSE_WHEEL_EVENT_MASK) != 0 &&
                    id == MouseEvent.MOUSE_WHEEL)
                    || ((eventMask & AWTEvent.MOUSE_MOTION_EVENT_MASK) != 0 &&
                    (id == MouseEvent.MOUSE_MOVED ||
                            id == MouseEvent.MOUSE_DRAGGED))
                    || ((eventMask & AWTEvent.MOUSE_EVENT_MASK) != 0 &&
                    id != MouseEvent.MOUSE_MOVED &&
                    id != MouseEvent.MOUSE_DRAGGED &&
                    id != MouseEvent.MOUSE_WHEEL &&
                    id >= MouseEvent.MOUSE_FIRST &&
                    id <= MouseEvent.MOUSE_LAST)
                    || ((eventMask & AWTEvent.INPUT_METHOD_EVENT_MASK) != 0 &&
                    id >= InputMethodEvent.INPUT_METHOD_FIRST &&
                    id <= InputMethodEvent.INPUT_METHOD_LAST)
                    || ((eventMask & AWTEvent.HIERARCHY_EVENT_MASK) != 0 &&
                    id == HierarchyEvent.HIERARCHY_CHANGED)
                    || ((eventMask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0 &&
                    (id == HierarchyEvent.ANCESTOR_MOVED ||
                            id == HierarchyEvent.ANCESTOR_RESIZED)));
        }
    }

    /**
     * The default glassPane for the {@link javax.swing.JLayer}.
     * It is a subclass of {@code JPanel} which is non opaque by default.
     */
    private static class DefaultLayerGlassPane extends JPanel {
        /**
         * Creates a new {@link DefaultLayerGlassPane}
         */
        public DefaultLayerGlassPane() {
            setOpaque(false);
        }

        /**
         * First, implementatation of this method iterates through
         * glassPane's child components and returns {@code true}
         * if any of them is visible and contains passed x,y point.
         * After that it checks if no mouseListeners is attached to this component
         * and no mouse cursor is set, then it returns {@code false},
         * otherwise calls the super implementation of this method.
         *
         * @param x the <i>x</i> coordinate of the point
         * @param y the <i>y</i> coordinate of the point
         * @return true if this component logically contains x,y
         */
        public boolean contains(int x, int y) {
            for (int i = 0; i < getComponentCount(); i++) {
                Component c = getComponent(i);
                Point point = SwingUtilities.convertPoint(this, new Point(x, y), c);
                if(c.isVisible() && c.contains(point)){
                    return true;
                }
            }
            if (getMouseListeners().length == 0
                    && getMouseMotionListeners().length == 0
                    && getMouseWheelListeners().length == 0
                    && !isCursorSet()) {
                return false;
            }
            return super.contains(x, y);
        }
    }

    /**
     * The default layout manager for the {@link javax.swing.JLayer}.<br/>
     * It places the glassPane on top of the view component
     * and makes it the same size as {@code JLayer},
     * it also makes the view component the same size but minus layer's insets<br/>
     */
    private static class DefaultLayerLayout implements LayoutManager, Serializable {
        /**
         * {@inheritDoc}
         */
        public void layoutContainer(Container parent) {
            JLayer layer = (JLayer) parent;
            Component view = layer.getView();
            Component glassPane = layer.getGlassPane();
            if (view != null) {
                Insets insets = layer.getInsets();
                view.setLocation(insets.left, insets.top);
                view.setSize(layer.getWidth() - insets.left - insets.right,
                        layer.getHeight() - insets.top - insets.bottom);
            }
            if (glassPane != null) {
                glassPane.setLocation(0, 0);
                glassPane.setSize(layer.getWidth(), layer.getHeight());
            }
        }

        /**
         * {@inheritDoc}
         */
        public Dimension minimumLayoutSize(Container parent) {
            JLayer layer = (JLayer) parent;
            Insets insets = layer.getInsets();
            Dimension ret = new Dimension(insets.left + insets.right,
                    insets.top + insets.bottom);
            Component view = layer.getView();
            if (view != null) {
                Dimension size = view.getMinimumSize();
                ret.width += size.width;
                ret.height += size.height;
            }
            if (ret.width == 0 || ret.height == 0) {
                ret.width = ret.height = 4;
            }
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        public Dimension preferredLayoutSize(Container parent) {
            JLayer layer = (JLayer) parent;
            Insets insets = layer.getInsets();
            Dimension ret = new Dimension(insets.left + insets.right,
                    insets.top + insets.bottom);
            Component view = layer.getView();
            if (view != null) {
                Dimension size = view.getPreferredSize();
                if (size.width > 0 && size.height > 0) {
                    ret.width += size.width;
                    ret.height += size.height;
                }
            }
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        public void addLayoutComponent(String name, Component comp) {
        }

        /**
         * {@inheritDoc}
         */
        public void removeLayoutComponent(Component comp) {
        }
    }
}
