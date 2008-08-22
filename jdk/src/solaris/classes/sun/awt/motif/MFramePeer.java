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
import sun.awt.motif.MInputMethodControl;
import sun.awt.im.*;
import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;
import sun.awt.image.ImageRepresentation;
import sun.awt.image.ToolkitImage;

class MFramePeer extends MWindowPeer implements FramePeer, MInputMethodControl {
    static Vector allFrames = new Vector();

    // XXX: Stub out for now.  Need to propagate to normal size hints.
    public void setMaximizedBounds(Rectangle b) {}

    public void create(MComponentPeer parent, Object arg) {
        super.create( parent );
    }

    MFramePeer(Frame target) {
        super();
        // set the window attributes for this Frame
        winAttr.nativeDecor = !target.isUndecorated();
        winAttr.initialFocus = true;
        winAttr.isResizable =  target.isResizable();
        winAttr.initialState = target.getState();
        winAttr.title = target.getTitle();
        winAttr.icon = target.getIconImage();
        if (winAttr.nativeDecor) {
            winAttr.decorations = winAttr.AWT_DECOR_ALL;
        } else {
            winAttr.decorations = winAttr.AWT_DECOR_NONE;
        }

        // for input method windows, use minimal decorations
        if (target instanceof InputMethodWindow) {
            winAttr.initialFocus = false;
            winAttr.decorations = (winAttr.AWT_DECOR_TITLE | winAttr.AWT_DECOR_BORDER);
        }

        // create and init native component
        init( target);
    if (winAttr.icon != null) {
        setIconImage(winAttr.icon);
    }
        allFrames.addElement(this);
    }

    public void setTitle(String title) {
        pSetTitle(title);
    }

    protected void disposeImpl() {
        allFrames.removeElement(this);
        super.disposeImpl();
    }

    public void setMenuBar(MenuBar mb) {
        MMenuBarPeer mbpeer = (MMenuBarPeer) MToolkit.targetToPeer(mb);
        pSetMenuBar(mbpeer);

        Rectangle r = target.bounds();

        pReshape(r.x, r.y, r.width, r.height);
        if (target.isVisible()) {
            target.validate();
        }
    }

    public void setIconImage(Image im) {
        int width;
        int height;
        GraphicsConfiguration defaultGC;
        if (im != null) {  // 4633887  Avoid Null pointer exception.
        if (im instanceof ToolkitImage) {
            ImageRepresentation ir = ((ToolkitImage)im).getImageRep();
            ir.reconstruct(ImageObserver.ALLBITS);
            width = ir.getWidth();
            height = ir.getHeight();
        }
        else {
            width = im.getWidth(null);
            height = im.getHeight(null);
        }
        if (pGetIconSize(width, height)) {
            //Icons are displayed using the default visual, so create image
            //using default GraphicsConfiguration
            defaultGC = getGraphicsConfiguration().getDevice().
                getDefaultConfiguration();
            ColorModel model = defaultGC.getColorModel();
            WritableRaster raster =
                model.createCompatibleWritableRaster(iconWidth, iconHeight);
            Image image = new BufferedImage(model, raster,
                                            model.isAlphaPremultiplied(),
                                            null);

            // ARGB BufferedImage to hunt for transparent pixels
            BufferedImage bimage =
                new BufferedImage(iconWidth, iconHeight,
                                  BufferedImage.TYPE_INT_ARGB);
            ColorModel alphaCheck = bimage.getColorModel();
            Graphics g = image.getGraphics();
            Graphics big = bimage.getGraphics();
            try {
                g.drawImage(im, 0, 0, iconWidth, iconHeight, null);
                big.drawImage(im, 0, 0, iconWidth, iconHeight, null);
            } finally {
                g.dispose();
                big.dispose();
            }

            DataBuffer db = ((BufferedImage)image).getRaster().getDataBuffer();
            DataBuffer bidb = bimage.getRaster().getDataBuffer();
            byte[] bytedata = null;
            int[] intdata = null;
            int bidbLen = bidb.getSize();
            int imgDataIdx;
            //Get native RGB value for window background color
            //Should work for byte as well as int
            int bgRGB = getNativeColor(SystemColor.window, defaultGC);

            /* My first attempt at a solution to bug 4175560 was to use
             * the iconMask and iconPixmap attributes of Windows.
             * This worked fine on CDE/dtwm, however olwm displayed only
             * single color icons (white on background).  Instead, the
             * fix gets the default background window color and replaces
             * transparent pixels in the icon image with this color.  This
             * solutions works well with dtwm as well as olwm.
             */

            for (imgDataIdx = 0; imgDataIdx < bidbLen; imgDataIdx++) {
                if (alphaCheck.getAlpha(bidb.getElem(imgDataIdx)) == 0 ) {
                    //Assuming single data bank
                    db.setElem(imgDataIdx, bgRGB);
                }
            }
            short[] ushortdata = null;
            if (db instanceof DataBufferByte) {
                // Pseudocolor data
                bytedata = ((DataBufferByte)db).getData();
            }
            else if (db instanceof DataBufferInt) {
                // Truecolor data
                intdata = ((DataBufferInt) db).getData();
            }
            else if (db instanceof DataBufferUShort) {
                // Truecolor data
                ushortdata = ((DataBufferUShort) db).getData();
            }
               pSetIconImage(bytedata, intdata, ushortdata,
                          iconWidth, iconHeight);
        }
        }
    }

