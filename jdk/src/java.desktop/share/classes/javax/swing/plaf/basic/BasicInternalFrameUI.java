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

package javax.swing.plaf.basic;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.event.*;
import java.beans.*;
import sun.swing.DefaultLookup;
import sun.swing.UIAction;

/**
 * A basic L&amp;F implementation of JInternalFrame.
 *
 * @author David Kloba
 * @author Rich Schiavi
 */
public class BasicInternalFrameUI extends InternalFrameUI
{

    protected JInternalFrame frame;

    private Handler handler;
    protected MouseInputAdapter          borderListener;
    protected PropertyChangeListener     propertyChangeListener;
    protected LayoutManager              internalFrameLayout;
    protected ComponentListener          componentListener;
    protected MouseInputListener         glassPaneDispatcher;
    private InternalFrameListener        internalFrameListener;

    protected JComponent northPane;
    protected JComponent southPane;
    protected JComponent westPane;
    protected JComponent eastPane;

    protected BasicInternalFrameTitlePane titlePane; // access needs this

    private static DesktopManager sharedDesktopManager;
    private boolean componentListenerAdded = false;

    private Rectangle parentBounds;

    private boolean dragging = false;
    private boolean resizing = false;

    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    @Deprecated
    protected KeyStroke openMenuKey;

    private boolean keyBindingRegistered = false;
    private boolean keyBindingActive = false;

/////////////////////////////////////////////////////////////////////////////
// ComponentUI Interface Implementation methods
/////////////////////////////////////////////////////////////////////////////
    public static ComponentUI createUI(JComponent b)    {
        return new BasicInternalFrameUI((JInternalFrame)b);
    }

