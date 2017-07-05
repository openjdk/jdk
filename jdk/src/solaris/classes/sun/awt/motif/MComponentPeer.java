/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.motif;

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.PaintEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;

import sun.awt.*;
import sun.awt.image.ToolkitImage;
import sun.awt.image.SunVolatileImage;
import java.awt.image.ImageProducer;
import java.awt.image.ImageObserver;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;

import java.awt.dnd.DropTarget;
import java.awt.dnd.peer.DropTargetPeer;

import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;

import java.lang.reflect.Method;

import java.util.logging.*;

import sun.java2d.pipe.Region;


public /* REMIND: should not be public */
abstract class MComponentPeer implements ComponentPeer, DropTargetPeer, X11ComponentPeer {

    private static final Logger log = Logger.getLogger("sun.awt.motif.MComponentPeer");
    private static final Logger focusLog = Logger.getLogger("sun.awt.motif.focus.MComponentPeer");

    Component   target;
    long        pData;
    long        jniGlobalRef;
    protected X11GraphicsConfig graphicsConfig;
    SurfaceData surfaceData;
    int         oldWidth = -1;
    int         oldHeight = -1;

    private RepaintArea paintArea;

    boolean isLayouting = false;
    boolean paintPending = false;

    protected boolean disposed = false;
    private static int JAWT_LOCK_ERROR=0x00000001;
    private static int JAWT_LOCK_CLIP_CHANGED=0x00000002;
    private static int JAWT_LOCK_BOUNDS_CHANGED=0x00000004;
    private static int JAWT_LOCK_SURFACE_CHANGED=0x00000008;
    private int drawState = JAWT_LOCK_CLIP_CHANGED |
    JAWT_LOCK_BOUNDS_CHANGED |
    JAWT_LOCK_SURFACE_CHANGED;

    /* These are the enumerated types in awt_p.h*/
    static final int MOTIF_NA = 0 ;
    static final int MOTIF_V1 = 1 ;
    static final int MOTIF_V2 = 2 ;

    private Font font;
    private long backBuffer = 0;
    private VolatileImage xBackBuffer = null;

    static {
        initIDs();
    }

    /* initialize the fieldIDs of fields that may be accessed from C */
    private native static void initIDs();


    /* This will return the last state of a window. ie the specific
     * "gotcha" is that if you iconify a window its obscurity remains
     * unchanged. Current use of this is just in user-initiated scrolling.
     * If that use expands to more cases you may need to "and" this with
     * the value of the iconic state of a Frame.
     * Note that de-iconifying an X11 window DOES generate a new event
     * correctly notifying you of the new visibility of the window
     */
    public boolean isObscured() {

        Container container  = (target instanceof Container) ?
            (Container)target : target.getParent();

        if (container == null) {
            return true;
        }

        Container parent;
        while ((parent = container.getParent()) != null) {
            container = parent;
        }

        if (container instanceof Window) {
            MWindowPeer wpeer = (MWindowPeer)(container.getPeer());
            if (wpeer != null) {
                return (wpeer.winAttr.visibilityState !=
                        MWindowAttributes.AWT_UNOBSCURED);
            }
        }
        return true;
    }

    public boolean canDetermineObscurity() {
        return true;
    }

    abstract void create(MComponentPeer parent);
    void create(MComponentPeer parent, Object arg) {
        create(parent);
    }

    void EFcreate(MComponentPeer parent, int x){}

    native void pInitialize();
    native void pShow();
    native void pHide();
    native void pEnable();
    native void pDisable();
    native void pReshape(int x, int y, int width, int height);
    native void pDispose();
    native void pMakeCursorVisible();
    native Point pGetLocationOnScreen();
    native Point pGetLocationOnScreen2(Window win, MWindowPeer wpeer);
    native void pSetForeground(Color c);
    native void pSetBackground(Color c);
    private native void pSetFont(Font f);

    //Added for bug 4175560
    //Returns the native representation for the Color argument,
    //using the given GraphicsConfiguration.
    native int getNativeColor(Color clr, GraphicsConfiguration gc);

    // Returns the parent of the component, without invoking client
    // code. This must go through native code, because it invokes
    // private methods in the java.awt package, which we cannot
    // do from this package.
    static native Container getParent_NoClientCode(Component component);