    native boolean pGetIconSize(int widthHint, int heightHint);

    // [jk] added ushortData for 16-bpp displays
    native void pSetIconImage(byte[] byteData,
                              int[] intData,
                              short[] ushortData,
                              int iconWidth, int iconHeight);

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


    /**
     * Called to inform the Frame that it has moved.
     */
    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleMoved(int x, int y) {
        postEvent(new ComponentEvent(target, ComponentEvent.COMPONENT_MOVED));
    }

    static final int CROSSHAIR_INSET = 5;

    static final int BUTTON_Y = CROSSHAIR_INSET + 1;
    static final int BUTTON_W = 17;
    static final int BUTTON_H = 17;

    static final int SYS_MENU_X = CROSSHAIR_INSET + 1;
    static final int SYS_MENU_CONTAINED_X = SYS_MENU_X + 5;
    static final int SYS_MENU_CONTAINED_Y = BUTTON_Y + 7;
    static final int SYS_MENU_CONTAINED_W = 8;
    static final int SYS_MENU_CONTAINED_H = 3;

    static final int MAXIMIZE_X_DIFF = CROSSHAIR_INSET + BUTTON_W;
    static final int MAXIMIZE_CONTAINED_X_DIFF = MAXIMIZE_X_DIFF - 5;
    static final int MAXIMIZE_CONTAINED_Y = BUTTON_Y + 5;
    static final int MAXIMIZE_CONTAINED_W = 8;
    static final int MAXIMIZE_CONTAINED_H = 8;

    static final int MINIMIZE_X_DIFF = MAXIMIZE_X_DIFF + BUTTON_W;
    static final int MINIMIZE_CONTAINED_X_DIFF = MINIMIZE_X_DIFF - 7;
    static final int MINIMIZE_CONTAINED_Y = BUTTON_Y + 7;
    static final int MINIMIZE_CONTAINED_W = 3;
    static final int MINIMIZE_CONTAINED_H = 3;

    static final int TITLE_X = SYS_MENU_X + BUTTON_W;
    static final int TITLE_W_DIFF = BUTTON_W * 3 + CROSSHAIR_INSET * 2 - 1;
    static final int TITLE_MID_Y = BUTTON_Y + (BUTTON_H / 2);

    static final int MENUBAR_X = CROSSHAIR_INSET + 1;
    static final int MENUBAR_Y = BUTTON_Y + BUTTON_H;

    static final int HORIZ_RESIZE_INSET = CROSSHAIR_INSET + BUTTON_H;
    static final int VERT_RESIZE_INSET = CROSSHAIR_INSET + BUTTON_W;