    public BasicInternalFrameUI(JInternalFrame b)   {
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf instanceof BasicLookAndFeel) {
            ((BasicLookAndFeel)laf).installAWTEventListener();
        }
    }

    public void installUI(JComponent c)   {

        frame = (JInternalFrame)c;

        installDefaults();
        installListeners();
        installComponents();
        installKeyboardActions();

        LookAndFeel.installProperty(frame, "opaque", Boolean.TRUE);
    }

    public void uninstallUI(JComponent c) {
        if(c != frame)
            throw new IllegalComponentStateException(
                this + " was asked to deinstall() "
                + c + " when it only knows about "
                + frame + ".");

        uninstallKeyboardActions();
        uninstallComponents();
        uninstallListeners();
        uninstallDefaults();
        updateFrameCursor();
        handler = null;
        frame = null;
    }

    protected void installDefaults(){
        Icon frameIcon = frame.getFrameIcon();
        if (frameIcon == null || frameIcon instanceof UIResource) {
            frame.setFrameIcon(UIManager.getIcon("InternalFrame.icon"));
        }

        // Enable the content pane to inherit background color from its
        // parent by setting its background color to null.
        Container contentPane = frame.getContentPane();
        if (contentPane != null) {
          Color bg = contentPane.getBackground();
          if (bg instanceof UIResource)
            contentPane.setBackground(null);
        }
        frame.setLayout(internalFrameLayout = createLayoutManager());
        frame.setBackground(UIManager.getLookAndFeelDefaults().getColor("control"));

        LookAndFeel.installBorder(frame, "InternalFrame.border");

    }
    protected void installKeyboardActions(){
        createInternalFrameListener();
        if (internalFrameListener != null) {
            frame.addInternalFrameListener(internalFrameListener);
        }

        LazyActionMap.installLazyActionMap(frame, BasicInternalFrameUI.class,
            "InternalFrame.actionMap");
    }

    static void loadActionMap(LazyActionMap map) {
        map.put(new UIAction("showSystemMenu") {
            public void actionPerformed(ActionEvent evt) {
                JInternalFrame iFrame = (JInternalFrame)evt.getSource();
                if (iFrame.getUI() instanceof BasicInternalFrameUI) {
                    JComponent comp = ((BasicInternalFrameUI)
                        iFrame.getUI()).getNorthPane();
                    if (comp instanceof BasicInternalFrameTitlePane) {
                        ((BasicInternalFrameTitlePane)comp).
                            showSystemMenu();
                    }
                }
            }

            public boolean isEnabled(Object sender){
                if (sender instanceof JInternalFrame) {
                    JInternalFrame iFrame = (JInternalFrame)sender;
                    if (iFrame.getUI() instanceof BasicInternalFrameUI) {
                        return ((BasicInternalFrameUI)iFrame.getUI()).
                            isKeyBindingActive();
                    }
                }
                return false;
            }
        });

        // Set the ActionMap's parent to the Auditory Feedback Action Map
        BasicLookAndFeel.installAudioActionMap(map);
    }

    protected void installComponents(){
        setNorthPane(createNorthPane(frame));
        setSouthPane(createSouthPane(frame));
        setEastPane(createEastPane(frame));
        setWestPane(createWestPane(frame));
    }

    /**
     * @since 1.3
     */
    protected void installListeners() {
        borderListener = createBorderListener(frame);
        propertyChangeListener = createPropertyChangeListener();
        frame.addPropertyChangeListener(propertyChangeListener);
        installMouseHandlers(frame);
        glassPaneDispatcher = createGlassPaneDispatcher();
        if (glassPaneDispatcher != null) {
            frame.getGlassPane().addMouseListener(glassPaneDispatcher);
            frame.getGlassPane().addMouseMotionListener(glassPaneDispatcher);
        }
        componentListener =  createComponentListener();
        if (frame.getParent() != null) {
          parentBounds = frame.getParent().getBounds();
        }
        if ((frame.getParent() != null) && !componentListenerAdded) {
            frame.getParent().addComponentListener(componentListener);
            componentListenerAdded = true;
        }
    }

    // Provide a FocusListener to listen for a WINDOW_LOST_FOCUS event,
    // so that a resize can be cancelled if the focus is lost while resizing
    // when an Alt-Tab, modal dialog popup, iconify, dispose, or remove
    // of the internal frame occurs.
    private WindowFocusListener getWindowFocusListener(){
        return getHandler();
    }

    // Cancel a resize in progress by calling finishMouseReleased().
    private void cancelResize() {
        if (resizing) {
            if (borderListener instanceof BorderListener) {
                ((BorderListener)borderListener).finishMouseReleased();
            }
        }
    }

    private Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    InputMap getInputMap(int condition) {
        if (condition == JComponent.WHEN_IN_FOCUSED_WINDOW) {
            return createInputMap(condition);
        }
        return null;
    }

    InputMap createInputMap(int condition) {
        if (condition == JComponent.WHEN_IN_FOCUSED_WINDOW) {
            Object[] bindings = (Object[])DefaultLookup.get(
                    frame, this, "InternalFrame.windowBindings");

            if (bindings != null) {
                return LookAndFeel.makeComponentInputMap(frame, bindings);
            }
        }
        return null;
    }

    protected void uninstallDefaults() {
        Icon frameIcon = frame.getFrameIcon();
        if (frameIcon instanceof UIResource) {
            frame.setFrameIcon(null);
        }
        internalFrameLayout = null;
        frame.setLayout(null);
        LookAndFeel.uninstallBorder(frame);
    }

    protected void uninstallComponents(){
        setNorthPane(null);
        setSouthPane(null);
        setEastPane(null);
        setWestPane(null);
        if(titlePane != null) {
            titlePane.uninstallDefaults();
        }
        titlePane = null;
    }

    /**
     * @since 1.3
     */
    protected void uninstallListeners() {
        if ((frame.getParent() != null) && componentListenerAdded) {
            frame.getParent().removeComponentListener(componentListener);
            componentListenerAdded = false;
        }
        componentListener = null;
      if (glassPaneDispatcher != null) {
          frame.getGlassPane().removeMouseListener(glassPaneDispatcher);
          frame.getGlassPane().removeMouseMotionListener(glassPaneDispatcher);
          glassPaneDispatcher = null;
      }
      deinstallMouseHandlers(frame);
      frame.removePropertyChangeListener(propertyChangeListener);
      propertyChangeListener = null;
      borderListener = null;
    }

    protected void uninstallKeyboardActions(){
        if (internalFrameListener != null) {
            frame.removeInternalFrameListener(internalFrameListener);
        }
        internalFrameListener = null;

        SwingUtilities.replaceUIInputMap(frame, JComponent.
                                         WHEN_IN_FOCUSED_WINDOW, null);
        SwingUtilities.replaceUIActionMap(frame, null);

    }

    void updateFrameCursor() {
        if (resizing) {
            return;
        }
        Cursor s = frame.getLastCursor();
        if (s == null) {
            s = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        }
        frame.setCursor(s);
    }

    protected LayoutManager createLayoutManager(){
        return getHandler();
    }

    protected PropertyChangeListener createPropertyChangeListener(){
        return getHandler();
    }



    public Dimension getPreferredSize(JComponent x)    {
        if(frame == x)
            return frame.getLayout().preferredLayoutSize(x);
        return new Dimension(100, 100);
    }

    public Dimension getMinimumSize(JComponent x)  {
        if(frame == x) {
            return frame.getLayout().minimumLayoutSize(x);
        }
        return new Dimension(0, 0);
    }

    public Dimension getMaximumSize(JComponent x) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }



    /**
     * Installs necessary mouse handlers on <code>newPane</code>
     * and adds it to the frame.
     * Reverse process for the <code>currentPane</code>.
     *
     * @param currentPane this {@code Jcomponent} is the current pane being
     * viewed that has mouse handlers installed
     * @param newPane this {@code Jcomponent} is the pane which will be added
     * and have mouse handlers installed
     */
    protected void replacePane(JComponent currentPane, JComponent newPane) {
        if(currentPane != null) {
            deinstallMouseHandlers(currentPane);
            frame.remove(currentPane);
        }
        if(newPane != null) {
           frame.add(newPane);
           installMouseHandlers(newPane);
        }
    }

    protected void deinstallMouseHandlers(JComponent c) {
      c.removeMouseListener(borderListener);
      c.removeMouseMotionListener(borderListener);
    }

    protected void installMouseHandlers(JComponent c) {
      c.addMouseListener(borderListener);
      c.addMouseMotionListener(borderListener);
    }

    protected JComponent createNorthPane(JInternalFrame w) {
      titlePane = new BasicInternalFrameTitlePane(w);
      return titlePane;
    }


    protected JComponent createSouthPane(JInternalFrame w) {
        return null;
    }

    protected JComponent createWestPane(JInternalFrame w) {
        return null;
    }

    protected JComponent createEastPane(JInternalFrame w) {
        return null;
    }


    protected MouseInputAdapter createBorderListener(JInternalFrame w) {
        return new BorderListener();
    }

    protected void createInternalFrameListener(){
        internalFrameListener = getHandler();
    }

    protected final boolean isKeyBindingRegistered(){
      return keyBindingRegistered;
    }

    protected final void setKeyBindingRegistered(boolean b){
      keyBindingRegistered = b;
    }

    public final boolean isKeyBindingActive(){
      return keyBindingActive;
    }

    protected final void setKeyBindingActive(boolean b){
      keyBindingActive = b;
    }


    protected void setupMenuOpenKey(){
        // PENDING(hania): Why are these WHEN_IN_FOCUSED_WINDOWs? Shouldn't
        // they be WHEN_ANCESTOR_OF_FOCUSED_COMPONENT?
        // Also, no longer registering on the desktopicon, the previous
        // action did nothing.
        InputMap map = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        SwingUtilities.replaceUIInputMap(frame,
                                      JComponent.WHEN_IN_FOCUSED_WINDOW, map);
        //ActionMap actionMap = getActionMap();
        //SwingUtilities.replaceUIActionMap(frame, actionMap);
    }

    protected void setupMenuCloseKey(){
    }

    public JComponent getNorthPane() {
        return northPane;
    }

    public void setNorthPane(JComponent c) {
        if (northPane != null &&
                northPane instanceof BasicInternalFrameTitlePane) {
            ((BasicInternalFrameTitlePane)northPane).uninstallListeners();
        }
        replacePane(northPane, c);
        northPane = c;
        if (c instanceof BasicInternalFrameTitlePane) {
          titlePane = (BasicInternalFrameTitlePane)c;
        }
    }

    public JComponent getSouthPane() {
        return southPane;
    }

    public void setSouthPane(JComponent c) {
        southPane = c;
    }

    public JComponent getWestPane() {
        return westPane;
    }

    public void setWestPane(JComponent c) {
        westPane = c;
    }

    public JComponent getEastPane() {
        return eastPane;
    }

    public void setEastPane(JComponent c) {
        eastPane = c;
    }

    public class InternalFramePropertyChangeListener implements
        PropertyChangeListener {
        // NOTE: This class exists only for backward compatibility. All
        // its functionality has been moved into Handler. If you need to add
        // new functionality add it to the Handler, but make sure this
        // class calls into the Handler.
        /**
         * Detects changes in state from the JInternalFrame and handles
         * actions.
         */
        public void propertyChange(PropertyChangeEvent evt) {
            getHandler().propertyChange(evt);
        }
    }

  public class InternalFrameLayout implements LayoutManager {
    // NOTE: This class exists only for backward compatibility. All
    // its functionality has been moved into Handler. If you need to add
    // new functionality add it to the Handler, but make sure this
    // class calls into the Handler.
    public void addLayoutComponent(String name, Component c) {
        getHandler().addLayoutComponent(name, c);
    }

    public void removeLayoutComponent(Component c) {
        getHandler().removeLayoutComponent(c);
    }

    public Dimension preferredLayoutSize(Container c)  {
        return getHandler().preferredLayoutSize(c);
    }

    public Dimension minimumLayoutSize(Container c) {
        return getHandler().minimumLayoutSize(c);
    }

    public void layoutContainer(Container c) {
        getHandler().layoutContainer(c);
    }
  }

