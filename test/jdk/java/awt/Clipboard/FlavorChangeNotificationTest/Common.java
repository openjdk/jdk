/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

public class Common {}

class FlavorListenerImpl implements FlavorListener {
    public boolean notified1, notified2;
    private int count;
    public void flavorsChanged(FlavorEvent evt) {
        switch (count) {
            case 0:
                notified1 = true;
                break;
            case 1:
                notified2 = true;
                break;
        }
        count++;
        System.err.println("listener's " + this +
                " flavorChanged() called " + count + " time");
    }
    public String toString() {
        return "notified1=" + notified1 + " notified2=" + notified2 +
                " count=" + count;
    }
};

 class Util {
    public static void setClipboardContents(Clipboard cb,
                                            Transferable contents,
                                            ClipboardOwner owner) {
        while (true) {
            try {
                cb.setContents(contents, owner);
                return;
            } catch (IllegalStateException ise) {
                ise.printStackTrace();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    public static Image createImage() {
        int w = 100;
        int h = 100;
        int[] pix = new int[w * h];

        int index = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int red = 127;
                int green = 127;
                int blue = y > h / 2 ? 127 : 0;
                int alpha = 255;
                if (x < w / 4 && y < h / 4) {
                    alpha = 0;
                    red = 0;
                }
                pix[index++] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }

        return Toolkit
                .getDefaultToolkit().
                        createImage(new java.awt.image.MemoryImageSource(
                                w, h, pix, 0, w
                        ));
    }

}


class TransferableUnion implements Transferable {

    private static final DataFlavor[] ZERO_LENGTH_ARRAY = new DataFlavor[0];

    private final Transferable TRANSF1, TRANSF2;

    private final DataFlavor[] FLAVORS;


    public TransferableUnion(Transferable t1, Transferable t2) {
        if (t1 == null) {
            throw new NullPointerException("t1");
        }
        if (t2 == null) {
            throw new NullPointerException("t2");
        }

        this.TRANSF1 = t1;
        this.TRANSF2 = t2;

        java.util.Set<DataFlavor> flavorSet = new java.util.HashSet<>();
        flavorSet.addAll(java.util.Arrays.asList(t1.getTransferDataFlavors()));
        flavorSet.addAll(java.util.Arrays.asList(t2.getTransferDataFlavors()));

        FLAVORS = flavorSet.toArray(ZERO_LENGTH_ARRAY);
    }

    /**
     * Returns an array of flavors in which this <code>Transferable</code>
     * can provide the data.
     */
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    /**
     * Returns whether the requested flavor is supported by this
     * <code>Transferable</code>.
     *
     * @param flavor the requested flavor for the data
     * @throws NullPointerException if flavor is <code>null</code>
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor == null) {
            throw new NullPointerException("flavor");
        }

        return TRANSF1.isDataFlavorSupported(flavor)
                || TRANSF2.isDataFlavorSupported(flavor);
    }

    /**
     * Returns the <code>Transferable</code>'s data in the requested
     * <code>DataFlavor</code> if possible.
     *
     * @param flavor the requested flavor for the data
     * @return the data in the requested flavor
     * @throws UnsupportedFlavorException if the requested data flavor is
     *         not supported by this Transferable
     * @throws IOException if an <code>IOException</code> occurs while
     *         retrieving the data.
     * @throws NullPointerException if flavor is <code>null</code>
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, java.io.IOException {

        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        java.io.IOException ioexc = null;

        if (TRANSF1.isDataFlavorSupported(flavor)) {
            try {
                return TRANSF1.getTransferData(flavor);
            } catch (java.io.IOException exc) {
                ioexc = exc;
            }
        }

        if (TRANSF2.isDataFlavorSupported(flavor)) {
            return TRANSF2.getTransferData(flavor);
        }

        if (ioexc != null) {
            throw ioexc;
        }

        // unreachable
        return null;
    }

}

/**
 * A <code>Transferable</code> that implements the capability required
 * to transfer an <code>Image</code>.
 *
 * This <code>Transferable</code> properly supports
 * <code>DataFlavor.imageFlavor</code>
 * and all equivalent flavors.
 * No other <code>DataFlavor</code>s are supported.
 *
 * @see java.awt.datatransfer.DataFlavor.imageFlavor
 */
class ImageSelection implements Transferable {

    private static final DataFlavor[] flavors = { DataFlavor.imageFlavor };

    private Image data;

    /**
     * Creates a <code>Transferable</code> capable of transferring
     * the specified <code>Image</code>.
     */
    public ImageSelection(Image data) {
        this.data = data;
    }

    /**
     * Returns an array of flavors in which this <code>Transferable</code>
     * can provide the data. <code>DataFlavor.stringFlavor</code>
     * is supported.
     *
     * @return an array of length one, whose element is <code>DataFlavor.
     *         imageFlavor</code>
     */
    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    /**
     * Returns whether the requested flavor is supported by this
     * <code>Transferable</code>.
     *
     * @param flavor the requested flavor for the data
     * @return true if <code>flavor</code> is equal to
     *   <code>DataFlavor.imageFlavor</code>;
     *   false otherwise
     * @throws NullPointerException if flavor is <code>null</code>
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (int i = 0; i < flavors.length; i++) {
            if (flavor.equals(flavors[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the <code>Transferable</code>'s data in the requested
     * <code>DataFlavor</code> if possible. If the desired flavor is
     * <code>DataFlavor.imageFlavor</code>, or an equivalent flavor,
     * the <code>Image</code> representing the selection is
     * returned.
     *
     * @param flavor the requested flavor for the data
     * @return the data in the requested flavor, as outlined above
     * @throws UnsupportedFlavorException if the requested data flavor is
     *         not equivalent to <code>DataFlavor.imageFlavor</code>
     * @throws IOException if an <code>IOException</code> occurs while
     *         retrieving the data. By default, <code>ImageSelection</code>
     *         never throws this exception, but a subclass may.
     * @throws NullPointerException if flavor is <code>null</code>
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, java.io.IOException {
        if (flavor.equals(DataFlavor.imageFlavor)) {
            return data;
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

} // class ImageSelection