    /*
     * Print the native component by rendering the Motif look ourselves.
     * We also explicitly print the MenuBar since a MenuBar isn't a subclass
     * of Component (and thus it has no "print" method which gets called by
     * default).
     */
    public void print(Graphics g) {
        super.print(g);

        Frame f = (Frame)target;
        Insets finsets = f.getInsets();
        Dimension fsize = f.getSize();

        Color bg = f.getBackground();
        Color fg = f.getForeground();
        Color highlight = bg.brighter();
        Color shadow = bg.darker();

        // Well, we could query for the currently running window manager
        // and base the look on that, or we could just always do dtwm.
        // aim, tball, and levenson all agree we'll just do dtwm.

        if (hasDecorations(MWindowAttributes.AWT_DECOR_BORDER)) {

            // top outer -- because we'll most likely be drawing on white paper,
            // for aesthetic reasons, don't make any part of the outer border
            // pure white
            if (highlight.equals(Color.white)) {
                g.setColor(new Color(230, 230, 230));
            }
            else {
                g.setColor(highlight);
            }
            g.drawLine(0, 0, fsize.width, 0);
            g.drawLine(0, 1, fsize.width - 1, 1);

            // left outer
            // if (highlight.equals(Color.white)) {
            //     g.setColor(new Color(230, 230, 230));
            // }
            // else {
            //     g.setColor(highlight);
            // }
            g.drawLine(0, 0, 0, fsize.height);
            g.drawLine(1, 0, 1, fsize.height - 1);

            // bottom cross-hair
            g.setColor(highlight);
            g.drawLine(CROSSHAIR_INSET + 1, fsize.height - CROSSHAIR_INSET,
                       fsize.width - CROSSHAIR_INSET,
                       fsize.height - CROSSHAIR_INSET);

            // right cross-hair
            // g.setColor(highlight);
            g.drawLine(fsize.width - CROSSHAIR_INSET, CROSSHAIR_INSET + 1,
                       fsize.width - CROSSHAIR_INSET,
                       fsize.height - CROSSHAIR_INSET);

            // bottom outer
            g.setColor(shadow);
            g.drawLine(1, fsize.height, fsize.width, fsize.height);
            g.drawLine(2, fsize.height - 1, fsize.width, fsize.height - 1);

            // right outer
            // g.setColor(shadow);
            g.drawLine(fsize.width, 1, fsize.width, fsize.height);
            g.drawLine(fsize.width - 1, 2, fsize.width - 1, fsize.height);

            // top cross-hair
            // g.setColor(shadow);
            g.drawLine(CROSSHAIR_INSET, CROSSHAIR_INSET,
                       fsize.width - CROSSHAIR_INSET, CROSSHAIR_INSET);

            // left cross-hair
            // g.setColor(shadow);
            g.drawLine(CROSSHAIR_INSET, CROSSHAIR_INSET, CROSSHAIR_INSET,
                   fsize.height - CROSSHAIR_INSET);
        }

        if (hasDecorations(MWindowAttributes.AWT_DECOR_TITLE)) {

            if (hasDecorations(MWindowAttributes.AWT_DECOR_MENU)) {

                // system menu
                g.setColor(bg);
                g.fill3DRect(SYS_MENU_X, BUTTON_Y, BUTTON_W, BUTTON_H, true);
                g.fill3DRect(SYS_MENU_CONTAINED_X, SYS_MENU_CONTAINED_Y,
                             SYS_MENU_CONTAINED_W, SYS_MENU_CONTAINED_H, true);
            }

            // title bar
            // g.setColor(bg);
            g.fill3DRect(TITLE_X, BUTTON_Y, fsize.width - TITLE_W_DIFF, BUTTON_H,
                         true);

            if (hasDecorations(MWindowAttributes.AWT_DECOR_MINIMIZE)) {

                // minimize button
                // g.setColor(bg);
                g.fill3DRect(fsize.width - MINIMIZE_X_DIFF, BUTTON_Y, BUTTON_W,
                             BUTTON_H, true);
                g.fill3DRect(fsize.width - MINIMIZE_CONTAINED_X_DIFF,
                             MINIMIZE_CONTAINED_Y, MINIMIZE_CONTAINED_W,
                             MINIMIZE_CONTAINED_H, true);
            }

            if (hasDecorations(MWindowAttributes.AWT_DECOR_MAXIMIZE)) {

                // maximize button
                // g.setColor(bg);
                g.fill3DRect(fsize.width - MAXIMIZE_X_DIFF, BUTTON_Y, BUTTON_W,
                             BUTTON_H, true);
                g.fill3DRect(fsize.width - MAXIMIZE_CONTAINED_X_DIFF,
                             MAXIMIZE_CONTAINED_Y, MAXIMIZE_CONTAINED_W,
                             MAXIMIZE_CONTAINED_H, true);
            }

            // title bar text
            g.setColor(fg);
            Font sysfont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
            g.setFont(sysfont);
            FontMetrics sysfm = g.getFontMetrics();
            String ftitle = f.getTitle();
            g.drawString(ftitle,
                         ((TITLE_X + TITLE_X + fsize.width - TITLE_W_DIFF) / 2) -
                         (sysfm.stringWidth(ftitle) / 2),
                         TITLE_MID_Y + sysfm.getMaxDescent());
        }

        if (f.isResizable() &&
            hasDecorations(MWindowAttributes.AWT_DECOR_RESIZEH)) {

            // add resize cross hairs

            // upper-left horiz (shadow)
            g.setColor(shadow);
            g.drawLine(1, HORIZ_RESIZE_INSET, CROSSHAIR_INSET,
                       HORIZ_RESIZE_INSET);
            // upper-left vert (shadow)
            // g.setColor(shadow);
            g.drawLine(VERT_RESIZE_INSET, 1, VERT_RESIZE_INSET, CROSSHAIR_INSET);
            // upper-right horiz (shadow)
            // g.setColor(shadow);
            g.drawLine(fsize.width - CROSSHAIR_INSET + 1, HORIZ_RESIZE_INSET,
                       fsize.width, HORIZ_RESIZE_INSET);
            // upper-right vert (shadow)
            // g.setColor(shadow);
            g.drawLine(fsize.width - VERT_RESIZE_INSET - 1, 2,
                       fsize.width - VERT_RESIZE_INSET - 1, CROSSHAIR_INSET + 1);
            // lower-left horiz (shadow)
            // g.setColor(shadow);
            g.drawLine(1, fsize.height - HORIZ_RESIZE_INSET - 1,
                       CROSSHAIR_INSET, fsize.height - HORIZ_RESIZE_INSET - 1);
            // lower-left vert (shadow)
            // g.setColor(shadow);
            g.drawLine(VERT_RESIZE_INSET, fsize.height - CROSSHAIR_INSET + 1,
                       VERT_RESIZE_INSET, fsize.height);
            // lower-right horiz (shadow)
            // g.setColor(shadow);
            g.drawLine(fsize.width - CROSSHAIR_INSET + 1,
                       fsize.height - HORIZ_RESIZE_INSET - 1, fsize.width,
                       fsize.height - HORIZ_RESIZE_INSET - 1);
            // lower-right vert (shadow)
            // g.setColor(shadow);
            g.drawLine(fsize.width - VERT_RESIZE_INSET - 1,
                       fsize.height - CROSSHAIR_INSET + 1,
                       fsize.width - VERT_RESIZE_INSET - 1, fsize.height);

            // upper-left horiz (highlight)
            g.setColor(highlight);
            g.drawLine(2, HORIZ_RESIZE_INSET + 1, CROSSHAIR_INSET,
                       HORIZ_RESIZE_INSET + 1);
            // upper-left vert (highlight)
            // g.setColor(highlight);
            g.drawLine(VERT_RESIZE_INSET + 1, 2, VERT_RESIZE_INSET + 1,
                       CROSSHAIR_INSET);
            // upper-right horiz (highlight)
            // g.setColor(highlight);
            g.drawLine(fsize.width - CROSSHAIR_INSET + 1,
                       HORIZ_RESIZE_INSET + 1, fsize.width - 1,
                       HORIZ_RESIZE_INSET + 1);
            // upper-right vert (highlight)
            // g.setColor(highlight);
            g.drawLine(fsize.width - VERT_RESIZE_INSET, 2,
                       fsize.width - VERT_RESIZE_INSET, CROSSHAIR_INSET);
            // lower-left horiz (highlight)
            // g.setColor(highlight);
            g.drawLine(2, fsize.height - HORIZ_RESIZE_INSET, CROSSHAIR_INSET,
                       fsize.height - HORIZ_RESIZE_INSET);
            // lower-left vert (highlight)
            // g.setColor(highlight);
            g.drawLine(VERT_RESIZE_INSET + 1,
                       fsize.height - CROSSHAIR_INSET + 1,
                       VERT_RESIZE_INSET + 1, fsize.height - 1);
            // lower-right horiz (highlight)
            // g.setColor(highlight);
            g.drawLine(fsize.width - CROSSHAIR_INSET + 1,
                       fsize.height - HORIZ_RESIZE_INSET, fsize.width - 1,
                       fsize.height - HORIZ_RESIZE_INSET);
            // lower-right vert (highlight)
            // g.setColor(highlight);
            g.drawLine(fsize.width - VERT_RESIZE_INSET,
                       fsize.height - CROSSHAIR_INSET + 1,
                       fsize.width - VERT_RESIZE_INSET, fsize.height - 1);
        }

        MenuBar mb = f.getMenuBar();
        if (mb != null) {
            MMenuBarPeer peer = (MMenuBarPeer) MToolkit.targetToPeer(mb);
            if (peer != null) {
                Insets insets = getInsets();
                Graphics ng = g.create();
                int menubarX = 0;
                int menubarY = 0;
                if (hasDecorations(MWindowAttributes.AWT_DECOR_BORDER)) {
                    menubarX += CROSSHAIR_INSET + 1;
                    menubarY += CROSSHAIR_INSET + 1;
                }
                if (hasDecorations(MWindowAttributes.AWT_DECOR_TITLE)) {
                    menubarY += BUTTON_H;
                }
                try {
                    ng.translate(menubarX, menubarY);
                    peer.print(ng);
                } finally {
                    ng.dispose();
                }
            }
        }
    }

    // Saveunders are not done by Frame.
    void setSaveUnder(boolean state) {}

    /* Returns the native paint should be posted after setting new size
     */
    public boolean checkNativePaintOnSetBounds(int width, int height) {
        // Fix for 4418155. Undecorated Frame does not repaint
        // automticaly if shrinking. Should not wait for Expose
        return ((Frame)target).isUndecorated() ?
            ((width > oldWidth) || (height > oldHeight)):
            ((width != oldWidth) || (height != oldHeight));
    }

    public void setBoundsPrivate(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    public Rectangle getBoundsPrivate() {
        return getBounds();
    }

    @Override
    final boolean isTargetUndecorated() {
        return ((Frame)target).isUndecorated();
    }
}
