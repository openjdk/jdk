/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.*;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.VolatileImage;
import java.awt.peer.*;
import sun.awt.*;
import sun.awt.motif.X11FontMetrics;
import java.lang.reflect.*;
import java.util.logging.*;
import java.util.*;

// FIXME: Add X errors handling
// FIXME: Add chaining of parameters to XEmbed-client if we are both(accelerators; XDND; focus already automatically)
public class MEmbedCanvasPeer extends MCanvasPeer implements WindowFocusListener, KeyEventPostProcessor, ModalityListener, WindowIDProvider {
    private static final Logger xembedLog = Logger.getLogger("sun.awt.motif.xembed.MEmbedCanvasPeer");

    final static int XEMBED_VERSION = 0,
        XEMBED_MAPPED = (1 << 0);
/* XEMBED messages */
    final static int XEMBED_EMBEDDED_NOTIFY     =       0;
    final static int XEMBED_WINDOW_ACTIVATE  =  1;
    final static int XEMBED_WINDOW_DEACTIVATE =         2;
    final static int XEMBED_REQUEST_FOCUS               =3;
    final static int XEMBED_FOCUS_IN    =       4;
    final static int XEMBED_FOCUS_OUT   =       5;
    final static int XEMBED_FOCUS_NEXT  =       6;
    final static int XEMBED_FOCUS_PREV  =       7;
/* 8-9 were used for XEMBED_GRAB_KEY/XEMBED_UNGRAB_KEY */
    final static int XEMBED_GRAB_KEY = 8;
    final static int XEMBED_UNGRAB_KEY = 9;
    final static int XEMBED_MODALITY_ON         =       10;
    final static int XEMBED_MODALITY_OFF        =       11;
    final static int XEMBED_REGISTER_ACCELERATOR =    12;
    final static int XEMBED_UNREGISTER_ACCELERATOR=   13;
    final static int XEMBED_ACTIVATE_ACCELERATOR  =   14;

    final static int NON_STANDARD_XEMBED_GTK_GRAB_KEY = 108;
    final static int NON_STANDARD_XEMBED_GTK_UNGRAB_KEY = 109;

//     A detail code is required for XEMBED_FOCUS_IN. The following values are valid:
/* Details for  XEMBED_FOCUS_IN: */
    final static int XEMBED_FOCUS_CURRENT       =       0;
    final static int XEMBED_FOCUS_FIRST         =       1;
    final static int XEMBED_FOCUS_LAST  =       2;

// Modifiers bits
    final static int XEMBED_MODIFIER_SHIFT   = (1 << 0);
    final static int XEMBED_MODIFIER_CONTROL = (1 << 1);
    final static int XEMBED_MODIFIER_ALT     = (1 << 2);
    final static int XEMBED_MODIFIER_SUPER   = (1 << 3);
    final static int XEMBED_MODIFIER_HYPER   = (1 << 4);

    boolean applicationActive; // Whether the application is active(has focus)
    Map<Long, AWTKeyStroke> accelerators = new HashMap<Long, AWTKeyStroke>(); // Maps accelerator ID into AWTKeyStroke
    Map<AWTKeyStroke, Long> accel_lookup = new HashMap<AWTKeyStroke, Long>(); // Maps AWTKeyStroke into accelerator ID
    Set<GrabbedKey> grabbed_keys = new HashSet<GrabbedKey>(); // A set of keys grabbed by client
    Object ACCEL_LOCK = accelerators; // Lock object for working with accelerators;
    Object GRAB_LOCK = grabbed_keys; // Lock object for working with keys grabbed by client

    MEmbedCanvasPeer() {}

    MEmbedCanvasPeer(Component target) {
        super(target);
    }

    void initialize() {
        super.initialize();

        installActivateListener();
        installAcceleratorListener();
        installModalityListener();

        // XEmbed canvas should be non-traversable.
        // FIXME: Probably should be removed and enforced setting of it by the users
        target.setFocusTraversalKeysEnabled(false);

        initXEmbedServer();
    }

    void installModalityListener() {
        ((SunToolkit)Toolkit.getDefaultToolkit()).addModalityListener(this);
    }

    void deinstallModalityListener() {
        ((SunToolkit)Toolkit.getDefaultToolkit()).removeModalityListener(this);
    }

