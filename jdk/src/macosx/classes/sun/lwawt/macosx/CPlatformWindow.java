/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import java.awt.BufferCapabilities.FlipContents;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.*;
import java.beans.*;
import java.util.List;

import javax.swing.*;

import sun.awt.*;
import sun.java2d.SurfaceData;
import sun.java2d.opengl.CGLSurfaceData;
import sun.lwawt.*;
import sun.lwawt.LWWindowPeer.PeerType;
import sun.util.logging.PlatformLogger;

import com.apple.laf.*;
import com.apple.laf.ClientPropertyApplicator.Property;
import com.sun.awt.AWTUtilities;

public class CPlatformWindow extends CFRetainedResource implements PlatformWindow {
    private native long nativeCreateNSWindow(long nsViewPtr, long styleBits, double x, double y, double w, double h);
    private static native void nativeSetNSWindowStyleBits(long nsWindowPtr, int mask, int data);
    private static native void nativeSetNSWindowMenuBar(long nsWindowPtr, long menuBarPtr);
    private static native Insets nativeGetNSWindowInsets(long nsWindowPtr);
    private static native void nativeSetNSWindowBounds(long nsWindowPtr, double x, double y, double w, double h);
    private static native void nativeSetNSWindowMinMax(long nsWindowPtr, double minW, double minH, double maxW, double maxH);
    private static native void nativePushNSWindowToBack(long nsWindowPtr);
    private static native void nativePushNSWindowToFront(long nsWindowPtr);
    private static native void nativeSetNSWindowTitle(long nsWindowPtr, String title);
    private static native void nativeSetNSWindowAlpha(long nsWindowPtr, float alpha);
    private static native void nativeRevalidateNSWindowShadow(long nsWindowPtr);
    private static native void nativeSetNSWindowMinimizedIcon(long nsWindowPtr, long nsImage);
    private static native void nativeSetNSWindowRepresentedFilename(long nsWindowPtr, String representedFilename);
    private static native void nativeSetNSWindowSecurityWarningPositioning(long nsWindowPtr, double x, double y, float biasX, float biasY);

    private static native int nativeGetScreenNSWindowIsOn_AppKitThread(long nsWindowPtr);

    // Loger to report issues happened during execution but that do not affect functionality
    private static final PlatformLogger logger = PlatformLogger.getLogger("sun.lwawt.macosx.CPlatformWindow");

    // for client properties
    public static final String WINDOW_BRUSH_METAL_LOOK = "apple.awt.brushMetalLook";
    public static final String WINDOW_DRAGGABLE_BACKGROUND = "apple.awt.draggableWindowBackground";

    public static final String WINDOW_ALPHA = "Window.alpha";
    public static final String WINDOW_SHADOW = "Window.shadow";

    public static final String WINDOW_STYLE = "Window.style";
    public static final String WINDOW_SHADOW_REVALIDATE_NOW = "apple.awt.windowShadow.revalidateNow";

    public static final String WINDOW_DOCUMENT_MODIFIED = "Window.documentModified";
    public static final String WINDOW_DOCUMENT_FILE = "Window.documentFile";

    public static final String WINDOW_CLOSEABLE = "Window.closeable";
    public static final String WINDOW_MINIMIZABLE = "Window.minimizable";
    public static final String WINDOW_ZOOMABLE = "Window.zoomable";
    public static final String WINDOW_HIDES_ON_DEACTIVATE="Window.hidesOnDeactivate";

    public static final String WINDOW_DOC_MODAL_SHEET = "apple.awt.documentModalSheet";
    public static final String WINDOW_FADE_DELEGATE = "apple.awt._windowFadeDelegate";
    public static final String WINDOW_FADE_IN = "apple.awt._windowFadeIn";
    public static final String WINDOW_FADE_OUT = "apple.awt._windowFadeOut";
    public static final String WINDOW_FULLSCREENABLE = "apple.awt.fullscreenable";


    // Yeah, I know. But it's easier to deal with ints from JNI
    static final int MODELESS = 0;
    static final int DOCUMENT_MODAL = 1;
    static final int APPLICATION_MODAL = 2;
    static final int TOOLKIT_MODAL = 3;