    // Returns the parent of the component, without invoking client
    // code. This must go through native code, because it invokes
    // private methods in the java.awt package, which we cannot
    // do from this package.
    static native Component[] getComponents_NoClientCode(Container container);

    void initialize() {
        if (!target.isVisible()) {
            hide();
        }
        Color c;
        Font  f;
        Cursor cursor;

        pInitialize();

        if ((c = target.getForeground()) != null) {
            setForeground(c);
        }
        if ((c = target.getBackground()) != null) {
            setBackground(c);
        }
        if ((f = target.getFont()) != null) {
            setFont(f);
        }
        pSetCursor(target.getCursor());
        if (!target.isEnabled()) {
            disable();
        }
        Rectangle r = target.getBounds();
        reshape(r.x, r.y, r.width, r.height);
        if (target.isVisible()) {
            show();
        }

        surfaceData = graphicsConfig.createSurfaceData(this);
    }

    public void init(Component target, Object arg) {
        this.target = target;
        this.paintArea = new RepaintArea();

        Container parent = MToolkit.getNativeContainer(target);
        MComponentPeer parentPeer = (MComponentPeer) MToolkit.targetToPeer(parent);
        create(parentPeer, arg);

        initialize();
    }

    MComponentPeer(Component target, Object arg) {
        init(target, arg);
    }

    MComponentPeer() {}

    public void init(Component target) {
        this.target = target;
        this.paintArea = new RepaintArea();

        Container parent = MToolkit.getNativeContainer(target);
        MComponentPeer parentPeer = (MComponentPeer) MToolkit.targetToPeer(parent);
        create(parentPeer);

        if (parent != null && parent instanceof ScrollPane) {
            MScrollPanePeer speer = (MScrollPanePeer) parentPeer;
            speer.setScrollChild(this);
        }
        initialize();
    }

    MComponentPeer(Component target) {
        init(target);
    }

    protected void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    public void setForeground(Color c) {
        pSetForeground(c);
    }

    public void setBackground(Color c) {
        pSetBackground(c);
    }

    public void updateCursorImmediately() {
        MGlobalCursorManager.getCursorManager().updateCursorImmediately();
    }

    public void setFont(Font f) {
        ComponentPeer peer;
        if (f == null) {
            f = defaultFont;
        }
        pSetFont(f);
        if ( target instanceof Container ) {
            Container container = (Container) target;
            int count = container.getComponentCount();
            Component[] children = container.getComponents();
            for (int i=0; i<count; i++) {
                if ( children[i] != null ) {
/*
** note: recursion in the widget in pSetFont() has by now broken any
**       children with different Fonts - so fix now:
*/
                    peer = children[i].getPeer();
                    if (peer != null) {
                        Font rightFont = children[i].getFont();
                        if (!f.equals(rightFont)) {
                            peer.setFont(rightFont);
                        } else
                            if (children[i] instanceof Container) {
                                peer.setFont(f);
                            }
                    }
                }
            }
        }

        /*
         * Keep a reference to the java.awt.Font object in order to
         * preserve the XFontStructs which underlying widgets are using.
         * Save this AFTER changing the widgets in order to keep the
         * previous reference (if any) alive.
         */
        font = f;
    }


    public native void setTargetBackground(Color c);
    public native void pSetCursor(Cursor c);
    public native void pSetScrollbarBackground(Color c);
    public native void pSetInnerForeground(Color c);

    public boolean isFocusable() {
        return false;
    }

    public SurfaceData getSurfaceData() {
        return surfaceData;
    }

    public ColorModel getColorModel() {
        return graphicsConfig.getColorModel();
    }

    public ColorModel getColorModel(int transparency) {
        return graphicsConfig.getColorModel(transparency);
    }

    public int updatePriority() {
        return Thread.NORM_PRIORITY;
    }

    public void repaint(long tm, int x, int y, int width, int height) {
    }

