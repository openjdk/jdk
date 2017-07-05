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

import java.awt.dnd.DropTarget;
import java.awt.dnd.peer.DropTargetPeer;
import java.awt.event.*;

import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.VolatileImage;

import java.awt.peer.ComponentPeer;
import java.awt.peer.ContainerPeer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.awt.*;

import sun.awt.event.IgnorePaintEvent;

import sun.awt.image.SunVolatileImage;
import sun.awt.image.ToolkitImage;

import sun.java2d.SunGraphics2D;
import sun.java2d.opengl.OGLRenderQueue;
import sun.java2d.pipe.Region;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.RepaintManager;

import sun.lwawt.macosx.CDropTarget;

import com.sun.java.swing.SwingUtilities3;

public abstract class LWComponentPeer<T extends Component, D extends JComponent>
        implements ComponentPeer, DropTargetPeer {
    // State lock is to be used for modifications to this
    // peer's fields (e.g. bounds, background, font, etc.)
    // It should be the last lock in the lock chain
    private final Object stateLock =
            new StringBuilder("LWComponentPeer.stateLock");

    // The lock to operate with the peers hierarchy. AWT tree
    // lock is not used as there are many peers related ops
    // to be done on the toolkit thread, and we don't want to
    // depend on a public lock on this thread
    private final static Object peerTreeLock =
            new StringBuilder("LWComponentPeer.peerTreeLock");

    /**
     * A custom tree-lock used for the hierarchy of the delegate Swing
     * components.
     * The lock synchronizes access to the delegate
     * internal state. Think of it as a 'virtual EDT'.
     */
//    private final Object delegateTreeLock =
//        new StringBuilder("LWComponentPeer.delegateTreeLock");

    private T target;

    // Container peer. It may not be the peer of the target's direct
    // parent, for example, in the case of hw/lw mixing. However,
    // let's skip this scenario for the time being. We also assume
    // the container peer is not null, which might also be false if
    // addNotify() is called for a component outside of the hierarchy.
    // The exception is LWWindowPeers: their parents are always null
    private LWContainerPeer containerPeer;

    // Handy reference to the top-level window peer. Window peer is
    // borrowed from the containerPeer in constructor, and should also
    // be updated when the component is reparented to another container
    private LWWindowPeer windowPeer;

    private AtomicBoolean disposed = new AtomicBoolean(false);

    // Bounds are relative to parent peer
    private Rectangle bounds = new Rectangle();
    private Region region;

    // Component state. Should be accessed under the state lock
    private boolean visible = false;
    private boolean enabled = true;

    private Color background;
    private Color foreground;
    private Font font;

    // Paint area to coalesce all the paint events and store
    // the target dirty area
    private RepaintArea targetPaintArea;

    //   private volatile boolean paintPending;
    private volatile boolean isLayouting;

    private D delegate = null;
    private Container delegateContainer;
    private Component delegateDropTarget;
    private final Object dropTargetLock = new Object();

    private int fNumDropTargets = 0;
    private CDropTarget fDropTarget = null;

    private PlatformComponent platformComponent;

    private final class DelegateContainer extends Container {
        {
            enableEvents(0xFFFFFFFF);
        }

        DelegateContainer() {
            super();
        }

        @Override
        public boolean isLightweight() {
            return false;
        }

        @Override
        public Point getLocation() {
            return getLocationOnScreen();
        }

        @Override
        public Point getLocationOnScreen() {
            return LWComponentPeer.this.getLocationOnScreen();
        }

        @Override
        public int getX() {
            return getLocation().x;
        }

        @Override
        public int getY() {
            return getLocation().y;
        }
    }

    public LWComponentPeer(T target, PlatformComponent platformComponent) {
        this.target = target;
        this.platformComponent = platformComponent;

        initializeContainerPeer();
        // Container peer is always null for LWWindowPeers, so
        // windowPeer is always null for them as well. On the other
        // hand, LWWindowPeer shouldn't use windowPeer at all
        if (containerPeer != null) {
            windowPeer = containerPeer.getWindowPeerOrSelf();
        }
        // don't bother about z-order here as updateZOrder()
        // will be called from addNotify() later anyway
        if (containerPeer != null) {
            containerPeer.addChildPeer(this);
        }

        // the delegate must be created after the target is set
        AWTEventListener toolkitListener = null;
        synchronized (Toolkit.getDefaultToolkit()) {
            try {
                toolkitListener = getToolkitAWTEventListener();
                setToolkitAWTEventListener(null);

                synchronized (getDelegateLock()) {
                    delegate = createDelegate();
                    if (delegate != null) {
                        delegateContainer = new DelegateContainer();
                        delegateContainer.add(delegate);
                        delegateContainer.addNotify();
                        delegate.addNotify();
                    } else {
                        return;
                    }
                }

            } finally {
                setToolkitAWTEventListener(toolkitListener);
            }

            // todo swing: later on we will probably have one global RM
            SwingUtilities3.setDelegateRepaintManager(delegate, new RepaintManager() {
                @Override
                public void addDirtyRegion(final JComponent c, final int x, final int y, final int w, final int h) {
                    if (SunToolkit.isDispatchThreadForAppContext(getTarget())) {
                        synchronized (getDelegateLock()) {
                            if (getDelegate().isPaintingForPrint()) {
                                return;
                            }
                        }
                    }
                    Rectangle res = SwingUtilities.convertRectangle(
                            c, new Rectangle(x, y, w, h), getDelegate());
                    repaintPeer(res);
                }
            });
        }
    }

    /**
     * This method must be called under Toolkit.getDefaultToolkit() lock
     * and followed by setToolkitAWTEventListener()
     */
    protected final AWTEventListener getToolkitAWTEventListener() {
        return AccessController.doPrivileged(new PrivilegedAction<AWTEventListener>() {
            public AWTEventListener run() {
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                try {
                    Field field = Toolkit.class.getDeclaredField("eventListener");
                    field.setAccessible(true);
                    return (AWTEventListener) field.get(toolkit);
                } catch (Exception e) {
                    throw new InternalError(e.toString());
                }
            }
        });
    }

    protected final void setToolkitAWTEventListener(final AWTEventListener listener) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                try {
                    Field field = Toolkit.class.getDeclaredField("eventListener");
                    field.setAccessible(true);
                    field.set(toolkit, listener);
                } catch (Exception e) {
                    throw new InternalError(e.toString());
                }
                return null;
            }
        });
    }

    /**
     * This method is called under getDelegateLock().
     * Overridden in subclasses.
     */
    protected D createDelegate() {
        return null;
    }

    protected final D getDelegate() {
        synchronized (getStateLock()) {
            return delegate;
        }
    }

    protected Component getDelegateFocusOwner() {
        return getDelegate();
    }

    /*
     * Initializes this peer by fetching all the properties from the target.
     * The call to initialize() is not placed to LWComponentPeer ctor to
     * let the subclass ctor to finish completely first. Instead, it's the
     * LWToolkit object who is responsible for initialization.
     */
    public void initialize() {
        platformComponent.initialize(target, this, getPlatformWindow());
        targetPaintArea = new LWRepaintArea();
        if (getDelegate() != null) {
            synchronized (getDelegateLock()) {
                resetColorsAndFont(delegate);
                getDelegate().setOpaque(true);
            }
        }
        setBackground(target.getBackground());
        setForeground(target.getForeground());
        setFont(target.getFont());
        setBounds(target.getBounds());
        setEnabled(target.isEnabled());
        setVisible(target.isVisible());
    }

    private static void resetColorsAndFont(final Container c) {
        c.setBackground(null);
        c.setForeground(null);
        c.setFont(null);
        for (int i = 0; i < c.getComponentCount(); i++) {
            resetColorsAndFont((Container) c.getComponent(i));
        }
    }

    final Object getStateLock() {
        return stateLock;
    }

    // Synchronize all operations with the Swing delegates under
    // AWT tree lock, using a new separate lock to synchronize
    // access to delegates may lead deadlocks
    final Object getDelegateLock() {
        //return delegateTreeLock;
        return getTarget().getTreeLock();
    }

    protected final static Object getPeerTreeLock() {
        return peerTreeLock;
    }

    final T getTarget() {
        return target;
    }

    // Just a helper method
    // Returns the window peer or null if this is a window peer
    protected final LWWindowPeer getWindowPeer() {
        return windowPeer;
    }

    // Returns the window peer or 'this' if this is a window peer
    protected LWWindowPeer getWindowPeerOrSelf() {
        return getWindowPeer();
    }

    // Just a helper method
    protected final LWContainerPeer getContainerPeer() {
        return containerPeer;
    }

    // Just a helper method
    // Overridden in LWWindowPeer to skip containerPeer initialization
    protected void initializeContainerPeer() {
        Container parent = LWToolkit.getNativeContainer(target);
        if (parent != null) {
            containerPeer = (LWContainerPeer) LWToolkit.targetToPeer(parent);
        }
    }

    public PlatformWindow getPlatformWindow() {
        LWWindowPeer windowPeer = getWindowPeer();
        return windowPeer.getPlatformWindow();
    }

    protected AppContext getAppContext() {
        return SunToolkit.targetToAppContext(getTarget());
    }

    // ---- PEER METHODS ---- //

    @Override
    public Toolkit getToolkit() {
        return LWToolkit.getLWToolkit();
    }

    // Just a helper method
    public LWToolkit getLWToolkit() {
        return LWToolkit.getLWToolkit();
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            disposeImpl();
        }
    }

    protected void disposeImpl() {
        LWContainerPeer cp = getContainerPeer();
        if (cp != null) {
            cp.removeChildPeer(this);
        }
        platformComponent.dispose();
        LWToolkit.targetDisposedPeer(getTarget(), this);
    }

    public final boolean isDisposed() {
        return disposed.get();
    }

    /*
     * GraphicsConfiguration is borrowed from the parent peer. The
     * return value must not be null.
     *
     * Overridden in LWWindowPeer.
     */
    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        // Don't check windowPeer for null as it can only happen
        // for windows, but this method is overridden in
        // LWWindowPeer and doesn't call super()
        return getWindowPeer().getGraphicsConfiguration();
    }

    /*
     * Overridden in LWWindowPeer to replace its surface
     * data and back buffer.
     */
    @Override
    public boolean updateGraphicsData(GraphicsConfiguration gc) {
        // TODO: not implemented
//        throw new RuntimeException("Has not been implemented yet.");
        return false;
    }

    @Override
    public final Graphics getGraphics() {
        Graphics g = getWindowPeerOrSelf().isOpaque() ? getOnscreenGraphics()
                                                      : getOffscreenGraphics();
        if (g != null) {
            synchronized (getPeerTreeLock()){
                applyConstrain(g);
            }
        }
        return g;
    }

    /*
     * Peer Graphics is borrowed from the parent peer, while
     * foreground and background colors and font are specific to
     * this peer.
     */
    public final Graphics getOnscreenGraphics() {
        final LWWindowPeer wp = getWindowPeerOrSelf();
        return wp.getOnscreenGraphics(getForeground(), getBackground(),
                                      getFont());
    }

    public final Graphics getOffscreenGraphics() {
        final LWWindowPeer wp = getWindowPeerOrSelf();

        return wp.getOffscreenGraphics(getForeground(), getBackground(),
                                       getFont());
    }

    private void applyConstrain(final Graphics g) {
        final SunGraphics2D sg2d = (SunGraphics2D) g;
        final Rectangle constr = localToWindow(getSize());
        // translate and set rectangle constrain.
        sg2d.constrain(constr.x, constr.y, constr.width, constr.height);
        // set region constrain.
        //sg2d.constrain(getVisibleRegion());
        SG2DConstraint(sg2d, getVisibleRegion());
    }

    //TODO Move this method to SG2D?
    private void SG2DConstraint(final SunGraphics2D sg2d, Region r) {
        sg2d.constrainX = sg2d.transX;
        sg2d.constrainY = sg2d.transY;

        Region c = sg2d.constrainClip;
        if ((sg2d.constrainX | sg2d.constrainY) != 0) {
            r = r.getTranslatedRegion(sg2d.constrainX, sg2d.constrainY);
        }
        if (c == null) {
            c = r;
        } else {
            c = c.getIntersection(r);
            if (c == sg2d.constrainClip) {
                // Common case to ignore
                return;
            }
        }
        sg2d.constrainClip = c;
        //validateCompClip() forced call.
        sg2d.setDevClip(r.getLoX(), r.getLoY(), r.getWidth(), r.getHeight());
    }

    public Region getVisibleRegion() {
        return computeVisibleRect(this, getRegion());
    }

    static final Region computeVisibleRect(LWComponentPeer c, Region region) {
        final LWContainerPeer p = c.getContainerPeer();
        if (p != null) {
            final Rectangle r = c.getBounds();
            region = region.getTranslatedRegion(r.x, r.y);
            region = region.getIntersection(p.getRegion());
            region = region.getIntersection(p.getContentSize());
            region = p.cutChildren(region, c);
            region = computeVisibleRect(p, region);
            region = region.getTranslatedRegion(-r.x, -r.y);
        }
        return region;
    }

    @Override
    public ColorModel getColorModel() {
        // Is it a correct implementation?
        return getGraphicsConfiguration().getColorModel();
    }

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps)
            throws AWTException {
        throw new AWTException("Back buffers are only supported for " +
                "Window or Canvas components.");
    }

    /*
     * To be overridden in LWWindowPeer and LWCanvasPeer.
     */
    @Override
    public Image getBackBuffer() {
        // Return null or throw AWTException?
        return null;
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction) {
        // Skip silently or throw AWTException?
    }

    @Override
    public void destroyBuffers() {
        // Do nothing
    }

    // Helper method
    public void setBounds(Rectangle r) {
        setBounds(r.x, r.y, r.width, r.height, SET_BOUNDS);
    }

    /**
     * This method could be called on the toolkit thread.
     */
    @Override
    public void setBounds(int x, int y, int w, int h, int op) {
        setBounds(x, y, w, h, op, true, false);
    }

    protected void setBounds(int x, int y, int w, int h, int op, boolean notify,
                             final boolean updateTarget) {
        Rectangle oldBounds;
        synchronized (getStateLock()) {
            oldBounds = new Rectangle(bounds);
            if ((op & (SET_LOCATION | SET_BOUNDS)) != 0) {
                bounds.x = x;
                bounds.y = y;
            }
            if ((op & (SET_SIZE | SET_BOUNDS)) != 0) {
                bounds.width = w;
                bounds.height = h;
            }
        }
        boolean moved = (oldBounds.x != x) || (oldBounds.y != y);
        boolean resized = (oldBounds.width != w) || (oldBounds.height != h);
        if (!moved && !resized) {
            return;
        }
        final D delegate = getDelegate();
        if (delegate != null) {
            synchronized (getDelegateLock()) {
                delegateContainer.setBounds(0, 0, w, h);
                delegate.setBounds(delegateContainer.getBounds());
                // TODO: the following means that the delegateContainer NEVER gets validated. That's WRONG!
                delegate.validate();
            }
        }

        final Point locationInWindow = localToWindow(0, 0);
        platformComponent.setBounds(locationInWindow.x, locationInWindow.y, w,
                                    h);
        if (notify) {
            repaintOldNewBounds(oldBounds);
            if (resized) {
                handleResize(w, h, updateTarget);
            }
            if (moved) {
                handleMove(x, y, updateTarget);
            }
        }
    }

    public final Rectangle getBounds() {
        synchronized (getStateLock()) {
            // Return a copy to prevent subsequent modifications
            return bounds.getBounds();
        }
    }

    public final Rectangle getSize() {
        synchronized (getStateLock()) {
            // Return a copy to prevent subsequent modifications
            return new Rectangle(bounds.width, bounds.height);
        }
    }

    @Override
    public Point getLocationOnScreen() {
        Point windowLocation = getWindowPeer().getLocationOnScreen();
        Point locationInWindow = localToWindow(0, 0);
        return new Point(windowLocation.x + locationInWindow.x,
                windowLocation.y + locationInWindow.y);
    }

    @Override
    public void setBackground(final Color c) {
        final Color oldBg = getBackground();
        if (oldBg == c || (oldBg != null && oldBg.equals(c))) {
            return;
        }
        synchronized (getStateLock()) {
            background = c;
        }
        final D delegate = getDelegate();
        if (delegate != null) {
            synchronized (getDelegateLock()) {
                // delegate will repaint the target
                delegate.setBackground(c);
            }
        } else {
            repaintPeer();
        }
    }

    protected final Color getBackground() {
        synchronized (getStateLock()) {
            return background;
        }
    }

    @Override
    public void setForeground(final Color c) {
        final Color oldFg = getForeground();
        if (oldFg == c || (oldFg != null && oldFg.equals(c))) {
            return;
        }
        synchronized (getStateLock()) {
            foreground = c;
        }
        final D delegate = getDelegate();
        if (delegate != null) {
            synchronized (getDelegateLock()) {
                // delegate will repaint the target
                delegate.setForeground(c);
            }
        } else {
            repaintPeer();
        }
    }

    protected final Color getForeground() {
        synchronized (getStateLock()) {
            return foreground;
        }
    }

    @Override
    public void setFont(final Font f) {
        final Font oldF = getFont();
        if (oldF == f || (oldF != null && oldF.equals(f))) {
            return;
        }
        synchronized (getStateLock()) {
            font = f;
        }
        final D delegate = getDelegate();
        if (delegate != null) {
            synchronized (getDelegateLock()) {
                // delegate will repaint the target
                delegate.setFont(f);
            }
        } else {
            repaintPeer();
        }
    }

    protected final Font getFont() {
        synchronized (getStateLock()) {
            return font;
        }
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        // Borrow the metrics from the top-level window
//        return getWindowPeer().getFontMetrics(f);
        // Obtain the metrics from the offscreen window where this peer is
        // mostly drawn to.
        // TODO: check for "use platform metrics" settings
        Graphics g = getWindowPeer().getOffscreenGraphics();
        try {
            if (g != null) {
                return g.getFontMetrics(f);
            } else {
                synchronized (getDelegateLock()) {
                    return delegateContainer.getFontMetrics(f);
                }
            }
        } finally {
            if (g != null) {
                g.dispose();
            }
        }
    }

    @Override
    public void setEnabled(final boolean e) {
        boolean status = e;
        final LWComponentPeer cp = getContainerPeer();
        if (cp != null) {
            status &= cp.isEnabled();
        }
        synchronized (getStateLock()) {
            if (enabled == status) {
                return;
            }
            enabled = status;
        }

        final D delegate = getDelegate();

        if (delegate != null) {
            synchronized (getDelegateLock()) {
                delegate.setEnabled(status);
            }
        } else {
            repaintPeer();
        }
    }

    // Helper method
    public final boolean isEnabled() {
        synchronized (getStateLock()) {
            return enabled;
        }
    }

    @Override
    public void setVisible(boolean v) {
        synchronized (getStateLock()) {
            if (visible == v) {
                return;
            }
            visible = v;
        }

        final D delegate = getDelegate();

        if (delegate != null) {
            synchronized (getDelegateLock()) {
                delegate.setVisible(v);
            }
        }
        if (visible) {
            repaintPeer();
        } else {
            repaintParent(getBounds());
        }
    }

    // Helper method
    public final boolean isVisible() {
        synchronized (getStateLock()) {
            return visible;
        }
    }

    @Override
    public void paint(final Graphics g) {
        getTarget().paint(g);
    }

    @Override
    public void print(final Graphics g) {
        getTarget().print(g);
    }

    @Override
    public void reparent(ContainerPeer newContainer) {
        // TODO: not implemented
        throw new UnsupportedOperationException("ComponentPeer.reparent()");
    }

    @Override
    public boolean isReparentSupported() {
        // TODO: not implemented
        return false;
    }

    @Override
    public void setZOrder(ComponentPeer above) {
        LWContainerPeer cp = getContainerPeer();
        // Don't check containerPeer for null as it can only happen
        // for windows, but this method is overridden in
        // LWWindowPeer and doesn't call super()
        cp.setChildPeerZOrder(this, (LWComponentPeer) above);
    }

    @Override
    public void coalescePaintEvent(PaintEvent e) {
        if (!(e instanceof IgnorePaintEvent)) {
            Rectangle r = e.getUpdateRect();
            if ((r != null) && !r.isEmpty()) {
                targetPaintArea.add(r, e.getID());
            }
        }
    }

    /*
     * Should be overridden in subclasses which use complex Swing components.
     */
    @Override
    public void layout() {
        // TODO: not implemented
    }

    @Override
    public boolean isObscured() {
        // TODO: not implemented
        return false;
    }

    @Override
    public boolean canDetermineObscurity() {
        // TODO: not implemented
        return false;
    }

    /**
     * Should be overridden in subclasses to forward the request
     * to the Swing helper component, if required.
     */
    @Override
    public Dimension getPreferredSize() {
        // It looks like a default implementation for all toolkits
        return getMinimumSize();
    }

    /*
     * Should be overridden in subclasses to forward the request
     * to the Swing helper component.
     */
    @Override
    public Dimension getMinimumSize() {
        D delegate = getDelegate();

        if (delegate == null) {
            // Is it a correct default value?
            return getBounds().getSize();
        } else {
            synchronized (getDelegateLock()) {
                return delegate.getMinimumSize();
            }
        }
    }

    @Override
    public void updateCursorImmediately() {
        getLWToolkit().getCursorManager().updateCursor();
    }

    @Override
    public boolean isFocusable() {
        // Overridden in focusable subclasses like buttons
        return false;
    }

    @Override
    public boolean requestFocus(Component lightweightChild, boolean temporary,
                                boolean focusedWindowChangeAllowed, long time,
                                CausedFocusEvent.Cause cause) {
        if (LWKeyboardFocusManagerPeer.getInstance(getAppContext()).
                processSynchronousLightweightTransfer(getTarget(), lightweightChild, temporary,
                        focusedWindowChangeAllowed, time)) {
            return true;
        }

        int result = LWKeyboardFocusManagerPeer.getInstance(getAppContext()).
                shouldNativelyFocusHeavyweight(getTarget(), lightweightChild, temporary,
                        focusedWindowChangeAllowed, time, cause);
        switch (result) {
            case LWKeyboardFocusManagerPeer.SNFH_FAILURE:
                return false;
            case LWKeyboardFocusManagerPeer.SNFH_SUCCESS_PROCEED:
                Window parentWindow = SunToolkit.getContainingWindow(getTarget());
                if (parentWindow == null) {
                    LWKeyboardFocusManagerPeer.removeLastFocusRequest(getTarget());
                    return false;
                }
                LWWindowPeer parentPeer = (LWWindowPeer) parentWindow.getPeer();
                if (parentPeer == null) {
                    LWKeyboardFocusManagerPeer.removeLastFocusRequest(getTarget());
                    return false;
                }

                boolean res = parentPeer.requestWindowFocus(cause);
                // If parent window can be made focused and has been made focused (synchronously)
                // then we can proceed with children, otherwise we retreat
                if (!res || !parentWindow.isFocused()) {
                    LWKeyboardFocusManagerPeer.removeLastFocusRequest(getTarget());
                    return false;
                }

                LWComponentPeer focusOwnerPeer =
                    LWKeyboardFocusManagerPeer.getInstance(getAppContext()).
                        getFocusOwner();
                Component focusOwner = (focusOwnerPeer != null) ? focusOwnerPeer.getTarget() : null;
                return LWKeyboardFocusManagerPeer.deliverFocus(lightweightChild,
                        getTarget(), temporary,
                        focusedWindowChangeAllowed,
                        time, cause, focusOwner);
            case LWKeyboardFocusManagerPeer.SNFH_SUCCESS_HANDLED:
                return true;
        }

        return false;
    }

    @Override
    public Image createImage(ImageProducer producer) {
        return new ToolkitImage(producer);
    }

    @Override
    public Image createImage(int w, int h) {
        // TODO: accelerated image
        return getGraphicsConfiguration().createCompatibleImage(w, h);
    }

    @Override
    public VolatileImage createVolatileImage(int w, int h) {
        // TODO: is it a right/complete implementation?
        return new SunVolatileImage(getTarget(), w, h);
    }

    @Override
    public boolean prepareImage(Image img, int w, int h, ImageObserver o) {
        // TODO: is it a right/complete implementation?
        return getToolkit().prepareImage(img, w, h, o);
    }

    @Override
    public int checkImage(Image img, int w, int h, ImageObserver o) {
        // TODO: is it a right/complete implementation?
        return getToolkit().checkImage(img, w, h, o);
    }

    @Override
    public boolean handlesWheelScrolling() {
        // TODO: not implemented
        return false;
    }

    @Override
    public final void applyShape(final Region shape) {
        synchronized (getStateLock()) {
            region = shape;
        }
        repaintParent(getBounds());
    }

    protected final Region getRegion() {
        synchronized (getStateLock()) {
            return region == null ? Region.getInstance(getSize()) : region;
        }
    }

    // DropTargetPeer Method
    @Override
    public void addDropTarget(DropTarget dt) {
        synchronized (dropTargetLock){
            // 10-14-02 VL: Windows WComponentPeer would add (or remove) the drop target only
            // if it's the first (or last) one for the component. Otherwise this call is a no-op.
            if (++fNumDropTargets == 1) {
                // Having a non-null drop target would be an error but let's check just in case:
                if (fDropTarget != null)
                    System.err.println("CComponent.addDropTarget(): current drop target is non-null.");

                // Create a new drop target:
                fDropTarget = CDropTarget.createDropTarget(dt, target, this);
            }
        }
    }

    // DropTargetPeer Method
    @Override
    public void removeDropTarget(DropTarget dt) {
        synchronized (dropTargetLock){
            // 10-14-02 VL: Windows WComponentPeer would add (or remove) the drop target only
            // if it's the first (or last) one for the component. Otherwise this call is a no-op.
            if (--fNumDropTargets == 0) {
                // Having a null drop target would be an error but let's check just in case:
                if (fDropTarget != null) {
                    // Dispose of the drop target:
                    fDropTarget.dispose();
                    fDropTarget = null;
                } else
                    System.err.println("CComponent.removeDropTarget(): current drop target is null.");
            }
        }
    }

    // ---- PEER NOTIFICATIONS ---- //

    /**
     * Called when this peer's location has been changed either as a result
     * of target.setLocation() or as a result of user actions (window is
     * dragged with mouse).
     *
     * To be overridden in LWWindowPeer to update its GraphicsConfig.
     *
     * This method could be called on the toolkit thread.
     */
    protected final void handleMove(final int x, final int y,
                                    final boolean updateTarget) {
        if (updateTarget) {
            AWTAccessor.getComponentAccessor().setLocation(getTarget(), x, y);
        }
        postEvent(new ComponentEvent(getTarget(),
                                     ComponentEvent.COMPONENT_MOVED));
    }

    /**
     * Called when this peer's size has been changed either as a result of
     * target.setSize() or as a result of user actions (window is resized).
     *
     * To be overridden in LWWindowPeer to update its SurfaceData and
     * GraphicsConfig.
     *
     * This method could be called on the toolkit thread.
     */
    protected final void handleResize(final int w, final int h,
                                      final boolean updateTarget) {
        if (updateTarget) {
            AWTAccessor.getComponentAccessor().setSize(getTarget(), w, h);
        }
        postEvent(new ComponentEvent(getTarget(),
                                     ComponentEvent.COMPONENT_RESIZED));
    }

    protected final void repaintOldNewBounds(final Rectangle oldB) {
        repaintParent(oldB);
        repaintPeer(getSize());
    }

    protected final void repaintParent(final Rectangle oldB) {
        final LWContainerPeer cp = getContainerPeer();
        if (cp != null) {
            // Repaint unobscured part of the parent
            cp.repaintPeer(cp.getContentSize().intersection(oldB));
        }
    }

    // ---- EVENTS ---- //

    /**
     * Post an event to the proper Java EDT.
     */
    public void postEvent(AWTEvent event) {
        SunToolkit.postEvent(getAppContext(), event);
    }

    protected void postPaintEvent(int x, int y, int w, int h) {
        // TODO: call getIgnoreRepaint() directly with the right ACC
        if (AWTAccessor.getComponentAccessor().getIgnoreRepaint(target)) {
            return;
        }
        PaintEvent event = PaintEventDispatcher.getPaintEventDispatcher().
                createPaintEvent(getTarget(), x, y, w, h);
        if (event != null) {
            postEvent(event);
        }
    }

    /*
     * Gives a chance for the peer to handle the event after it's been
     * processed by the target.
     */
    @Override
    public void handleEvent(AWTEvent e) {
        if ((e instanceof InputEvent) && ((InputEvent) e).isConsumed()) {
            return;
        }
        switch (e.getID()) {
            case FocusEvent.FOCUS_GAINED:
            case FocusEvent.FOCUS_LOST:
                handleJavaFocusEvent((FocusEvent) e);
                break;
            case PaintEvent.PAINT:
                // Got a native paint event
//                paintPending = false;
                // fall through to the next statement
            case PaintEvent.UPDATE:
                handleJavaPaintEvent();
                break;
            case MouseEvent.MOUSE_PRESSED:
                handleJavaMouseEvent((MouseEvent)e);
        }

        sendEventToDelegate(e);
    }

    private void sendEventToDelegate(final AWTEvent e) {
        synchronized (getDelegateLock()) {
            if (getDelegate() == null || !isShowing() || !isEnabled()) {
                return;
            }
            AWTEvent delegateEvent = createDelegateEvent(e);
            if (delegateEvent != null) {
                AWTAccessor.getComponentAccessor()
                        .processEvent((Component) delegateEvent.getSource(),
                                delegateEvent);
                if (delegateEvent instanceof KeyEvent) {
                    KeyEvent ke = (KeyEvent) delegateEvent;
                    SwingUtilities.processKeyBindings(ke);
                }
            }
        }
    }

    protected AWTEvent createDelegateEvent(AWTEvent e) {
        AWTEvent delegateEvent = null;
        if (e instanceof MouseWheelEvent) {
            MouseWheelEvent me = (MouseWheelEvent) e;
            delegateEvent = new MouseWheelEvent(
                    delegate, me.getID(), me.getWhen(),
                    me.getModifiers(),
                    me.getX(), me.getY(),
                    me.getClickCount(),
                    me.isPopupTrigger(),
                    me.getScrollType(),
                    me.getScrollAmount(),
                    me.getWheelRotation());
        } else if (e instanceof MouseEvent) {
            MouseEvent me = (MouseEvent) e;

            Component eventTarget = SwingUtilities.getDeepestComponentAt(delegate, me.getX(), me.getY());

            if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
                if (delegateDropTarget == null) {
                    delegateDropTarget = eventTarget;
                } else {
                    eventTarget = delegateDropTarget;
                }
            }
            if (me.getID() == MouseEvent.MOUSE_RELEASED && delegateDropTarget != null) {
                eventTarget = delegateDropTarget;
                delegateDropTarget = null;
            }
            if (eventTarget == null) {
                eventTarget = delegate;
            }
            delegateEvent = SwingUtilities.convertMouseEvent(getTarget(), me, eventTarget);
        } else if (e instanceof KeyEvent) {
            KeyEvent ke = (KeyEvent) e;
            delegateEvent = new KeyEvent(getDelegateFocusOwner(), ke.getID(), ke.getWhen(),
                    ke.getModifiers(), ke.getKeyCode(), ke.getKeyChar(), ke.getKeyLocation());
        } else if (e instanceof FocusEvent) {
            FocusEvent fe = (FocusEvent) e;
            delegateEvent = new FocusEvent(getDelegateFocusOwner(), fe.getID(), fe.isTemporary());
        }
        return delegateEvent;
    }

    protected void handleJavaMouseEvent(MouseEvent e) {
        Component target = getTarget();
        assert (e.getSource() == target);

        if (!target.isFocusOwner() && LWKeyboardFocusManagerPeer.shouldFocusOnClick(target)) {
            LWKeyboardFocusManagerPeer.requestFocusFor(target, CausedFocusEvent.Cause.MOUSE_EVENT);
        } else {
            // Anyway request focus to the toplevel.
            getWindowPeerOrSelf().requestWindowFocus(CausedFocusEvent.Cause.MOUSE_EVENT);
        }
    }

    /**
     * Handler for FocusEvents.
     */
    protected void handleJavaFocusEvent(FocusEvent e) {
        // Note that the peer receives all the FocusEvents from
        // its lightweight children as well
        LWKeyboardFocusManagerPeer.getInstance(getAppContext()).
                setFocusOwner(e.getID() == FocusEvent.FOCUS_GAINED ? this : null);
    }

    /**
     * All peers should clear background before paint.
     *
     * @return false on components that DO NOT require a clearRect() before
     *         painting.
     */
    protected final boolean shouldClearRectBeforePaint() {
        // TODO: sun.awt.noerasebackground
        return true;
    }

    /**
     * Handler for PAINT and UPDATE PaintEvents.
     */
    private void handleJavaPaintEvent() {
        // Skip all painting while layouting and all UPDATEs
        // while waiting for native paint
//        if (!isLayouting && !paintPending) {
        if (!isLayouting()) {
            targetPaintArea.paint(getTarget(), shouldClearRectBeforePaint());
        }
    }

    // ---- UTILITY METHODS ---- //

    /**
     * Finds a top-most visible component for the given point. The location is
     * specified relative to the peer's parent.
     */
    public LWComponentPeer findPeerAt(final int x, final int y) {
        final Rectangle r = getBounds();
        final Region sh = getRegion();
        final boolean found = isVisible() && sh.contains(x - r.x, y - r.y);
        return found ? this : null;
    }

    /*
     * Translated the given point in Window coordinates to the point in
     * coordinates local to this component. The given window peer must be
     * the window where this component is in.
     */
    public Point windowToLocal(int x, int y, LWWindowPeer wp) {
        return windowToLocal(new Point(x, y), wp);
    }

    public Point windowToLocal(Point p, LWWindowPeer wp) {
        LWComponentPeer cp = this;
        while (cp != wp) {
            Rectangle cpb = cp.getBounds();
            p.x -= cpb.x;
            p.y -= cpb.y;
            cp = cp.getContainerPeer();
        }
        // Return a copy to prevent subsequent modifications
        return new Point(p);
    }

    public Rectangle windowToLocal(Rectangle r, LWWindowPeer wp) {
        Point p = windowToLocal(r.getLocation(), wp);
        return new Rectangle(p, r.getSize());
    }

    public Point localToWindow(int x, int y) {
        return localToWindow(new Point(x, y));
    }

    public Point localToWindow(Point p) {
        LWComponentPeer cp = getContainerPeer();
        Rectangle r = getBounds();
        while (cp != null) {
            p.x += r.x;
            p.y += r.y;
            r = cp.getBounds();
            cp = cp.getContainerPeer();
        }
        // Return a copy to prevent subsequent modifications
        return new Point(p);
    }

    public Rectangle localToWindow(Rectangle r) {
        Point p = localToWindow(r.getLocation());
        return new Rectangle(p, r.getSize());
    }

    public final void repaintPeer() {
        repaintPeer(getSize());
    }

    public void repaintPeer(final Rectangle r) {
        final Rectangle toPaint = getSize().intersection(r);
        if (!isShowing() || toPaint.isEmpty()) {
            return;
        }

        postPaintEvent(toPaint.x, toPaint.y, toPaint.width, toPaint.height);
    }

    /**
     * Determines whether this peer is showing on screen. This means that the
     * peer must be visible, and it must be in a container that is visible and
     * showing.
     *
     * @see #isVisible()
     */
    protected boolean isShowing() {
        synchronized (getPeerTreeLock()) {
            if (isVisible()) {
                final LWContainerPeer container = getContainerPeer();
                return (container == null) || container.isShowing();
            }
        }
        return false;
    }

    /**
     * Paints the peer. Overridden in subclasses to delegate the actual painting
     * to Swing components.
     */
    protected final void paintPeer(final Graphics g) {
        final D delegate = getDelegate();
        if (delegate != null) {
            if (!SwingUtilities.isEventDispatchThread()) {
                throw new InternalError("Painting must be done on EDT");
            }
            synchronized (getDelegateLock()) {
                // JComponent.print() is guaranteed to not affect the double buffer
                getDelegate().print(g);
            }
        }
    }

    // Just a helper method, thus final
    protected final void flushOffscreenGraphics() {
        flushOffscreenGraphics(getSize());
    }

    protected static final void flushOnscreenGraphics(){
        final OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.lock();
        try {
            rq.flushNow();
        } finally {
            rq.unlock();
        }
    }

    /*
     * Flushes the given rectangle from the back buffer to the screen.
     */
    protected void flushOffscreenGraphics(Rectangle r) {
        flushOffscreenGraphics(r.x, r.y, r.width, r.height);
    }

    private void flushOffscreenGraphics(int x, int y, int width, int height) {
        Image bb = getWindowPeerOrSelf().getBackBuffer();
        if (bb != null) {
            // g is a screen Graphics from the delegate
            final Graphics g = getOnscreenGraphics();

            if (g != null && g instanceof Graphics2D) {
                try {
                    Graphics2D g2d = (Graphics2D)g;
                    Point p = localToWindow(new Point(0, 0));
                    Composite composite = g2d.getComposite();
                    g2d.setComposite(AlphaComposite.Src);
                    g.drawImage(bb, x, y, x + width, y + height, p.x + x,
                            p.y + y, p.x + x + width, p.y + y + height,
                            null);
                    g2d.setComposite(composite);
                } finally {
                    g.dispose();
                }
            }
        }
    }

    /**
     * Used by ContainerPeer to skip all the paint events during layout.
     *
     * @param isLayouting layouting state.
     */
    protected final void setLayouting(final boolean isLayouting) {
        this.isLayouting = isLayouting;
    }

    /**
     * Returns layouting state. Used by ComponentPeer to skip all the paint
     * events during layout.
     *
     * @return true during layout, false otherwise.
     */
    private final boolean isLayouting() {
        return isLayouting;
    }
}
