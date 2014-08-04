/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.awt.peer.MenuComponentPeer;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import sun.awt.AppContext;
import sun.awt.AWTAccessor;
import javax.accessibility.*;

import java.security.AccessControlContext;
import java.security.AccessController;

/**
 * The abstract class <code>MenuComponent</code> is the superclass
 * of all menu-related components. In this respect, the class
 * <code>MenuComponent</code> is analogous to the abstract superclass
 * <code>Component</code> for AWT components.
 * <p>
 * Menu components receive and process AWT events, just as components do,
 * through the method <code>processEvent</code>.
 *
 * @author      Arthur van Hoff
 * @since       1.0
 */
public abstract class MenuComponent implements java.io.Serializable {

    static {
        /* ensure that the necessary native libraries are loaded */
        Toolkit.loadLibraries();
        if (!GraphicsEnvironment.isHeadless()) {
            initIDs();
        }
    }

    transient MenuComponentPeer peer;
    transient MenuContainer parent;

    /**
     * The <code>AppContext</code> of the <code>MenuComponent</code>.
     * This is set in the constructor and never changes.
     */
    transient AppContext appContext;

    /**
     * The menu component's font. This value can be
     * <code>null</code> at which point a default will be used.
     * This defaults to <code>null</code>.
     *
     * @serial
     * @see #setFont(Font)
     * @see #getFont()
     */
    Font font;

    /**
     * The menu component's name, which defaults to <code>null</code>.
     * @serial
     * @see #getName()
     * @see #setName(String)
     */
    private String name;

    /**
     * A variable to indicate whether a name is explicitly set.
     * If <code>true</code> the name will be set explicitly.
     * This defaults to <code>false</code>.
     * @serial
     * @see #setName(String)
     */
    private boolean nameExplicitlySet = false;

    /**
     * Defaults to <code>false</code>.
     * @serial
     * @see #dispatchEvent(AWTEvent)
     */
    boolean newEventsOnly = false;

    /*
     * The menu's AccessControlContext.
     */
    private transient volatile AccessControlContext acc =
            AccessController.getContext();

    /*
     * Returns the acc this menu component was constructed with.
     */
    final AccessControlContext getAccessControlContext() {
        if (acc == null) {
            throw new SecurityException(
                    "MenuComponent is missing AccessControlContext");
        }
        return acc;
    }

    /*
     * Internal constants for serialization.
     */
    final static String actionListenerK = Component.actionListenerK;
    final static String itemListenerK = Component.itemListenerK;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -4536902356223894379L;

    static {
        AWTAccessor.setMenuComponentAccessor(
            new AWTAccessor.MenuComponentAccessor() {
                @Override
                public AppContext getAppContext(MenuComponent menuComp) {
                    return menuComp.appContext;
                }
                @Override
                public void setAppContext(MenuComponent menuComp,
                                          AppContext appContext) {
                    menuComp.appContext = appContext;
                }
                @Override
                public MenuContainer getParent(MenuComponent menuComp) {
                    return menuComp.parent;
                }
                @Override
                public void setParent(MenuComponent menuComp, MenuContainer menuContainer) {
                    menuComp.parent = menuContainer;
                }
                @Override
                public Font getFont_NoClientCode(MenuComponent menuComp) {
                    return menuComp.getFont_NoClientCode();
                }
            });
    }

    /**
     * Creates a <code>MenuComponent</code>.
     * @exception HeadlessException if
     *    <code>GraphicsEnvironment.isHeadless</code>
     *    returns <code>true</code>
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public MenuComponent() throws HeadlessException {
        GraphicsEnvironment.checkHeadless();
        appContext = AppContext.getAppContext();
    }

    /**
     * Constructs a name for this <code>MenuComponent</code>.
     * Called by <code>getName</code> when the name is <code>null</code>.
     * @return a name for this <code>MenuComponent</code>
     */
    String constructComponentName() {
        return null; // For strict compliance with prior platform versions, a MenuComponent
                     // that doesn't set its name should return null from
                     // getName()
    }

