/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import sun.awt.AWTAccessor;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.peer.TrayIconPeer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static sun.awt.AWTAccessor.*;

public class CTrayIcon extends CFRetainedResource implements TrayIconPeer {
    private TrayIcon target;
    private PopupMenu popup;
    private JDialog messageDialog;
    private DialogEventHandler handler;

    // In order to construct MouseEvent object, we need to specify a
    // Component target. Because TrayIcon isn't Component's subclass,
    // we use this dummy frame instead
    private final Frame dummyFrame;

    // A bitmask that indicates what mouse buttons produce MOUSE_CLICKED events
    // on MOUSE_RELEASE. Click events are only generated if there were no drag
    // events between MOUSE_PRESSED and MOUSE_RELEASED for particular button
    private static int mouseClickButtons = 0;

    CTrayIcon(TrayIcon target) {
        super(0, true);

        this.messageDialog = null;
        this.handler = null;
        this.target = target;
        this.popup = target.getPopupMenu();
        this.dummyFrame = new Frame();
        setPtr(createModel());

        //if no one else is creating the peer.
        checkAndCreatePopupPeer();
        updateImage();
    }

    private CPopupMenu checkAndCreatePopupPeer() {
        CPopupMenu menuPeer = null;
        if (popup != null) {
            try {
                final MenuComponentAccessor acc = getMenuComponentAccessor();
                menuPeer = acc.getPeer(popup);
                if (menuPeer == null) {
                    popup.addNotify();
                    menuPeer = acc.getPeer(popup);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return menuPeer;
    }

    private long createModel() {
        return nativeCreate();
    }

    private long getModel() {
        return ptr;
    }

    private native long nativeCreate();

    //invocation from the AWTTrayIcon.m
    public long getPopupMenuModel(){
        if(popup == null) {
            PopupMenu popupMenu = target.getPopupMenu();
            if (popupMenu != null) {
                popup = popupMenu;
            } else {
                return 0L;
            }
        }
        return checkAndCreatePopupPeer().getModel();
    }

    /**
     * We display tray icon message as a small dialog with OK button.
     * This is lame, but JDK 1.6 does basically the same. There is a new
     * kind of window in Lion, NSPopover, so perhaps it could be used it
     * to implement better looking notifications.
     */
    public void displayMessage(final String caption, final String text,
                               final String messageType) {

        if (SwingUtilities.isEventDispatchThread()) {
            displayMessageOnEDT(caption, text, messageType);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        displayMessageOnEDT(caption, text, messageType);
                    }
                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Override
    public void dispose() {
        if (messageDialog != null) {
            disposeMessageDialog();
        }

        dummyFrame.dispose();

        if (popup != null) {
            popup.removeNotify();
        }

        LWCToolkit.targetDisposedPeer(target, this);
        target = null;

        super.dispose();
    }

    @Override
    public void setToolTip(String tooltip) {
        nativeSetToolTip(getModel(), tooltip);
    }

    //adds tooltip to the NSStatusBar's NSButton.
    private native void nativeSetToolTip(long trayIconModel, String tooltip);

    @Override
    public void showPopupMenu(int x, int y) {
        //Not used. The popupmenu is shown from the native code.
    }

    @Override
    public void updateImage() {
        Image image = target.getImage();
        if (image == null) return;

        MediaTracker tracker = new MediaTracker(new Button(""));
        tracker.addImage(image, 0);
        try {
            tracker.waitForAll();
        } catch (InterruptedException ignore) { }

        if (image.getWidth(null) <= 0 ||
            image.getHeight(null) <= 0)
        {
            return;
        }

        CImage cimage = CImage.getCreator().createFromImage(image);
        setNativeImage(getModel(), cimage.ptr, target.isImageAutoSize());
    }

    private native void setNativeImage(final long model, final long nsimage, final boolean autosize);

    private void postEvent(final AWTEvent event) {
        SunToolkit.executeOnEventHandlerThread(target, new Runnable() {
            public void run() {
                SunToolkit.postEvent(SunToolkit.targetToAppContext(target), event);
            }
        });
    }

    //invocation from the AWTTrayIcon.m
    private void handleMouseEvent(NSEvent nsEvent) {
        int buttonNumber = nsEvent.getButtonNumber();
        final SunToolkit tk = (SunToolkit)Toolkit.getDefaultToolkit();
        if ((buttonNumber > 2 && !tk.areExtraMouseButtonsEnabled())
                || buttonNumber > tk.getNumberOfButtons() - 1) {
            return;
        }

        int jeventType = NSEvent.nsToJavaEventType(nsEvent.getType());

        int jbuttonNumber = MouseEvent.NOBUTTON;
        int jclickCount = 0;
        if (jeventType != MouseEvent.MOUSE_MOVED) {
            jbuttonNumber = NSEvent.nsToJavaButton(buttonNumber);
            jclickCount = nsEvent.getClickCount();
        }

        int jmodifiers = NSEvent.nsToJavaModifiers(
                nsEvent.getModifierFlags());
        boolean isPopupTrigger = NSEvent.isPopupTrigger(jmodifiers);

        int eventButtonMask = (jbuttonNumber > 0)?
                MouseEvent.getMaskForButton(jbuttonNumber) : 0;
        long when = System.currentTimeMillis();

        if (jeventType == MouseEvent.MOUSE_PRESSED) {
            mouseClickButtons |= eventButtonMask;
        } else if (jeventType == MouseEvent.MOUSE_DRAGGED) {
            mouseClickButtons = 0;
        }

        // The MouseEvent's coordinates are relative to screen
        int absX = nsEvent.getAbsX();
        int absY = nsEvent.getAbsY();

        MouseEvent mouseEvent = new MouseEvent(dummyFrame, jeventType, when,
                jmodifiers, absX, absY, absX, absY, jclickCount, isPopupTrigger,
                jbuttonNumber);
        mouseEvent.setSource(target);
        postEvent(mouseEvent);

        // fire ACTION event
        if (jeventType == MouseEvent.MOUSE_PRESSED && isPopupTrigger) {
            final String cmd = target.getActionCommand();
            final ActionEvent event = new ActionEvent(target,
                    ActionEvent.ACTION_PERFORMED, cmd);
            postEvent(event);
        }

        // synthesize CLICKED event
        if (jeventType == MouseEvent.MOUSE_RELEASED) {
            if ((mouseClickButtons & eventButtonMask) != 0) {
                MouseEvent clickEvent = new MouseEvent(dummyFrame,
                        MouseEvent.MOUSE_CLICKED, when, jmodifiers, absX, absY,
                        absX, absY, jclickCount, isPopupTrigger, jbuttonNumber);
                clickEvent.setSource(target);
                postEvent(clickEvent);
            }

            mouseClickButtons &= ~eventButtonMask;
        }
    }

    private native Point2D nativeGetIconLocation(long trayIconModel);

    public void displayMessageOnEDT(String caption, String text,
                                    String messageType) {
        if (messageDialog != null) {
            disposeMessageDialog();
        }

        // obtain icon to show along the message
        Icon icon = getIconForMessageType(messageType);
        if (icon != null) {
            icon = new ImageIcon(scaleIcon(icon, 0.75));
        }

        // We want the message dialog text area to be about 1/8 of the screen
        // size. There is nothing special about this value, it's just makes the
        // message dialog to look nice
        Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int textWidth = screenSize.width / 8;

        // create dialog to show
        messageDialog = createMessageDialog(caption, text, textWidth, icon);

        // finally, show the dialog to user
        showMessageDialog();
    }

    /**
     * Creates dialog window used to display the message
     */
    private JDialog createMessageDialog(String caption, String text,
                                     int textWidth, Icon icon) {
        JDialog dialog;
        handler = new DialogEventHandler();

        JTextArea captionArea = null;
        if (caption != null) {
            captionArea = createTextArea(caption, textWidth, false, true);
        }

        JTextArea textArea = null;
        if (text != null){
            textArea = createTextArea(text, textWidth, true, false);
        }

        Object[] panels = null;
        if (captionArea != null) {
            if (textArea != null) {
                panels = new Object[] {captionArea, new JLabel(), textArea};
            } else {
                panels = new Object[] {captionArea};
            }
        } else {
           if (textArea != null) {
                panels = new Object[] {textArea};
            }
        }

        // We want message dialog with small title bar. There is a client
        // property property that does it, however, it must be set before
        // dialog's native window is created. This is why we create option
        // pane and dialog separately
        final JOptionPane op = new JOptionPane(panels);
        op.setIcon(icon);
        op.addPropertyChangeListener(handler);

        // Make Ok button small. Most likely won't work for L&F other then Aqua
        try {
            JPanel buttonPanel = (JPanel)op.getComponent(1);
            JButton ok = (JButton)buttonPanel.getComponent(0);
            ok.putClientProperty("JComponent.sizeVariant", "small");
        } catch (Throwable t) {
            // do nothing, we tried and failed, no big deal
        }

        dialog = new JDialog((Dialog) null);
        JRootPane rp = dialog.getRootPane();

        // gives us dialog window with small title bar and not zoomable
        rp.putClientProperty(CPlatformWindow.WINDOW_STYLE, "small");
        rp.putClientProperty(CPlatformWindow.WINDOW_ZOOMABLE, "false");

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setModal(false);
        dialog.setModalExclusionType(Dialog.ModalExclusionType.TOOLKIT_EXCLUDE);
        dialog.setAlwaysOnTop(true);
        dialog.setAutoRequestFocus(false);
        dialog.setResizable(false);
        dialog.setContentPane(op);

        dialog.addWindowListener(handler);

        // suppress security warning for untrusted windows
        AWTAccessor.getWindowAccessor().setTrayIconWindow(dialog, true);

        dialog.pack();

        return dialog;
    }

    private void showMessageDialog() {

        Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        Point2D iconLoc = nativeGetIconLocation(getModel());

        int dialogY = (int)iconLoc.getY();
        int dialogX = (int)iconLoc.getX();
        if (dialogX + messageDialog.getWidth() > screenSize.width) {
            dialogX = screenSize.width - messageDialog.getWidth();
        }

        messageDialog.setLocation(dialogX, dialogY);
        messageDialog.setVisible(true);
    }

   private void disposeMessageDialog() {
        if (SwingUtilities.isEventDispatchThread()) {
            disposeMessageDialogOnEDT();
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        disposeMessageDialogOnEDT();
                    }
                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
   }

    private void disposeMessageDialogOnEDT() {
        if (messageDialog != null) {
            messageDialog.removeWindowListener(handler);
            messageDialog.removePropertyChangeListener(handler);
            messageDialog.dispose();

            messageDialog = null;
            handler = null;
        }
    }

    /**
     * Scales an icon using specified scale factor
     *
     * @param icon        icon to scale
     * @param scaleFactor scale factor to use
     * @return scaled icon as BuffedredImage
     */
    private static BufferedImage scaleIcon(Icon icon, double scaleFactor) {
        if (icon == null) {
            return null;
        }

        int w = icon.getIconWidth();
        int h = icon.getIconHeight();

        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        // convert icon into image
        BufferedImage iconImage = gc.createCompatibleImage(w, h,
                Transparency.TRANSLUCENT);
        Graphics2D g = iconImage.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        // and scale it nicely
        int scaledW = (int) (w * scaleFactor);
        int scaledH = (int) (h * scaleFactor);
        BufferedImage scaledImage = gc.createCompatibleImage(scaledW, scaledH,
                Transparency.TRANSLUCENT);
        g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(iconImage, 0, 0, scaledW, scaledH, null);
        g.dispose();

        return scaledImage;
    }


    /**
     * Gets Aqua icon used in message dialog.
     */
    private static Icon getIconForMessageType(String messageType) {
        if (messageType.equals("ERROR")) {
            return UIManager.getIcon("OptionPane.errorIcon");
        } else if (messageType.equals("WARNING")) {
            return UIManager.getIcon("OptionPane.warningIcon");
        } else {
            // this is just an application icon
            return UIManager.getIcon("OptionPane.informationIcon");
        }
    }

    private static JTextArea createTextArea(String text, int width,
                                            boolean isSmall, boolean isBold) {
        JTextArea textArea = new JTextArea(text);

        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setBorder(null);
        textArea.setBackground(new JLabel().getBackground());

        if (isSmall) {
            textArea.putClientProperty("JComponent.sizeVariant", "small");
        }

        if (isBold) {
            Font font = textArea.getFont();
            Font boldFont = new Font(font.getName(), Font.BOLD, font.getSize());
            textArea.setFont(boldFont);
        }

        textArea.setSize(width, 1);

        return textArea;
    }

    /**
     * Implements all the Listeners needed by message dialog
     */
    private final class DialogEventHandler extends WindowAdapter
            implements PropertyChangeListener {

        public void windowClosing(WindowEvent we) {
                disposeMessageDialog();
        }

        public void propertyChange(PropertyChangeEvent e) {
            if (messageDialog == null) {
                return;
            }

            String prop = e.getPropertyName();
            Container cp = messageDialog.getContentPane();

            if (messageDialog.isVisible() && e.getSource() == cp &&
                    (prop.equals(JOptionPane.VALUE_PROPERTY))) {
                disposeMessageDialog();
            }
        }
    }
}

