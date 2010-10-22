/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.windows;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.peer.*;

import java.beans.*;

import java.lang.reflect.*;

import java.util.*;
import java.util.List;
import sun.util.logging.PlatformLogger;

import sun.awt.*;

import sun.java2d.pipe.Region;

public class WWindowPeer extends WPanelPeer implements WindowPeer,
       DisplayChangedListener
{

    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.windows.WWindowPeer");
    private static final PlatformLogger screenLog = PlatformLogger.getLogger("sun.awt.windows.screen.WWindowPeer");

    // we can't use WDialogPeer as blocker may be an instance of WPrintDialogPeer that
    // extends WWindowPeer, not WDialogPeer
    private WWindowPeer modalBlocker = null;

    private boolean isOpaque;

    private TranslucentWindowPainter painter;

    /*
     * A key used for storing a list of active windows in AppContext. The value
     * is a list of windows, sorted by the time of activation: later a window is
     * activated, greater its index is in the list.
     */
    private final static StringBuffer ACTIVE_WINDOWS_KEY =
        new StringBuffer("active_windows_list");

    /*
     * Listener for 'activeWindow' KFM property changes. It is added to each
     * AppContext KFM. See ActiveWindowListener inner class below.
     */
    private static PropertyChangeListener activeWindowListener =
        new ActiveWindowListener();

    /*
     * The object is a listener for the AppContext.GUI_DISPOSED property.
     */
    private final static PropertyChangeListener guiDisposedListener =
        new GuiDisposedListener();

    /*
     * Called (on the Toolkit thread) before the appropriate
     * WindowStateEvent is posted to the EventQueue.
     */
    private WindowListener windowListener;

    /**
     * Initialize JNI field IDs
     */
    private static native void initIDs();
    static {
        initIDs();
    }

    // WComponentPeer overrides

    protected void disposeImpl() {
        AppContext appContext = SunToolkit.targetToAppContext(target);
        synchronized (appContext) {
            List<WWindowPeer> l = (List<WWindowPeer>)appContext.get(ACTIVE_WINDOWS_KEY);
            if (l != null) {
                l.remove(this);
            }
        }

        // Remove ourself from the Map of DisplayChangeListeners
        GraphicsConfiguration gc = getGraphicsConfiguration();
        ((Win32GraphicsDevice)gc.getDevice()).removeDisplayChangedListener(this);

        synchronized (getStateLock()) {
            TranslucentWindowPainter currentPainter = painter;
            if (currentPainter != null) {
                currentPainter.flush();
                // don't set the current one to null here; reduces the chances of
                // MT issues (like NPEs)
            }
        }

        super.disposeImpl();
    }

    // WindowPeer implementation

    public void toFront() {
        updateFocusableWindowState();
        _toFront();
    }
    native void _toFront();
    public native void toBack();

    public native void setAlwaysOnTopNative(boolean value);
    public void setAlwaysOnTop(boolean value) {
        if ((value && ((Window)target).isVisible()) || !value) {
            setAlwaysOnTopNative(value);
        }
    }

    public void updateFocusableWindowState() {
        setFocusableWindow(((Window)target).isFocusableWindow());
    }
    native void setFocusableWindow(boolean value);

    // FramePeer & DialogPeer partial shared implementation

    public void setTitle(String title) {
        // allow a null title to pass as an empty string.
        if (title == null) {
            title = "";
        }
        _setTitle(title);
    }
    native void _setTitle(String title);

    public void setResizable(boolean resizable) {
        _setResizable(resizable);
    }
    public native void _setResizable(boolean resizable);

    // Toolkit & peer internals

    WWindowPeer(Window target) {
        super(target);
    }

    void initialize() {
        super.initialize();

        updateInsets(insets_);

        Font f = ((Window)target).getFont();
        if (f == null) {
            f = defaultFont;
            ((Window)target).setFont(f);
            setFont(f);
        }
        // Express our interest in display changes
        GraphicsConfiguration gc = getGraphicsConfiguration();
        ((Win32GraphicsDevice)gc.getDevice()).addDisplayChangedListener(this);

        initActiveWindowsTracking((Window)target);

        updateIconImages();

        Shape shape = ((Window)target).getShape();
        if (shape != null) {
            applyShape(Region.getInstance(shape, null));
        }

        float opacity = ((Window)target).getOpacity();
        if (opacity < 1.0f) {
            setOpacity(opacity);
        }

        synchronized (getStateLock()) {
            // default value of a boolean field is 'false', so set isOpaque to
            // true here explicitly
            this.isOpaque = true;
            setOpaque(((Window)target).isOpaque());
        }
    }

    native void createAwtWindow(WComponentPeer parent);

    private volatile Window.Type windowType = Window.Type.NORMAL;

    // This method must be called for Window, Dialog, and Frame before creating
    // the hwnd
    void preCreate(WComponentPeer parent) {
        windowType = ((Window)target).getType();
    }

    void create(WComponentPeer parent) {
        preCreate(parent);
        createAwtWindow(parent);
    }

    // should be overriden in WDialogPeer
    protected void realShow() {
        super.show();
    }

    public void show() {
        updateFocusableWindowState();

        boolean alwaysOnTop = ((Window)target).isAlwaysOnTop();

        // Fix for 4868278.
        // If we create a window with a specific GraphicsConfig, and then move it with
        // setLocation() or setBounds() to another one before its peer has been created,
        // then calling Window.getGraphicsConfig() returns wrong config. That may lead
        // to some problems like wrong-placed tooltips. It is caused by calling
        // super.displayChanged() in WWindowPeer.displayChanged() regardless of whether
        // GraphicsDevice was really changed, or not. So we need to track it here.
        updateGC();

        realShow();
        updateMinimumSize();

        if (((Window)target).isAlwaysOnTopSupported() && alwaysOnTop) {
            setAlwaysOnTop(alwaysOnTop);
        }

        synchronized (getStateLock()) {
            if (!isOpaque) {
                updateWindow(true);
            }
        }
    }

    // Synchronize the insets members (here & in helper) with actual window
    // state.
    native void updateInsets(Insets i);

    static native int getSysMinWidth();
    static native int getSysMinHeight();
    static native int getSysIconWidth();
    static native int getSysIconHeight();
    static native int getSysSmIconWidth();
    static native int getSysSmIconHeight();
    /**windows/classes/sun/awt/windows/
     * Creates native icon from specified raster data and updates
     * icon for window and all descendant windows that inherit icon.
     * Raster data should be passed in the ARGB form.
     * Note that raster data format was changed to provide support
     * for XP icons with alpha-channel
     */
    native void setIconImagesData(int[] iconRaster, int w, int h,
                                  int[] smallIconRaster, int smw, int smh);

    synchronized native void reshapeFrame(int x, int y, int width, int height);

    public boolean requestWindowFocus(CausedFocusEvent.Cause cause) {
        if (!focusAllowedFor()) {
            return false;
        }
        return requestWindowFocus(cause == CausedFocusEvent.Cause.MOUSE_EVENT);
    }
    public native boolean requestWindowFocus(boolean isMouseEventCause);

    public boolean focusAllowedFor() {
        Window window = (Window)this.target;
        if (!window.isVisible() ||
            !window.isEnabled() ||
            !window.isFocusableWindow())
        {
            return false;
        }
        if (isModalBlocked()) {
            return false;
        }
        return true;
    }

    public void hide() {
        WindowListener listener = windowListener;
        if (listener != null) {
            // We're not getting WINDOW_CLOSING from the native code when hiding
            // the window programmatically. So, create it and notify the listener.
            listener.windowClosing(new WindowEvent((Window)target, WindowEvent.WINDOW_CLOSING));
        }
        super.hide();
    }

    // WARNING: it's called on the Toolkit thread!
    void preprocessPostEvent(AWTEvent event) {
        if (event instanceof WindowEvent) {
            WindowListener listener = windowListener;
            if (listener != null) {
                switch(event.getID()) {
                    case WindowEvent.WINDOW_CLOSING:
                        listener.windowClosing((WindowEvent)event);
                        break;
                    case WindowEvent.WINDOW_ICONIFIED:
                        listener.windowIconified((WindowEvent)event);
                        break;
                }
            }
        }
    }

    synchronized void addWindowListener(WindowListener l) {
        windowListener = AWTEventMulticaster.add(windowListener, l);
    }

    synchronized void removeWindowListener(WindowListener l) {
        windowListener = AWTEventMulticaster.remove(windowListener, l);
    }

    public void updateMinimumSize() {
        Dimension minimumSize = null;
        if (((Component)target).isMinimumSizeSet()) {
            minimumSize = ((Component)target).getMinimumSize();
        }
        if (minimumSize != null) {
            int msw = getSysMinWidth();
            int msh = getSysMinHeight();
            int w = (minimumSize.width >= msw) ? minimumSize.width : msw;
            int h = (minimumSize.height >= msh) ? minimumSize.height : msh;
            setMinSize(w, h);
        } else {
            setMinSize(0, 0);
        }
    }

    public void updateIconImages() {
        java.util.List<Image> imageList = ((Window)target).getIconImages();
        if (imageList == null || imageList.size() == 0) {
            setIconImagesData(null, 0, 0, null, 0, 0);
        } else {
            int w = getSysIconWidth();
            int h = getSysIconHeight();
            int smw = getSysSmIconWidth();
            int smh = getSysSmIconHeight();
            DataBufferInt iconData = SunToolkit.getScaledIconData(imageList,
                                                                  w, h);
            DataBufferInt iconSmData = SunToolkit.getScaledIconData(imageList,
                                                                    smw, smh);
            if (iconData != null && iconSmData != null) {
                setIconImagesData(iconData.getData(), w, h,
                                  iconSmData.getData(), smw, smh);
            } else {
                setIconImagesData(null, 0, 0, null, 0, 0);
            }
        }
    }

    native void setMinSize(int width, int height);

/*
 * ---- MODALITY SUPPORT ----
 */

    /**
     * Some modality-related code here because WFileDialogPeer, WPrintDialogPeer and
     *   WPageDialogPeer are descendants of WWindowPeer, not WDialogPeer
     */

    public boolean isModalBlocked() {
        return modalBlocker != null;
    }

    public void setModalBlocked(Dialog dialog, boolean blocked) {
        synchronized (((Component)getTarget()).getTreeLock()) // State lock should always be after awtLock
        {
            // use WWindowPeer instead of WDialogPeer because of FileDialogs and PrintDialogs
            WWindowPeer blockerPeer = (WWindowPeer)dialog.getPeer();
            if (blocked)
            {
                modalBlocker = blockerPeer;
                // handle native dialogs separately, as they may have not
                // got HWND yet; modalEnable/modalDisable is called from
                // their setHWnd() methods
                if (blockerPeer instanceof WFileDialogPeer) {
                    ((WFileDialogPeer)blockerPeer).blockWindow(this);
                } else if (blockerPeer instanceof WPrintDialogPeer) {
                    ((WPrintDialogPeer)blockerPeer).blockWindow(this);
                } else {
                    modalDisable(dialog, blockerPeer.getHWnd());
                }
            } else {
                modalBlocker = null;
                if (blockerPeer instanceof WFileDialogPeer) {
                    ((WFileDialogPeer)blockerPeer).unblockWindow(this);
                } else if (blockerPeer instanceof WPrintDialogPeer) {
                    ((WPrintDialogPeer)blockerPeer).unblockWindow(this);
                } else {
                    modalEnable(dialog);
                }
            }
        }
    }

    native void modalDisable(Dialog blocker, long blockerHWnd);
    native void modalEnable(Dialog blocker);

    /*
     * Returns all the ever active windows from the current AppContext.
     * The list is sorted by the time of activation, so the latest
     * active window is always at the end.
     */
    public static long[] getActiveWindowHandles() {
        AppContext appContext = AppContext.getAppContext();
        synchronized (appContext) {
            List<WWindowPeer> l = (List<WWindowPeer>)appContext.get(ACTIVE_WINDOWS_KEY);
            if (l == null) {
                return null;
            }
            long[] result = new long[l.size()];
            for (int j = 0; j < l.size(); j++) {
                result[j] = l.get(j).getHWnd();
            }
            return result;
        }
    }

/*
 * ----DISPLAY CHANGE SUPPORT----
 */

    /*
     * Called from native code when we have been dragged onto another screen.
     */
    void draggedToNewScreen() {
        SunToolkit.executeOnEventHandlerThread((Component)target,new Runnable()
        {
            public void run() {
                displayChanged();
            }
        });
    }

    public void updateGC() {
        int scrn = getScreenImOn();
        if (screenLog.isLoggable(PlatformLogger.FINER)) {
            log.finer("Screen number: " + scrn);
        }

        // get current GD
        Win32GraphicsDevice oldDev = (Win32GraphicsDevice)winGraphicsConfig
                                     .getDevice();

        Win32GraphicsDevice newDev;
        GraphicsDevice devs[] = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getScreenDevices();
        // Occasionally during device addition/removal getScreenImOn can return
        // a non-existing screen number. Use the default device in this case.
        if (scrn >= devs.length) {
            newDev = (Win32GraphicsDevice)GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice();
        } else {
            newDev = (Win32GraphicsDevice)devs[scrn];
        }

        // Set winGraphicsConfig to the default GC for the monitor this Window
        // is now mostly on.
        winGraphicsConfig = (Win32GraphicsConfig)newDev
                            .getDefaultConfiguration();
        if (screenLog.isLoggable(PlatformLogger.FINE)) {
            if (winGraphicsConfig == null) {
                screenLog.fine("Assertion (winGraphicsConfig != null) failed");
            }
        }

        // if on a different display, take off old GD and put on new GD
        if (oldDev != newDev) {
            oldDev.removeDisplayChangedListener(this);
            newDev.addDisplayChangedListener(this);
        }

        SunToolkit.executeOnEventHandlerThread((Component)target,
                new Runnable() {
                    public void run() {
                        AWTAccessor.getComponentAccessor().
            setGraphicsConfiguration((Component)target, winGraphicsConfig);
                    }
                });
    }

    /**
     * From the DisplayChangedListener interface.
     *
     * This method handles a display change - either when the display settings
     * are changed, or when the window has been dragged onto a different
     * display.
     * Called after a change in the display mode.  This event
     * triggers replacing the surfaceData object (since that object
     * reflects the current display depth information, which has
     * just changed).
     */
    public void displayChanged() {
        updateGC();
    }

    /**
     * Part of the DisplayChangedListener interface: components
     * do not need to react to this event
     */
    public void paletteChanged() {
    }

    private native int getScreenImOn();

    // Used in Win32GraphicsDevice.
    public final native void setFullScreenExclusiveModeState(boolean state);

/*
 * ----END DISPLAY CHANGE SUPPORT----
 */

     public void grab() {
         nativeGrab();
     }

     public void ungrab() {
         nativeUngrab();
     }
     private native void nativeGrab();
     private native void nativeUngrab();

     private final boolean hasWarningWindow() {
         return ((Window)target).getWarningString() != null;
     }

     boolean isTargetUndecorated() {
         return true;
     }

     // These are the peer bounds. They get updated at:
     //    1. the WWindowPeer.setBounds() method.
     //    2. the native code (on WM_SIZE/WM_MOVE)
     private volatile int sysX = 0;
     private volatile int sysY = 0;
     private volatile int sysW = 0;
     private volatile int sysH = 0;

     public native void repositionSecurityWarning();

     @Override
     public void setBounds(int x, int y, int width, int height, int op) {
         sysX = x;
         sysY = y;
         sysW = width;
         sysH = height;

         super.setBounds(x, y, width, height, op);
     }

    @Override
    public void print(Graphics g) {
        // We assume we print the whole frame,
        // so we expect no clip was set previously
        Shape shape = AWTAccessor.getWindowAccessor().getShape((Window)target);
        if (shape != null) {
            g.setClip(shape);
        }
        super.print(g);
    }

    private void replaceSurfaceDataRecursively(Component c) {
        if (c instanceof Container) {
            for (Component child : ((Container)c).getComponents()) {
                replaceSurfaceDataRecursively(child);
            }
        }
        ComponentPeer cp = c.getPeer();
        if (cp instanceof WComponentPeer) {
            ((WComponentPeer)cp).replaceSurfaceDataLater();
        }
    }

    public final Graphics getTranslucentGraphics() {
        synchronized (getStateLock()) {
            return isOpaque ? null : painter.getBackBuffer(false).getGraphics();
        }
    }

    @Override
    public void setBackground(Color c) {
        super.setBackground(c);
        synchronized (getStateLock()) {
            if (!isOpaque && ((Window)target).isVisible()) {
                updateWindow(true);
            }
        }
    }

    private native void setOpacity(int iOpacity);
    private float opacity = 1.0f;

    public void setOpacity(float opacity) {
        if (!((SunToolkit)((Window)target).getToolkit()).
            isWindowOpacitySupported())
        {
            return;
        }

        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException(
                "The value of opacity should be in the range [0.0f .. 1.0f].");
        }

        if (((this.opacity == 1.0f && opacity <  1.0f) ||
             (this.opacity <  1.0f && opacity == 1.0f)) &&
            !Win32GraphicsEnvironment.isVistaOS())
        {
            // non-Vista OS: only replace the surface data if opacity status
            // changed (see WComponentPeer.isAccelCapable() for more)
            replaceSurfaceDataRecursively((Component)getTarget());
        }

        this.opacity = opacity;

        final int maxOpacity = 0xff;
        int iOpacity = (int)(opacity * maxOpacity);
        if (iOpacity < 0) {
            iOpacity = 0;
        }
        if (iOpacity > maxOpacity) {
            iOpacity = maxOpacity;
        }

        setOpacity(iOpacity);

        synchronized (getStateLock()) {
            if (!isOpaque && ((Window)target).isVisible()) {
                updateWindow(true);
            }
        }
    }

    private native void setOpaqueImpl(boolean isOpaque);

    public void setOpaque(boolean isOpaque) {
        synchronized (getStateLock()) {
            if (this.isOpaque == isOpaque) {
                return;
            }
        }

        Window target = (Window)getTarget();

        if (!isOpaque) {
            SunToolkit sunToolkit = (SunToolkit)target.getToolkit();
            if (!sunToolkit.isWindowTranslucencySupported() ||
                !sunToolkit.isTranslucencyCapable(target.getGraphicsConfiguration()))
            {
                return;
            }
        }

        boolean isVistaOS = Win32GraphicsEnvironment.isVistaOS();

        if (this.isOpaque != isOpaque && !isVistaOS) {
            // non-Vista OS: only replace the surface data if the opacity
            // status changed (see WComponentPeer.isAccelCapable() for more)
            replaceSurfaceDataRecursively(target);
        }

        synchronized (getStateLock()) {
            this.isOpaque = isOpaque;
            setOpaqueImpl(isOpaque);
            if (isOpaque) {
                TranslucentWindowPainter currentPainter = painter;
                if (currentPainter != null) {
                    currentPainter.flush();
                    painter = null;
                }
            } else {
                painter = TranslucentWindowPainter.createInstance(this);
            }
        }

        if (isVistaOS) {
            // On Vista: setting the window non-opaque makes the window look
            // rectangular, though still catching the mouse clicks within
            // its shape only. To restore the correct visual appearance
            // of the window (i.e. w/ the correct shape) we have to reset
            // the shape.
            Shape shape = ((Window)target).getShape();
            if (shape != null) {
                ((Window)target).setShape(shape);
            }
        }

        if (((Window)target).isVisible()) {
            updateWindow(true);
        }
    }

    public native void updateWindowImpl(int[] data, int width, int height);

    public void updateWindow() {
        updateWindow(false);
    }

    private void updateWindow(boolean repaint) {
        Window w = (Window)target;
        synchronized (getStateLock()) {
            if (isOpaque || !w.isVisible() ||
                (w.getWidth() <= 0) || (w.getHeight() <= 0))
            {
                return;
            }
            TranslucentWindowPainter currentPainter = painter;
            if (currentPainter != null) {
                currentPainter.updateWindow(repaint);
            } else if (log.isLoggable(PlatformLogger.FINER)) {
                log.finer("Translucent window painter is null in updateWindow");
            }
        }
    }

    /*
     * The method maps the list of the active windows to the window's AppContext,
     * then the method registers ActiveWindowListener, GuiDisposedListener listeners;
     * it executes the initilialization only once per AppContext.
     */
    private static void initActiveWindowsTracking(Window w) {
        AppContext appContext = AppContext.getAppContext();
        synchronized (appContext) {
            List<WWindowPeer> l = (List<WWindowPeer>)appContext.get(ACTIVE_WINDOWS_KEY);
            if (l == null) {
                l = new LinkedList<WWindowPeer>();
                appContext.put(ACTIVE_WINDOWS_KEY, l);
                appContext.addPropertyChangeListener(AppContext.GUI_DISPOSED, guiDisposedListener);

                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                kfm.addPropertyChangeListener("activeWindow", activeWindowListener);
            }
        }
    }

    /*
     * The GuiDisposedListener class listens for the AppContext.GUI_DISPOSED property,
     * it removes the list of the active windows from the disposed AppContext and
     * unregisters ActiveWindowListener listener.
     */
    private static class GuiDisposedListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            boolean isDisposed = (Boolean)e.getNewValue();
            if (isDisposed != true) {
                if (log.isLoggable(PlatformLogger.FINE)) {
                    log.fine(" Assertion (newValue != true) failed for AppContext.GUI_DISPOSED ");
                }
            }
            AppContext appContext = AppContext.getAppContext();
            synchronized (appContext) {
                appContext.remove(ACTIVE_WINDOWS_KEY);
                appContext.removePropertyChangeListener(AppContext.GUI_DISPOSED, this);

                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                kfm.removePropertyChangeListener("activeWindow", activeWindowListener);
            }
        }
    }

    /*
     * Static inner class, listens for 'activeWindow' KFM property changes and
     * updates the list of active windows per AppContext, so the latest active
     * window is always at the end of the list. The list is stored in AppContext.
     */
    private static class ActiveWindowListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            Window w = (Window)e.getNewValue();
            if (w == null) {
                return;
            }
            AppContext appContext = SunToolkit.targetToAppContext(w);
            synchronized (appContext) {
                WWindowPeer wp = (WWindowPeer)w.getPeer();
                // add/move wp to the end of the list
                List<WWindowPeer> l = (List<WWindowPeer>)appContext.get(ACTIVE_WINDOWS_KEY);
                if (l != null) {
                    l.remove(wp);
                    l.add(wp);
                }
            }
        }
    }
}