    // window style bits
    static final int _RESERVED_FOR_DATA = 1 << 0;

    // corresponds to native style mask bits
    static final int DECORATED = 1 << 1;
    static final int TEXTURED = 1 << 2;
    static final int UNIFIED = 1 << 3;
    static final int UTILITY = 1 << 4;
    static final int HUD = 1 << 5;
    static final int SHEET = 1 << 6;

    static final int CLOSEABLE = 1 << 7;
    static final int MINIMIZABLE = 1 << 8;

    static final int RESIZABLE = 1 << 9; // both a style bit and prop bit

    static final int _STYLE_PROP_BITMASK = DECORATED | TEXTURED | UNIFIED | UTILITY | HUD | SHEET | CLOSEABLE | MINIMIZABLE | RESIZABLE;

    // corresponds to method-based properties
    static final int HAS_SHADOW = 1 << 10;
    static final int ZOOMABLE = 1 << 11;

    static final int ALWAYS_ON_TOP = 1 << 15;
    static final int HIDES_ON_DEACTIVATE = 1 << 17;
    static final int DRAGGABLE_BACKGROUND = 1 << 19;
    static final int DOCUMENT_MODIFIED = 1 << 21;
    static final int FULLSCREENABLE = 1 << 23;

    static final int _METHOD_PROP_BITMASK = RESIZABLE | HAS_SHADOW | ZOOMABLE | ALWAYS_ON_TOP | HIDES_ON_DEACTIVATE | DRAGGABLE_BACKGROUND | DOCUMENT_MODIFIED | FULLSCREENABLE;

    // not sure
    static final int POPUP = 1 << 14;

    // corresponds to callback-based properties
    static final int SHOULD_BECOME_KEY = 1 << 12;
    static final int SHOULD_BECOME_MAIN = 1 << 13;
    static final int MODAL_EXCLUDED = 1 << 16;

    static final int _CALLBACK_PROP_BITMASK = SHOULD_BECOME_KEY | SHOULD_BECOME_MAIN | MODAL_EXCLUDED;

    static int SET(final int bits, final int mask, final boolean value) {
        if (value) return (bits | mask);
        return bits & ~mask;
    }

    static boolean IS(final int bits, final int mask) {
        return (bits & mask) != 0;
    }

