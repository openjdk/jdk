/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.peer.*;
import java.util.List;

import javax.swing.*;

import sun.awt.*;
import sun.java2d.*;
import sun.java2d.loops.Blit;
import sun.java2d.loops.CompositeType;
import sun.util.logging.PlatformLogger;

public class LWWindowPeer
    extends LWContainerPeer<Window, JComponent>
    implements WindowPeer, FramePeer, DialogPeer, FullScreenCapable
{
    public static enum PeerType {
        SIMPLEWINDOW,
        FRAME,
        DIALOG,
        EMBEDDEDFRAME
    }

    private static final sun.util.logging.PlatformLogger focusLog = PlatformLogger.getLogger("sun.lwawt.focus.LWWindowPeer");

    private PlatformWindow platformWindow;

    // Window bounds reported by the native system (as opposed to
    // regular bounds inherited from LWComponentPeer which are
    // requested by user and may haven't been applied yet because
    // of asynchronous requests to the windowing system)
    private int sysX;
    private int sysY;
    private int sysW;
    private int sysH;

    private static final int MINIMUM_WIDTH = 1;
    private static final int MINIMUM_HEIGHT = 1;

    private Insets insets = new Insets(0, 0, 0, 0);

    private int screenOn = -1;
    private GraphicsConfiguration graphicsConfig;

    private SurfaceData surfaceData;
    private final Object surfaceDataLock = new Object();

    private int backBufferCount;
    private BufferCapabilities backBufferCaps;

    // The back buffer is used for two purposes:
    // 1. To render all the lightweight peers
    // 2. To provide user with a BufferStrategy
    // Need to check if a single back buffer can be used for both
// TODO: VolatileImage
//    private VolatileImage backBuffer;
    private volatile BufferedImage backBuffer;

    private volatile int windowState = Frame.NORMAL;

    // A peer where the last mouse event came to. Used to generate
    // MOUSE_ENTERED/EXITED notifications and by cursor manager to
    // find the component under cursor
    private static volatile LWComponentPeer lastMouseEventPeer = null;

    // Peers where all dragged/released events should come to,
    // depending on what mouse button is being dragged according to Cocoa
    private static LWComponentPeer mouseDownTarget[] = new LWComponentPeer[3];

    // A bitmask that indicates what mouse buttons produce MOUSE_CLICKED events
    // on MOUSE_RELEASE. Click events are only generated if there were no drag
    // events between MOUSE_PRESSED and MOUSE_RELEASED for particular button
    private static int mouseClickButtons = 0;

    private volatile boolean cachedFocusableWindow;

    private volatile boolean isOpaque = true;

    private static final Font DEFAULT_FONT = new Font("Lucida Grande", Font.PLAIN, 13);

    private static LWWindowPeer grabbingWindow;

    private volatile boolean skipNextFocusChange;

    /**
     * Current modal blocker or null.
     *
     * Synchronization: peerTreeLock.
     */
    private LWWindowPeer blocker;

    public LWWindowPeer(Window target, PlatformComponent platformComponent,
                        PlatformWindow platformWindow)
    {
        super(target, platformComponent);
        this.platformWindow = platformWindow;

        Window owner = target.getOwner();
        LWWindowPeer ownerPeer = (owner != null) ? (LWWindowPeer)owner.getPeer() : null;
        PlatformWindow ownerDelegate = (ownerPeer != null) ? ownerPeer.getPlatformWindow() : null;

        // The delegate.initialize() needs a non-null GC on X11.
        GraphicsConfiguration gc = getTarget().getGraphicsConfiguration();
        synchronized (getStateLock()) {
            // graphicsConfig should be updated according to the real window
            // bounds when the window is shown, see 4868278
            this.graphicsConfig = gc;
        }

        if (!target.isFontSet()) {
            target.setFont(DEFAULT_FONT);
        }

        if (!target.isBackgroundSet()) {
            target.setBackground(SystemColor.window);
        } else {
            // first we check if user provided alpha for background. This is
            // similar to what Apple's Java do.
            // Since JDK7 we should rely on setOpacity() only.
            // this.opacity = c.getAlpha();
            // System.out.println("Delegate assigns alpha (we ignore setOpacity()):"
            // +this.opacity);
        }

        if (!target.isForegroundSet()) {
            target.setForeground(SystemColor.windowText);
            // we should not call setForeground because it will call a repaint
            // which the peer may not be ready to do yet.
        }

        platformWindow.initialize(target, this, ownerDelegate);
    }

    @Override
    public void initialize() {
        if (getTarget() instanceof Frame) {
            setTitle(((Frame)getTarget()).getTitle());
            setState(((Frame)getTarget()).getExtendedState());
        } else if (getTarget() instanceof Dialog) {
            setTitle(((Dialog)getTarget()).getTitle());
        }

        setAlwaysOnTop(getTarget().isAlwaysOnTop());
        updateMinimumSize();

        cachedFocusableWindow = getTarget().isFocusableWindow();

        setOpacity(getTarget().getOpacity());
        setOpaque(getTarget().isOpaque());

        super.initialize();

        updateInsets(platformWindow.getInsets());
    }

    // Just a helper method
    public PlatformWindow getPlatformWindow() {
        return platformWindow;
    }

    @Override
    protected LWWindowPeer getWindowPeerOrSelf() {
        return this;
    }

    @Override
    protected void initializeContainerPeer() {
        // No-op as LWWindowPeer doesn't have any containerPeer
    }

    // ---- PEER METHODS ---- //

    @Override
    protected void disposeImpl() {
        SurfaceData oldData = getSurfaceData();
        synchronized (surfaceDataLock){
            surfaceData = null;
        }
        if (oldData != null) {
            oldData.invalidate();
        }
        if (isGrabbing()) {
            ungrab();
        }
        destroyBuffers();
        platformWindow.dispose();
        super.disposeImpl();
    }

    @Override
    public void setVisible(final boolean visible) {
        if (getSurfaceData() == null) {
            replaceSurfaceData();
        }

        if (isVisible() == visible) {
            return;
        }
        super.setVisible(visible);

        // TODO: update graphicsConfig, see 4868278
        // TODO: don't notify the delegate if our visibility is unchanged

        // it is important to call this method on EDT
        // to prevent the deadlocks during the painting of the lightweight delegates
        //TODO: WHY? This is a native-system related call. Perhaps NOT calling
        // the painting procedure right from the setVisible(), but rather relying
        // on the native Expose event (or, scheduling the repainting asynchronously)
        // is better?
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                platformWindow.setVisible(visible);
                if (isSimpleWindow()) {
                    LWKeyboardFocusManagerPeer manager = LWKeyboardFocusManagerPeer.
                        getInstance(getAppContext());

                    if (visible) {
                        updateFocusableWindowState();
                        changeFocusedWindow(true, true);

                    // Focus the owner in case this window is focused.
                    } else if (manager.getCurrentFocusedWindow() == getTarget()) {
                        LWWindowPeer owner = getOwnerFrameDialog(LWWindowPeer.this);
                        if (owner != null) {
                            // KFM will do all the rest.
                            owner.changeFocusedWindow(true, false);
                        }
                    }
                }
            }
        });
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        return graphicsConfig;
    }

    @Override
    public boolean updateGraphicsData(GraphicsConfiguration gc) {
        setGraphicsConfig(gc);
        return false;
    }

    protected final Graphics getOnscreenGraphics(Color fg, Color bg, Font f) {
        if (getSurfaceData() == null) {
            return null;
        }
        if (fg == null) {
            fg = SystemColor.windowText;
        }
        if (bg == null) {
            bg = SystemColor.window;
        }
        if (f == null) {
            f = DEFAULT_FONT;
        }
        return platformWindow.transformGraphics(new SunGraphics2D(getSurfaceData(), fg, bg, f));
    }

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps)
        throws AWTException
    {
        try {
            // Assume this method is never called with numBuffers <= 1, as 0 is
            // unsupported, and 1 corresponds to a SingleBufferStrategy which
            // doesn't depend on the peer. Screen is considered as a separate
            // "buffer", that's why numBuffers - 1
            assert numBuffers > 1;

            replaceSurfaceData(numBuffers - 1, caps);
        } catch (InvalidPipeException z) {
            throw new AWTException(z.toString());
        }
    }

    @Override
    public final Image getBackBuffer() {
        synchronized (getStateLock()) {
            return backBuffer;
        }
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction)
    {
        platformWindow.flip(x1, y1, x2, y2, flipAction);
    }

    @Override
    public final void destroyBuffers() {
        final Image oldBB = getBackBuffer();
        synchronized (getStateLock()) {
            backBuffer = null;
        }
        if (oldBB != null) {
            oldBB.flush();
        }
    }

    @Override
    public void setBounds(int x, int y, int w, int h, int op) {
        if ((op & SET_CLIENT_SIZE) != 0) {
            // SET_CLIENT_SIZE is only applicable to window peers, so handle it here
            // instead of pulling 'insets' field up to LWComponentPeer
            // no need to add insets since Window's notion of width and height includes insets.
            op &= ~SET_CLIENT_SIZE;
            op |= SET_SIZE;
        }

        if (w < MINIMUM_WIDTH) {
            w = MINIMUM_WIDTH;
        }
        if (h < MINIMUM_HEIGHT) {
            h = MINIMUM_HEIGHT;
        }

        // Don't post ComponentMoved/Resized and Paint events
        // until we've got a notification from the delegate
        setBounds(x, y, w, h, op, false, false);
        // Get updated bounds, so we don't have to handle 'op' here manually
        Rectangle r = getBounds();
        platformWindow.setBounds(r.x, r.y, r.width, r.height);
    }

    @Override
    public Point getLocationOnScreen() {
        return platformWindow.getLocationOnScreen();
    }

    /**
     * Overridden from LWContainerPeer to return the correct insets.
     * Insets are queried from the delegate and are kept up to date by
     * requiering when needed (i.e. when the window geometry is changed).
     */
    @Override
    public Insets getInsets() {
        synchronized (getStateLock()) {
            return insets;
        }
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        // TODO: check for "use platform metrics" settings
        return platformWindow.getFontMetrics(f);
    }

    @Override
    public void toFront() {
        platformWindow.toFront();
    }

    @Override
    public void toBack() {
        platformWindow.toBack();
    }

    @Override
    public void setZOrder(ComponentPeer above) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void setAlwaysOnTop(boolean value) {
        platformWindow.setAlwaysOnTop(value);
    }

    @Override
    public void updateFocusableWindowState() {
        cachedFocusableWindow = getTarget().isFocusableWindow();
        platformWindow.updateFocusableWindowState();
    }

    @Override
    public void setModalBlocked(Dialog blocker, boolean blocked) {
        synchronized (getPeerTreeLock()) {
            this.blocker = blocked ? (LWWindowPeer)blocker.getPeer() : null;
        }
    }

    @Override
    public void updateMinimumSize() {
        Dimension d = null;
        if (getTarget().isMinimumSizeSet()) {
            d = getTarget().getMinimumSize();
        }
        if (d == null) {
            d = new Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT);
        }
        platformWindow.setMinimumSize(d.width, d.height);
    }

    @Override
    public void updateIconImages() {
        getPlatformWindow().updateIconImages();
    }

    @Override
    public void setOpacity(float opacity) {
        getPlatformWindow().setOpacity(opacity);
        repaintPeer();
    }

    @Override
    public final void setOpaque(final boolean isOpaque) {
        if (this.isOpaque != isOpaque) {
            this.isOpaque = isOpaque;
            getPlatformWindow().setOpaque(isOpaque);
            replaceSurfaceData();
            repaintPeer();
        }
    }

    public final boolean isOpaque() {
        return isOpaque;
    }

    @Override
    public void updateWindow() {
        flushOffscreenGraphics();
    }

    @Override
    public void repositionSecurityWarning() {
        throw new RuntimeException("not implemented");
    }

    // ---- FRAME PEER METHODS ---- //

    @Override // FramePeer and DialogPeer
    public void setTitle(String title) {
        platformWindow.setTitle(title == null ? "" : title);
    }

    @Override
    public void setMenuBar(MenuBar mb) {
         platformWindow.setMenuBar(mb);
    }

    @Override // FramePeer and DialogPeer
    public void setResizable(boolean resizable) {
        platformWindow.setResizable(resizable);
    }

    @Override
    public void setState(int state) {
        platformWindow.setWindowState(state);
    }

    @Override
    public int getState() {
        return windowState;
    }

    @Override
    public void setMaximizedBounds(Rectangle bounds) {
        // TODO: not implemented
    }

    @Override
    public void setBoundsPrivate(int x, int y, int width, int height) {
        setBounds(x, y, width, height, SET_BOUNDS | NO_EMBEDDED_CHECK);
    }

    @Override
    public Rectangle getBoundsPrivate() {
        throw new RuntimeException("not implemented");
    }

    // ---- DIALOG PEER METHODS ---- //

    @Override
    public void blockWindows(List<Window> windows) {
        //TODO: LWX will probably need some collectJavaToplevels to speed this up
        for (Window w : windows) {
            WindowPeer wp = (WindowPeer)w.getPeer();
            if (wp != null) {
                wp.setModalBlocked((Dialog)getTarget(), true);
            }
        }
    }

    // ---- PEER NOTIFICATIONS ---- //

    public void notifyIconify(boolean iconify) {
        //The toplevel target is Frame and states are applicable to it.
        //Otherwise, the target is Window and it don't have state property.
        //Hopefully, no such events are posted in the queue so consider the
        //target as Frame in all cases.

        // REMIND: should we send it anyway if the state not changed since last
        // time?
        WindowEvent iconifyEvent = new WindowEvent(getTarget(),
                iconify ? WindowEvent.WINDOW_ICONIFIED
                        : WindowEvent.WINDOW_DEICONIFIED);
        postEvent(iconifyEvent);

        int newWindowState = iconify ? Frame.ICONIFIED : Frame.NORMAL;
        postWindowStateChangedEvent(newWindowState);

        // REMIND: RepaintManager doesn't repaint iconified windows and
        // hence ignores any repaint request during deiconification.
        // So, we need to repaint window explicitly when it becomes normal.
        if (!iconify) {
            repaintPeer();
        }
    }

    public void notifyZoom(boolean isZoomed) {
        int newWindowState = isZoomed ? Frame.MAXIMIZED_BOTH : Frame.NORMAL;
        postWindowStateChangedEvent(newWindowState);
    }

    /**
     * Called by the delegate when any part of the window should be repainted.
     */
    public void notifyExpose(final int x, final int y, final int w, final int h) {
        // TODO: there's a serious problem with Swing here: it handles
        // the exposition internally, so SwingPaintEventDispatcher always
        // return null from createPaintEvent(). However, we flush the
        // back buffer here unconditionally, so some flickering may appear.
        // A possible solution is to split postPaintEvent() into two parts,
        // and override that part which is only called after if
        // createPaintEvent() returned non-null value and flush the buffer
        // from the overridden method
        flushOnscreenGraphics();
        repaintPeer(new Rectangle(x, y, w, h));
    }

    /**
     * Called by the delegate when this window is moved/resized by user.
     * There's no notifyReshape() in LWComponentPeer as the only
     * components which could be resized by user are top-level windows.
     */
    public final void notifyReshape(int x, int y, int w, int h) {
        boolean moved = false;
        boolean resized = false;
        synchronized (getStateLock()) {
            moved = (x != sysX) || (y != sysY);
            resized = (w != sysW) || (h != sysH);
            sysX = x;
            sysY = y;
            sysW = w;
            sysH = h;
        }

        // Check if anything changed
        if (!moved && !resized) {
            return;
        }
        // First, update peer's bounds
        setBounds(x, y, w, h, SET_BOUNDS, false, false);

        // Second, update the graphics config and surface data
        checkIfOnNewScreen();
        if (resized) {
            replaceSurfaceData();
            flushOnscreenGraphics();
        }

        // Third, COMPONENT_MOVED/COMPONENT_RESIZED events
        if (moved) {
            handleMove(x, y, true);
        }
        if (resized) {
            handleResize(w, h,true);
        }
    }

    private void clearBackground(final int w, final int h) {
        final Graphics g = getOnscreenGraphics(getForeground(), getBackground(),
                                               getFont());
        if (g != null) {
            try {
                g.clearRect(0, 0, w, h);
            } finally {
                g.dispose();
            }
        }
    }

    public void notifyUpdateCursor() {
        getLWToolkit().getCursorManager().updateCursorLater(this);
    }

    public void notifyActivation(boolean activation) {
        changeFocusedWindow(activation, false);
    }

    // MouseDown in non-client area
    public void notifyNCMouseDown() {
        // Ungrab except for a click on a Dialog with the grabbing owner
        if (grabbingWindow != null &&
            grabbingWindow != getOwnerFrameDialog(this))
        {
            grabbingWindow.ungrab();
        }
    }

    // ---- EVENTS ---- //

    /*
     * Called by the delegate to dispatch the event to Java. Event
     * coordinates are relative to non-client window are, i.e. the top-left
     * point of the client area is (insets.top, insets.left).
     */
    public void dispatchMouseEvent(int id, long when, int button,
                                   int x, int y, int screenX, int screenY,
                                   int modifiers, int clickCount, boolean popupTrigger,
                                   byte[] bdata)
    {
        // TODO: fill "bdata" member of AWTEvent
        Rectangle r = getBounds();
        // findPeerAt() expects parent coordinates
        LWComponentPeer targetPeer = findPeerAt(r.x + x, r.y + y);
        LWWindowPeer lastWindowPeer =
            (lastMouseEventPeer != null) ? lastMouseEventPeer.getWindowPeerOrSelf() : null;
        LWWindowPeer curWindowPeer =
            (targetPeer != null) ? targetPeer.getWindowPeerOrSelf() : null;

        if (id == MouseEvent.MOUSE_EXITED) {
            // Sometimes we may get MOUSE_EXITED after lastMouseEventPeer is switched
            // to a peer from another window. So we must first check if this peer is
            // the same as lastWindowPeer
            if (lastWindowPeer == this) {
                if (isEnabled()) {
                    Point lp = lastMouseEventPeer.windowToLocal(x, y,
                                                                lastWindowPeer);
                    postEvent(new MouseEvent(lastMouseEventPeer.getTarget(),
                                             MouseEvent.MOUSE_EXITED, when,
                                             modifiers, lp.x, lp.y, screenX,
                                             screenY, clickCount, popupTrigger,
                                             button));
                }
                lastMouseEventPeer = null;
            }
        } else {
            if (targetPeer != lastMouseEventPeer) {
                // lastMouseEventPeer may be null if mouse was out of Java windows
                if (lastMouseEventPeer != null && lastMouseEventPeer.isEnabled()) {
                    // Sometimes, MOUSE_EXITED is not sent by delegate (or is sent a bit
                    // later), in which case lastWindowPeer is another window
                    if (lastWindowPeer != this) {
                        Point oldp = lastMouseEventPeer.windowToLocal(x, y, lastWindowPeer);
                        // Additionally translate from this to lastWindowPeer coordinates
                        Rectangle lr = lastWindowPeer.getBounds();
                        oldp.x += r.x - lr.x;
                        oldp.y += r.y - lr.y;
                        postEvent(new MouseEvent(lastMouseEventPeer.getTarget(),
                                                 MouseEvent.MOUSE_EXITED,
                                                 when, modifiers,
                                                 oldp.x, oldp.y, screenX, screenY,
                                                 clickCount, popupTrigger, button));
                    } else {
                        Point oldp = lastMouseEventPeer.windowToLocal(x, y, this);
                        postEvent(new MouseEvent(lastMouseEventPeer.getTarget(),
                                                 MouseEvent.MOUSE_EXITED,
                                                 when, modifiers,
                                                 oldp.x, oldp.y, screenX, screenY,
                                                 clickCount, popupTrigger, button));
                    }
                }
                lastMouseEventPeer = targetPeer;
                if (targetPeer != null && targetPeer.isEnabled() && id != MouseEvent.MOUSE_ENTERED) {
                    Point newp = targetPeer.windowToLocal(x, y, curWindowPeer);
                    postEvent(new MouseEvent(targetPeer.getTarget(),
                                             MouseEvent.MOUSE_ENTERED,
                                             when, modifiers,
                                             newp.x, newp.y, screenX, screenY,
                                             clickCount, popupTrigger, button));
                }
            }
            // TODO: fill "bdata" member of AWTEvent

            int eventButtonMask = (button > 0)? MouseEvent.getMaskForButton(button) : 0;
            int otherButtonsPressed = modifiers & ~eventButtonMask;

            // For pressed/dragged/released events OS X treats other
            // mouse buttons as if they were BUTTON2, so we do the same
            int targetIdx = (button > 3) ? MouseEvent.BUTTON2 - 1 : button - 1;

            // MOUSE_ENTERED/EXITED are generated for the components strictly under
            // mouse even when dragging. That's why we first update lastMouseEventPeer
            // based on initial targetPeer value and only then recalculate targetPeer
            // for MOUSE_DRAGGED/RELEASED events
            if (id == MouseEvent.MOUSE_PRESSED) {

                // Ungrab only if this window is not an owned window of the grabbing one.
                if (!isGrabbing() && grabbingWindow != null &&
                    grabbingWindow != getOwnerFrameDialog(this))
                {
                    grabbingWindow.ungrab();
                }
                if (otherButtonsPressed == 0) {
                    mouseClickButtons = eventButtonMask;
                } else {
                    mouseClickButtons |= eventButtonMask;
                }

                mouseDownTarget[targetIdx] = targetPeer;
            } else if (id == MouseEvent.MOUSE_DRAGGED) {
                // Cocoa dragged event has the information about which mouse
                // button is being dragged. Use it to determine the peer that
                // should receive the dragged event.
                targetPeer = mouseDownTarget[targetIdx];
                mouseClickButtons &= ~modifiers;
            } else if (id == MouseEvent.MOUSE_RELEASED) {
                // TODO: currently, mouse released event goes to the same component
                // that received corresponding mouse pressed event. For most cases,
                // it's OK, however, we need to make sure that our behavior is consistent
                // with 1.6 for cases where component in question have been
                // hidden/removed in between of mouse pressed/released events.
                targetPeer = mouseDownTarget[targetIdx];

                if ((modifiers & eventButtonMask) == 0) {
                    mouseDownTarget[targetIdx] = null;
                }

                // mouseClickButtons is updated below, after MOUSE_CLICK is sent
            }

            // check if we receive mouseEvent from outside the window's bounds
            // it can be either mouseDragged or mouseReleased
            if (curWindowPeer == null) {
                //TODO This can happen if this window is invisible. this is correct behavior in this case?
                curWindowPeer = this;
            }
            if (targetPeer == null) {
                //TODO This can happen if this window is invisible. this is correct behavior in this case?
                targetPeer = this;
            }


            Point lp = targetPeer.windowToLocal(x, y, curWindowPeer);
            if (targetPeer.isEnabled()) {
                MouseEvent event = new MouseEvent(targetPeer.getTarget(), id,
                                                  when, modifiers, lp.x, lp.y,
                                                  screenX, screenY, clickCount,
                                                  popupTrigger, button);
                postEvent(event);
            }

            if (id == MouseEvent.MOUSE_RELEASED) {
                if ((mouseClickButtons & eventButtonMask) != 0
                    && targetPeer.isEnabled()) {
                    postEvent(new MouseEvent(targetPeer.getTarget(),
                                             MouseEvent.MOUSE_CLICKED,
                                             when, modifiers,
                                             lp.x, lp.y, screenX, screenY,
                                             clickCount, popupTrigger, button));
                }
                mouseClickButtons &= ~eventButtonMask;
            }

            notifyUpdateCursor();
        }
    }

    public void dispatchMouseWheelEvent(long when, int x, int y, int modifiers,
                                        int scrollType, int scrollAmount,
                                        int wheelRotation, double preciseWheelRotation,
                                        byte[] bdata)
    {
        // TODO: could we just use the last mouse event target here?
        Rectangle r = getBounds();
        // findPeerAt() expects parent coordinates
        final LWComponentPeer targetPeer = findPeerAt(r.x + x, r.y + y);
        if (targetPeer == null || !targetPeer.isEnabled()) {
            return;
        }

        Point lp = targetPeer.windowToLocal(x, y, this);
        // TODO: fill "bdata" member of AWTEvent
        // TODO: screenX/screenY
        postEvent(new MouseWheelEvent(targetPeer.getTarget(),
                                      MouseEvent.MOUSE_WHEEL,
                                      when, modifiers,
                                      lp.x, lp.y,
                                      0, 0, /* screenX, Y */
                                      0 /* clickCount */, false /* popupTrigger */,
                                      scrollType, scrollAmount,
                                      wheelRotation, preciseWheelRotation));
    }

    /*
     * Called by the delegate when a key is pressed.
     */
    public void dispatchKeyEvent(int id, long when, int modifiers,
                                 int keyCode, char keyChar, int keyLocation)
    {
        LWComponentPeer focusOwner =
            LWKeyboardFocusManagerPeer.getInstance(getAppContext()).
                getFocusOwner();

        // Null focus owner may receive key event when
        // application hides the focused window upon ESC press
        // (AWT transfers/clears the focus owner) and pending ESC release
        // may come to already hidden window. This check eliminates NPE.
        if (focusOwner != null) {
            KeyEvent event =
                new KeyEvent(focusOwner.getTarget(), id, when, modifiers,
                             keyCode, keyChar, keyLocation);
            focusOwner.postEvent(event);
        }
    }


    // ---- UTILITY METHODS ---- //

    private void postWindowStateChangedEvent(int newWindowState) {
        if (getTarget() instanceof Frame) {
            AWTAccessor.getFrameAccessor().setExtendedState(
                    (Frame)getTarget(), newWindowState);
        }
        WindowEvent stateChangedEvent = new WindowEvent(getTarget(),
                WindowEvent.WINDOW_STATE_CHANGED,
                windowState, newWindowState);
        postEvent(stateChangedEvent);
        windowState = newWindowState;
    }

    private static int getGraphicsConfigScreen(GraphicsConfiguration gc) {
        // TODO: this method can be implemented in a more
        // efficient way by forwarding to the delegate
        GraphicsDevice gd = gc.getDevice();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        for (int i = 0; i < gds.length; i++) {
            if (gds[i] == gd) {
                return i;
            }
        }
        // Should never happen if gc is a screen device config
        return 0;
    }

    private static GraphicsConfiguration getScreenGraphicsConfig(int screen) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        if (screen >= gds.length) {
            // This could happen during device addition/removal. Use
            // the default screen device in this case
            return ge.getDefaultScreenDevice().getDefaultConfiguration();
        }
        return gds[screen].getDefaultConfiguration();
    }

    /*
     * This method is called when window's graphics config is changed from
     * the app code (e.g. when the window is made non-opaque) or when
     * the window is moved to another screen by user.
     *
     * Returns true if the graphics config has been changed, false otherwise.
     */
    private boolean setGraphicsConfig(GraphicsConfiguration gc) {
        synchronized (getStateLock()) {
            if (graphicsConfig == gc) {
                return false;
            }
            // If window's graphics config is changed from the app code, the
            // config correspond to the same device as before; when the window
            // is moved by user, screenOn is updated in checkIfOnNewScreen().
            // In either case, there's nothing to do with screenOn here
            graphicsConfig = gc;
        }
        // SurfaceData is replaced later in updateGraphicsData()
        return true;
    }

    private void checkIfOnNewScreen() {
        int windowScreen = platformWindow.getScreenImOn();
        synchronized (getStateLock()) {
            if (windowScreen == screenOn) {
                return;
            }
            screenOn = windowScreen;
        }

        // TODO: DisplayChangedListener stuff
        final GraphicsConfiguration newGC = getScreenGraphicsConfig(windowScreen);
        if (!setGraphicsConfig(newGC)) return;

        SunToolkit.executeOnEventHandlerThread(getTarget(), new Runnable() {
            public void run() {
                AWTAccessor.getComponentAccessor().setGraphicsConfiguration(getTarget(), newGC);
            }
        });
    }

    /**
     * This method returns a back buffer Graphics to render all the
     * peers to. After the peer is painted, the back buffer contents
     * should be flushed to the screen. All the target painting
     * (Component.paint() method) should be done directly to the screen.
     */
    protected final Graphics getOffscreenGraphics(Color fg, Color bg, Font f) {
        final Image bb = getBackBuffer();
        if (bb == null) {
            return null;
        }
        if (fg == null) {
            fg = SystemColor.windowText;
        }
        if (bg == null) {
            bg = SystemColor.window;
        }
        if (f == null) {
            f = DEFAULT_FONT;
        }
        final Graphics2D g = (Graphics2D) bb.getGraphics();
        if (g != null) {
            g.setColor(fg);
            g.setBackground(bg);
            g.setFont(f);
        }
        return g;
    }

    /*
     * May be called by delegate to provide SD to Java2D code.
     */
    public SurfaceData getSurfaceData() {
        synchronized (surfaceDataLock) {
            return surfaceData;
        }
    }

    private void replaceSurfaceData() {
        replaceSurfaceData(backBufferCount, backBufferCaps);
    }

    private void replaceSurfaceData(int newBackBufferCount,
                                                 BufferCapabilities newBackBufferCaps) {
        synchronized (surfaceDataLock) {
            final SurfaceData oldData = getSurfaceData();
            surfaceData = platformWindow.replaceSurfaceData();
            // TODO: volatile image
    //        VolatileImage oldBB = backBuffer;
            BufferedImage oldBB = backBuffer;
            backBufferCount = newBackBufferCount;
            backBufferCaps = newBackBufferCaps;
            final Rectangle size = getSize();
            if (getSurfaceData() != null && oldData != getSurfaceData()) {
                clearBackground(size.width, size.height);
            }
            blitSurfaceData(oldData, getSurfaceData());

            if (oldData != null && oldData != getSurfaceData()) {
                // TODO: drop oldData for D3D/WGL pipelines
                // This can only happen when this peer is being created
                oldData.flush();
            }

            // TODO: volatile image
    //        backBuffer = (VolatileImage)delegate.createBackBuffer();
            backBuffer = (BufferedImage) platformWindow.createBackBuffer();
            if (backBuffer != null) {
                Graphics g = backBuffer.getGraphics();
                try {
                    Rectangle r = getBounds();
                    g.setColor(getBackground());
                    g.fillRect(0, 0, r.width, r.height);
                    if (oldBB != null) {
                        // Draw the old back buffer to the new one
                        g.drawImage(oldBB, 0, 0, null);
                        oldBB.flush();
                    }
                } finally {
                    g.dispose();
                }
            }
        }
    }

    private void blitSurfaceData(final SurfaceData src, final SurfaceData dst) {
        //TODO blit. proof-of-concept
        if (src != dst && src != null && dst != null
            && !(dst instanceof NullSurfaceData)
            && !(src instanceof NullSurfaceData)
            && src.getSurfaceType().equals(dst.getSurfaceType())) {
            final Rectangle size = getSize();
            final Blit blit = Blit.locate(src.getSurfaceType(),
                                          CompositeType.Src,
                                          dst.getSurfaceType());
            if (blit != null) {
                blit.Blit(src, dst, ((Graphics2D) getGraphics()).getComposite(),
                          getRegion(), 0, 0, 0, 0, size.width, size.height);
            }
        }
    }

    public int getBackBufferCount() {
        return backBufferCount;
    }

    public BufferCapabilities getBackBufferCaps() {
        return backBufferCaps;
    }

    /*
     * Request the window insets from the delegate and compares it
     * with the current one. This method is mostly called by the
     * delegate, e.g. when the window state is changed and insets
     * should be recalculated.
     *
     * This method may be called on the toolkit thread.
     */
    public boolean updateInsets(Insets newInsets) {
        boolean changed = false;
        synchronized (getStateLock()) {
            changed = (insets.equals(newInsets));
            insets = newInsets;
        }

        if (changed) {
            replaceSurfaceData();
            repaintPeer();
        }

        return changed;
    }

    public static LWWindowPeer getWindowUnderCursor() {
        return lastMouseEventPeer != null ? lastMouseEventPeer.getWindowPeerOrSelf() : null;
    }

    public boolean requestWindowFocus(CausedFocusEvent.Cause cause) {
        if (focusLog.isLoggable(PlatformLogger.FINE)) {
            focusLog.fine("requesting native focus to " + this);
        }

        if (!focusAllowedFor()) {
            focusLog.fine("focus is not allowed");
            return false;
        }

        // Cross-app activation requests are not allowed.
        if (cause != CausedFocusEvent.Cause.MOUSE_EVENT &&
            !((LWToolkit)Toolkit.getDefaultToolkit()).isApplicationActive())
        {
            focusLog.fine("the app is inactive, so the request is rejected");
            return false;
        }

        Window currentActive = KeyboardFocusManager.
            getCurrentKeyboardFocusManager().getActiveWindow();

        // Make the owner active window.
        if (isSimpleWindow()) {
            LWWindowPeer owner = getOwnerFrameDialog(this);

            // If owner is not natively active, request native
            // activation on it w/o sending events up to java.
            if (owner != null && !owner.platformWindow.isActive()) {
                if (focusLog.isLoggable(PlatformLogger.FINE)) {
                    focusLog.fine("requesting native focus to the owner " + owner);
                }
                LWWindowPeer currentActivePeer = (currentActive != null ?
                    (LWWindowPeer)currentActive.getPeer() : null);

                // Ensure the opposite is natively active and suppress sending events.
                if (currentActivePeer != null && currentActivePeer.platformWindow.isActive()) {
                    if (focusLog.isLoggable(PlatformLogger.FINE)) {
                        focusLog.fine("the opposite is " + currentActivePeer);
                    }
                    currentActivePeer.skipNextFocusChange = true;
                }
                owner.skipNextFocusChange = true;

                owner.platformWindow.requestWindowFocus();
            }

            // DKFM will synthesize all the focus/activation events correctly.
            changeFocusedWindow(true, false);
            return true;

        // In case the toplevel is active but not focused, change focus directly,
        // as requesting native focus on it will not have effect.
        } else if (getTarget() == currentActive && !getTarget().hasFocus()) {

            changeFocusedWindow(true, false);
            return true;
        }
        return platformWindow.requestWindowFocus();
    }

    private boolean focusAllowedFor() {
        Window window = getTarget();
        // TODO: check if modal blocked
        return window.isVisible() && window.isEnabled() && window.isFocusableWindow();
    }

    public boolean isSimpleWindow() {
        Window window = getTarget();
        return !(window instanceof Dialog || window instanceof Frame);
    }

    /*
     * "Delegates" the responsibility of managing focus to keyboard focus manager.
     */
    private void changeFocusedWindow(boolean becomesFocused, boolean isShowing) {
        if (focusLog.isLoggable(PlatformLogger.FINE)) {
            focusLog.fine((becomesFocused?"gaining":"loosing") + " focus window: " + this);
        }
        if (isShowing && !getTarget().isAutoRequestFocus() || skipNextFocusChange) {
            focusLog.fine("skipping focus change");
            skipNextFocusChange = false;
            return;
        }

        if (!cachedFocusableWindow) {
            return;
        }
        if (becomesFocused) {
            synchronized (getPeerTreeLock()) {
                if (blocker != null) {
                    if (focusLog.isLoggable(PlatformLogger.FINEST)) {
                        focusLog.finest("the window is blocked by " + blocker);
                    }
                    return;
                }
            }
        }

        LWKeyboardFocusManagerPeer manager = LWKeyboardFocusManagerPeer.
            getInstance(getAppContext());

        Window oppositeWindow = becomesFocused ? manager.getCurrentFocusedWindow() : null;

        // Note, the method is not called:
        // - when the opposite (gaining focus) window is an owned/owner window.
        // - for a simple window in any case.
        if (!becomesFocused &&
            (isGrabbing() || getOwnerFrameDialog(grabbingWindow) == this))
        {
            focusLog.fine("ungrabbing on " + grabbingWindow);
            // ungrab a simple window if its owner looses activation.
            grabbingWindow.ungrab();
        }

        manager.setFocusedWindow(becomesFocused ? LWWindowPeer.this : null);

        int eventID = becomesFocused ? WindowEvent.WINDOW_GAINED_FOCUS : WindowEvent.WINDOW_LOST_FOCUS;
        WindowEvent windowEvent = new WindowEvent(getTarget(), eventID, oppositeWindow);

        // TODO: wrap in SequencedEvent
        postEvent(windowEvent);
    }

    private static LWWindowPeer getOwnerFrameDialog(LWWindowPeer peer) {
        Window owner = (peer != null ? peer.getTarget().getOwner() : null);
        while (owner != null && !(owner instanceof Frame || owner instanceof Dialog)) {
            owner = owner.getOwner();
        }
        return owner != null ? (LWWindowPeer)owner.getPeer() : null;
    }

    /**
     * Returns the foremost modal blocker of this window, or null.
     */
    public LWWindowPeer getBlocker() {
        synchronized (getPeerTreeLock()) {
            LWWindowPeer blocker = this.blocker;
            if (blocker == null) {
                return null;
            }
            while (blocker.blocker != null) {
                blocker = blocker.blocker;
            }
            return blocker;
        }
    }

    public void enterFullScreenMode() {
        platformWindow.enterFullScreenMode();
    }

    public void exitFullScreenMode() {
        platformWindow.exitFullScreenMode();
    }

    public long getLayerPtr() {
        return getPlatformWindow().getLayerPtr();
    }

    void grab() {
        if (grabbingWindow != null && !isGrabbing()) {
            grabbingWindow.ungrab();
        }
        grabbingWindow = this;
    }

    void ungrab() {
        if (isGrabbing()) {
            grabbingWindow = null;
            postEvent(new UngrabEvent(getTarget()));
        }
    }

    private boolean isGrabbing() {
        return this == grabbingWindow;
    }

    @Override
    public String toString() {
        return super.toString() + " [target is " + getTarget() + "]";
    }
}