    public void paint(Graphics g) {
        Dimension d = target.getSize();
        if (g instanceof Graphics2D ||
            g instanceof sun.awt.Graphics2Delegate) {
            // background color is setup correctly, so just use clearRect
            g.clearRect(0, 0, d.width, d.height);
        } else {
            // emulate clearRect
            g.setColor(target.getBackground());
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(target.getForeground());
        }

        target.paint(g);
    }
    public void print(Graphics g) {
        Dimension d = target.getSize();
        if (g instanceof Graphics2D ||
            g instanceof sun.awt.Graphics2Delegate) {
            // background color is setup correctly, so just use clearRect
            g.clearRect(0, 0, d.width, d.height);
        } else {
            // emulate clearRect
            g.setColor(target.getBackground());
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(target.getForeground());
        }

        target.print(g);
    }

    public void coalescePaintEvent(PaintEvent e) {
        Rectangle r = e.getUpdateRect();
        paintArea.add(r, e.getID());

        if (log.isLoggable(Level.FINEST)) {
            switch(e.getID()) {
              case PaintEvent.UPDATE:
                  log.log(Level.FINEST, "coalescePaintEvent: UPDATE: add: x = " +
                          r.x + ", y = " + r.y + ", width = " + r.width + ", height = " + r.height);
                  return;
              case PaintEvent.PAINT:
                  log.log(Level.FINEST, "coalescePaintEvent: PAINT: add: x = " +
                          r.x + ", y = " + r.y + ", width = " + r.width + ", height = " + r.height);
                  return;
            }
        }
    }

    native void nativeHandleEvent(AWTEvent e);

    /**
     * Returns whether or not this component should be given focus on mouse click.
     * Default implementation return whether or not this peer is "focusable"
     * Descendants might want to override it to extend/restrict conditions at which this
     * component should be focused by click (see MCanvasPeer and MPanelPeer)
     */
    protected boolean shouldFocusOnClick() {
        return isFocusable();
    }

    /**
     * Checks whether or not this component would be focused by native system if it would be allowed to do so.
     * Currently it checks that it displayable, visible, enabled and focusable.
     */
    static boolean canBeFocusedByClick(Component component) {
        if (component == null) {
            return false;
        } else {
            return component.isDisplayable() && component.isVisible() && component.isEnabled() && component.isFocusable();
        }
    }

    static Method requestFocusWithCause;