    void installAcceleratorListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(this);
    }

    void deinstallAcceleratorListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventPostProcessor(this);
    }

    void installActivateListener() {
        // FIXME: should watch for hierarchy changes
        Window toplevel = getTopLevel(target);
        if (toplevel != null) {
            toplevel.addWindowFocusListener(this);
            applicationActive = toplevel.isFocused();
        }
    }

    void deinstallActivateListener() {
        Window toplevel = getTopLevel(target);
        if (toplevel != null) {
            toplevel.removeWindowFocusListener(this);
        }
    }

    native boolean isXEmbedActive();

    boolean isApplicationActive() {
        return applicationActive;
    }

    native void initDispatching();

    native void endDispatching();

    native void embedChild(long child);

    native void childDestroyed();

    public void handleEvent(AWTEvent e) {
        super.handleEvent(e);
        if (isXEmbedActive()) {
            switch (e.getID()) {
              case FocusEvent.FOCUS_GAINED:
                  canvasFocusGained((FocusEvent)e);
                  break;
              case FocusEvent.FOCUS_LOST:
                  canvasFocusLost((FocusEvent)e);
                  break;
              case KeyEvent.KEY_PRESSED:
              case KeyEvent.KEY_RELEASED:
                  if (!((InputEvent)e).isConsumed()) {
                      forwardKeyEvent((KeyEvent)e);
                  }
                  break;
            }
        }
    }

    public Dimension getPreferredSize() {
        if (isXEmbedActive()) {
            Dimension dim = getEmbedPreferredSize();
            if (dim == null) {
                return super.getPreferredSize();
            } else {
                return dim;
            }
        } else {
            return super.getPreferredSize();
        }
    }
    native Dimension getEmbedPreferredSize();
    public Dimension getMinimumSize() {
        if (isXEmbedActive()) {
            Dimension dim = getEmbedMinimumSize();
            if (dim == null) {
                return super.getMinimumSize();
            } else {
                return dim;
            }
        } else {
            return super.getMinimumSize();
        }
    }
    native Dimension getEmbedMinimumSize();
    protected void disposeImpl() {
        if (isXEmbedActive()) {
            detachChild();
        }
        deinstallActivateListener();
        deinstallModalityListener();
        deinstallAcceleratorListener();

        destroyXEmbedServer();
        super.disposeImpl();
    }

    public boolean isFocusable() {
        return true;
    }

    Window getTopLevel(Component comp) {
        while (comp != null && !(comp instanceof Window)) {
            comp = comp.getParent();
        }
        return (Window)comp;
    }

    native Rectangle getClientBounds();

    void childResized() {
        if (xembedLog.isLoggable(Level.FINER)) {
            Rectangle bounds = getClientBounds();
            xembedLog.finer("Child resized: " + bounds);
            // It is not required to update embedder's size when client size changes
            // However, since there is no any means to get client size it seems to be the
            // only way to provide it. However, it contradicts with Java layout concept -
            // so it is disabled for now.
//             Rectangle my_bounds = getBounds();
//             setBounds(my_bounds.x, my_bounds.y, bounds.width, bounds.height, SET_BOUNDS);
        }
        postEvent(new ComponentEvent(target, ComponentEvent.COMPONENT_RESIZED));
    }

    void focusNext() {
        if (isXEmbedActive()) {
            xembedLog.fine("Requesting focus for the next component after embedder");
            postEvent(new InvocationEvent(target, new Runnable() {
                    public void run() {
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(target);
                    }
                }));
        } else {
            xembedLog.fine("Application is not active - denying focus next");
        }
    }

    void focusPrev() {
        if (isXEmbedActive()) {
            xembedLog.fine("Requesting focus for the next component after embedder");
            postEvent(new InvocationEvent(target, new Runnable() {
                    public void run() {
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(target);
                    }
                }));
        } else {
            xembedLog.fine("Application is not active - denying focus prev");
        }
    }

    void requestXEmbedFocus() {
        if (isXEmbedActive()) {
            xembedLog.fine("Requesting focus for client");
            postEvent(new InvocationEvent(target, new Runnable() {
                    public void run() {
                        target.requestFocusInWindow();
                    }
                }));
        } else {
            xembedLog.fine("Application is not active - denying request focus");
        }
    }

    native void notifyChildEmbedded();

    native void detachChild();

    public void windowGainedFocus(WindowEvent e) {
        applicationActive = true;
        if (isXEmbedActive()) {
            xembedLog.fine("Sending WINDOW_ACTIVATE");
            sendMessage(XEMBED_WINDOW_ACTIVATE);
        }
    }

    public void windowLostFocus(WindowEvent e) {
        applicationActive = false;
        if (isXEmbedActive()) {
            xembedLog.fine("Sending WINDOW_DEACTIVATE");
            sendMessage(XEMBED_WINDOW_DEACTIVATE);
        }
    }

    void canvasFocusGained(FocusEvent e) {
        if (isXEmbedActive()) {
            xembedLog.fine("Forwarding FOCUS_GAINED");
            int flavor = XEMBED_FOCUS_CURRENT;
            if (e instanceof CausedFocusEvent) {
                CausedFocusEvent ce = (CausedFocusEvent)e;
                if (ce.getCause() == CausedFocusEvent.Cause.TRAVERSAL_FORWARD) {
                    flavor = XEMBED_FOCUS_FIRST;
                } else if (ce.getCause() == CausedFocusEvent.Cause.TRAVERSAL_BACKWARD) {
                    flavor = XEMBED_FOCUS_LAST;
                }
            }
            sendMessage(XEMBED_FOCUS_IN, flavor, 0, 0);
        }
    }

    void canvasFocusLost(FocusEvent e) {
        if (isXEmbedActive() && !e.isTemporary()) {
            xembedLog.fine("Forwarding FOCUS_LOST");
            Component opp = e.getOppositeComponent();
            int num = 0;
            try {
                num = Integer.parseInt(opp.getName());
            } catch (NumberFormatException nfe) {
            }
            sendMessage(XEMBED_FOCUS_OUT, num, 0, 0);
        }
    }

    native void forwardKeyEvent(KeyEvent e);

    void grabKey(final long keysym, final long modifiers) {
        postEvent(new InvocationEvent(target, new Runnable() {
                public void run() {
                    GrabbedKey grab = new GrabbedKey(keysym, modifiers);
                    if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("Grabbing key: " + grab);
                    synchronized(GRAB_LOCK) {
                        grabbed_keys.add(grab);
                    }
                }
            }));
    }

    void ungrabKey(final long keysym, final long modifiers) {
        postEvent(new InvocationEvent(target, new Runnable() {
                public void run() {
                    GrabbedKey grab = new GrabbedKey(keysym, modifiers);
                    if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("UnGrabbing key: " + grab);
                    synchronized(GRAB_LOCK) {
                        grabbed_keys.remove(grab);
                    }
                }
            }));
    }

    void registerAccelerator(final long accel_id, final long keysym, final long modifiers) {
        postEvent(new InvocationEvent(target, new Runnable() {
                public void run() {
                    AWTKeyStroke stroke = getKeyStrokeForKeySym(keysym, modifiers);
                    if (stroke != null) {
                        if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("Registering accelerator " + accel_id + " for " + stroke);
                        synchronized(ACCEL_LOCK) {
                            accelerators.put(accel_id, stroke);
                            accel_lookup.put(stroke, accel_id);
                        }
                    }
                    // Propogate accelerators to the another embedder
                    propogateRegisterAccelerator(stroke);
                }
            }));
    }

    void unregisterAccelerator(final long accel_id) {
        postEvent(new InvocationEvent(target, new Runnable() {
                public void run() {
                    AWTKeyStroke stroke = null;
                    synchronized(ACCEL_LOCK) {
                        stroke = accelerators.get(accel_id);
                        if (stroke != null) {
                            if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("Unregistering accelerator: " + accel_id);
                            accelerators.remove(accel_id);
                            accel_lookup.remove(stroke); // FIXME: How about several accelerators with the same stroke?
                        }
                    }
                    // Propogate accelerators to the another embedder
                    propogateUnRegisterAccelerator(stroke);
                }
            }));
    }

    void propogateRegisterAccelerator(AWTKeyStroke stroke) {
        // Find the top-level and see if it is XEmbed client. If so, ask him to
        // register the accelerator
        MWindowPeer parent = getParentWindow();
        if (parent != null && parent instanceof MEmbeddedFramePeer) {
            MEmbeddedFramePeer embedded = (MEmbeddedFramePeer)parent;
            embedded.registerAccelerator(stroke);
        }
    }

    void propogateUnRegisterAccelerator(AWTKeyStroke stroke) {
        // Find the top-level and see if it is XEmbed client. If so, ask him to
        // register the accelerator
        MWindowPeer parent = getParentWindow();
        if (parent != null && parent instanceof MEmbeddedFramePeer) {
            MEmbeddedFramePeer embedded = (MEmbeddedFramePeer)parent;
            embedded.unregisterAccelerator(stroke);
        }
    }

    public boolean postProcessKeyEvent(KeyEvent e) {
        // Processing events only if we are in the focused window.
        MWindowPeer parent = getParentWindow();
        if (parent == null || !((Window)parent.target).isFocused() || target.isFocusOwner()) {
            return false;
        }

        boolean result = false;

        if (xembedLog.isLoggable(Level.FINER)) xembedLog.finer("Post-processing event " + e);

        // Process ACCELERATORS
        AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(e);
        long accel_id = 0;
        boolean exists = false;
        synchronized(ACCEL_LOCK) {
            exists = accel_lookup.containsKey(stroke);
            if (exists) {
                accel_id = accel_lookup.get(stroke).longValue();
            }
        }
        if (exists) {
            if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("Activating accelerator " + accel_id);
            sendMessage(XEMBED_ACTIVATE_ACCELERATOR, accel_id, 0, 0); // FIXME: How about overloaded?
            result = true;
        }

        // Process Grabs, unofficial GTK feature
        exists = false;
        GrabbedKey key = new GrabbedKey(e);
        synchronized(GRAB_LOCK) {
            exists = grabbed_keys.contains(key);
        }
        if (exists) {
            if (xembedLog.isLoggable(Level.FINE)) xembedLog.fine("Forwarding grabbed key " + e);
            forwardKeyEvent(e);
            result = true;
        }

        return result;
    }

    public void modalityPushed(ModalityEvent ev) {
        sendMessage(XEMBED_MODALITY_ON);
    }

    public void modalityPopped(ModalityEvent ev) {
        sendMessage(XEMBED_MODALITY_OFF);
    }

    int getModifiers(int state) {
        int mods = 0;
        if ((state & XEMBED_MODIFIER_SHIFT) != 0) {
            mods |= InputEvent.SHIFT_DOWN_MASK;
        }
        if ((state & XEMBED_MODIFIER_CONTROL) != 0) {
            mods |= InputEvent.CTRL_DOWN_MASK;
        }
        if ((state & XEMBED_MODIFIER_ALT) != 0) {
            mods |= InputEvent.ALT_DOWN_MASK;
        }
        // FIXME: What is super/hyper?
        // FIXME: Experiments show that SUPER is ALT. So what is Alt then?
        if ((state & XEMBED_MODIFIER_SUPER) != 0) {
            mods |= InputEvent.ALT_DOWN_MASK;
        }
//         if ((state & XEMBED_MODIFIER_HYPER) != 0) {
//             mods |= InputEvent.DOWN_MASK;
//         }
        return mods;
    }

    // Shouldn't be called on Toolkit thread.
    AWTKeyStroke getKeyStrokeForKeySym(long keysym, long state) {

        int keycode = getAWTKeyCodeForKeySym((int)keysym);
        int modifiers = getModifiers((int)state);
        return AWTKeyStroke.getAWTKeyStroke(keycode, modifiers);
    }
    native int getAWTKeyCodeForKeySym(int keysym);
    native void sendMessage(int msg);
    native void sendMessage(int msg, long detail, long data1, long data2);
    MWindowPeer getParentWindow() {
        Component parent = target.getParent();
        synchronized(target.getTreeLock()) {
            while (parent != null && !(parent.getPeer() instanceof MWindowPeer)) {
                parent = parent.getParent();
            }
            return (parent != null)?(MWindowPeer)parent.getPeer():null;
        }
    }

    private static class XEmbedDropTarget extends DropTarget {
        public void addDropTargetListener(DropTargetListener dtl)
          throws TooManyListenersException {
            // Drop target listeners registered with this target will never be
            // notified, since all drag notifications are routed to the XEmbed
            // client. To avoid confusion we prohibit listeners registration
            // by throwing TooManyListenersException as if there is a listener
            // registered with this target already.
            throw new TooManyListenersException();
        }
    }

    public void setXEmbedDropTarget() {
        // Register a drop site on the top level.
        Runnable r = new Runnable() {
                public void run() {
                    target.setDropTarget(new XEmbedDropTarget());
                }
            };
        SunToolkit.executeOnEventHandlerThread(target, r);
    }

    public void removeXEmbedDropTarget() {
        // Unregister a drop site on the top level.
        Runnable r = new Runnable() {
                public void run() {
                    if (target.getDropTarget() instanceof XEmbedDropTarget) {
                        target.setDropTarget(null);
                    }
                }
            };
        SunToolkit.executeOnEventHandlerThread(target, r);
    }

    public boolean processXEmbedDnDEvent(long ctxt, int eventID) {
        if (target.getDropTarget() instanceof XEmbedDropTarget) {
            forwardEventToEmbedded(ctxt, eventID);
            return true;
        } else {
            return false;
        }
    }

    native void forwardEventToEmbedded(long ctxt, int eventID);
    native void initXEmbedServer();
    native void destroyXEmbedServer();
    public native long getWindow();
}
class GrabbedKey {
    long keysym;
    long modifiers;
    GrabbedKey(long keysym, long modifiers) {
        this.keysym = keysym;
        this.modifiers = modifiers;
    }

    GrabbedKey(KeyEvent ev) {
        init(ev);
    }

    native void initKeySymAndModifiers(KeyEvent e);

    private void init(KeyEvent e) {
        initKeySymAndModifiers(e);
    }

    public int hashCode() {
        return (int)keysym & 0xFFFFFFFF;
    }

    public boolean equals(Object o) {
        if (!(o instanceof GrabbedKey)) {
            return false;
        }
        GrabbedKey key = (GrabbedKey)o;
        return (keysym == key.keysym && modifiers == key.modifiers);
    }

    public String toString() {
        return "Key combination[keysym=" + keysym + ", mods=" + modifiers + "]";
    }
}
