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

import java.util.Vector;
import java.awt.*;
import java.awt.peer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import sun.awt.image.ImageRepresentation;
import sun.awt.motif.MInputMethod;
import sun.awt.motif.MInputMethodControl;
import sun.awt.im.*;
import sun.awt.DisplayChangedListener;
import sun.awt.SunToolkit;
import sun.awt.X11GraphicsDevice;

class MWindowPeer extends MPanelPeer implements WindowPeer,
DisplayChangedListener {

    Insets insets = new Insets( 0, 0, 0, 0 );
    MWindowAttributes winAttr;
    static Vector allWindows = new Vector();
    int         iconWidth  = -1;
    int         iconHeight = -1;

    int dropTargetCount = 0;
    boolean alwaysOnTop;

    native void pCreate(MComponentPeer parent, String targetClassName, boolean isFocusableWindow);
    native void pShow();
    native void pToFront();
    native void pShowModal(boolean isModal);
    native void pHide();
    native void pReshape(int x, int y, int width, int height);
    native void pDispose();
    native void pSetTitle(String title);
    public native void setState(int state);
    public native int getState();

    public native void setResizable(boolean resizable);
    native void addTextComponentNative(MComponentPeer tc);
    native void removeTextComponentNative();
    native void pSetIMMOption(String option);
    native void pSetMenuBar(MMenuBarPeer mbpeer);
    native void setSaveUnder(boolean state);

    native void registerX11DropTarget(Component target);
    native void unregisterX11DropTarget(Component target);
    native void updateAlwaysOnTop(boolean isAlwaysOnTop);

    private static native void initIDs();

    static {
        initIDs();
    }

    // this function is privileged! do not change it to public!
    private static int getInset(final String name, final int def) {
        Integer tmp = (Integer) java.security.AccessController.doPrivileged(
            new sun.security.action.GetIntegerAction(name, def));
        return tmp.intValue();
    }

    MWindowPeer() {
        insets = new Insets(0,0,0,0);
        winAttr = new MWindowAttributes();
    }

    MWindowPeer(Window target) {

        this();
        init(target);

        allWindows.addElement(this);
    }

    void create(MComponentPeer parent) {
        pCreate(parent, target.getClass().getName(), ((Window)target).isFocusableWindow());
    }

    void init( Window target ) {
        if ( winAttr.nativeDecor == true ) {
            insets.top = getInset("awt.frame.topInset", -1);
            insets.left = getInset("awt.frame.leftInset", -1);
            insets.bottom = getInset("awt.frame.bottomInset", -1);
            insets.right = getInset("awt.frame.rightInset", -1);
        }

        Rectangle bounds = target.getBounds();
        sysX = bounds.x;
        sysY = bounds.y;
        sysW = bounds.width;
        sysH = bounds.height;

        super.init(target);
        InputMethodManager imm = InputMethodManager.getInstance();
        String menuString = imm.getTriggerMenuString();
        if (menuString != null)
        {
            pSetIMMOption(menuString);
        }
        pSetTitle(winAttr.title);

        /*
         * For Windows and undecorated Frames and Dialogs this just
         * disables/enables resizing functions in the system menu.
         */
        setResizable(winAttr.isResizable);

        setSaveUnder(true);

        Font f = target.getFont();
        if (f == null) {
            f = defaultFont;
            target.setFont(f);
            setFont(f);
        }
        Color c = target.getBackground();
        if (c == null) {
            target.setBackground(SystemColor.window);
            setBackground(SystemColor.window);
        }
        c = target.getForeground();
        if (c == null) {
            target.setForeground(SystemColor.windowText);
            setForeground(SystemColor.windowText);
        }
        alwaysOnTop = ((Window)target).isAlwaysOnTop() && ((Window)target).isAlwaysOnTopSupported();

        GraphicsConfiguration gc = getGraphicsConfiguration();
        ((X11GraphicsDevice)gc.getDevice()).addDisplayChangedListener(this);

    }

    /* Support for multiple icons is not implemented in MAWT */
    public void updateIconImages() {
        if (this instanceof MFramePeer) {
            ((MFramePeer)this).setIconImage(((Frame)target).getIconImage());
        }
    }


    /* Not implemented in MAWT */
    public void updateMinimumSize() {
    }

    protected void disposeImpl() {
        allWindows.removeElement(this);
        super.disposeImpl();
    }

    public native void toBack();

    public void setAlwaysOnTop(boolean alwaysOnTop) {
        this.alwaysOnTop = alwaysOnTop;
        updateAlwaysOnTop(alwaysOnTop);
    }

    public void toFront() {
        if (target.isVisible()) {
            updateFocusableWindowState();
            pToFront();
        }
    }

    public void updateFocusableWindowState() {
        setFocusableWindow(((Window)target).isFocusableWindow());
    }
    native void setFocusableWindow(boolean value);

    public void setVisible( boolean b ) {
        if (b) {
            updateFocusableWindowState();
        }
        super.setVisible(b);
        updateAlwaysOnTop(alwaysOnTop);
    }

    public Insets getInsets() {
        return insets;
    }

    public void handleQuit() {
        postEvent(new WindowEvent((Window)target, WindowEvent.WINDOW_CLOSING));
    }

    // XXX: nasty WM, foul play.  spank WM author.
    public void handleDestroy() {
        final Window target = (Window)this.target;
        SunToolkit.executeOnEventHandlerThread(target,
                                               new Runnable() {
                                                   public void run() {
                                                       // This seems like the only reasonable thing we
                                                       // could do in this situation as the native window
                                                       // is already dead.
                                                       target.dispose();
                                                   }
                                               });
    }


    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleIconify() {
        postEvent(new WindowEvent((Window)target, WindowEvent.WINDOW_ICONIFIED));
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleDeiconify() {
        postEvent(new WindowEvent((Window)target, WindowEvent.WINDOW_DEICONIFIED));
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleStateChange(int oldState, int newState) {
        postEvent(new WindowEvent((Window)target,
                                  WindowEvent.WINDOW_STATE_CHANGED,
                                  oldState, newState));
    }

    /**
     * Called to inform the Window that its size has changed and it
     * should layout its children.
     */
    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleResize(int width, int height) {
        sysW = width;
        sysH = height;

        // REMIND: Is this secure? Can client code subclass input method?
        if (!tcList.isEmpty() &&
            !imList.isEmpty()){
            int i;
            for (i = 0; i < imList.size(); i++){
                ((MInputMethod)imList.elementAt(i)).configureStatus();
            }
        }
        validateSurface(width, height);
        postEvent(new ComponentEvent(target, ComponentEvent.COMPONENT_RESIZED));
    }


    /**
     * DEPRECATED:  Replaced by getInsets().
     */
    public Insets insets() {
        return getInsets();
    }

    public void handleMoved(int x, int y) {
        sysX = x;
        sysY = y;
        postEvent(new ComponentEvent(target, ComponentEvent.COMPONENT_MOVED));
    }

    private native AWTEvent wrapInSequenced(AWTEvent event);

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleWindowFocusIn() {
        WindowEvent we = new WindowEvent((Window)target, WindowEvent.WINDOW_GAINED_FOCUS);
        /* wrap in Sequenced, then post*/
        postEvent(wrapInSequenced((AWTEvent) we));
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleWindowFocusOut(Window oppositeWindow) {
        WindowEvent we = new WindowEvent((Window)target, WindowEvent.WINDOW_LOST_FOCUS,
                                         oppositeWindow);
        /* wrap in Sequenced, then post*/
        postEvent(wrapInSequenced((AWTEvent) we));
    }


// relocation of Imm stuff
    private Vector imList = new Vector();
    private Vector tcList = new Vector();

    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void notifyIMMOptionChange(){

        // REMIND: IS THIS SECURE??? CAN USER CODE SUBCLASS INPUTMETHODMGR???
        InputMethodManager.getInstance().notifyChangeRequest(target);
    }

    public void addInputMethod(MInputMethod im) {
        if (!imList.contains(im))
            imList.addElement(im);
    }

    public void removeInputMethod(MInputMethod im) {
        if (imList.contains(im))
            imList.removeElement(im);
    }

    public void addTextComponent(MComponentPeer tc) {
        if (tcList.contains(tc))
            return;
        if (tcList.isEmpty()){
            addTextComponentNative(tc);
            if (!imList.isEmpty()) {
                for (int i = 0; i < imList.size(); i++) {
                    ((MInputMethod)imList.elementAt(i)).reconfigureXIC((MInputMethodControl)this);
                }
            }
            MToolkit.executeOnEventHandlerThread(target, new Runnable() {
                    public void run() {
                        synchronized(target.getTreeLock()) {
                            target.doLayout();
                        }
                    }
                });
        }
        tcList.addElement(tc);

    }

    public void removeTextComponent(MComponentPeer tc) {
        if (!tcList.contains(tc))
            return;
        tcList.removeElement(tc);
        if (tcList.isEmpty()){
            removeTextComponentNative();
            if (!imList.isEmpty()) {
                for (int i = 0; i < imList.size(); i++) {
                    ((MInputMethod)imList.elementAt(i)).reconfigureXIC((MInputMethodControl)this);
                }
            }
            target.doLayout();
        }
    }

    public MComponentPeer getTextComponent() {
        if (!tcList.isEmpty()) {
            return (MComponentPeer)tcList.firstElement();
        } else {
            return null;
        }
    }

    boolean hasDecorations(int decor) {
        if (!winAttr.nativeDecor) {
            return false;
        }
        else {
            int myDecor = winAttr.decorations;
            boolean hasBits = ((myDecor & decor) == decor);
            if ((myDecor & MWindowAttributes.AWT_DECOR_ALL) != 0)
                return !hasBits;
            else
                return hasBits;
        }
    }

    /* Returns the native paint should be posted after setting new size
     */
    public boolean checkNativePaintOnSetBounds(int width, int height) {
        // Fix for 4418155. Window does not repaint
        // automticaly if shrinking. Should not wait for Expose
        return (width > oldWidth) || (height > oldHeight);
    }

/* --- DisplayChangedListener Stuff --- */

    native void resetTargetGC(Component target);

    /* Xinerama
     * called to update our GC when dragged onto another screen
     */
    public void draggedToNewScreen(int screenNum) {
        final int finalScreenNum = screenNum;

        SunToolkit.executeOnEventHandlerThread((Component)target, new Runnable()
            {
                public void run() {
                    displayChanged(finalScreenNum);
                }
            });
    }

    /* Xinerama
     * called to update our GC when dragged onto another screen
     */
    public void displayChanged(int screenNum) {
        // update our GC
        resetLocalGC(screenNum);         /* upcall to MCanvasPeer */
        resetTargetGC(target);           /* call Window.resetGC() via native */

        //propagate to children
        super.displayChanged(screenNum); /* upcall to MPanelPeer */
    }

    /**
     * Helper method that executes the displayChanged(screen) method on
     * the event dispatch thread.  This method is used in the Xinerama case
     * and after display mode change events.
     */
    private void executeDisplayChangedOnEDT(int screenNum) {
        final int finalScreenNum = screenNum;
        Runnable dc = new Runnable() {
            public void run() {
                displayChanged(finalScreenNum);
            }
        };
        SunToolkit.executeOnEventHandlerThread((Component)target, dc);
    }

    /**
     * From the DisplayChangedListener interface; called from
     * X11GraphicsDevice when the display mode has been changed.
     */
    public void displayChanged() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        int curScreenNum = ((X11GraphicsDevice)gc.getDevice()).getScreen();
        executeDisplayChangedOnEDT(curScreenNum);
    }

    /**
     * From the DisplayChangedListener interface; top-levels do not need
     * to react to this event.
     */
    public void paletteChanged() {
    }

    public synchronized void addDropTarget() {
        if (dropTargetCount == 0) {
            registerX11DropTarget(target);
        }
        dropTargetCount++;
    }

    public synchronized void removeDropTarget() {
        dropTargetCount--;
        if (dropTargetCount == 0) {
            unregisterX11DropTarget(target);
        }
    }

    protected synchronized void updateDropTarget() {
        if (dropTargetCount > 0) {
            unregisterX11DropTarget(target);
            registerX11DropTarget(target);
        }
    }

    public boolean requestWindowFocus() {
        return false;
    }

    public void setModalBlocked(Dialog blocker, boolean blocked) {
        // do nothing
    }

    public void postUngrabEvent() {
        postEvent(new sun.awt.UngrabEvent((Window)target));
    }

    boolean isOwnerOf(MComponentPeer child) {
        if (child == null) return false;

        Component comp = child.target;
        while (comp != null && !(comp instanceof Window)) {
            comp = getParent_NoClientCode(comp);
        }
        if (!(comp instanceof Window)) {
            return false;
        }

        while (comp != null && !(comp == target) && !(comp instanceof Dialog)) {
            comp = getParent_NoClientCode(comp);
        }
        return (comp == target);
    }

    boolean processUngrabMouseEvent(MComponentPeer compPeer, int x_root, int y_root, int type) {
        switch (type) {
          case 4: // ButtonPress
              // Check that the target is the child of the grabbed
              // window or the child of one of the owned windows of
              // the grabbed window
              if (!isOwnerOf(compPeer)) {
                  postUngrabEvent();
                  return true;
              }
        }
        return false;
    }

    private final boolean hasWarningWindow() {
        return ((Window)target).getWarningString() != null;
    }

    // This method is overriden at Dialog and Frame peers.
    boolean isTargetUndecorated() {
        return true;
    }

    private volatile int sysX = 0;
    private volatile int sysY = 0;
    private volatile int sysW = 0;
    private volatile int sysH = 0;

    Rectangle constrainBounds(int x, int y, int width, int height) {
        // We don't restrict the setBounds() operation if the code is trusted.
        if (!hasWarningWindow()) {
            return new Rectangle(x, y, width, height);
        }

        int newX = x;
        int newY = y;
        int newW = width;
        int newH = height;

        GraphicsConfiguration gc = ((Window)target).getGraphicsConfiguration();
        Rectangle sB = gc.getBounds();
        Insets sIn = ((Window)target).getToolkit().getScreenInsets(gc);

        int screenW = sB.width - sIn.left - sIn.right;
        int screenH = sB.height - sIn.top - sIn.bottom;

        // If it's undecorated or is not currently visible,
        // then check each point is within the visible part of the screen
        if (!target.isVisible() || isTargetUndecorated()) {
            int screenX = sB.x + sIn.left;
            int screenY = sB.y + sIn.top;

            // First make sure the size is withing the visible part of the screen
            if (newW > screenW) {
                newW = screenW;
            }

            if (newH > screenH) {
                newH = screenH;
            }

            // Tweak the location if needed
            if (newX < screenX) {
                newX = screenX;
            } else if (newX + newW > screenX + screenW) {
                newX = screenX + screenW - newW;
            }

            if (newY < screenY) {
                newY = screenY;
            } else if (newY + newH > screenY + screenH) {
                newY = screenY + screenH - newH;
            }
        } else {
            int maxW = Math.max(screenW, sysW);
            int maxH = Math.max(screenH, sysH);

            // Make sure the size is withing the visible part of the screen
            // OR is less that the current size of the window.
            if (newW > maxW) {
                newW = maxW;
            }

            if (newH > maxH) {
                newH = maxH;
            }
        }

        return new Rectangle(newX, newY, newW, newH);
    }

    public void setBounds(int x, int y, int width, int height, int op) {
        Rectangle newBounds = constrainBounds(x, y, width, height);
        super.setBounds(newBounds.x, newBounds.y, newBounds.width, newBounds.height, op);
    }

}