    static void callRequestFocusInWindow(Component target, CausedFocusEvent.Cause cause) {
        if (requestFocusWithCause == null) {
            requestFocusWithCause = SunToolkit.getMethod(Component.class, "requestFocusInWindow", new Class[] {CausedFocusEvent.Cause.class});
        }
        if (requestFocusWithCause != null) {
            try {
                requestFocusWithCause.invoke(target, new Object[] {cause});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void handleEvent(AWTEvent e) {
        int id = e.getID();

        switch(id) {
          case PaintEvent.PAINT:
              // Got native painting
              paintPending = false;
              // Fallthrough to next statement
          case PaintEvent.UPDATE:
              // Skip all painting while layouting and all UPDATEs
              // while waiting for native paint
              if (!isLayouting && !paintPending) {
                  paintArea.paint(target,false);
              }
              return;
          case MouseEvent.MOUSE_PRESSED:
              if (target == e.getSource() && !((InputEvent)e).isConsumed() && shouldFocusOnClick()
                  && !target.isFocusOwner() && canBeFocusedByClick(target))
              {
                  callRequestFocusInWindow(target, CausedFocusEvent.Cause.MOUSE_EVENT);
              }
              break;
          default:
              break;
        }

        // Call the native code
        nativeHandleEvent(e);
    }

    /* New API for 1.1 */
    public Dimension getMinimumSize() {
        return target.getSize();
    }

    /* New API for 1.1 */
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

    // Do nothing for heavyweight implementation
    public void layout() {}

    public Rectangle getBounds() {
        return ((Component)target).getBounds();
    }

    public Object getTarget() {
        return target;
    }

    public java.awt.Toolkit getToolkit() {
        // XXX: bogus
        return Toolkit.getDefaultToolkit();
    }

    // fallback default font object
    final static Font defaultFont = new Font(Font.DIALOG, Font.PLAIN, 12);

    public synchronized Graphics getGraphics() {
        if (!disposed) {
            Component target = (Component) this.target;

            /* Fix for bug 4746122. Color and Font shouldn't be null */
            Color bgColor = target.getBackground();
            if (bgColor == null) {
                bgColor = SystemColor.window;
            }
            Color fgColor = target.getForeground();
            if (fgColor == null) {
                fgColor = SystemColor.windowText;
            }
            Font font = target.getFont();
            if (font == null) {
                font = defaultFont;
            }
            return new SunGraphics2D(surfaceData, fgColor, bgColor, font);
        }

        return null;
    }

    public Image createImage(ImageProducer producer) {
        return new ToolkitImage(producer);
    }

    public Image createImage(int width, int height) {
        return graphicsConfig.createAcceleratedImage(target, width, height);
    }

    public VolatileImage createVolatileImage(int width, int height) {
        return new SunVolatileImage(target, width, height);
    }

    public boolean prepareImage(Image img, int w, int h, ImageObserver o) {
        return getToolkit().prepareImage(img, w, h, o);
    }

    public int checkImage(Image img, int w, int h, ImageObserver o) {
        return getToolkit().checkImage(img, w, h, o);
    }

    public FontMetrics getFontMetrics(Font font) {
        return X11FontMetrics.getFontMetrics(font);
    }

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    protected void disposeImpl() {
        SurfaceData oldData = surfaceData;
        surfaceData = null;
        oldData.invalidate();
        MToolkit.targetDisposedPeer(target, this);
        pDispose();
    }
    public final void dispose() {
        boolean call_disposeImpl = false;

        if (!disposed) {
            synchronized (this) {
                SunToolkit.awtLock();
                try {
                    if (!disposed) {
                        disposed = call_disposeImpl = true;
                    }
                } finally {
                    SunToolkit.awtUnlock();
                }
            }
        }

        if (call_disposeImpl) {
            disposeImpl();
        }
    }

    native static boolean processSynchronousLightweightTransfer(Component heavyweight, Component descendant,
                                                                boolean temporary, boolean focusedWindowChangeAllowed,
                                                                long time);
    public boolean requestFocus
    (Component lightweightChild, boolean temporary,
         boolean focusedWindowChangeAllowed, long time, CausedFocusEvent.Cause cause) {
        if (processSynchronousLightweightTransfer((Component)target, lightweightChild, temporary,
                                                  focusedWindowChangeAllowed, time)) {
            return true;
        } else {
            if (focusLog.isLoggable(Level.FINER)) {
                focusLog.log(Level.FINER, "Current native focused window " + getNativeFocusedWindow());
            }
            /**
             * The problems with requests in non-focused window arise because shouldNativelyFocusHeavyweight
             * checks that native window is focused while appropriate WINDOW_GAINED_FOCUS has not yet
             * been processed - it is in EventQueue. Thus, SNFH allows native request and stores request record
             * in requests list - and it breaks our requests sequence as first record on WGF should be the last focus
             * owner which had focus before WLF. So, we should not add request record for such requests
             * but store this component in mostRecent - and return true as before for compatibility.
             */
            Container parent = (target instanceof Container) ? ((Container)target) : (target.getParent());
            // Search for parent window
            while (parent != null && !(parent instanceof Window)) {
                parent = getParent_NoClientCode(parent);
            }
            if (parent != null) {
                Window parentWindow = (Window)parent;
                // and check that it is focused
                if (focusLog.isLoggable(Level.FINER)) {
                    focusLog.log(Level.FINER, "Parent window " + parentWindow);
                }
                if (!parentWindow.isFocused() && getNativeFocusedWindow() == parentWindow) {
                    // if it is not - skip requesting focus on Solaris
                    // but return true for compatibility.
                    return true;
                } else if (getNativeFocusedWindow() != parentWindow) {
                    WindowPeer wpeer = (WindowPeer)parentWindow.getPeer();
                    boolean res = wpeer.requestWindowFocus();
                    if (focusLog.isLoggable(Level.FINER)) {
                        focusLog.log(Level.FINER, "Requested window focus: " + res);
                    }
                    // If parent window can be made focused and has been made focused(synchronously)
                    // then we can proceed with children, otherwise we retreat.
                    if (!(res && parentWindow.isFocused())) {
                        focusLog.finer("Waiting for asynchronous processing of window focus request");
                        KeyboardFocusManagerPeerImpl.removeLastFocusRequest(target);
                        return false;
                    }
                }
            }
            return _requestFocus(lightweightChild, temporary, focusedWindowChangeAllowed, time, cause);
        }
    }

    native boolean _requestFocus
        (Component lightweightChild, boolean temporary,
         boolean focusedWindowChangeAllowed, long time, CausedFocusEvent.Cause cause);

    static native Window getNativeFocusedWindow();

    /*
     * Post an event to the event queue.
     */
    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void postEvent(AWTEvent event) {
        MToolkit.postEvent(MToolkit.targetToAppContext(target), event);
    }

    /* Callbacks for window-system events to the frame
     *
     * NOTE: This method may be called by privileged threads.
     *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
     */
    void handleExpose(int x, int y, int w, int h) {
        if ( !ComponentAccessor.getIgnoreRepaint(target) ) {
            postEvent(new PaintEvent(target, PaintEvent.PAINT,
                                     new Rectangle(x, y, w, h)));
        }
    }

    /* Callbacks for window-system events to the frame
     *
     * NOTE: This method may be called by privileged threads.
     *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
     */
    void handleRepaint(int x, int y, int w, int h) {
        if ( !ComponentAccessor.getIgnoreRepaint(target) ) {
            postEvent(new PaintEvent(target, PaintEvent.UPDATE,
                                     new Rectangle(x, y, w, h)));
        }
    }

    /* Return the component's z-order position relative to
     * other peer'd siblings (don't count lightweight siblings
     * or siblings who don't yet have valid peers).
     *
     * NOTE: This method may be called by privileged threads.
     *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
     */
    public int getZOrderPosition_NoClientCode() {
        // SECURITY: use _NoClientCode() methods, because we may
        //           be running on a privileged thread
        Container p = getParent_NoClientCode(target);
        if (p != null) {
            // SECURITY: use _NoClientCode() methods, because we may
            //           be running on a privileged thread
            Component children[] = getComponents_NoClientCode(p);
            int i;
            int index = 0;
            for (i = 0; i < children.length; i++) {
                if (children[i] == target) {
                    return index;
                } else {
                    Object cpeer = MToolkit.targetToPeer(children[i]);
                    if (cpeer != null &&
                        !(cpeer instanceof java.awt.peer.LightweightPeer)) {
                        index++;
                    }
                }
            }
        }
        return -1;
    }

    /*
     * drawXXX() methods are used to print the native components by
     * rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate color
     * information.
     */
    void draw3DOval(Graphics g, Color bg,
                    int x, int y, int w, int h, boolean raised) {
        Color c = g.getColor();
        Color shadow = bg.darker();
        Color highlight = bg.brighter();

        g.setColor(raised ? highlight : shadow);
        g.drawArc(x, y, w, h, 45, 180);
        g.setColor(raised ? shadow : highlight);
        g.drawArc(x, y, w, h, 225, 180);
        g.setColor(c);
    }
    void draw3DRect(Graphics g, Color bg,
                    int x, int y, int width, int height,
                    boolean raised) {
        Color c = g.getColor();
        Color shadow = bg.darker();
        Color highlight = bg.brighter();

        g.setColor(raised ? highlight : shadow);
        g.drawLine(x, y, x, y + height);
        g.drawLine(x + 1, y, x + width - 1, y);
        g.setColor(raised ? shadow : highlight);
        g.drawLine(x + 1, y + height, x + width, y + height);
        g.drawLine(x + width, y, x + width, y + height - 1);
        g.setColor(c);
    }
    void drawScrollbar(Graphics g, Color bg, int thickness, int length,
                       int min, int max, int val, int vis, boolean horizontal) {
        Color c = g.getColor();
        double f = (double)(length - 2*(thickness-1)) / Math.max(1, ((max - min) + vis));
        int v1 = thickness + (int)(f * (val - min));
        int v2 = (int)(f * vis);
        int w2 = thickness-4;
        int tpts_x[] = new int[3];
        int tpts_y[] = new int[3];

        if (length < 3*w2 ) {
            v1 = v2 = 0;
            if (length < 2*w2 + 2) {
                w2 = (length-2)/2;
            }
        } else  if (v2 < 7) {
            // enforce a minimum handle size
            v1 = Math.max(0, v1 - ((7 - v2)>>1));
            v2 = 7;
        }

        int ctr   = thickness/2;
        int sbmin = ctr - w2/2;
        int sbmax = ctr + w2/2;

        // paint the background slightly darker
        {
            Color d = new Color((int) (bg.getRed()   * 0.85),
                                (int) (bg.getGreen() * 0.85),
                                (int) (bg.getBlue()  * 0.85));

            g.setColor(d);
            if (horizontal) {
                g.fillRect(0, 0, length, thickness);
            } else {
                g.fillRect(0, 0, thickness, length);
            }
        }

        // paint the thumb and arrows in the normal background color
        g.setColor(bg);
        if (v1 > 0) {
            if (horizontal) {
                g.fillRect(v1, 3, v2, thickness-3);
            } else {
                g.fillRect(3, v1, thickness-3, v2);
            }
        }

        tpts_x[0] = ctr;        tpts_y[0] = 2;
        tpts_x[1] = sbmin;      tpts_y[1] = w2;
        tpts_x[2] = sbmax;      tpts_y[2] = w2;
        if (horizontal) {
            g.fillPolygon(tpts_y, tpts_x, 3);
        } else {
            g.fillPolygon(tpts_x, tpts_y, 3);
        }

        tpts_y[0] = length-2;
        tpts_y[1] = length-w2;
        tpts_y[2] = length-w2;
        if (horizontal) {
            g.fillPolygon(tpts_y, tpts_x, 3);
        } else {
            g.fillPolygon(tpts_x, tpts_y, 3);
        }

        Color highlight = bg.brighter();

        // // // // draw the "highlighted" edges
        g.setColor(highlight);

        // outline & arrows
        if (horizontal) {
            g.drawLine(1, thickness, length - 1, thickness);
            g.drawLine(length - 1, 1, length - 1, thickness);

            // arrows
            g.drawLine(1, ctr, w2, sbmin);
            g.drawLine(length - w2, sbmin, length - w2, sbmax);
            g.drawLine(length - w2, sbmin, length - 2, ctr);

        } else {
            g.drawLine(thickness, 1, thickness, length - 1);
            g.drawLine(1, length - 1, thickness, length - 1);

            // arrows
            g.drawLine(ctr, 1, sbmin, w2);
            g.drawLine(sbmin, length - w2, sbmax, length - w2);
            g.drawLine(sbmin, length - w2, ctr, length - 2);
        }

        // thumb
        if (v1 > 0) {
            if (horizontal) {
                g.drawLine(v1, 2, v1 + v2, 2);
                g.drawLine(v1, 2, v1, thickness-3);
            } else {
                g.drawLine(2, v1, 2, v1 + v2);
                g.drawLine(2, v1, thickness-3, v1);
            }
        }

        Color shadow = bg.darker();

        // // // // draw the "shadowed" edges
        g.setColor(shadow);

        // outline && arrows
        if (horizontal) {
            g.drawLine(0, 0, 0, thickness);
            g.drawLine(0, 0, length - 1, 0);

            // arrows
            g.drawLine(w2, sbmin, w2, sbmax);
            g.drawLine(w2, sbmax, 1, ctr);
            g.drawLine(length-2, ctr, length-w2, sbmax);

        } else {
            g.drawLine(0, 0, thickness, 0);
            g.drawLine(0, 0, 0, length - 1);

            // arrows
            g.drawLine(sbmin, w2, sbmax, w2);
            g.drawLine(sbmax, w2, ctr, 1);
            g.drawLine(ctr, length-2, sbmax, length-w2);
        }

        // thumb
        if (v1 > 0) {
            if (horizontal) {
                g.drawLine(v1 + v2, 2, v1 + v2, thickness-2);
                g.drawLine(v1, thickness-2, v1 + v2, thickness-2);
            } else {
                g.drawLine(2, v1 + v2, thickness-2, v1 + v2);
                g.drawLine(thickness-2, v1, thickness-2, v1 + v2);
            }
        }
        g.setColor(c);
    }

    public String toString() {
        return getClass().getName() + "[" + target + "]";
    }

    /* New 1.1 API */
    public void setVisible(boolean b) {
        if (b) {
            Dimension s = target.getSize();
            oldWidth = s.width;
            oldHeight = s.height;
            pShow();
        } else {
            pHide();
        }
    }

    /* New 1.1 API */
    public void setEnabled(boolean b) {
        if (b) {
            pEnable();
        } else {
            pDisable();
        }
    }

    /* New 1.1 API */
    public Point getLocationOnScreen() {
        synchronized (target.getTreeLock()) {
            Component comp = target;
            while (comp != null && !(comp instanceof Window)) {
                comp = getParent_NoClientCode(comp);
            }

            // applets, embedded, etc - translate directly
            if (comp == null || comp instanceof sun.awt.EmbeddedFrame) {
                return pGetLocationOnScreen();
            }

            MWindowPeer wpeer = (MWindowPeer)(MToolkit.targetToPeer(comp));
            if (wpeer == null) {
                return pGetLocationOnScreen();
            }
            return pGetLocationOnScreen2((Window)comp, wpeer);
        }
    }

    public int serialNum = 0;

    /* Returns the native paint should be posted after setting new size
     */
    public boolean checkNativePaintOnSetBounds(int width, int height) {
        return (width != oldWidth) || (height != oldHeight);
    }

    void setBounds(int x, int y, int width, int height) {
        setBounds(x, y, width, height, SET_BOUNDS);
    }

    /* New 1.1 API */
    public void setBounds(int x, int y, int width, int height, int op) {
        if (disposed) return;

        Container parent = getParent_NoClientCode(target);

        // Should set paintPending before reshape to prevent
        // thread race between PaintEvent and setBounds
        // This part of the 4267393 fix proved to be unstable under solaris,
        // dissabled due to regressions 4418155, 4486762, 4490079
        paintPending = false; //checkNativePaintOnSetBounds(width, height);

        // Note: it would be ideal to NOT execute this if it's
        // merely a Move which is occurring.
        if (parent != null && parent instanceof ScrollPane) {
            MScrollPanePeer speer = (MScrollPanePeer)parent.getPeer();
            if (!speer.ignore) {
                pReshape(x, y, width, height);
                speer.childResized(width, height);
            }
        } else {
            pReshape(x, y, width, height);
        }

        if ((width != oldWidth) || (height != oldHeight)) {
            SurfaceData oldData = surfaceData;
            if (oldData != null) {
                surfaceData = graphicsConfig.createSurfaceData(this);
                oldData.invalidate();
            }
            oldWidth = width;
            oldHeight = height;
        }
        validateSurface(width, height);
        serialNum++;
    }

    void validateSurface(int width, int height) {
        SunToolkit.awtLock();
        try {
            if (!disposed && (width != oldWidth || height != oldHeight)) {
                SurfaceData oldData = surfaceData;
                if (oldData != null) {
                    surfaceData = graphicsConfig.createSurfaceData(this);
                    oldData.invalidate();
                }
                oldWidth = width;
                oldHeight = height;
            }
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    public void beginValidate() {
    }

    native void restoreFocus();

    public void endValidate() {
        restoreFocus();
    }

    public void beginLayout() {
        // Skip all painting till endLayout
        isLayouting = true;
    }

    public void endLayout() {
        if (!paintPending && !paintArea.isEmpty() &&
            !((Component)target).getIgnoreRepaint()) {
            // if not waiting for native painting repaint damaged area
            postEvent(new PaintEvent((Component)target, PaintEvent.PAINT,
                                     new Rectangle()));
        }
        isLayouting = false;
    }

    /**
     * DEPRECATED:  Replaced by setVisible(boolean).
     */
    public void show() {
        setVisible(true);
    }

    /**
     * DEPRECATED:  Replaced by setVisible(boolean).
     */
    public void hide() {
        setVisible(false);
    }

    /**
     * DEPRECATED:  Replaced by setEnabled(boolean).
     */
    public void enable() {
        setEnabled(true);
    }

    /**
     * DEPRECATED:  Replaced by setEnabled(boolean).
     */
    public void disable() {
        setEnabled(false);
    }

    /**
     * DEPRECATED:  Replaced by setBounds(int, int, int, int).
     */
    public void reshape(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    /**
     * DEPRECATED:  Replaced by getMinimumSize().
     */
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * DEPRECATED:  Replaced by getPreferredSize().
     */
    public Dimension preferredSize() {
        return getPreferredSize();
    }

    /**
     *
     */

    public void addDropTarget(DropTarget dt) {
        if (MToolkit.useMotifDnD()) {
            addNativeDropTarget(dt);
        } else {
            Component comp = target;
            while(!(comp == null || comp instanceof java.awt.Window)) {
                comp = getParent_NoClientCode(comp);
            }

            if (comp instanceof Window) {
                MWindowPeer wpeer = (MWindowPeer)(comp.getPeer());
                if (wpeer != null) {
                    wpeer.addDropTarget();
                }
            }
        }
    }

    /**
     *
     */

    public void removeDropTarget(DropTarget dt) {
        if (MToolkit.useMotifDnD()) {
            removeNativeDropTarget(dt);
        } else {
            Component comp = target;
            while(!(comp == null || comp instanceof java.awt.Window)) {
                comp = getParent_NoClientCode(comp);
            }

            if (comp instanceof Window) {
                MWindowPeer wpeer = (MWindowPeer)(comp.getPeer());
                if (wpeer != null) {
                    wpeer.removeDropTarget();
                }
            }
        }
    }

    public void notifyTextComponentChange(boolean add){
        Container parent = getParent_NoClientCode(target);
        while(!(parent == null ||
                parent instanceof java.awt.Frame ||
                parent instanceof java.awt.Dialog)) {
            parent = getParent_NoClientCode(parent);
        }

        if (parent instanceof java.awt.Frame ||
            parent instanceof java.awt.Dialog) {
            if (add)
                ((MInputMethodControl)parent.getPeer()).addTextComponent((MComponentPeer)this);
            else
                ((MInputMethodControl)parent.getPeer()).removeTextComponent((MComponentPeer)this);
        }
    }

    native void addNativeDropTarget(DropTarget dt);

    native void removeNativeDropTarget(DropTarget dt);

    public GraphicsConfiguration getGraphicsConfiguration() {
        GraphicsConfiguration ret = graphicsConfig;
        if (ret == null) {
            ret = target.getGraphicsConfiguration();
        }
        return ret;
    }

    // Returns true if we are inside begin/endLayout and
    // are waiting for native painting
    public boolean isPaintPending() {
        return paintPending && isLayouting;
    }

    public boolean handlesWheelScrolling() {
        return false;
    }

    /**
     * The following multibuffering-related methods delegate to our
     * associated GraphicsConfig (X11 or GLX) to handle the appropriate
     * native windowing system specific actions.
     */

    private native long getWindow(long pData);

    public long getContentWindow() {
        return getWindow(pData);
    }

    public void createBuffers(int numBuffers, BufferCapabilities caps)
      throws AWTException
    {
        backBuffer = graphicsConfig.createBackBuffer(this, numBuffers, caps);
        xBackBuffer = graphicsConfig.createBackBufferImage(target,
                                                           backBuffer);
    }

    public void flip(int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction)
    {
        if (backBuffer == 0) {
            throw new IllegalStateException("Buffers have not been created");
        }
        graphicsConfig.flip(this, target, xBackBuffer,
                            x1, y1, x2, y2, flipAction);
    }

    public Image getBackBuffer() {
        if (backBuffer == 0) {
            throw new IllegalStateException("Buffers have not been created");
        }
        return xBackBuffer;
    }

    public void destroyBuffers() {
        graphicsConfig.destroyBackBuffer(backBuffer);
        backBuffer = 0;
        xBackBuffer = null;
    }

    /**
     * @see java.awt.peer.ComponentPeer#isReparentSupported
     */
    public boolean isReparentSupported() {
        return false;
    }

    /**
     * @see java.awt.peer.ComponentPeer#reparent
     */
    public void reparent(ContainerPeer newNativeParent) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the shape to the native component window.
     * @since 1.7
     */
    public void applyShape(Region shape) {
    }

}
