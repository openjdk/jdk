/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.X11;

import java.awt.*;
import java.awt.event.*;
import java.awt.peer.TrayIconPeer;
import sun.awt.*;
import java.awt.image.*;
import java.text.BreakIterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ArrayBlockingQueue;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.lang.reflect.InvocationTargetException;

public class XTrayIconPeer implements TrayIconPeer {
    private static final Logger ctrLog = Logger.getLogger("sun.awt.X11.XTrayIconPeer.centering");

    TrayIcon target;
    TrayIconEventProxy eventProxy;
    XTrayIconEmbeddedFrame eframe;
    TrayIconCanvas canvas;
    Balloon balloon;
    Tooltip tooltip;
    PopupMenu popup;
    String tooltipString;
    boolean isTrayIconDisplayed;
    long eframeParentID;
    final XEventDispatcher parentXED, eframeXED;

    static final XEventDispatcher dummyXED = new XEventDispatcher() {
            public void dispatchEvent(XEvent ev) {}
        };

    volatile boolean isDisposed;

    boolean isParentWindowLocated;
    int old_x, old_y;
    int ex_width, ex_height;

    final static int TRAY_ICON_WIDTH = 24;
    final static int TRAY_ICON_HEIGHT = 24;

    XTrayIconPeer(TrayIcon target)
      throws AWTException
    {
        this.target = target;

        eventProxy = new TrayIconEventProxy(this);

        canvas = new TrayIconCanvas(target, TRAY_ICON_WIDTH, TRAY_ICON_HEIGHT);

        eframe = new XTrayIconEmbeddedFrame();

        eframe.setSize(TRAY_ICON_WIDTH, TRAY_ICON_HEIGHT);
        eframe.add(canvas);

        // Fix for 6317038: as EmbeddedFrame is instance of Frame, it is blocked
        // by modal dialogs, but in the case of TrayIcon it shouldn't. So we
        // set ModalExclusion property on it.
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                eframe.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);
                return null;
            }
        });


        if (XWM.getWMID() != XWM.METACITY_WM) {
            parentXED = dummyXED; // We don't like to leave it 'null'.

        } else {
            parentXED = new XEventDispatcher() {
                // It's executed under AWTLock.
                public void dispatchEvent(XEvent ev) {
                    if (isDisposed() || ev.get_type() != XlibWrapper.ConfigureNotify) {
                        return;
                    }

                    XConfigureEvent ce = ev.get_xconfigure();

                    ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}: {1}x{2}+{3}+{4} (old: {5}+{6})",
                               new Object[] { XTrayIconPeer.this, ce.get_width(), ce.get_height(),
                                              ce.get_x(), ce.get_y(), old_x, old_y });

                    // A workaround for Gnome/Metacity (it doesn't affect the behaviour on KDE).
                    // On Metacity the EmbeddedFrame's parent window bounds are larger
                    // than TrayIcon size required (that is we need a square but a rectangle
                    // is provided by the Panel Notification Area). The parent's background color
                    // differs from the Panel's one. To hide the background we resize parent
                    // window so that it fits the EmbeddedFrame.
                    // However due to resizing the parent window it loses centering in the Panel.
                    // We center it when discovering that some of its side is of size greater
                    // than the fixed value. Centering is being done by "X" (when the parent's width
                    // is greater) and by "Y" (when the parent's height is greater).

                    // Actually we need this workaround until we could detect taskbar color.

                    if (ce.get_height() != TRAY_ICON_HEIGHT && ce.get_width() != TRAY_ICON_WIDTH) {

                        // If both the height and the width differ from the fixed size then WM
                        // must level at least one side to the fixed size. For some reason it may take
                        // a few hops (even after reparenting) and we have to skip the intermediate ones.
                        ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Skipping as intermediate resizing.",
                                   XTrayIconPeer.this);
                        return;

                    } else if (ce.get_height() > TRAY_ICON_HEIGHT) {

                        ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Centering by \"Y\".",
                                   XTrayIconPeer.this);

                        XlibWrapper.XMoveResizeWindow(XToolkit.getDisplay(), eframeParentID,
                                                      ce.get_x(),
                                                      ce.get_y()+ce.get_height()/2-TRAY_ICON_HEIGHT/2,
                                                      TRAY_ICON_WIDTH,
                                                      TRAY_ICON_HEIGHT);
                        ex_height = ce.get_height();
                        ex_width = 0;

                    } else if (ce.get_width() > TRAY_ICON_WIDTH) {

                        ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Centering by \"X\".",
                                   XTrayIconPeer.this);

                        XlibWrapper.XMoveResizeWindow(XToolkit.getDisplay(), eframeParentID,
                                                      ce.get_x()+ce.get_width()/2 - TRAY_ICON_WIDTH/2,
                                                      ce.get_y(),
                                                      TRAY_ICON_WIDTH,
                                                      TRAY_ICON_HEIGHT);
                        ex_width = ce.get_width();
                        ex_height = 0;

                    } else if (isParentWindowLocated && ce.get_x() != old_x && ce.get_y() != old_y) {
                        // If moving by both "X" and "Y".
                        // When some tray icon gets removed from the tray, a Java icon may be repositioned.
                        // In this case the parent window also lose centering. We have to restore it.

                        if (ex_height != 0) {

                            ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Move detected. Centering by \"Y\".",
                                       XTrayIconPeer.this);

                            XlibWrapper.XMoveWindow(XToolkit.getDisplay(), eframeParentID,
                                                    ce.get_x(),
                                                    ce.get_y() + ex_height/2 - TRAY_ICON_HEIGHT/2);

                        } else if (ex_width != 0) {

                            ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Move detected. Centering by \"X\".",
                                       XTrayIconPeer.this);

                            XlibWrapper.XMoveWindow(XToolkit.getDisplay(), eframeParentID,
                                                    ce.get_x() + ex_width/2 - TRAY_ICON_WIDTH/2,
                                                    ce.get_y());
                        } else {
                            ctrLog.log(Level.FINE, "ConfigureNotify on parent of {0}. Move detected. Skipping.",
                                       XTrayIconPeer.this);
                        }
                    }
                    old_x = ce.get_x();
                    old_y = ce.get_y();
                    isParentWindowLocated = true;
                }
            };
        }
        eframeXED = new XEventDispatcher() {
                // It's executed under AWTLock.
                XTrayIconPeer xtiPeer = XTrayIconPeer.this;

                public void dispatchEvent(XEvent ev) {
                    if (isDisposed() || ev.get_type() != XlibWrapper.ReparentNotify) {
                        return;
                    }

                    XReparentEvent re = ev.get_xreparent();
                    eframeParentID = re.get_parent();

                    if (eframeParentID == XToolkit.getDefaultRootWindow()) {

                        if (isTrayIconDisplayed) { // most likely Notification Area was removed
                            SunToolkit.executeOnEventHandlerThread(xtiPeer.target, new Runnable() {
                                    public void run() {
                                        SystemTray.getSystemTray().remove(xtiPeer.target);
                                    }
                                });
                        }
                        return;
                    }

                    if (!isTrayIconDisplayed) {
                        addXED(eframeParentID, parentXED, XlibWrapper.StructureNotifyMask);

                        isTrayIconDisplayed = true;
                        XToolkit.awtLockNotifyAll();
                    }
                }
            };

        addXED(getWindow(), eframeXED, XlibWrapper.StructureNotifyMask);

        XSystemTrayPeer.getPeerInstance().addTrayIcon(this); // throws AWTException

        // Wait till the EmbeddedFrame is reparented
        long start = System.currentTimeMillis();
        final long PERIOD = 2000L;
        XToolkit.awtLock();
        try {
            while (!isTrayIconDisplayed) {
                try {
                    XToolkit.awtLockWait(PERIOD);
                } catch (InterruptedException e) {
                    break;
                }
                if (System.currentTimeMillis() - start > PERIOD) {
                    break;
                }
            }
        } finally {
            XToolkit.awtUnlock();
        }

        // This is unlikely to happen.
        if (!isTrayIconDisplayed || eframeParentID == 0 ||
            eframeParentID == XToolkit.getDefaultRootWindow())
        {
            throw new AWTException("TrayIcon couldn't be displayed.");
        }

        eframe.setVisible(true);
        updateImage();

        balloon = new Balloon(this, eframe);
        tooltip = new Tooltip(this, eframe);

        addListeners();
    }

    public void dispose() {
        if (SunToolkit.isDispatchThreadForAppContext(target)) {
            disposeOnEDT();
        } else {
            try {
                SunToolkit.executeOnEDTAndWait(target, new Runnable() {
                        public void run() {
                            disposeOnEDT();
                        }
                    });
            } catch (InterruptedException ie) {
            } catch (InvocationTargetException ite) {}
        }
    }

    private void disposeOnEDT() {
        // All actions that is to be synchronized with disposal
        // should be executed either under AWTLock, or on EDT.
        // isDisposed value must be checked.
        XToolkit.awtLock();
        isDisposed = true;
        XToolkit.awtUnlock();

        removeXED(getWindow(), eframeXED);
        removeXED(eframeParentID, parentXED);
        eframe.realDispose();
        balloon.dispose();
        isTrayIconDisplayed = false;
        XToolkit.targetDisposedPeer(target, this);
    }

    public static void suppressWarningString(Window w) {
        WindowAccessor.setTrayIconWindow(w, true);
    }

    public void setToolTip(String tooltip) {
        tooltipString = tooltip;
    }

    public void updateImage() {
        Runnable r = new Runnable() {
                public void run() {
                    canvas.updateImage(target.getImage());
                }
            };

        if (!SunToolkit.isDispatchThreadForAppContext(target)) {
            SunToolkit.executeOnEventHandlerThread(target, r);
        } else {
            r.run();
        }
    }

    public void displayMessage(String caption, String text, String messageType) {
        Point loc = getLocationOnScreen();
        Rectangle screen = eframe.getGraphicsConfiguration().getBounds();

        // Check if the tray icon is in the bounds of a screen.
        if (!(loc.x < screen.x || loc.x >= screen.x + screen.width ||
              loc.y < screen.y || loc.y >= screen.y + screen.height))
        {
            balloon.display(caption, text, messageType);
        }
    }

    // It's synchronized with disposal by EDT.
    public void showPopupMenu(int x, int y) {
        if (isDisposed())
            return;

        assert SunToolkit.isDispatchThreadForAppContext(target);

        PopupMenu newPopup = target.getPopupMenu();
        if (popup != newPopup) {
            if (popup != null) {
                eframe.remove(popup);
            }
            if (newPopup != null) {
                eframe.add(newPopup);
            }
            popup = newPopup;
        }

        if (popup != null) {
            Point loc = ((XBaseWindow)eframe.getPeer()).toLocal(new Point(x, y));
            popup.show(eframe, loc.x, loc.y);
        }
    }


    // ******************************************************************
    // ******************************************************************


    private void addXED(long window, XEventDispatcher xed, long mask) {
        if (window == 0) {
            return;
        }
        XToolkit.awtLock();
        try {
            XlibWrapper.XSelectInput(XToolkit.getDisplay(), window, mask);
        } finally {
            XToolkit.awtUnlock();
        }
        XToolkit.addEventDispatcher(window, xed);
    }

    private void removeXED(long window, XEventDispatcher xed) {
        if (window == 0) {
            return;
        }
        XToolkit.awtLock();
        try {
            XToolkit.removeEventDispatcher(window, xed);
        } finally {
            XToolkit.awtUnlock();
        }
    }

    // Private method for testing purposes.
    private Point getLocationOnScreen() {
        return eframe.getLocationOnScreen();
    }

    private Rectangle getBounds() {
        Point loc = getLocationOnScreen();
        return new Rectangle(loc.x, loc.y, loc.x + TRAY_ICON_WIDTH, loc.y + TRAY_ICON_HEIGHT);
    }

    void addListeners() {
        canvas.addMouseListener(eventProxy);
        canvas.addMouseMotionListener(eventProxy);
    }

    long getWindow() {
        return ((XEmbeddedFramePeer)eframe.getPeer()).getWindow();
    }

    boolean isDisposed() {
        return isDisposed;
    }

    static class TrayIconEventProxy implements MouseListener, MouseMotionListener {
        XTrayIconPeer xtiPeer;

        TrayIconEventProxy(XTrayIconPeer xtiPeer) {
            this.xtiPeer = xtiPeer;
        }

        public void handleEvent(MouseEvent e) {
            //prevent DRAG events from being posted with TrayIcon source(CR 6565779)
            if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
                return;
            }

            // Event handling is synchronized with disposal by EDT.
            if (xtiPeer.isDisposed()) {
                return;
            }
            Point coord = XBaseWindow.toOtherWindow(xtiPeer.getWindow(),
                                                    XToolkit.getDefaultRootWindow(),
                                                    e.getX(), e.getY());

            if (e.isPopupTrigger()) {
                xtiPeer.showPopupMenu(coord.x, coord.y);
            }

            e.translatePoint(coord.x - e.getX(), coord.y - e.getY());
            // This is a hack in order to set non-Component source to MouseEvent
            // instance.
            // In some cases this could lead to unpredictable result (e.g. when
            // other class tries to cast source field to Component).
            // We already filter DRAG events out (CR 6565779).
            e.setSource(xtiPeer.target);
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(e);
        }
        public void mouseClicked(MouseEvent e) {
            if ((e.getClickCount() > 1 || xtiPeer.balloon.isVisible()) &&
                e.getButton() == MouseEvent.BUTTON1)
            {
                ActionEvent aev = new ActionEvent(xtiPeer.target, ActionEvent.ACTION_PERFORMED,
                                                  xtiPeer.target.getActionCommand(), e.getWhen(),
                                                  e.getModifiers());
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(aev);
            }
            if (xtiPeer.balloon.isVisible()) {
                xtiPeer.balloon.hide();
            }
            handleEvent(e);
        }
        public void mouseEntered(MouseEvent e) {
            xtiPeer.tooltip.enter();
            handleEvent(e);
        }
        public void mouseExited(MouseEvent e) {
            xtiPeer.tooltip.exit();
            handleEvent(e);
        }
        public void mousePressed(MouseEvent e) {
            handleEvent(e);
        }
        public void mouseReleased(MouseEvent e) {
            handleEvent(e);
        }
        public void mouseDragged(MouseEvent e) {
            handleEvent(e);
        }
        public void mouseMoved(MouseEvent e) {
            handleEvent(e);
        }
    }

    static boolean isTrayIconStuffWindow(Window w) {
        return (w instanceof Tooltip) ||
               (w instanceof Balloon) ||
               (w instanceof XTrayIconEmbeddedFrame);
    }

    // ***************************************
    // Special embedded frame for tray icon
    // ***************************************

    private static class XTrayIconEmbeddedFrame extends XEmbeddedFrame {
        public XTrayIconEmbeddedFrame(){
            super(XToolkit.getDefaultRootWindow(), true, true);
        }

        public boolean isUndecorated() {
            return true;
        }

        public boolean isResizable() {
            return false;
        }

        // embedded frame for tray icon shouldn't be disposed by anyone except tray icon
        public void dispose(){
        }

        public void realDispose(){
            super.dispose();
        }
    };

    // ***************************************
    // Classes for painting an image on canvas
    // ***************************************

    static class TrayIconCanvas extends IconCanvas {
        TrayIcon target;
        boolean autosize;

        TrayIconCanvas(TrayIcon target, int width, int height) {
            super(width, height);
            this.target = target;
        }

        // Invoke on EDT.
        protected void repaintImage(boolean doClear) {
            boolean old_autosize = autosize;
            autosize = target.isImageAutoSize();

            curW = autosize ? width : image.getWidth(observer);
            curH = autosize ? height : image.getHeight(observer);

            super.repaintImage(doClear || (old_autosize != autosize));
        }
    }

    static class IconCanvas extends Canvas {
        volatile Image image;
        IconObserver observer;
        int width, height;
        int curW, curH;

        IconCanvas(int width, int height) {
            this.width = curW = width;
            this.height = curH = height;
        }

        // Invoke on EDT.
        public void updateImage(Image image) {
            this.image = image;
            if (observer == null) {
                observer = new IconObserver();
            }
            repaintImage(true);
        }

        // Invoke on EDT.
        protected void repaintImage(boolean doClear) {
            Graphics g = getGraphics();
            if (g != null) {
                try {
                    if (isVisible()) {
                        if (doClear) {
                            update(g);
                        } else {
                            paint(g);
                        }
                    }
                } finally {
                    g.dispose();
                }
            }
        }

        // Invoke on EDT.
        public void paint(Graphics g) {
            if (g != null && curW > 0 && curH > 0) {
                BufferedImage bufImage = new BufferedImage(curW, curH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gr = bufImage.createGraphics();
                if (gr != null) {
                    try {
                        gr.setColor(getBackground());
                        gr.fillRect(0, 0, curW, curH);
                        gr.drawImage(image, 0, 0, curW, curH, observer);
                        gr.dispose();

                        g.drawImage(bufImage, 0, 0, curW, curH, null);
                    } finally {
                        gr.dispose();
                    }
                }
            }
        }

        class IconObserver implements ImageObserver {
            public boolean imageUpdate(final Image image, final int flags, int x, int y, int width, int height) {
                if (image != IconCanvas.this.image || // if the image has been changed
                    !IconCanvas.this.isVisible())
                {
                    return false;
                }
                if ((flags & (ImageObserver.FRAMEBITS | ImageObserver.ALLBITS |
                              ImageObserver.WIDTH | ImageObserver.HEIGHT)) != 0)
                {
                    SunToolkit.executeOnEventHandlerThread(IconCanvas.this, new Runnable() {
                            public void run() {
                                repaintImage(false);
                            }
                        });
                }
                return (flags & ImageObserver.ALLBITS) == 0;
            }
        }
    }

    // ***************************************
    // Classes for toolitp and balloon windows
    // ***************************************

    static class Tooltip extends InfoWindow {
        XTrayIconPeer xtiPeer;
        Label textLabel = new Label("");
        Runnable starter = new Runnable() {
                public void run() {
                    display();
                }};

        final static int TOOLTIP_SHOW_TIME = 10000;
        final static int TOOLTIP_START_DELAY_TIME = 1000;
        final static int TOOLTIP_MAX_LENGTH = 64;
        final static int TOOLTIP_MOUSE_CURSOR_INDENT = 5;
        final static Color TOOLTIP_BACKGROUND_COLOR = new Color(255, 255, 220);
        final static Font TOOLTIP_TEXT_FONT = XWindow.getDefaultFont();

        Tooltip(XTrayIconPeer xtiPeer, Frame parent) {
            super(parent, Color.black);
            this.xtiPeer = xtiPeer;

            suppressWarningString(this);

            setCloser(null, TOOLTIP_SHOW_TIME);
            textLabel.setBackground(TOOLTIP_BACKGROUND_COLOR);
            textLabel.setFont(TOOLTIP_TEXT_FONT);
            add(textLabel);
        }

        /*
         * WARNING: this method is executed on Toolkit thread!
         */
        void display() {
            String tip = xtiPeer.tooltipString;
            if (tip == null) {
                return;
            } else if (tip.length() >  TOOLTIP_MAX_LENGTH) {
                textLabel.setText(tip.substring(0, TOOLTIP_MAX_LENGTH));
            } else {
                textLabel.setText(tip);
            }

            // Execute on EDT to avoid deadlock (see 6280857).
            SunToolkit.executeOnEventHandlerThread(xtiPeer.target, new Runnable() {
                    public void run() {
                        if (xtiPeer.isDisposed()) {
                            return;
                        }
                        Point pointer = (Point)AccessController.doPrivileged(new PrivilegedAction() {
                                public Object run() {
                                    if (!isPointerOverTrayIcon(xtiPeer.getBounds())) {
                                        return null;
                                    }
                                    return MouseInfo.getPointerInfo().getLocation();
                                }
                            });
                        if (pointer == null) {
                            return;
                        }
                        show(new Point(pointer.x, pointer.y), TOOLTIP_MOUSE_CURSOR_INDENT);
                    }
                });
        }

        void enter() {
            XToolkit.schedule(starter, TOOLTIP_START_DELAY_TIME);
        }

        void exit() {
            XToolkit.remove(starter);
            if (isVisible()) {
                hide();
            }
        }

        boolean isPointerOverTrayIcon(Rectangle trayRect) {
            Point p = MouseInfo.getPointerInfo().getLocation();
            return !(p.x < trayRect.x || p.x > (trayRect.x + trayRect.width) ||
                     p.y < trayRect.y || p.y > (trayRect.y + trayRect.height));
        }
    }

    static class Balloon extends InfoWindow {
        final static int BALLOON_SHOW_TIME = 10000;
        final static int BALLOON_TEXT_MAX_LENGTH = 256;
        final static int BALLOON_WORD_LINE_MAX_LENGTH = 16;
        final static int BALLOON_WORD_LINE_MAX_COUNT = 4;
        final static int BALLOON_ICON_WIDTH = 32;
        final static int BALLOON_ICON_HEIGHT = 32;
        final static int BALLOON_TRAY_ICON_INDENT = 0;
        final static Color BALLOON_CAPTION_BACKGROUND_COLOR = new Color(200, 200 ,255);
        final static Font BALLOON_CAPTION_FONT = new Font(Font.DIALOG, Font.BOLD, 12);

        XTrayIconPeer xtiPeer;
        Panel mainPanel = new Panel();
        Panel captionPanel = new Panel();
        Label captionLabel = new Label("");
        Button closeButton = new Button("X");
        Panel textPanel = new Panel();
        IconCanvas iconCanvas = new IconCanvas(BALLOON_ICON_WIDTH, BALLOON_ICON_HEIGHT);
        Label[] lineLabels = new Label[BALLOON_WORD_LINE_MAX_COUNT];
        ActionPerformer ap = new ActionPerformer();

        Image iconImage;
        Image errorImage;
        Image warnImage;
        Image infoImage;
        boolean gtkImagesLoaded;

        Displayer displayer = new Displayer();

        Balloon(final XTrayIconPeer xtiPeer, Frame parent) {
            super(parent, new Color(90, 80 ,190));
            this.xtiPeer = xtiPeer;

            suppressWarningString(this);

            setCloser(new Runnable() {
                    public void run() {
                        if (textPanel != null) {
                            textPanel.removeAll();
                            textPanel.setSize(0, 0);
                            iconCanvas.setSize(0, 0);
                            XToolkit.awtLock();
                            try {
                                displayer.isDisplayed = false;
                                XToolkit.awtLockNotifyAll();
                            } finally {
                                XToolkit.awtUnlock();
                            }
                        }
                    }
                }, BALLOON_SHOW_TIME);

            add(mainPanel);

            captionLabel.setFont(BALLOON_CAPTION_FONT);
            captionLabel.addMouseListener(ap);

            captionPanel.setLayout(new BorderLayout());
            captionPanel.add(captionLabel, BorderLayout.WEST);
            captionPanel.add(closeButton, BorderLayout.EAST);
            captionPanel.setBackground(BALLOON_CAPTION_BACKGROUND_COLOR);
            captionPanel.addMouseListener(ap);

            closeButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        hide();
                    }
                });

            mainPanel.setLayout(new BorderLayout());
            mainPanel.setBackground(Color.white);
            mainPanel.add(captionPanel, BorderLayout.NORTH);
            mainPanel.add(iconCanvas, BorderLayout.WEST);
            mainPanel.add(textPanel, BorderLayout.CENTER);

            iconCanvas.addMouseListener(ap);

            for (int i = 0; i < BALLOON_WORD_LINE_MAX_COUNT; i++) {
                lineLabels[i] = new Label();
                lineLabels[i].addMouseListener(ap);
                lineLabels[i].setBackground(Color.white);
            }

            displayer.start();
        }

        void display(String caption, String text, String messageType) {
            if (!gtkImagesLoaded) {
                loadGtkImages();
            }
            displayer.display(caption, text, messageType);
        }

        private void _display(String caption, String text, String messageType) {
            captionLabel.setText(caption);

            BreakIterator iter = BreakIterator.getWordInstance();
            if (text != null) {
                iter.setText(text);
                int start = iter.first(), end;
                int nLines = 0;

                do {
                    end = iter.next();

                    if (end == BreakIterator.DONE ||
                        text.substring(start, end).length() >= 50)
                    {
                        lineLabels[nLines].setText(text.substring(start, end == BreakIterator.DONE ?
                                                                  iter.last() : end));
                        textPanel.add(lineLabels[nLines++]);
                        start = end;
                    }
                    if (nLines == BALLOON_WORD_LINE_MAX_COUNT) {
                        if (end != BreakIterator.DONE) {
                            lineLabels[nLines - 1].setText(
                                new String(lineLabels[nLines - 1].getText() + " ..."));
                        }
                        break;
                    }
                } while (end != BreakIterator.DONE);


                textPanel.setLayout(new GridLayout(nLines, 1));
            }

            if ("ERROR".equals(messageType)) {
                iconImage = errorImage;
            } else if ("WARNING".equals(messageType)) {
                iconImage = warnImage;
            } else if ("INFO".equals(messageType)) {
                iconImage = infoImage;
            } else {
                iconImage = null;
            }

            if (iconImage != null) {
                Dimension tpSize = textPanel.getSize();
                iconCanvas.setSize(BALLOON_ICON_WIDTH, (BALLOON_ICON_HEIGHT > tpSize.height ?
                                                        BALLOON_ICON_HEIGHT : tpSize.height));
            }

            SunToolkit.executeOnEventHandlerThread(xtiPeer.target, new Runnable() {
                    public void run() {
                        if (xtiPeer.isDisposed()) {
                            return;
                        }
                        Point parLoc = getParent().getLocationOnScreen();
                        Dimension parSize = getParent().getSize();
                        show(new Point(parLoc.x + parSize.width/2, parLoc.y + parSize.height/2),
                             BALLOON_TRAY_ICON_INDENT);
                        if (iconImage != null) {
                            iconCanvas.updateImage(iconImage); // call it after the show(..) above
                        }
                    }
                });
        }

        public void dispose() {
            displayer.interrupt();
            super.dispose();
        }

        void loadGtkImages() {
            if (!gtkImagesLoaded) {
                errorImage = (Image)Toolkit.getDefaultToolkit().getDesktopProperty(
                    "gtk.icon.gtk-dialog-error.6.rtl");
                warnImage = (Image)Toolkit.getDefaultToolkit().getDesktopProperty(
                    "gtk.icon.gtk-dialog-warning.6.rtl");
                infoImage = (Image)Toolkit.getDefaultToolkit().getDesktopProperty(
                    "gtk.icon.gtk-dialog-info.6.rtl");
                gtkImagesLoaded = true;
            }
        }

        class ActionPerformer extends MouseAdapter {
            public void mouseClicked(MouseEvent e) {
                // hide the balloon by any click
                hide();
                if (e.getButton() == MouseEvent.BUTTON1) {
                    ActionEvent aev = new ActionEvent(xtiPeer.target, ActionEvent.ACTION_PERFORMED,
                                                      xtiPeer.target.getActionCommand(),
                                                      e.getWhen(), e.getModifiers());
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(aev);
                }
            }
        }

        class Displayer extends Thread {
            final int MAX_CONCURRENT_MSGS = 10;

            ArrayBlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(MAX_CONCURRENT_MSGS);
            boolean isDisplayed;

            Displayer() {
                setDaemon(true);
            }

            public void run() {
                while (true) {
                    Message msg = null;
                    try {
                        msg = (Message)messageQueue.take();
                    } catch (InterruptedException e) {
                        return;
                    }

                    /*
                     * Wait till the previous message is displayed if any
                     */
                    XToolkit.awtLock();
                    try {
                        while (isDisplayed) {
                            try {
                                XToolkit.awtLockWait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        isDisplayed = true;
                    } finally {
                        XToolkit.awtUnlock();
                    }
                    _display(msg.caption, msg.text, msg.messageType);
                }
            }

            void display(String caption, String text, String messageType) {
                messageQueue.offer(new Message(caption, text, messageType));
            }
        }

        class Message {
            String caption, text, messageType;

            Message(String caption, String text, String messageType) {
                this.caption = caption;
                this.text = text;
                this.messageType = messageType;
            }
        }
    }

    static class InfoWindow extends Window {
        Container container;
        Closer closer;

        InfoWindow(Frame parent, Color borderColor) {
            super(parent);
            container = new Container() {
                    public Insets getInsets() {
                        return new Insets(1, 1, 1, 1);
                    }
                };
            setLayout(new BorderLayout());
            setBackground(borderColor);
            add(container, BorderLayout.CENTER);
            container.setLayout(new BorderLayout());

            closer = new Closer();
        }

        public Component add(Component c) {
            container.add(c, BorderLayout.CENTER);
            return c;
        }

        void setCloser(Runnable action, int time) {
            closer.set(action, time);
        }

        // Must be executed on EDT.
        protected void show(Point corner, int indent) {
            assert SunToolkit.isDispatchThreadForAppContext(InfoWindow.this);

            pack();

            Dimension size = getSize();
            // TODO: When 6356322 is fixed we should get screen bounds in
            // this way: eframe.getGraphicsConfiguration().getBounds().
            Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();

            if (corner.x < scrSize.width/2 && corner.y < scrSize.height/2) { // 1st square
                setLocation(corner.x + indent, corner.y + indent);

            } else if (corner.x >= scrSize.width/2 && corner.y < scrSize.height/2) { // 2nd square
                setLocation(corner.x - indent - size.width, corner.y + indent);

            } else if (corner.x < scrSize.width/2 && corner.y >= scrSize.height/2) { // 3rd square
                setLocation(corner.x + indent, corner.y - indent - size.height);

            } else if (corner.x >= scrSize.width/2 && corner.y >= scrSize.height/2) { // 4th square
                setLocation(corner.x - indent - size.width, corner.y - indent - size.height);
            }

            InfoWindow.super.show();
            InfoWindow.this.closer.schedule();
        }

        public void hide() {
            closer.close();
        }

        class Closer implements Runnable {
            Runnable action;
            int time;

            public void run() {
                doClose();
            }

            void set(Runnable action, int time) {
                this.action = action;
                this.time = time;
            }

            void schedule() {
                XToolkit.schedule(this, time);
            }

            void close() {
                XToolkit.remove(this);
                doClose();
            }

            // WARNING: this method may be executed on Toolkit thread.
            private void doClose() {
                SunToolkit.executeOnEventHandlerThread(InfoWindow.this, new Runnable() {
                        public void run() {
                            InfoWindow.super.hide();
                            invalidate();
                            if (action != null) {
                                action.run();
                            }
                        }
                    });
            }
        }
    }
}