    /**
     * Gets the name of the menu component.
     * @return        the name of the menu component
     * @see           java.awt.MenuComponent#setName(java.lang.String)
     * @since         1.1
     */
    public String getName() {
        if (name == null && !nameExplicitlySet) {
            synchronized(this) {
                if (name == null && !nameExplicitlySet)
                    name = constructComponentName();
            }
        }
        return name;
    }

    /**
     * Sets the name of the component to the specified string.
     * @param         name    the name of the menu component
     * @see           java.awt.MenuComponent#getName
     * @since         1.1
     */
    public void setName(String name) {
        synchronized(this) {
            this.name = name;
            nameExplicitlySet = true;
        }
    }

    /**
     * Returns the parent container for this menu component.
     * @return    the menu component containing this menu component,
     *                 or <code>null</code> if this menu component
     *                 is the outermost component, the menu bar itself
     */
    public MenuContainer getParent() {
        return getParent_NoClientCode();
    }
    // NOTE: This method may be called by privileged threads.
    //       This functionality is implemented in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    final MenuContainer getParent_NoClientCode() {
        return parent;
    }

    /**
     * @deprecated As of JDK version 1.1,
     * programs should not directly manipulate peers.
     * @return the peer for this component
     */
    @Deprecated
    public MenuComponentPeer getPeer() {
        return peer;
    }

    /**
     * Gets the font used for this menu component.
     * @return   the font used in this menu component, if there is one;
     *                  <code>null</code> otherwise
     * @see     java.awt.MenuComponent#setFont
     */
    public Font getFont() {
        Font font = this.font;
        if (font != null) {
            return font;
        }
        MenuContainer parent = this.parent;
        if (parent != null) {
            return parent.getFont();
        }
        return null;
    }

    // NOTE: This method may be called by privileged threads.
    //       This functionality is implemented in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    final Font getFont_NoClientCode() {
        Font font = this.font;
        if (font != null) {
            return font;
        }

        // The MenuContainer interface does not have getFont_NoClientCode()
        // and it cannot, because it must be package-private. Because of
        // this, we must manually cast classes that implement
        // MenuContainer.
        Object parent = this.parent;
        if (parent != null) {
            if (parent instanceof Component) {
                font = ((Component)parent).getFont_NoClientCode();
            } else if (parent instanceof MenuComponent) {
                font = ((MenuComponent)parent).getFont_NoClientCode();
            }
        }
        return font;
    } // getFont_NoClientCode()


    /**
     * Sets the font to be used for this menu component to the specified
     * font. This font is also used by all subcomponents of this menu
     * component, unless those subcomponents specify a different font.
     * <p>
     * Some platforms may not support setting of all font attributes
     * of a menu component; in such cases, calling <code>setFont</code>
     * will have no effect on the unsupported font attributes of this
     * menu component.  Unless subcomponents of this menu component
     * specify a different font, this font will be used by those
     * subcomponents if supported by the underlying platform.
     *
     * @param     f   the font to be set
     * @see       #getFont
     * @see       Font#getAttributes
     * @see       java.awt.font.TextAttribute
     */
    public void setFont(Font f) {
        font = f;
        //Fixed 6312943: NullPointerException in method MenuComponent.setFont(Font)
        MenuComponentPeer peer = this.peer;
        if (peer != null) {
            peer.setFont(f);
        }
    }

    /**
     * Removes the menu component's peer.  The peer allows us to modify the
     * appearance of the menu component without changing the functionality of
     * the menu component.
     */
    public void removeNotify() {
        synchronized (getTreeLock()) {
            MenuComponentPeer p = this.peer;
            if (p != null) {
                Toolkit.getEventQueue().removeSourceEvents(this, true);
                this.peer = null;
                p.dispose();
            }
        }
    }

    /**
     * Posts the specified event to the menu.
     * This method is part of the Java&nbsp;1.0 event system
     * and it is maintained only for backwards compatibility.
     * Its use is discouraged, and it may not be supported
     * in the future.
     * @param evt the event which is to take place
     * @deprecated As of JDK version 1.1, replaced by {@link
     * #dispatchEvent(AWTEvent) dispatchEvent}.
     */
    @Deprecated
    public boolean postEvent(Event evt) {
        MenuContainer parent = this.parent;
        if (parent != null) {
            parent.postEvent(evt);
        }
        return false;
    }

    /**
     * Delivers an event to this component or one of its sub components.
     * @param e the event
     */
    public final void dispatchEvent(AWTEvent e) {
        dispatchEventImpl(e);
    }

    void dispatchEventImpl(AWTEvent e) {
        EventQueue.setCurrentEventAndMostRecentTime(e);

        Toolkit.getDefaultToolkit().notifyAWTEventListeners(e);

        if (newEventsOnly ||
            (parent != null && parent instanceof MenuComponent &&
             ((MenuComponent)parent).newEventsOnly)) {
            if (eventEnabled(e)) {
                processEvent(e);
            } else if (e instanceof ActionEvent && parent != null) {
                e.setSource(parent);
                ((MenuComponent)parent).dispatchEvent(e);
            }

        } else { // backward compatibility
            Event olde = e.convertToOld();
            if (olde != null) {
                postEvent(olde);
            }
        }
    }

    // REMIND: remove when filtering is done at lower level
    boolean eventEnabled(AWTEvent e) {
        return false;
    }
    /**
     * Processes events occurring on this menu component.
     * <p>Note that if the event parameter is <code>null</code>
     * the behavior is unspecified and may result in an
     * exception.
     *
     * @param e the event
     * @since 1.1
     */
    protected void processEvent(AWTEvent e) {
    }

    /**
     * Returns a string representing the state of this
     * <code>MenuComponent</code>. This method is intended to be used
     * only for debugging purposes, and the content and format of the
     * returned string may vary between implementations. The returned
     * string may be empty but may not be <code>null</code>.
     *
     * @return     the parameter string of this menu component
     */
    protected String paramString() {
        String thisName = getName();
        return (thisName != null? thisName : "");
    }

    /**
     * Returns a representation of this menu component as a string.
     * @return  a string representation of this menu component
     */
    public String toString() {
        return getClass().getName() + "[" + paramString() + "]";
    }

    /**
     * Gets this component's locking object (the object that owns the thread
     * synchronization monitor) for AWT component-tree and layout
     * operations.
     * @return this component's locking object
     */
    protected final Object getTreeLock() {
        return Component.LOCK;
    }

    /**
     * Reads the menu component from an object input stream.
     *
     * @param s the <code>ObjectInputStream</code> to read
     * @exception HeadlessException if
     *   <code>GraphicsEnvironment.isHeadless</code> returns
     *   <code>true</code>
     * @serial
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException, HeadlessException
    {
        GraphicsEnvironment.checkHeadless();

        acc = AccessController.getContext();

        s.defaultReadObject();

        appContext = AppContext.getAppContext();
    }

    /**
     * Initialize JNI field and method IDs.
     */
    private static native void initIDs();


    /*
     * --- Accessibility Support ---
     *
     *  MenuComponent will contain all of the methods in interface Accessible,
     *  though it won't actually implement the interface - that will be up
     *  to the individual objects which extend MenuComponent.
     */

    AccessibleContext accessibleContext = null;

    /**
     * Gets the <code>AccessibleContext</code> associated with
     * this <code>MenuComponent</code>.
     *
     * The method implemented by this base class returns <code>null</code>.
     * Classes that extend <code>MenuComponent</code>
     * should implement this method to return the
     * <code>AccessibleContext</code> associated with the subclass.
     *
     * @return the <code>AccessibleContext</code> of this
     *     <code>MenuComponent</code>
     * @since 1.3
     */
    public AccessibleContext getAccessibleContext() {
        return accessibleContext;
    }

    /**
     * Inner class of <code>MenuComponent</code> used to provide
     * default support for accessibility.  This class is not meant
     * to be used directly by application developers, but is instead
     * meant only to be subclassed by menu component developers.
     * <p>
     * The class used to obtain the accessible role for this object.
     * @since 1.3
     */
    protected abstract class AccessibleAWTMenuComponent
        extends AccessibleContext
        implements java.io.Serializable, AccessibleComponent,
                   AccessibleSelection
    {
        /*
         * JDK 1.3 serialVersionUID
         */
        private static final long serialVersionUID = -4269533416223798698L;

        /**
         * Although the class is abstract, this should be called by
         * all sub-classes.
         */
        protected AccessibleAWTMenuComponent() {
        }

        // AccessibleContext methods
        //

        /**
         * Gets the <code>AccessibleSelection</code> associated with this
         * object which allows its <code>Accessible</code> children to be selected.
         *
         * @return <code>AccessibleSelection</code> if supported by object;
         *      else return <code>null</code>
         * @see AccessibleSelection
         */
        public AccessibleSelection getAccessibleSelection() {
            return this;
        }

        /**
         * Gets the accessible name of this object.  This should almost never
         * return <code>java.awt.MenuComponent.getName</code>, as that
         * generally isn't a localized name, and doesn't have meaning for the
         * user.  If the object is fundamentally a text object (e.g. a menu item), the
         * accessible name should be the text of the object (e.g. "save").
         * If the object has a tooltip, the tooltip text may also be an
         * appropriate String to return.
         *
         * @return the localized name of the object -- can be <code>null</code>
         *         if this object does not have a name
         * @see AccessibleContext#setAccessibleName
         */
        public String getAccessibleName() {
            return accessibleName;
        }

        /**
         * Gets the accessible description of this object.  This should be
         * a concise, localized description of what this object is - what
         * is its meaning to the user.  If the object has a tooltip, the
         * tooltip text may be an appropriate string to return, assuming
         * it contains a concise description of the object (instead of just
         * the name of the object - e.g. a "Save" icon on a toolbar that
         * had "save" as the tooltip text shouldn't return the tooltip
         * text as the description, but something like "Saves the current
         * text document" instead).
         *
         * @return the localized description of the object -- can be
         *     <code>null</code> if this object does not have a description
         * @see AccessibleContext#setAccessibleDescription
         */
        public String getAccessibleDescription() {
            return accessibleDescription;
        }

        /**
         * Gets the role of this object.
         *
         * @return an instance of <code>AccessibleRole</code>
         *     describing the role of the object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.AWT_COMPONENT; // Non-specific -- overridden in subclasses
        }

        /**
         * Gets the state of this object.
         *
         * @return an instance of <code>AccessibleStateSet</code>
         *     containing the current state set of the object
         * @see AccessibleState
         */
        public AccessibleStateSet getAccessibleStateSet() {
            return MenuComponent.this.getAccessibleStateSet();
        }

        /**
         * Gets the <code>Accessible</code> parent of this object.
         * If the parent of this object implements <code>Accessible</code>,
         * this method should simply return <code>getParent</code>.
         *
         * @return the <code>Accessible</code> parent of this object -- can
         *    be <code>null</code> if this object does not have an
         *    <code>Accessible</code> parent
         */
        public Accessible getAccessibleParent() {
            if (accessibleParent != null) {
                return accessibleParent;
            } else {
                MenuContainer parent = MenuComponent.this.getParent();
                if (parent instanceof Accessible) {
                    return (Accessible) parent;
                }
            }
            return null;
        }

        /**
         * Gets the index of this object in its accessible parent.
         *
         * @return the index of this object in its parent; -1 if this
         *     object does not have an accessible parent
         * @see #getAccessibleParent
         */
        public int getAccessibleIndexInParent() {
            return MenuComponent.this.getAccessibleIndexInParent();
        }

        /**
         * Returns the number of accessible children in the object.  If all
         * of the children of this object implement <code>Accessible</code>,
         * then this method should return the number of children of this object.
         *
         * @return the number of accessible children in the object
         */
        public int getAccessibleChildrenCount() {
            return 0; // MenuComponents don't have children
        }

        /**
         * Returns the nth <code>Accessible</code> child of the object.
         *
         * @param i zero-based index of child
         * @return the nth Accessible child of the object
         */
        public Accessible getAccessibleChild(int i) {
            return null; // MenuComponents don't have children
        }

        /**
         * Returns the locale of this object.
         *
         * @return the locale of this object
         */
        public java.util.Locale getLocale() {
            MenuContainer parent = MenuComponent.this.getParent();
            if (parent instanceof Component)
                return ((Component)parent).getLocale();
            else
                return java.util.Locale.getDefault();
        }

        /**
         * Gets the <code>AccessibleComponent</code> associated with
         * this object if one exists.  Otherwise return <code>null</code>.
         *
         * @return the component
         */
        public AccessibleComponent getAccessibleComponent() {
            return this;
        }


        // AccessibleComponent methods
        //
        /**
         * Gets the background color of this object.
         *
         * @return the background color, if supported, of the object;
         *     otherwise, <code>null</code>
         */
        public Color getBackground() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Sets the background color of this object.
         * (For transparency, see <code>isOpaque</code>.)
         *
         * @param c the new <code>Color</code> for the background
         * @see Component#isOpaque
         */
        public void setBackground(Color c) {
            // Not supported for MenuComponents
        }

        /**
         * Gets the foreground color of this object.
         *
         * @return the foreground color, if supported, of the object;
         *     otherwise, <code>null</code>
         */
        public Color getForeground() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Sets the foreground color of this object.
         *
         * @param c the new <code>Color</code> for the foreground
         */
        public void setForeground(Color c) {
            // Not supported for MenuComponents
        }

        /**
         * Gets the <code>Cursor</code> of this object.
         *
         * @return the <code>Cursor</code>, if supported, of the object;
         *     otherwise, <code>null</code>
         */
        public Cursor getCursor() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Sets the <code>Cursor</code> of this object.
         * <p>
         * The method may have no visual effect if the Java platform
         * implementation and/or the native system do not support
         * changing the mouse cursor shape.
         * @param cursor the new <code>Cursor</code> for the object
         */
        public void setCursor(Cursor cursor) {
            // Not supported for MenuComponents
        }

        /**
         * Gets the <code>Font</code> of this object.
         *
         * @return the <code>Font</code>,if supported, for the object;
         *     otherwise, <code>null</code>
         */
        public Font getFont() {
            return MenuComponent.this.getFont();
        }

        /**
         * Sets the <code>Font</code> of this object.
         *
         * @param f the new <code>Font</code> for the object
         */
        public void setFont(Font f) {
            MenuComponent.this.setFont(f);
        }

        /**
         * Gets the <code>FontMetrics</code> of this object.
         *
         * @param f the <code>Font</code>
         * @return the FontMetrics, if supported, the object;
         *              otherwise, <code>null</code>
         * @see #getFont
         */
        public FontMetrics getFontMetrics(Font f) {
            return null; // Not supported for MenuComponents
        }

        /**
         * Determines if the object is enabled.
         *
         * @return true if object is enabled; otherwise, false
         */
        public boolean isEnabled() {
            return true; // Not supported for MenuComponents
        }

        /**
         * Sets the enabled state of the object.
         *
         * @param b if true, enables this object; otherwise, disables it
         */
        public void setEnabled(boolean b) {
            // Not supported for MenuComponents
        }

        /**
         * Determines if the object is visible.  Note: this means that the
         * object intends to be visible; however, it may not in fact be
         * showing on the screen because one of the objects that this object
         * is contained by is not visible.  To determine if an object is
         * showing on the screen, use <code>isShowing</code>.
         *
         * @return true if object is visible; otherwise, false
         */
        public boolean isVisible() {
            return true; // Not supported for MenuComponents
        }

        /**
         * Sets the visible state of the object.
         *
         * @param b if true, shows this object; otherwise, hides it
         */
        public void setVisible(boolean b) {
            // Not supported for MenuComponents
        }

        /**
         * Determines if the object is showing.  This is determined by checking
         * the visibility of the object and ancestors of the object.  Note:
         * this will return true even if the object is obscured by another
         * (for example, it happens to be underneath a menu that was pulled
         * down).
         *
         * @return true if object is showing; otherwise, false
         */
        public boolean isShowing() {
            return true; // Not supported for MenuComponents
        }

        /**
         * Checks whether the specified point is within this object's bounds,
         * where the point's x and y coordinates are defined to be relative to
         * the coordinate system of the object.
         *
         * @param p the <code>Point</code> relative to the coordinate
         *     system of the object
         * @return true if object contains <code>Point</code>; otherwise false
         */
        public boolean contains(Point p) {
            return false; // Not supported for MenuComponents
        }

        /**
         * Returns the location of the object on the screen.
         *
         * @return location of object on screen -- can be <code>null</code>
         *     if this object is not on the screen
         */
        public Point getLocationOnScreen() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Gets the location of the object relative to the parent in the form
         * of a point specifying the object's top-left corner in the screen's
         * coordinate space.
         *
         * @return an instance of <code>Point</code> representing the
         *    top-left corner of the object's bounds in the coordinate
         *    space of the screen; <code>null</code> if
         *    this object or its parent are not on the screen
         */
        public Point getLocation() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Sets the location of the object relative to the parent.
         */
        public void setLocation(Point p) {
            // Not supported for MenuComponents
        }

        /**
         * Gets the bounds of this object in the form of a
         * <code>Rectangle</code> object.
         * The bounds specify this object's width, height, and location
         * relative to its parent.
         *
         * @return a rectangle indicating this component's bounds;
         *     <code>null</code> if this object is not on the screen
         */
        public Rectangle getBounds() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Sets the bounds of this object in the form of a
         * <code>Rectangle</code> object.
         * The bounds specify this object's width, height, and location
         * relative to its parent.
         *
         * @param r a rectangle indicating this component's bounds
         */
        public void setBounds(Rectangle r) {
            // Not supported for MenuComponents
        }

        /**
         * Returns the size of this object in the form of a
         * <code>Dimension</code> object. The height field of
         * the <code>Dimension</code> object contains this object's
         * height, and the width field of the <code>Dimension</code>
         * object contains this object's width.
         *
         * @return a <code>Dimension</code> object that indicates the
         *         size of this component; <code>null</code>
         *         if this object is not on the screen
         */
        public Dimension getSize() {
            return null; // Not supported for MenuComponents
        }

        /**
         * Resizes this object.
         *
         * @param d - the <code>Dimension</code> specifying the
         *    new size of the object
         */
        public void setSize(Dimension d) {
            // Not supported for MenuComponents
        }

        /**
         * Returns the <code>Accessible</code> child, if one exists,
         * contained at the local coordinate <code>Point</code>.
         * If there is no <code>Accessible</code> child, <code>null</code>
         * is returned.
         *
         * @param p the point defining the top-left corner of the
         *    <code>Accessible</code>, given in the coordinate space
         *    of the object's parent
         * @return the <code>Accessible</code>, if it exists,
         *    at the specified location; else <code>null</code>
         */
        public Accessible getAccessibleAt(Point p) {
            return null; // MenuComponents don't have children
        }

        /**
         * Returns whether this object can accept focus or not.
         *
         * @return true if object can accept focus; otherwise false
         */
        public boolean isFocusTraversable() {
            return true; // Not supported for MenuComponents
        }

        /**
         * Requests focus for this object.
         */
        public void requestFocus() {
            // Not supported for MenuComponents
        }

        /**
         * Adds the specified focus listener to receive focus events from this
         * component.
         *
         * @param l the focus listener
         */
        public void addFocusListener(java.awt.event.FocusListener l) {
            // Not supported for MenuComponents
        }

        /**
         * Removes the specified focus listener so it no longer receives focus
         * events from this component.
         *
         * @param l the focus listener
         */
        public void removeFocusListener(java.awt.event.FocusListener l) {
            // Not supported for MenuComponents
        }

        // AccessibleSelection methods
        //

        /**
         * Returns the number of <code>Accessible</code> children currently selected.
         * If no children are selected, the return value will be 0.
         *
         * @return the number of items currently selected
         */
         public int getAccessibleSelectionCount() {
             return 0;  //  To be fully implemented in a future release
         }

        /**
         * Returns an <code>Accessible</code> representing the specified
         * selected child in the object.  If there isn't a selection, or there are
         * fewer children selected than the integer passed in, the return
         * value will be <code>null</code>.
         * <p>Note that the index represents the i-th selected child, which
         * is different from the i-th child.
         *
         * @param i the zero-based index of selected children
         * @return the i-th selected child
         * @see #getAccessibleSelectionCount
         */
         public Accessible getAccessibleSelection(int i) {
             return null;  //  To be fully implemented in a future release
         }

        /**
         * Determines if the current child of this object is selected.
         *
         * @return true if the current child of this object is selected;
         *    else false
         * @param i the zero-based index of the child in this
         *      <code>Accessible</code> object
         * @see AccessibleContext#getAccessibleChild
         */
         public boolean isAccessibleChildSelected(int i) {
             return false;  //  To be fully implemented in a future release
         }

        /**
         * Adds the specified <code>Accessible</code> child of the object
         * to the object's selection.  If the object supports multiple selections,
         * the specified child is added to any existing selection, otherwise
         * it replaces any existing selection in the object.  If the
         * specified child is already selected, this method has no effect.
         *
         * @param i the zero-based index of the child
         * @see AccessibleContext#getAccessibleChild
         */
         public void addAccessibleSelection(int i) {
               //  To be fully implemented in a future release
         }

        /**
         * Removes the specified child of the object from the object's
         * selection.  If the specified item isn't currently selected, this
         * method has no effect.
         *
         * @param i the zero-based index of the child
         * @see AccessibleContext#getAccessibleChild
         */
         public void removeAccessibleSelection(int i) {
               //  To be fully implemented in a future release
         }

        /**
         * Clears the selection in the object, so that no children in the
         * object are selected.
         */
         public void clearAccessibleSelection() {
               //  To be fully implemented in a future release
         }

        /**
         * Causes every child of the object to be selected
         * if the object supports multiple selections.
         */
         public void selectAllAccessibleSelection() {
               //  To be fully implemented in a future release
         }

    } // inner class AccessibleAWTComponent

    /**
     * Gets the index of this object in its accessible parent.
     *
     * @return -1 if this object does not have an accessible parent;
     *      otherwise, the index of the child in its accessible parent.
     */
    int getAccessibleIndexInParent() {
        MenuContainer localParent = parent;
        if (!(localParent instanceof MenuComponent)) {
            // MenuComponents only have accessible index when inside MenuComponents
            return -1;
        }
        MenuComponent localParentMenu = (MenuComponent)localParent;
        return localParentMenu.getAccessibleChildIndex(this);
    }

    /**
     * Gets the index of the child within this MenuComponent.
     *
     * @param child MenuComponent whose index we are interested in.
     * @return -1 if this object doesn't contain the child,
     *      otherwise, index of the child.
     */
    int getAccessibleChildIndex(MenuComponent child) {
        return -1; // Overridden in subclasses.
    }

    /**
     * Gets the state of this object.
     *
     * @return an instance of <code>AccessibleStateSet</code>
     *     containing the current state set of the object
     * @see AccessibleState
     */
    AccessibleStateSet getAccessibleStateSet() {
        AccessibleStateSet states = new AccessibleStateSet();
        return states;
    }

}