/// DesktopManager methods
    /**
     * Returns the proper DesktopManager. Calls getDesktopPane() to
     * find the JDesktop component and returns the desktopManager from
     * it. If this fails, it will return a default DesktopManager that
     * should work in arbitrary parents.
     */
    protected DesktopManager getDesktopManager() {
        if(frame.getDesktopPane() != null
           && frame.getDesktopPane().getDesktopManager() != null)
            return frame.getDesktopPane().getDesktopManager();
        if(sharedDesktopManager == null)
          sharedDesktopManager = createDesktopManager();
        return sharedDesktopManager;
    }

    protected DesktopManager createDesktopManager(){
      return new DefaultDesktopManager();
    }

    /**
     * This method is called when the user wants to close the frame.
     * The <code>playCloseSound</code> Action is fired.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void closeFrame(JInternalFrame f) {
        // Internal Frame Auditory Cue Activation
        BasicLookAndFeel.playSound(frame,"InternalFrame.closeSound");
        // delegate to desktop manager
        getDesktopManager().closeFrame(f);
    }

    /**
     * This method is called when the user wants to maximize the frame.
     * The <code>playMaximizeSound</code> Action is fired.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void maximizeFrame(JInternalFrame f) {
        // Internal Frame Auditory Cue Activation
        BasicLookAndFeel.playSound(frame,"InternalFrame.maximizeSound");
        // delegate to desktop manager
        getDesktopManager().maximizeFrame(f);
    }

    /**
     * This method is called when the user wants to minimize the frame.
     * The <code>playRestoreDownSound</code> Action is fired.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void minimizeFrame(JInternalFrame f) {
        // Internal Frame Auditory Cue Activation
        if ( ! f.isIcon() ) {
            // This method seems to regularly get called after an
            // internal frame is iconified. Don't play this sound then.
            BasicLookAndFeel.playSound(frame,"InternalFrame.restoreDownSound");
        }
        // delegate to desktop manager
        getDesktopManager().minimizeFrame(f);
    }

    /**
     * This method is called when the user wants to iconify the frame.
     * The <code>playMinimizeSound</code> Action is fired.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void iconifyFrame(JInternalFrame f) {
        // Internal Frame Auditory Cue Activation
        BasicLookAndFeel.playSound(frame, "InternalFrame.minimizeSound");
        // delegate to desktop manager
        getDesktopManager().iconifyFrame(f);
    }

    /**
     * This method is called when the user wants to deiconify the frame.
     * The <code>playRestoreUpSound</code> Action is fired.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void deiconifyFrame(JInternalFrame f) {
        // Internal Frame Auditory Cue Activation
        if ( ! f.isMaximum() ) {
            // This method seems to regularly get called after an
            // internal frame is maximized. Don't play this sound then.
            BasicLookAndFeel.playSound(frame, "InternalFrame.restoreUpSound");
        }
        // delegate to desktop manager
        getDesktopManager().deiconifyFrame(f);
    }

    /**
      * This method is called when the frame becomes selected.
      * This action is delegated to the desktopManager.
      *
      * @param f the {@code JInternalFrame} being viewed
      */
    protected void activateFrame(JInternalFrame f) {
        getDesktopManager().activateFrame(f);
    }
    /**
     * This method is called when the frame is no longer selected.
     * This action is delegated to the desktopManager.
     *
     * @param f the {@code JInternalFrame} being viewed
     */
    protected void deactivateFrame(JInternalFrame f) {
        getDesktopManager().deactivateFrame(f);
    }

    /////////////////////////////////////////////////////////////////////////
    /// Border Listener Class
    /////////////////////////////////////////////////////////////////////////
    /**
     * Listens for border adjustments.
     */
    protected class BorderListener extends MouseInputAdapter implements SwingConstants
    {
        // _x & _y are the mousePressed location in absolute coordinate system
        int _x, _y;
        // __x & __y are the mousePressed location in source view's coordinate system
        int __x, __y;
        Rectangle startingBounds;
        int resizeDir;


        protected final int RESIZE_NONE  = 0;
        private boolean discardRelease = false;

        int resizeCornerSize = 16;

        public void mouseClicked(MouseEvent e) {
            if(e.getClickCount() > 1 && e.getSource() == getNorthPane()) {
                if(frame.isIconifiable() && frame.isIcon()) {
                    try { frame.setIcon(false); } catch (PropertyVetoException e2) { }
                } else if(frame.isMaximizable()) {
                    if(!frame.isMaximum())
                        try { frame.setMaximum(true); } catch (PropertyVetoException e2) { }
                    else
                        try { frame.setMaximum(false); } catch (PropertyVetoException e3) { }
                }
            }
        }

        // Factor out finishMouseReleased() from mouseReleased(), so that
        // it can be called by cancelResize() without passing it a null
        // MouseEvent.
        void finishMouseReleased() {
           if (discardRelease) {
             discardRelease = false;
             return;
          }
            if (resizeDir == RESIZE_NONE) {
                getDesktopManager().endDraggingFrame(frame);
                dragging = false;
            } else {
                // Remove the WindowFocusListener for handling a
                // WINDOW_LOST_FOCUS event with a cancelResize().
                Window windowAncestor =
                    SwingUtilities.getWindowAncestor(frame);
                if (windowAncestor != null) {
                    windowAncestor.removeWindowFocusListener(
                        getWindowFocusListener());
                }
                Container c = frame.getTopLevelAncestor();
                if (c instanceof RootPaneContainer) {
                    Component glassPane = ((RootPaneContainer)c).getGlassPane();
                    glassPane.setCursor(Cursor.getPredefinedCursor(
                        Cursor.DEFAULT_CURSOR));
                    glassPane.setVisible(false);
                }
                getDesktopManager().endResizingFrame(frame);
                resizing = false;
                updateFrameCursor();
            }
            _x = 0;
            _y = 0;
            __x = 0;
            __y = 0;
            startingBounds = null;
            resizeDir = RESIZE_NONE;
            // Set discardRelease to true, so that only a mousePressed()
            // which sets it to false, will allow entry to the above code
            // for finishing a resize.
            discardRelease = true;
        }

        public void mouseReleased(MouseEvent e) {
            finishMouseReleased();
        }

        public void mousePressed(MouseEvent e) {
            Point p = SwingUtilities.convertPoint((Component)e.getSource(),
                        e.getX(), e.getY(), null);
            __x = e.getX();
            __y = e.getY();
            _x = p.x;
            _y = p.y;
            startingBounds = frame.getBounds();
            resizeDir = RESIZE_NONE;
            discardRelease = false;

            try { frame.setSelected(true); }
            catch (PropertyVetoException e1) { }

            Insets i = frame.getInsets();

            Point ep = new Point(__x, __y);
            if (e.getSource() == getNorthPane()) {
                Point np = getNorthPane().getLocation();
                ep.x += np.x;
                ep.y += np.y;
            }

            if (e.getSource() == getNorthPane()) {
                if (ep.x > i.left && ep.y > i.top && ep.x < frame.getWidth() - i.right) {
                    getDesktopManager().beginDraggingFrame(frame);
                    dragging = true;
                    return;
                }
            }
            if (!frame.isResizable()) {
              return;
            }

            if (e.getSource() == frame || e.getSource() == getNorthPane()) {
                if (ep.x <= i.left) {
                    if (ep.y < resizeCornerSize + i.top) {
                        resizeDir = NORTH_WEST;
                    } else if (ep.y > frame.getHeight()
                              - resizeCornerSize - i.bottom) {
                        resizeDir = SOUTH_WEST;
                    } else {
                        resizeDir = WEST;
}
                } else if (ep.x >= frame.getWidth() - i.right) {
                    if (ep.y < resizeCornerSize + i.top) {
                        resizeDir = NORTH_EAST;
                    } else if (ep.y > frame.getHeight()
                              - resizeCornerSize - i.bottom) {
                        resizeDir = SOUTH_EAST;
                    } else {
                        resizeDir = EAST;
                    }
                } else if (ep.y <= i.top) {
                    if (ep.x < resizeCornerSize + i.left) {
                        resizeDir = NORTH_WEST;
                    } else if (ep.x > frame.getWidth()
                              - resizeCornerSize - i.right) {
                        resizeDir = NORTH_EAST;
                    } else {
                        resizeDir = NORTH;
                    }
                } else if (ep.y >= frame.getHeight() - i.bottom) {
                    if (ep.x < resizeCornerSize + i.left) {
                        resizeDir = SOUTH_WEST;
                    } else if (ep.x > frame.getWidth()
                              - resizeCornerSize - i.right) {
                        resizeDir = SOUTH_EAST;
                    } else {
                      resizeDir = SOUTH;
                    }
                } else {
                    /* the mouse press happened inside the frame, not in the
                     border */
                  discardRelease = true;
                  return;
                }
                Cursor s = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
                switch (resizeDir) {
                case SOUTH:
                  s = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                  break;
                case NORTH:
                  s = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                  break;
                case WEST:
                  s = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                  break;
                case EAST:
                  s = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                  break;
                case SOUTH_EAST:
                  s = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                  break;
                case SOUTH_WEST:
                  s = Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                  break;
                case NORTH_WEST:
                  s = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                  break;
                case NORTH_EAST:
                  s = Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                  break;
                }
                Container c = frame.getTopLevelAncestor();
                if (c instanceof RootPaneContainer) {
                    Component glassPane = ((RootPaneContainer)c).getGlassPane();
                    glassPane.setVisible(true);
                    glassPane.setCursor(s);
                }
                getDesktopManager().beginResizingFrame(frame, resizeDir);
                resizing = true;
                // Add the WindowFocusListener for handling a
                // WINDOW_LOST_FOCUS event with a cancelResize().
                Window windowAncestor = SwingUtilities.getWindowAncestor(frame);
                if (windowAncestor != null) {
                    windowAncestor.addWindowFocusListener(
                        getWindowFocusListener());
                }
                return;
            }
        }

        public void mouseDragged(MouseEvent e) {

            if ( startingBounds == null ) {
              // (STEVE) Yucky work around for bug ID 4106552
                 return;
            }

            Point p = SwingUtilities.convertPoint((Component)e.getSource(),
                    e.getX(), e.getY(), null);
            int deltaX = _x - p.x;
            int deltaY = _y - p.y;
            Dimension min = frame.getMinimumSize();
            Dimension max = frame.getMaximumSize();
            int newX, newY, newW, newH;
            Insets i = frame.getInsets();

            // Handle a MOVE
            if (dragging) {
                if (frame.isMaximum() || ((e.getModifiers() &
                        InputEvent.BUTTON1_MASK) !=
                        InputEvent.BUTTON1_MASK)) {
                    // don't allow moving of frames if maximixed or left mouse
                    // button was not used.
                    return;
                }
                int pWidth, pHeight;
                Dimension s = frame.getParent().getSize();
                pWidth = s.width;
                pHeight = s.height;


                newX = startingBounds.x - deltaX;
                newY = startingBounds.y - deltaY;

                // Make sure we stay in-bounds
                if(newX + i.left <= -__x)
                    newX = -__x - i.left + 1;
                if(newY + i.top <= -__y)
                    newY = -__y - i.top + 1;
                if(newX + __x + i.right >= pWidth)
                    newX = pWidth - __x - i.right - 1;
                if(newY + __y + i.bottom >= pHeight)
                    newY =  pHeight - __y - i.bottom - 1;

                getDesktopManager().dragFrame(frame, newX, newY);
                return;
            }

            if(!frame.isResizable()) {
                return;
            }

            newX = frame.getX();
            newY = frame.getY();
            newW = frame.getWidth();
            newH = frame.getHeight();

            parentBounds = frame.getParent().getBounds();

            switch(resizeDir) {
            case RESIZE_NONE:
                return;
            case NORTH:
                if(startingBounds.height + deltaY < min.height)
                    deltaY = -(startingBounds.height - min.height);
                else if(startingBounds.height + deltaY > max.height)
                    deltaY = max.height - startingBounds.height;
                if (startingBounds.y - deltaY < 0) {deltaY = startingBounds.y;}

                newX = startingBounds.x;
                newY = startingBounds.y - deltaY;
                newW = startingBounds.width;
                newH = startingBounds.height + deltaY;
                break;
            case NORTH_EAST:
                if(startingBounds.height + deltaY < min.height)
                    deltaY = -(startingBounds.height - min.height);
                else if(startingBounds.height + deltaY > max.height)
                    deltaY = max.height - startingBounds.height;
                if (startingBounds.y - deltaY < 0) {deltaY = startingBounds.y;}

                if(startingBounds.width - deltaX < min.width)
                    deltaX = startingBounds.width - min.width;
                else if(startingBounds.width - deltaX > max.width)
                    deltaX = -(max.width - startingBounds.width);
                if (startingBounds.x + startingBounds.width - deltaX >
                    parentBounds.width) {
                  deltaX = startingBounds.x + startingBounds.width -
                    parentBounds.width;
                }

                newX = startingBounds.x;
                newY = startingBounds.y - deltaY;
                newW = startingBounds.width - deltaX;
                newH = startingBounds.height + deltaY;
                break;
            case EAST:
                if(startingBounds.width - deltaX < min.width)
                    deltaX = startingBounds.width - min.width;
                else if(startingBounds.width - deltaX > max.width)
                    deltaX = -(max.width - startingBounds.width);
                if (startingBounds.x + startingBounds.width - deltaX >
                    parentBounds.width) {
                  deltaX = startingBounds.x + startingBounds.width -
                    parentBounds.width;
                }

                newW = startingBounds.width - deltaX;
                newH = startingBounds.height;
                break;
            case SOUTH_EAST:
                if(startingBounds.width - deltaX < min.width)
                    deltaX = startingBounds.width - min.width;
                else if(startingBounds.width - deltaX > max.width)
                    deltaX = -(max.width - startingBounds.width);
                if (startingBounds.x + startingBounds.width - deltaX >
                    parentBounds.width) {
                  deltaX = startingBounds.x + startingBounds.width -
                    parentBounds.width;
                }

                if(startingBounds.height - deltaY < min.height)
                    deltaY = startingBounds.height - min.height;
                else if(startingBounds.height - deltaY > max.height)
                    deltaY = -(max.height - startingBounds.height);
                if (startingBounds.y + startingBounds.height - deltaY >
                     parentBounds.height) {
                  deltaY = startingBounds.y + startingBounds.height -
                    parentBounds.height ;
                }

                newW = startingBounds.width - deltaX;
                newH = startingBounds.height - deltaY;
                break;
            case SOUTH:
                if(startingBounds.height - deltaY < min.height)
                    deltaY = startingBounds.height - min.height;
                else if(startingBounds.height - deltaY > max.height)
                    deltaY = -(max.height - startingBounds.height);
                if (startingBounds.y + startingBounds.height - deltaY >
                     parentBounds.height) {
                  deltaY = startingBounds.y + startingBounds.height -
                    parentBounds.height ;
                }

                newW = startingBounds.width;
                newH = startingBounds.height - deltaY;
                break;
            case SOUTH_WEST:
                if(startingBounds.height - deltaY < min.height)
                    deltaY = startingBounds.height - min.height;
                else if(startingBounds.height - deltaY > max.height)
                    deltaY = -(max.height - startingBounds.height);
                if (startingBounds.y + startingBounds.height - deltaY >
                     parentBounds.height) {
                  deltaY = startingBounds.y + startingBounds.height -
                    parentBounds.height ;
                }

                if(startingBounds.width + deltaX < min.width)
                    deltaX = -(startingBounds.width - min.width);
                else if(startingBounds.width + deltaX > max.width)
                    deltaX = max.width - startingBounds.width;
                if (startingBounds.x - deltaX < 0) {
                  deltaX = startingBounds.x;
                }

                newX = startingBounds.x - deltaX;
                newY = startingBounds.y;
                newW = startingBounds.width + deltaX;
                newH = startingBounds.height - deltaY;
                break;
            case WEST:
                if(startingBounds.width + deltaX < min.width)
                    deltaX = -(startingBounds.width - min.width);
                else if(startingBounds.width + deltaX > max.width)
                    deltaX = max.width - startingBounds.width;
                if (startingBounds.x - deltaX < 0) {
                  deltaX = startingBounds.x;
                }

                newX = startingBounds.x - deltaX;
                newY = startingBounds.y;
                newW = startingBounds.width + deltaX;
                newH = startingBounds.height;
                break;
            case NORTH_WEST:
                if(startingBounds.width + deltaX < min.width)
                    deltaX = -(startingBounds.width - min.width);
                else if(startingBounds.width + deltaX > max.width)
                    deltaX = max.width - startingBounds.width;
                if (startingBounds.x - deltaX < 0) {
                  deltaX = startingBounds.x;
                }

                if(startingBounds.height + deltaY < min.height)
                    deltaY = -(startingBounds.height - min.height);
                else if(startingBounds.height + deltaY > max.height)
                    deltaY = max.height - startingBounds.height;
                if (startingBounds.y - deltaY < 0) {deltaY = startingBounds.y;}

                newX = startingBounds.x - deltaX;
                newY = startingBounds.y - deltaY;
                newW = startingBounds.width + deltaX;
                newH = startingBounds.height + deltaY;
                break;
            default:
                return;
            }
            getDesktopManager().resizeFrame(frame, newX, newY, newW, newH);
        }

        public void mouseMoved(MouseEvent e)    {

            if(!frame.isResizable())
                return;

            if (e.getSource() == frame || e.getSource() == getNorthPane()) {
                Insets i = frame.getInsets();
                Point ep = new Point(e.getX(), e.getY());
                if (e.getSource() == getNorthPane()) {
                    Point np = getNorthPane().getLocation();
                    ep.x += np.x;
                    ep.y += np.y;
                }
                if(ep.x <= i.left) {
                    if(ep.y < resizeCornerSize + i.top)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                    else if(ep.y > frame.getHeight() - resizeCornerSize - i.bottom)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
                    else
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                } else if(ep.x >= frame.getWidth() - i.right) {
                    if(e.getY() < resizeCornerSize + i.top)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
                    else if(ep.y > frame.getHeight() - resizeCornerSize - i.bottom)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                    else
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                } else if(ep.y <= i.top) {
                    if(ep.x < resizeCornerSize + i.left)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                    else if(ep.x > frame.getWidth() - resizeCornerSize - i.right)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
                    else
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                } else if(ep.y >= frame.getHeight() - i.bottom) {
                    if(ep.x < resizeCornerSize + i.left)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
                    else if(ep.x > frame.getWidth() - resizeCornerSize - i.right)
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                    else
                        frame.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
                }
                else
                    updateFrameCursor();
                return;
            }

            updateFrameCursor();
        }

        public void mouseEntered(MouseEvent e)    {
            updateFrameCursor();
        }

        public void mouseExited(MouseEvent e)    {
            updateFrameCursor();
        }

    }    /// End BorderListener Class

    protected class ComponentHandler implements ComponentListener {
      // NOTE: This class exists only for backward compatibility. All
      // its functionality has been moved into Handler. If you need to add
      // new functionality add it to the Handler, but make sure this
      // class calls into the Handler.
      /** Invoked when a JInternalFrame's parent's size changes. */
      public void componentResized(ComponentEvent e) {
          getHandler().componentResized(e);
      }

      public void componentMoved(ComponentEvent e) {
          getHandler().componentMoved(e);
      }
      public void componentShown(ComponentEvent e) {
          getHandler().componentShown(e);
      }
      public void componentHidden(ComponentEvent e) {
          getHandler().componentHidden(e);
      }
    }

    protected ComponentListener createComponentListener() {
      return getHandler();
    }


    protected class GlassPaneDispatcher implements MouseInputListener {
        // NOTE: This class exists only for backward compatibility. All
        // its functionality has been moved into Handler. If you need to add
        // new functionality add it to the Handler, but make sure this
        // class calls into the Handler.
        public void mousePressed(MouseEvent e) {
            getHandler().mousePressed(e);
        }

        public void mouseEntered(MouseEvent e) {
            getHandler().mouseEntered(e);
        }

        public void mouseMoved(MouseEvent e) {
            getHandler().mouseMoved(e);
        }

        public void mouseExited(MouseEvent e) {
            getHandler().mouseExited(e);
        }

        public void mouseClicked(MouseEvent e) {
            getHandler().mouseClicked(e);
        }

        public void mouseReleased(MouseEvent e) {
            getHandler().mouseReleased(e);
        }

        public void mouseDragged(MouseEvent e) {
            getHandler().mouseDragged(e);
        }
    }

    protected MouseInputListener createGlassPaneDispatcher() {
        return null;
    }


    protected class BasicInternalFrameListener implements InternalFrameListener
    {
      // NOTE: This class exists only for backward compatibility. All
      // its functionality has been moved into Handler. If you need to add
      // new functionality add it to the Handler, but make sure this
      // class calls into the Handler.
      public void internalFrameClosing(InternalFrameEvent e) {
          getHandler().internalFrameClosing(e);
      }

      public void internalFrameClosed(InternalFrameEvent e) {
          getHandler().internalFrameClosed(e);
      }

      public void internalFrameOpened(InternalFrameEvent e) {
          getHandler().internalFrameOpened(e);
      }

      public void internalFrameIconified(InternalFrameEvent e) {
          getHandler().internalFrameIconified(e);
      }

      public void internalFrameDeiconified(InternalFrameEvent e) {
          getHandler().internalFrameDeiconified(e);
      }

      public void internalFrameActivated(InternalFrameEvent e) {
          getHandler().internalFrameActivated(e);
      }


      public void internalFrameDeactivated(InternalFrameEvent e) {
          getHandler().internalFrameDeactivated(e);
      }
    }

    private class Handler implements ComponentListener, InternalFrameListener,
            LayoutManager, MouseInputListener, PropertyChangeListener,
            WindowFocusListener, SwingConstants {

        public void windowGainedFocus(WindowEvent e) {
        }

        public void windowLostFocus(WindowEvent e) {
            // Cancel a resize which may be in progress, when a
            // WINDOW_LOST_FOCUS event occurs, which may be
            // caused by an Alt-Tab or a modal dialog popup.
            cancelResize();
        }

        // ComponentHandler methods
        /** Invoked when a JInternalFrame's parent's size changes. */
        public void componentResized(ComponentEvent e) {
            // Get the JInternalFrame's parent container size
            Rectangle parentNewBounds = ((Component) e.getSource()).getBounds();
            JInternalFrame.JDesktopIcon icon = null;

            if (frame != null) {
                icon = frame.getDesktopIcon();
                // Resize the internal frame if it is maximized and relocate
                // the associated icon as well.
                if (frame.isMaximum()) {
                    frame.setBounds(0, 0, parentNewBounds.width,
                        parentNewBounds.height);
                }
            }

            // Relocate the icon base on the new parent bounds.
            if (icon != null) {
                Rectangle iconBounds = icon.getBounds();
                int y = iconBounds.y +
                        (parentNewBounds.height - parentBounds.height);
                icon.setBounds(iconBounds.x, y,
                        iconBounds.width, iconBounds.height);
            }

            // Update the new parent bounds for next resize.
            if (!parentBounds.equals(parentNewBounds)) {
                parentBounds = parentNewBounds;
            }

            // Validate the component tree for this container.
            if (frame != null) frame.validate();
        }

        public void componentMoved(ComponentEvent e) {}
        public void componentShown(ComponentEvent e) {}
        public void componentHidden(ComponentEvent e) {}


        // InternalFrameListener
        public void internalFrameClosed(InternalFrameEvent e) {
            frame.removeInternalFrameListener(getHandler());
        }

        public void internalFrameActivated(InternalFrameEvent e) {
            if (!isKeyBindingRegistered()){
                setKeyBindingRegistered(true);
                setupMenuOpenKey();
                setupMenuCloseKey();
            }
            if (isKeyBindingRegistered())
                setKeyBindingActive(true);
        }

        public void internalFrameDeactivated(InternalFrameEvent e) {
            setKeyBindingActive(false);
        }

        public void internalFrameClosing(InternalFrameEvent e) { }
        public void internalFrameOpened(InternalFrameEvent e) { }
        public void internalFrameIconified(InternalFrameEvent e) { }
        public void internalFrameDeiconified(InternalFrameEvent e) { }


        // LayoutManager
        public void addLayoutComponent(String name, Component c) {}
        public void removeLayoutComponent(Component c) {}
        public Dimension preferredLayoutSize(Container c)  {
            Dimension result;
            Insets i = frame.getInsets();

            result = new Dimension(frame.getRootPane().getPreferredSize());
            result.width += i.left + i.right;
            result.height += i.top + i.bottom;

            if(getNorthPane() != null) {
                Dimension d = getNorthPane().getPreferredSize();
                result.width = Math.max(d.width, result.width);
                result.height += d.height;
            }

            if(getSouthPane() != null) {
                Dimension d = getSouthPane().getPreferredSize();
                result.width = Math.max(d.width, result.width);
                result.height += d.height;
            }

            if(getEastPane() != null) {
                Dimension d = getEastPane().getPreferredSize();
                result.width += d.width;
                result.height = Math.max(d.height, result.height);
            }

            if(getWestPane() != null) {
                Dimension d = getWestPane().getPreferredSize();
                result.width += d.width;
                result.height = Math.max(d.height, result.height);
            }
            return result;
        }

        public Dimension minimumLayoutSize(Container c) {
            // The minimum size of the internal frame only takes into
            // account the title pane since you are allowed to resize
            // the frames to the point where just the title pane is visible.
            Dimension result = new Dimension();
            if (getNorthPane() != null &&
                getNorthPane() instanceof BasicInternalFrameTitlePane) {
                  result = new Dimension(getNorthPane().getMinimumSize());
            }
            Insets i = frame.getInsets();
            result.width += i.left + i.right;
            result.height += i.top + i.bottom;

            return result;
        }

        public void layoutContainer(Container c) {
            Insets i = frame.getInsets();
            int cx, cy, cw, ch;

            cx = i.left;
            cy = i.top;
            cw = frame.getWidth() - i.left - i.right;
            ch = frame.getHeight() - i.top - i.bottom;

            if(getNorthPane() != null) {
                Dimension size = getNorthPane().getPreferredSize();
                if (DefaultLookup.getBoolean(frame, BasicInternalFrameUI.this,
                          "InternalFrame.layoutTitlePaneAtOrigin", false)) {
                    cy = 0;
                    ch += i.top;
                    getNorthPane().setBounds(0, 0, frame.getWidth(),
                                             size.height);
                }
                else {
                    getNorthPane().setBounds(cx, cy, cw, size.height);
                }
                cy += size.height;
                ch -= size.height;
            }

            if(getSouthPane() != null) {
                Dimension size = getSouthPane().getPreferredSize();
                getSouthPane().setBounds(cx, frame.getHeight()
                                                    - i.bottom - size.height,
                                                    cw, size.height);
                ch -= size.height;
            }

            if(getWestPane() != null) {
                Dimension size = getWestPane().getPreferredSize();
                getWestPane().setBounds(cx, cy, size.width, ch);
                cw -= size.width;
                cx += size.width;
            }

            if(getEastPane() != null) {
                Dimension size = getEastPane().getPreferredSize();
                getEastPane().setBounds(cw - size.width, cy, size.width, ch);
                cw -= size.width;
            }

            if(frame.getRootPane() != null) {
                frame.getRootPane().setBounds(cx, cy, cw, ch);
            }
        }


        // MouseInputListener
        public void mousePressed(MouseEvent e) { }

        public void mouseEntered(MouseEvent e) { }

        public void mouseMoved(MouseEvent e) { }

        public void mouseExited(MouseEvent e) { }

        public void mouseClicked(MouseEvent e) { }

        public void mouseReleased(MouseEvent e) { }

        public void mouseDragged(MouseEvent e) { }

        // PropertyChangeListener
        public void propertyChange(PropertyChangeEvent evt) {
            String prop = evt.getPropertyName();
            JInternalFrame f = (JInternalFrame)evt.getSource();
            Object newValue = evt.getNewValue();
            Object oldValue = evt.getOldValue();

            if (JInternalFrame.IS_CLOSED_PROPERTY == prop) {
                if (newValue == Boolean.TRUE) {
                    // Cancel a resize in progress if the internal frame
                    // gets a setClosed(true) or dispose().
                    cancelResize();
                    if ((frame.getParent() != null) && componentListenerAdded) {
                        frame.getParent().removeComponentListener(componentListener);
                    }
                    closeFrame(f);
                }
            } else if (JInternalFrame.IS_MAXIMUM_PROPERTY == prop) {
                if(newValue == Boolean.TRUE) {
                    maximizeFrame(f);
                } else {
                    minimizeFrame(f);
                }
            } else if(JInternalFrame.IS_ICON_PROPERTY == prop) {
                if (newValue == Boolean.TRUE) {
                    iconifyFrame(f);
                } else {
                    deiconifyFrame(f);
                }
            } else if (JInternalFrame.IS_SELECTED_PROPERTY == prop) {
                if (newValue == Boolean.TRUE && oldValue == Boolean.FALSE) {
                    activateFrame(f);
                } else if (newValue == Boolean.FALSE &&
                           oldValue == Boolean.TRUE) {
                    deactivateFrame(f);
                }
            } else if (prop == "ancestor") {
                if (newValue == null) {
                    // Cancel a resize in progress, if the internal frame
                    // gets a remove(), removeNotify() or setIcon(true).
                    cancelResize();
                }
                if (frame.getParent() != null) {
                    parentBounds = f.getParent().getBounds();
                } else {
                    parentBounds = null;
                }
                if ((frame.getParent() != null) && !componentListenerAdded) {
                    f.getParent().addComponentListener(componentListener);
                    componentListenerAdded = true;
                }
            } else if (JInternalFrame.TITLE_PROPERTY == prop ||
                    prop == "closable" || prop == "iconable" ||
                    prop == "maximizable") {
                Dimension dim = frame.getMinimumSize();
                Dimension frame_dim = frame.getSize();
                if (dim.width > frame_dim.width) {
                    frame.setSize(dim.width, frame_dim.height);
                }
            }
        }
    }
}
