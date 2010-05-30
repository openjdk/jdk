/*
 * Copyright (c) 1995, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.motif;

import java.awt.*;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodDescriptor;
import java.awt.image.*;
import java.awt.peer.*;
import java.awt.datatransfer.Clipboard;
import java.awt.event.*;
import java.lang.reflect.*;
import java.lang.Math;
import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import java.util.Properties;
import java.util.Map;
import java.util.Iterator;

import sun.awt.AppContext;
import sun.awt.AWTAutoShutdown;
import sun.awt.SunToolkit;
import sun.awt.UNIXToolkit;
import sun.awt.GlobalCursorManager;
import sun.awt.datatransfer.DataTransferer;

import java.awt.dnd.DragSource;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;

//import sun.awt.motif.MInputMethod;
import sun.awt.X11FontManager;
import sun.awt.X11GraphicsConfig;
import sun.awt.X11GraphicsEnvironment;
import sun.awt.XSettings;

//import sun.awt.motif.MDragSourceContextPeer;

import sun.print.PrintJob2D;

import sun.misc.PerformanceLogger;
import sun.misc.Unsafe;

import sun.security.action.GetBooleanAction;
import sun.util.logging.PlatformLogger;

public class MToolkit extends UNIXToolkit implements Runnable {

    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.motif.MToolkit");

    // the system clipboard - CLIPBOARD selection
    //X11Clipboard clipboard;
    // the system selection - PRIMARY selection
    //X11Clipboard selection;

    // Dynamic Layout Resize client code setting
    protected static boolean dynamicLayoutSetting = false;

    /**
     * True when the x settings have been loaded.
     */
    private boolean loadedXSettings;

    /**
     * XSETTINGS for the default screen.
     * <p>
     * <strong>XXX:</strong> see <code>MToolkit.parseXSettings</code>
     * and <code>awt_xsettings_update</code> in
     * <samp>awt_MToolkit.c</samp>
     */
    private XSettings xs;

    /*
     * Note: The MToolkit object depends on the static initializer
     * of X11GraphicsEnvironment to initialize the connection to
     * the X11 server.
     */
    static final X11GraphicsConfig config;

    private static final boolean motifdnd;

    static {
        if (GraphicsEnvironment.isHeadless()) {
            config = null;
        } else {
            config = (X11GraphicsConfig) (GraphicsEnvironment.
                             getLocalGraphicsEnvironment().
                             getDefaultScreenDevice().
                             getDefaultConfiguration());
        }

        /* Add font properties font directories to the X11 font path.
         * Its called here *after* the X connection has been initialised
         * and when we know that MToolkit is the one that will be used,
         * since XToolkit doesn't need the X11 font path set
         */
        X11FontManager.getInstance().setNativeFontPath();

        motifdnd = ((Boolean)java.security.AccessController.doPrivileged(
            new GetBooleanAction("awt.dnd.motifdnd"))).booleanValue();
    }

    //public static final String DATA_TRANSFERER_CLASS_NAME = "sun.awt.motif.MDataTransferer";

    public MToolkit() {
        super();
        if (PerformanceLogger.loggingEnabled()) {
            PerformanceLogger.setTime("MToolkit construction");
        }
        if (!GraphicsEnvironment.isHeadless()) {
            String mainClassName = null;

            StackTraceElement trace[] = (new Throwable()).getStackTrace();
            int bottom = trace.length - 1;
            if (bottom >= 0) {
                mainClassName = trace[bottom].getClassName();
            }
            if (mainClassName == null || mainClassName.equals("")) {
                mainClassName = "AWT";
            }

            init(mainClassName);
            //SunToolkit.setDataTransfererClassName(DATA_TRANSFERER_CLASS_NAME);

            Thread toolkitThread = new Thread(this, "AWT-Motif");
            toolkitThread.setPriority(Thread.NORM_PRIORITY + 1);
            toolkitThread.setDaemon(true);

            PrivilegedAction<Void> a = new PrivilegedAction<Void>() {
                public Void run() {
                    ThreadGroup mainTG = Thread.currentThread().getThreadGroup();
                    ThreadGroup parentTG = mainTG.getParent();

                    while (parentTG != null) {
                        mainTG = parentTG;
                        parentTG = mainTG.getParent();
                    }
                    Thread shutdownThread = new Thread(mainTG, new Runnable() {
                            public void run() {
                                shutdown();
                            }
                        }, "Shutdown-Thread");
                    shutdownThread.setContextClassLoader(null);
                    Runtime.getRuntime().addShutdownHook(shutdownThread);
                    return null;
                }
            };
            AccessController.doPrivileged(a);

            /*
             * Fix for 4701990.
             * AWTAutoShutdown state must be changed before the toolkit thread
             * starts to avoid race condition.
             */
            AWTAutoShutdown.notifyToolkitThreadBusy();

            toolkitThread.start();
        }
    }

    public native void init(String mainClassName);
    public native void run();
    private native void shutdown();

    /*
     * Create peer objects.
     */

    public ButtonPeer createButton(Button target) {
        //ButtonPeer peer = new MButtonPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public TextFieldPeer createTextField(TextField target) {
        //TextFieldPeer peer = new MTextFieldPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public LabelPeer createLabel(Label target) {
        //LabelPeer peer = new MLabelPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public ListPeer createList(List target) {
        //ListPeer peer = new MListPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public CheckboxPeer createCheckbox(Checkbox target) {
        //CheckboxPeer peer = new MCheckboxPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public ScrollbarPeer createScrollbar(Scrollbar target) {
        //ScrollbarPeer peer = new MScrollbarPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public ScrollPanePeer createScrollPane(ScrollPane target) {
        //ScrollPanePeer peer = new MScrollPanePeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public TextAreaPeer createTextArea(TextArea target) {
        //TextAreaPeer peer = new MTextAreaPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public ChoicePeer createChoice(Choice target) {
        //ChoicePeer peer = new MChoicePeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public FramePeer  createFrame(Frame target) {
        //FramePeer peer = new MFramePeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public CanvasPeer createCanvas(Canvas target) {
        //CanvasPeer peer = (isXEmbedServerRequested() ? new MEmbedCanvasPeer(target) : new MCanvasPeer(target));
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public PanelPeer createPanel(Panel target) {
        //PanelPeer peer = new MPanelPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public WindowPeer createWindow(Window target) {
        //WindowPeer peer = new MWindowPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public DialogPeer createDialog(Dialog target) {
        //DialogPeer peer = new MDialogPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public FileDialogPeer createFileDialog(FileDialog target) {
        //FileDialogPeer peer = new MFileDialogPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public MenuBarPeer createMenuBar(MenuBar target) {
        //MenuBarPeer peer = new MMenuBarPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public MenuPeer createMenu(Menu target) {
        //MenuPeer peer = new MMenuPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public PopupMenuPeer createPopupMenu(PopupMenu target) {
        //PopupMenuPeer peer = new MPopupMenuPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public MenuItemPeer createMenuItem(MenuItem target) {
        //MenuItemPeer peer = new MMenuItemPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public CheckboxMenuItemPeer createCheckboxMenuItem(CheckboxMenuItem target) {
        //CheckboxMenuItemPeer peer = new MCheckboxMenuItemPeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
        return null;
    }

    public KeyboardFocusManagerPeer createKeyboardFocusManagerPeer(KeyboardFocusManager manager) {
        return null;
    }

    //public MEmbeddedFramePeer createEmbeddedFrame(MEmbeddedFrame target)
    //{
        //MEmbeddedFramePeer peer = new MEmbeddedFramePeer(target);
        //targetCreatedPeer(target, peer);
        //return peer;
    //    return null;
    //}


    public FontPeer getFontPeer(String name, int style){
        return new MFontPeer(name, style);
    }

    /*
     * On X, support for dynamic layout on resizing is governed by the
     * window manager.  If the window manager supports it, it happens
     * automatically.  The setter method for this property is
     * irrelevant on X.
     */
    public void setDynamicLayout(boolean b) {
        dynamicLayoutSetting = b;
    }

    protected boolean isDynamicLayoutSet() {
        return dynamicLayoutSetting;
    }

    /* Called from isDynamicLayoutActive() and from
     * lazilyLoadDynamicLayoutSupportedProperty()
     */
    protected native boolean isDynamicLayoutSupportedNative();

    public boolean isDynamicLayoutActive() {
        return isDynamicLayoutSupportedNative();
    }

    public native boolean isFrameStateSupported(int state);

    public TrayIconPeer createTrayIcon(TrayIcon target) throws HeadlessException {
        return null;
    }

    public SystemTrayPeer createSystemTray(SystemTray target) throws HeadlessException {
        return null;
    }

    public boolean isTraySupported() {
        return false;
    }

    static native ColorModel makeColorModel();
    static ColorModel screenmodel;

    static ColorModel getStaticColorModel() {
        if (screenmodel == null) {
            screenmodel = config.getColorModel ();
        }
        return screenmodel;
    }

    public ColorModel getColorModel() {
        return getStaticColorModel();
    }

    public native int getScreenResolution();

    public Insets getScreenInsets(GraphicsConfiguration gc) {
        return new Insets(0,0,0,0);
    }

    protected native int getScreenWidth();
    protected native int getScreenHeight();

    public FontMetrics getFontMetrics(Font font) {
        /*
        // REMIND: platform font flag should be obsolete soon
        if (!RasterOutputManager.usesPlatformFont()) {
            return super.getFontMetrics(font);
        } else {
            return X11FontMetrics.getFontMetrics(font);
        }
        */
        return super.getFontMetrics(font);
    }

    public PrintJob getPrintJob(final Frame frame, final String doctitle,
                                final Properties props) {

        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalArgumentException();
        }

        PrintJob2D printJob = new PrintJob2D(frame, doctitle, props);

        if (printJob.printDialog() == false) {
            printJob = null;
        }

        return printJob;
    }

    public PrintJob getPrintJob(final Frame frame, final String doctitle,
                                final JobAttributes jobAttributes,
                                final PageAttributes pageAttributes) {


        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalArgumentException();
        }

        PrintJob2D printJob = new PrintJob2D(frame, doctitle,
                                             jobAttributes, pageAttributes);

        if (printJob.printDialog() == false) {
            printJob = null;
        }

        return printJob;
    }

    public native void beep();

    public  Clipboard getSystemClipboard() {
        //SecurityManager security = System.getSecurityManager();
        //if (security != null) {
        //  security.checkSystemClipboardAccess();
        //}
        //synchronized (this) {
        //    if (clipboard == null) {
        //        clipboard = new X11Clipboard("System", "CLIPBOARD");
        //    }
        //}
        //return clipboard;
        return null;
    }

    public Clipboard getSystemSelection() {
        //SecurityManager security = System.getSecurityManager();
        //if (security != null) {
        //    security.checkSystemClipboardAccess();
        //}
        //synchronized (this) {
        //    if (selection == null) {
        //        selection = new X11Clipboard("Selection", "PRIMARY");
        //    }
        //}
        //return selection;
        return null;
    }

    public boolean getLockingKeyState(int key) {
        if (! (key == KeyEvent.VK_CAPS_LOCK || key == KeyEvent.VK_NUM_LOCK ||
               key == KeyEvent.VK_SCROLL_LOCK || key == KeyEvent.VK_KANA_LOCK)) {
            throw new IllegalArgumentException("invalid key for Toolkit.getLockingKeyState");
        }
        return getLockingKeyStateNative(key);
    }

    public native boolean getLockingKeyStateNative(int key);

    public native void loadSystemColors(int[] systemColors);

    /**
     * Give native peers the ability to query the native container
     * given a native component (e.g. the direct parent may be lightweight).
     */
    public static Container getNativeContainer(Component c) {
        return Toolkit.getNativeContainer(c);
    }

    protected static final Object targetToPeer(Object target) {
        return SunToolkit.targetToPeer(target);
    }

    protected static final void targetDisposedPeer(Object target, Object peer) {
        SunToolkit.targetDisposedPeer(target, peer);
    }

    public DragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        //if (MToolkit.useMotifDnD()) {
        //    return MDragSourceContextPeer.createDragSourceContextPeer(dge);
        //} else {
        //    return X11DragSourceContextPeer.createDragSourceContextPeer(dge);
        //}
        return null;
    }

    public <T extends DragGestureRecognizer> T
        createDragGestureRecognizer(Class<T> abstractRecognizerClass,
                                    DragSource ds, Component c, int srcActions,
                                    DragGestureListener dgl)
    {
        //if (MouseDragGestureRecognizer.class.equals(abstractRecognizerClass))
        //    return (T)new MMouseDragGestureRecognizer(ds, c, srcActions, dgl);
        //else
            return null;
    }

    /**
     * Returns a new input method adapter descriptor for native input methods.
     */
    public InputMethodDescriptor getInputMethodAdapterDescriptor() throws AWTException {
        return null; // return new MInputMethodDescriptor();
    }

    /**
     * Returns a style map for the input method highlight.
     */
    public Map mapInputMethodHighlight(InputMethodHighlight highlight) {
        return null; //return MInputMethod.mapInputMethodHighlight(highlight);
    }

    /**
     * Returns a new custom cursor.
     */
    public Cursor createCustomCursor(Image cursor, Point hotSpot, String name)
        throws IndexOutOfBoundsException {
        return null; //return new MCustomCursor(cursor, hotSpot, name);
    }

    /**
     * Returns the supported cursor size
     */
    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight) {
        return null; //MCustomCursor.getBestCursorSize(
            //java.lang.Math.max(1,preferredWidth), java.lang.Math.max(1,preferredHeight));
    }

    public int getMaximumCursorColors() {
        return 2;  // Black and white.
    }

    private final static String prefix  = "DnD.Cursor.";
    private final static String postfix = ".32x32";
    private static final String dndPrefix  = "DnD.";

    protected Object lazilyLoadDesktopProperty(String name) {
        if (name.startsWith(prefix)) {
            String cursorName = name.substring(prefix.length(), name.length()) + postfix;

            try {
                return Cursor.getSystemCustomCursor(cursorName);
            } catch (AWTException awte) {
                System.err.println("cannot load system cursor: " + cursorName);

                return null;
            }
        }

        if (name.equals("awt.dynamicLayoutSupported")) {
            return lazilyLoadDynamicLayoutSupportedProperty(name);
        }

        if (!loadedXSettings &&
            (name.startsWith("gnome.") ||
             name.equals(SunToolkit.DESKTOPFONTHINTS) ||
             name.startsWith(dndPrefix))) {
            loadedXSettings = true;
            if (!GraphicsEnvironment.isHeadless()) {
                loadXSettings();
                desktopProperties.put(SunToolkit.DESKTOPFONTHINTS,
                                      SunToolkit.getDesktopFontHints());
                return desktopProperties.get(name);
            }
        }

        return super.lazilyLoadDesktopProperty(name);
    }

    /*
     * Called from lazilyLoadDesktopProperty because we may not know if
     * the user has quit the previous window manager and started another.
     */
    protected Boolean lazilyLoadDynamicLayoutSupportedProperty(String name) {
        boolean nativeDynamic = isDynamicLayoutSupportedNative();

        if (log.isLoggable(PlatformLogger.FINER)) {
            log.finer("nativeDynamic == " + nativeDynamic);
        }

        return Boolean.valueOf(nativeDynamic);
    }

    private native int getMulticlickTime();

    protected void initializeDesktopProperties() {
        desktopProperties.put("DnD.Autoscroll.initialDelay",     Integer.valueOf(50));
        desktopProperties.put("DnD.Autoscroll.interval",         Integer.valueOf(50));
        desktopProperties.put("DnD.Autoscroll.cursorHysteresis", Integer.valueOf(5));

        /* As of 1.4, no wheel mice are supported on Solaris
         * however, they are on Linux, and there isn't a way to detect them,
         * so we leave this property unset to indicate we're not sure if there's
         * a wheel mouse or not.
         */
        //desktopProperties.put("awt.wheelMousePresent", Boolean.valueOf(false));

        // We don't want to call getMultilclickTime() if we're headless
        if (!GraphicsEnvironment.isHeadless()) {
            desktopProperties.put("awt.multiClickInterval",
                                  Integer.valueOf(getMulticlickTime()));
            desktopProperties.put("awt.mouse.numButtons",
                                  Integer.valueOf(getNumberOfButtons()));
        }
    }

    public RobotPeer createRobot(Robot target, GraphicsDevice screen) {
        /* 'target' is unused for now... */
        //return new MRobotPeer(screen.getDefaultConfiguration());
        return null;
    }

    static boolean useMotifDnD() {
        return motifdnd;
    }

    //
    // The following support Gnome's equivalent of desktop properties.
    // A writeup of this can be found at:
    // http://www.freedesktop.org/standards/xsettings/xsettings.html
    //

    /**
     * Triggers a callback to parseXSettings with the x settings values
     * from the window server. Note that this will NOT call
     * parseXSettings if we are not running on a GNOME desktop.
     */
    private native void loadXSettings();

    /**
     * Callback from the native side indicating some, or all, of the
     * desktop properties have changed and need to be reloaded.
     * <code>data</code> is the byte array directly from the x server and
     * may be in little endian format.
     * <p>
     * NB: This could be called from any thread if triggered by
     * <code>loadXSettings</code>.  It is called from the toolkit
     * thread if triggered by an XSETTINGS change.
     */
    private void parseXSettings(int screen_XXX_ignored, byte[] data) {
        // XXX: notyet: map screen -> per screen XSettings object
        // for now native code only calls us for default screen
        // see awt_MToolkit.c awt_xsettings_update().
        if (xs == null) {
            xs = new XSettings();
        }

        Map updatedSettings = xs.update(data);
        if (updatedSettings == null || updatedSettings.isEmpty()) {
            return;
        }

        Iterator i = updatedSettings.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry)i.next();
            String name = (String)e.getKey();

            name = "gnome." + name;
            setDesktopProperty(name, e.getValue());

            // XXX: we probably want to do something smarter.  In
            // particular, "Net" properties are of interest to the
            // "core" AWT itself.  E.g.
            //
            // Net/DndDragThreshold -> ???
            // Net/DoubleClickTime  -> awt.multiClickInterval
        }

        setDesktopProperty(SunToolkit.DESKTOPFONTHINTS,
                           SunToolkit.getDesktopFontHints());

        Integer dragThreshold = null;
        synchronized (this) {
            dragThreshold = (Integer)desktopProperties.get("gnome.Net/DndDragThreshold");
        }
        if (dragThreshold != null) {
            setDesktopProperty("DnD.gestureMotionThreshold", dragThreshold);
        }
    }

    protected boolean needsXEmbedImpl() {
        return true;
    }

    public boolean isModalityTypeSupported(Dialog.ModalityType modalityType) {
        return (modalityType == Dialog.ModalityType.MODELESS) ||
               (modalityType == Dialog.ModalityType.APPLICATION_MODAL);
    }

    public boolean isModalExclusionTypeSupported(Dialog.ModalExclusionType exclusionType) {
        return (exclusionType == Dialog.ModalExclusionType.NO_EXCLUDE);
    }

    private native boolean isSyncUpdated();
    private native boolean isSyncFailed();
    private native int getEventNumber();
    private native void updateSyncSelection();
    private static final long WORKAROUND_SLEEP = 100;

    /**
     * @inheritDoc
     */
    protected boolean syncNativeQueue(final long timeout) {
        awtLock();
        try {
            long event_number = getEventNumber();
            updateSyncSelection();

            // Wait for selection notify for oops on win
            long start = System.currentTimeMillis();
            while (!isSyncUpdated() && !isSyncFailed()) {
                try {
                    awtLockWait(timeout);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // This "while" is a protection from spurious
                // wake-ups.  However, we shouldn't wait for too long
                if (((System.currentTimeMillis() - start) > timeout) && (timeout >= 0)) {
                    throw new OperationTimedOut();
                }
            }
            if (isSyncFailed() && getEventNumber() - event_number == 1) {
                awtUnlock();
                try {
                    Thread.sleep(WORKAROUND_SLEEP);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                } finally {
                    awtLock();
                }
            }
            return getEventNumber() - event_number > 2;
        } finally {
            awtUnlock();
        }
    }

    public  void grab(Window w) {
        WindowPeer peer = (WindowPeer)w.getPeer();
        if (peer != null) {
            nativeGrab(peer);
        }
    }

    public void ungrab(Window w) {
        WindowPeer peer = (WindowPeer)w.getPeer();
        if (peer != null) {
            nativeUnGrab(peer);
        }
    }
    private native void nativeGrab(WindowPeer peer);
    private native void nativeUnGrab(WindowPeer peer);


    public boolean isDesktopSupported(){
        return false;
    }

    public DesktopPeer createDesktopPeer(Desktop target)
    throws HeadlessException{
        throw new UnsupportedOperationException();
    }

    public final static int
        UNDETERMINED_WM = 1,
        NO_WM = 2,
        OTHER_WM = 3,
        OPENLOOK_WM = 4,
        MOTIF_WM = 5,
        CDE_WM = 6,
        ENLIGHTEN_WM = 7,
        KDE2_WM = 8,
        SAWFISH_WM = 9,
        ICE_WM = 10,
        METACITY_WM = 11,
        COMPIZ_WM = 12,
        LG3D_WM = 13;

    public static int getWMID() {
        String wmName = getWMName();

        if ("NO_WM".equals(wmName)) {
            return NO_WM;
        } else if ("OTHER_WM".equals(wmName)) {
            return OTHER_WM;
        } else if ("ENLIGHTEN_WM".equals(wmName)) {
            return ENLIGHTEN_WM;
        } else if ("KDE2_WM".equals(wmName)) {
            return KDE2_WM;
        } else if ("SAWFISH_WM".equals(wmName)) {
            return SAWFISH_WM;
        } else if ("ICE_WM".equals(wmName)) {
            return ICE_WM;
        } else if ("METACITY_WM".equals(wmName)) {
            return METACITY_WM;
        } else if ("OPENLOOK_WM".equals(wmName)) {
            return OPENLOOK_WM;
        } else if ("MOTIF_WM".equals(wmName)) {
            return MOTIF_WM;
        } else if ("CDE_WM".equals(wmName)) {
            return CDE_WM;
        } else if ("COMPIZ_WM".equals(wmName)) {
            return COMPIZ_WM;
        } else if ("LG3D_WM".equals(wmName)) {
            return LG3D_WM;
        }
        return UNDETERMINED_WM;
    }

    private static native String getWMName();

} // class MToolkit