    @SuppressWarnings("unchecked")
    static ClientPropertyApplicator<JRootPane, CPlatformWindow> CLIENT_PROPERTY_APPLICATOR = new ClientPropertyApplicator<JRootPane, CPlatformWindow>(new Property[] {
        new Property<CPlatformWindow>(WINDOW_DOCUMENT_MODIFIED) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(DOCUMENT_MODIFIED, value == null ? false : Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_BRUSH_METAL_LOOK) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(TEXTURED, Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_ALPHA) { public void applyProperty(final CPlatformWindow c, final Object value) {
            AWTUtilities.setWindowOpacity(c.target, value == null ? 1.0f : Float.parseFloat(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_SHADOW) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(HAS_SHADOW, value == null ? true : Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_MINIMIZABLE) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(MINIMIZABLE, Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_CLOSEABLE) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(CLOSEABLE, Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_ZOOMABLE) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(ZOOMABLE, Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_FULLSCREENABLE) { public void applyProperty(final CPlatformWindow c, final Object value) {
            c.setStyleBits(FULLSCREENABLE, Boolean.parseBoolean(value.toString()));
        }},
        new Property<CPlatformWindow>(WINDOW_SHADOW_REVALIDATE_NOW) { public void applyProperty(final CPlatformWindow c, final Object value) {
            nativeRevalidateNSWindowShadow(c.getNSWindowPtr());
        }},
        new Property<CPlatformWindow>(WINDOW_DOCUMENT_FILE) { public void applyProperty(final CPlatformWindow c, final Object value) {
            if (value == null || !(value instanceof java.io.File)) {
                nativeSetNSWindowRepresentedFilename(c.getNSWindowPtr(), null);
                return;
            }

            final String filename = ((java.io.File)value).getAbsolutePath();
            nativeSetNSWindowRepresentedFilename(c.getNSWindowPtr(), filename);
        }}
    }) {
        public CPlatformWindow convertJComponentToTarget(final JRootPane p) {
            Component root = SwingUtilities.getRoot(p);
            if (root == null || (LWWindowPeer)root.getPeer() == null) return null;
            return (CPlatformWindow)((LWWindowPeer)root.getPeer()).getPlatformWindow();
        }
    };

    // Bounds of the native widget but in the Java coordinate system.
    // In order to keep it up-to-date we will update them on
    // 1) setting native bounds via nativeSetBounds() call
    // 2) getting notification from the native level via deliverMoveResizeEvent()
    private Rectangle nativeBounds;
    private volatile boolean isFullScreenMode = false;

    private Window target;
    private LWWindowPeer peer;
    private CPlatformView contentView;
    private CPlatformWindow owner;

    public CPlatformWindow(final PeerType peerType) {
        super(0, true);
        assert (peerType == PeerType.SIMPLEWINDOW || peerType == PeerType.DIALOG || peerType == PeerType.FRAME);
    }

    /*
     * Delegate initialization (create native window and all the
     * related resources).
     */
    @Override // PlatformWindow
    public void initialize(Window _target, LWWindowPeer _peer, PlatformWindow _owner) {
        this.peer = _peer;
        this.target = _target;
        if (_owner instanceof CPlatformWindow) {
            this.owner = (CPlatformWindow)_owner;
        }

        final int styleBits = getInitialStyleBits();

        // TODO: handle these misc properties
        final long parentNSWindowPtr = (owner != null ? owner.getNSWindowPtr() : 0);
        String warningString = target.getWarningString();

        contentView = new CPlatformView();
        contentView.initialize(peer);

        final long nativeWindowPtr = nativeCreateNSWindow(contentView.getAWTView(), styleBits, 0, 0, 0, 0);
        setPtr(nativeWindowPtr);

        // TODO: implement on top of JObjC bridged class
    //    NSWindow window = JObjC.getInstance().AppKit().NSWindow().getInstance(nativeWindowPtr, JObjCRuntime.getInstance());

        // Since JDK7 we have standard way to set opacity, so we should not pick
        // background's alpha.
        // TODO: set appropriate opacity value
        //        this.opacity = target.getOpacity();
        //        this.setOpacity(this.opacity);

        final float windowAlpha = target.getOpacity();
        if (windowAlpha != 1.0f) {
            nativeSetNSWindowAlpha(nativeWindowPtr, windowAlpha);
        }

        if (target instanceof javax.swing.RootPaneContainer) {
            final javax.swing.JRootPane rootpane = ((javax.swing.RootPaneContainer)target).getRootPane();
            if (rootpane != null) rootpane.addPropertyChangeListener("ancestor", new PropertyChangeListener() {
                public void propertyChange(final PropertyChangeEvent evt) {
                    CLIENT_PROPERTY_APPLICATOR.attachAndApplyClientProperties(rootpane);
                    rootpane.removePropertyChangeListener("ancestor", this);
                }
            });
        }

        validateSurface();
    }

    protected int getInitialStyleBits() {
        // defaults style bits
        int styleBits = DECORATED | HAS_SHADOW | CLOSEABLE | MINIMIZABLE | ZOOMABLE | RESIZABLE;

        if (target.getName() == "###overrideRedirect###") {
            styleBits = SET(styleBits, POPUP, true);
        }

        if (isNativelyFocusableWindow()) {
            styleBits = SET(styleBits, SHOULD_BECOME_KEY, true);
            styleBits = SET(styleBits, SHOULD_BECOME_MAIN, true);
        }

        final boolean isFrame = (target instanceof Frame);
        final boolean isDialog = (target instanceof Dialog);
        if (isDialog) {
            styleBits = SET(styleBits, MINIMIZABLE, false);
        }

        // Either java.awt.Frame or java.awt.Dialog can be undecorated, however java.awt.Window always is undecorated.
        {
            final boolean undecorated = isFrame ? ((Frame)target).isUndecorated() : (isDialog ? ((Dialog)target).isUndecorated() : true);
            if (undecorated) styleBits = SET(styleBits, DECORATED, false);
        }

        // Either java.awt.Frame or java.awt.Dialog can be resizable, however java.awt.Window is never resizable
        {
            final boolean resizable = isFrame ? ((Frame)target).isResizable() : (isDialog ? ((Dialog)target).isResizable() : false);
            styleBits = SET(styleBits, RESIZABLE, resizable);
            if (!resizable) {
                styleBits = SET(styleBits, RESIZABLE, false);
                styleBits = SET(styleBits, ZOOMABLE, false);
            }
        }

        if (target.isAlwaysOnTop()) {
            styleBits = SET(styleBits, ALWAYS_ON_TOP, true);
        }

        if (target.getModalExclusionType() == Dialog.ModalExclusionType.APPLICATION_EXCLUDE) {
            styleBits = SET(styleBits, MODAL_EXCLUDED, true);
        }

        // If the target is a dialog, popup or tooltip we want it to ignore the brushed metal look.
        if (!isDialog && IS(styleBits, POPUP)) {
            styleBits = SET(styleBits, TEXTURED, true);
        }

        if (target instanceof javax.swing.RootPaneContainer) {
            javax.swing.JRootPane rootpane = ((javax.swing.RootPaneContainer)target).getRootPane();
            Object prop = null;

            prop = rootpane.getClientProperty(WINDOW_BRUSH_METAL_LOOK);
            if (prop != null) {
                styleBits = SET(styleBits, TEXTURED, Boolean.parseBoolean(prop.toString()));
            }

            if (isDialog && ((Dialog)target).getModalityType() == ModalityType.DOCUMENT_MODAL) {
                prop = rootpane.getClientProperty(WINDOW_DOC_MODAL_SHEET);
                if (prop != null) {
                    styleBits = SET(styleBits, SHEET, Boolean.parseBoolean(prop.toString()));
                }
            }

            prop = rootpane.getClientProperty(WINDOW_STYLE);
            if (prop != null) {
                if ("small".equals(prop))  {
                    styleBits = SET(styleBits, UTILITY, true);
                    if (target.isAlwaysOnTop() && rootpane.getClientProperty(WINDOW_HIDES_ON_DEACTIVATE) == null) {
                        styleBits = SET(styleBits, HIDES_ON_DEACTIVATE, true);
                    }
                }
                if ("textured".equals(prop)) styleBits = SET(styleBits, TEXTURED, true);
                if ("unified".equals(prop)) styleBits = SET(styleBits, UNIFIED, true);
                if ("hud".equals(prop)) styleBits = SET(styleBits, HUD, true);
            }

            prop = rootpane.getClientProperty(WINDOW_HIDES_ON_DEACTIVATE);
            if (prop != null) {
                styleBits = SET(styleBits, HIDES_ON_DEACTIVATE, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_CLOSEABLE);
            if (prop != null) {
                styleBits = SET(styleBits, CLOSEABLE, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_MINIMIZABLE);
            if (prop != null) {
                styleBits = SET(styleBits, MINIMIZABLE, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_ZOOMABLE);
            if (prop != null) {
                styleBits = SET(styleBits, ZOOMABLE, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_FULLSCREENABLE);
            if (prop != null) {
                styleBits = SET(styleBits, FULLSCREENABLE, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_SHADOW);
            if (prop != null) {
                styleBits = SET(styleBits, HAS_SHADOW, Boolean.parseBoolean(prop.toString()));
            }

            prop = rootpane.getClientProperty(WINDOW_DRAGGABLE_BACKGROUND);
            if (prop != null) {
                styleBits = SET(styleBits, DRAGGABLE_BACKGROUND, Boolean.parseBoolean(prop.toString()));
            }
        }

        return styleBits;
    }

    // this is the counter-point to -[CWindow _nativeSetStyleBit:]
    protected void setStyleBits(final int mask, final boolean value) {
        nativeSetNSWindowStyleBits(getNSWindowPtr(), mask, value ? mask : 0);
    }

    private native void _toggleFullScreenMode(final long model);

    public void toggleFullScreen() {
        _toggleFullScreenMode(getNSWindowPtr());
    }

    @Override // PlatformWindow
    public void setMenuBar(MenuBar mb) {
        final long nsWindowPtr = getNSWindowPtr();
        CMenuBar mbPeer = (CMenuBar)LWToolkit.targetToPeer(mb);
        if (mbPeer != null) {
            nativeSetNSWindowMenuBar(nsWindowPtr, mbPeer.getModel());
        } else {
            nativeSetNSWindowMenuBar(nsWindowPtr, 0);
        }
    }

    @Override // PlatformWindow
    public Image createBackBuffer() {
        return contentView.createBackBuffer();
    }

    @Override // PlatformWindow
    public void dispose() {
        if (owner != null) {
            CWrapper.NSWindow.removeChildWindow(owner.getNSWindowPtr(), getNSWindowPtr());
        }
        // Make sure window is ordered out before it is disposed, we could order it out right here or
        // we could postpone the disposal, I think postponing is probably better.
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                contentView.dispose();
                CPlatformWindow.super.dispose();
            }
        });
    }

    @Override // PlatformWindow
    public void flip(int x1, int y1, int x2, int y2, FlipContents flipAction) {
        // TODO: not implemented
        (new RuntimeException("unimplemented")).printStackTrace();
    }

    @Override // PlatformWindow
    public FontMetrics getFontMetrics(Font f) {
        // TODO: not implemented
        (new RuntimeException("unimplemented")).printStackTrace();
        return null;
    }

    @Override // PlatformWindow
    public Insets getInsets() {
        final Insets insets = nativeGetNSWindowInsets(getNSWindowPtr());
        return insets;
    }

    @Override // PlatformWindow
    public Point getLocationOnScreen() {
        return new Point(nativeBounds.x, nativeBounds.y);
    }

    @Override // PlatformWindow
    public int getScreenImOn() {
    // REMIND: we could also acquire screenID from the
    // graphicsConfig.getDevice().getCoreGraphicsScreen()
    // which might look a bit less natural but don't
    // require new native accessor.
        return nativeGetScreenNSWindowIsOn_AppKitThread(getNSWindowPtr());
    }

    @Override // PlatformWindow
    public SurfaceData getScreenSurface() {
        // TODO: not implemented
        return null;
    }

    @Override // PlatformWindow
    public SurfaceData replaceSurfaceData() {
        return contentView.replaceSurfaceData();
    }

    @Override // PlatformWindow
    public void setBounds(int x, int y, int w, int h) {
//        assert CThreading.assertEventQueue();
        nativeSetNSWindowBounds(getNSWindowPtr(), x, y, w, h);
    }

    @Override // PlatformWindow
    public void setVisible(boolean visible) {
        final long nsWindowPtr = getNSWindowPtr();

        if (owner != null) {
            if (!visible) {
                CWrapper.NSWindow.removeChildWindow(owner.getNSWindowPtr(), nsWindowPtr);
            }
        }

        updateIconImages();
        updateFocusabilityForAutoRequestFocus(false);

        if (!visible) {
            // Cancel out the current native state of the window
            switch (peer.getState()) {
                case Frame.ICONIFIED:
                    CWrapper.NSWindow.deminiaturize(nsWindowPtr);
                    break;
                case Frame.MAXIMIZED_BOTH:
                    CWrapper.NSWindow.zoom(nsWindowPtr);
                    break;
            }
        }

        LWWindowPeer blocker = peer.getBlocker();
        if (blocker == null || !visible) {
            // If it ain't blocked, or is being hidden, go regular way
            if (visible) {
                CWrapper.NSWindow.makeFirstResponder(nsWindowPtr, contentView.getAWTView());
                boolean isKeyWindow = CWrapper.NSWindow.isKeyWindow(nsWindowPtr);
                if (!isKeyWindow) {
                    CWrapper.NSWindow.makeKeyAndOrderFront(nsWindowPtr);
                } else {
                    CWrapper.NSWindow.orderFront(nsWindowPtr);
                }
            } else {
                CWrapper.NSWindow.orderOut(nsWindowPtr);
            }
        } else {
            // otherwise, put it in a proper z-order
            CWrapper.NSWindow.orderWindow(nsWindowPtr, CWrapper.NSWindow.NSWindowBelow,
                    ((CPlatformWindow)blocker.getPlatformWindow()).getNSWindowPtr());
        }

        if (visible) {
            // Re-apply the extended state as expected in shared code
            if (target instanceof Frame) {
                switch (((Frame)target).getExtendedState()) {
                    case Frame.ICONIFIED:
                        CWrapper.NSWindow.miniaturize(nsWindowPtr);
                        break;
                    case Frame.MAXIMIZED_BOTH:
                        CWrapper.NSWindow.zoom(nsWindowPtr);
                        break;
                }
            }
        }

        updateFocusabilityForAutoRequestFocus(true);

        if (owner != null) {
            if (visible) {
                CWrapper.NSWindow.addChildWindow(owner.getNSWindowPtr(), nsWindowPtr, CWrapper.NSWindow.NSWindowAbove);
                if (target.isAlwaysOnTop()) {
                    CWrapper.NSWindow.setLevel(nsWindowPtr, CWrapper.NSWindow.NSFloatingWindowLevel);
                }
            }
        }

        if (blocker != null && visible) {
            // Make sure the blocker is above its siblings
            ((CPlatformWindow)blocker.getPlatformWindow()).orderAboveSiblings();
        }
    }

    @Override // PlatformWindow
    public void setTitle(String title) {
        nativeSetNSWindowTitle(getNSWindowPtr(), title);
    }

    // Should be called on every window key property change.
    @Override // PlatformWindow
    public void updateIconImages() {
        final long nsWindowPtr = getNSWindowPtr();
        final CImage cImage = getImageForTarget();
        nativeSetNSWindowMinimizedIcon(nsWindowPtr, cImage == null ? 0L : cImage.ptr);
    }

    public long getNSWindowPtr() {
        final long nsWindowPtr = ptr;
        if (nsWindowPtr == 0L) {
            if(logger.isLoggable(PlatformLogger.FINE)) {
                logger.fine("NSWindow already disposed?", new Exception("Pointer to native NSWindow is invalid."));
            }
        }
        return nsWindowPtr;
    }

    public SurfaceData getSurfaceData() {
        return contentView.getSurfaceData();
    }

    @Override  // PlatformWindow
    public void toBack() {
        final long nsWindowPtr = getNSWindowPtr();
        nativePushNSWindowToBack(nsWindowPtr);
    }

    @Override  // PlatformWindow
    public void toFront() {
        final long nsWindowPtr = getNSWindowPtr();
        updateFocusabilityForAutoRequestFocus(false);
        nativePushNSWindowToFront(nsWindowPtr);
        updateFocusabilityForAutoRequestFocus(true);
    }

    @Override
    public void setResizable(boolean resizable) {
        setStyleBits(RESIZABLE, resizable);
    }

    @Override
    public void setMinimumSize(int width, int height) {
        //TODO width, height should be used
        final long nsWindowPtr = getNSWindowPtr();
        final Dimension min = target.getMinimumSize();
        final Dimension max = target.getMaximumSize();
        nativeSetNSWindowMinMax(nsWindowPtr, min.getWidth(), min.getHeight(), max.getWidth(), max.getHeight());
    }

    @Override
    public boolean requestWindowFocus() {
        long ptr = getNSWindowPtr();
        if (CWrapper.NSWindow.canBecomeMainWindow(ptr)) {
            CWrapper.NSWindow.makeMainWindow(ptr);
        }
        CWrapper.NSWindow.makeKeyAndOrderFront(ptr);
        return true;
    }

    @Override
    public boolean isActive() {
        long ptr = getNSWindowPtr();
        return CWrapper.NSWindow.isKeyWindow(ptr);
    }

    @Override
    public void updateFocusableWindowState() {
        final boolean isFocusable = isNativelyFocusableWindow();
        setStyleBits(SHOULD_BECOME_KEY | SHOULD_BECOME_MAIN, isFocusable); // set both bits at once
    }

    @Override
    public Graphics transformGraphics(Graphics g) {
        // is this where we can inject a transform for HiDPI?
        return g;
    }

    @Override
    public void setAlwaysOnTop(boolean isAlwaysOnTop) {
        setStyleBits(ALWAYS_ON_TOP, isAlwaysOnTop);
    }

    @Override
    public void setOpacity(float opacity) {
        CWrapper.NSWindow.setAlphaValue(getNSWindowPtr(), opacity);
    }

    @Override
    public void setOpaque(boolean isOpaque) {
        CWrapper.NSWindow.setOpaque(getNSWindowPtr(), isOpaque);
        if (!isOpaque) {
            long clearColor = CWrapper.NSColor.clearColor();
            CWrapper.NSWindow.setBackgroundColor(getNSWindowPtr(), clearColor);
        }
    }

    @Override
    public void enterFullScreenMode() {
        isFullScreenMode = true;
        contentView.enterFullScreenMode(getNSWindowPtr());
    }

    @Override
    public void exitFullScreenMode() {
        contentView.exitFullScreenMode();
        isFullScreenMode = false;
    }

    @Override
    public void setWindowState(int windowState) {
        if (!peer.isVisible()) {
            // setVisible() applies the state
            return;
        }

        int prevWindowState = peer.getState();
        if (prevWindowState == windowState) return;

        final long nsWindowPtr = getNSWindowPtr();
        switch (windowState) {
            case Frame.ICONIFIED:
                if (prevWindowState == Frame.MAXIMIZED_BOTH) {
                    // let's return into the normal states first
                    // the zoom call toggles between the normal and the max states
                    CWrapper.NSWindow.zoom(nsWindowPtr);
                }
                CWrapper.NSWindow.miniaturize(nsWindowPtr);
                break;
            case Frame.MAXIMIZED_BOTH:
                if (prevWindowState == Frame.ICONIFIED) {
                    // let's return into the normal states first
                    CWrapper.NSWindow.deminiaturize(nsWindowPtr);
                }
                CWrapper.NSWindow.zoom(nsWindowPtr);
                break;
            case Frame.NORMAL:
                if (prevWindowState == Frame.ICONIFIED) {
                    CWrapper.NSWindow.deminiaturize(nsWindowPtr);
                } else if (prevWindowState == Frame.MAXIMIZED_BOTH) {
                    // the zoom call toggles between the normal and the max states
                    CWrapper.NSWindow.zoom(nsWindowPtr);
                }
                break;
            default:
                throw new RuntimeException("Unknown window state: " + windowState);
        }

        // NOTE: the SWP.windowState field gets updated to the newWindowState
        //       value when the native notification comes to us
    }

    // ----------------------------------------------------------------------
    //                          UTILITY METHODS
    // ----------------------------------------------------------------------

    /*
     * Find image to install into Title or into Application icon.
     * First try icons installed for toplevel. If there is no icon
     * use default Duke image.
     * This method shouldn't return null.
     */
    private CImage getImageForTarget() {
        List<Image> icons = target.getIconImages();
        if (icons == null || icons.size() == 0) {
            return null;
        }

        // TODO: need a walk-through to find the best image.
        // The best mean with higher resolution. Otherwise an icon looks bad.
        final Image image = icons.get(0);
        return CImage.getCreator().createFromImage(image);
    }

    /*
     * Returns LWWindowPeer associated with this delegate.
     */
    @Override
    public LWWindowPeer getPeer() {
        return peer;
    }

    public CPlatformView getContentView() {
        return contentView;
    }

    @Override
    public long getLayerPtr() {
        return contentView.getWindowLayerPtr();
    }

    private void validateSurface() {
        SurfaceData surfaceData = getSurfaceData();
        if (surfaceData instanceof CGLSurfaceData) {
            ((CGLSurfaceData)surfaceData).validate();
        }
    }

    /*************************************************************
     * Callbacks from the AWTWindow and AWTView objc classes.
     *************************************************************/
    private void deliverWindowFocusEvent(boolean gained){
        peer.notifyActivation(gained);
    }

    private void deliverMoveResizeEvent(int x, int y, int width, int height) {
        // when the content view enters the full-screen mode, the native
        // move/resize notifications contain a bounds smaller than
        // the whole screen and therefore we ignore the native notifications
        // and the content view itself creates correct synthetic notifications
        if (isFullScreenMode) return;

        nativeBounds = new Rectangle(x, y, width, height);
        peer.notifyReshape(x, y, width, height);
        //TODO validateSurface already called from notifyReshape
        validateSurface();
    }

    private void deliverWindowClosingEvent() {
        if (peer.getBlocker() == null)  {
            peer.postEvent(new WindowEvent(target, WindowEvent.WINDOW_CLOSING));
        }
    }

    private void deliverIconify(final boolean iconify) {
        peer.notifyIconify(iconify);
    }

    private void deliverZoom(final boolean isZoomed) {
        peer.notifyZoom(isZoomed);
    }

    private void deliverNCMouseDown() {
        peer.notifyNCMouseDown();
    }

    /*
     * Our focus model is synthetic and only non-simple window
     * may become natively focusable window.
     */
    private boolean isNativelyFocusableWindow() {
        return !peer.isSimpleWindow() && target.getFocusableWindowState();
    }

    /*
     * An utility method for the support of the auto request focus.
     * Updates the focusable state of the window under certain
     * circumstances.
     */
    private void updateFocusabilityForAutoRequestFocus(boolean isFocusable) {
        if (target.isAutoRequestFocus() || !isNativelyFocusableWindow()) return;
        setStyleBits(SHOULD_BECOME_KEY | SHOULD_BECOME_MAIN, isFocusable); // set both bits at once
    }

    private boolean checkBlocking() {
        LWWindowPeer blocker = peer.getBlocker();
        if (blocker == null) {
            return false;
        }

        CPlatformWindow pWindow = (CPlatformWindow)blocker.getPlatformWindow();

        pWindow.orderAboveSiblings();

        final long nsWindowPtr = pWindow.getNSWindowPtr();
        CWrapper.NSWindow.orderFrontRegardless(nsWindowPtr);
        CWrapper.NSWindow.makeKeyAndOrderFront(nsWindowPtr);
        CWrapper.NSWindow.makeMainWindow(nsWindowPtr);

        return true;
    }

    private void orderAboveSiblings() {
        if (owner == null) {
            return;
        }

        // Recursively pop up the windows from the very bottom so that only
        // the very top-most one becomes the main window
        owner.orderAboveSiblings();

        // Order the window to front of the stack of child windows
        final long nsWindowSelfPtr = getNSWindowPtr();
        final long nsWindowOwnerPtr = owner.getNSWindowPtr();
        CWrapper.NSWindow.removeChildWindow(nsWindowOwnerPtr, nsWindowSelfPtr);
        CWrapper.NSWindow.addChildWindow(nsWindowOwnerPtr, nsWindowSelfPtr, CWrapper.NSWindow.NSWindowAbove);
        if (target.isAlwaysOnTop()) {
            CWrapper.NSWindow.setLevel(getNSWindowPtr(), CWrapper.NSWindow.NSFloatingWindowLevel);
        }
    }

    // ----------------------------------------------------------------------
    //                          NATIVE CALLBACKS
    // ----------------------------------------------------------------------

    private void windowDidBecomeMain() {
        assert CThreading.assertAppKit();

        if (checkBlocking()) return;
        // If it's not blocked, make sure it's above its siblings
        orderAboveSiblings();
    }

    private void updateDisplay() {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                validateSurface();
            }
        });
    }

    private void updateWindowContent() {
        ComponentEvent resizeEvent = new ComponentEvent(target, ComponentEvent.COMPONENT_RESIZED);
        SunToolkit.postEvent(SunToolkit.targetToAppContext(target), resizeEvent);
    }

    private void windowWillEnterFullScreen() {
        updateWindowContent();
    }
    private void windowDidEnterFullScreen() {
        updateDisplay();
    }
    private void windowWillExitFullScreen() {
        updateWindowContent();
    }
    private void windowDidExitFullScreen() {}
}
