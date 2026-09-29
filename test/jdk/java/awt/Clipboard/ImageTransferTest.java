/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4397404 4720930
 * @summary tests that images of all supported native image formats
 *          are transferred properly
 * @key headful
 * @library /test/lib
 * @run main ImageTransferTest
 */

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ImageTransferTest {
    public static final int CODE_NOT_RETURNED = 100;
    public static final int CODE_CONSUMER_TEST_FAILED = 101;
    public static final int CODE_FAILURE = 102;

    private TImageProducer imPr;
    private int returnCode = CODE_NOT_RETURNED;

    public static void main(String[] args) throws Exception {
        ImageTransferTest imageTransferTest = new ImageTransferTest();
        imageTransferTest.init();
        imageTransferTest.start();
    }

    public void init() {
        imPr = new TImageProducer();
        imPr.begin();
    }

    public void start() throws Exception {
        String formats = "";

        String iniMsg = "Testing all native image formats from \n" +
            "SystemFlavorMap.getNativesForFlavor(DataFlavor.imageFlavor) \n";

        for (int i = 0; i < imPr.formats.length; i++) {
            formats += (imPr.formats[i] + " ");
        }
        System.out.println(iniMsg + formats);

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                TImageConsumer.class.getName(), formats
        );

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            returnCode = CODE_NOT_RETURNED;
        } else {
            returnCode = outputAnalyzer.getExitValue();
        }

        switch (returnCode) {
            case CODE_NOT_RETURNED:
                throw new RuntimeException("Child VM: failed to start");
            case CODE_FAILURE:
                throw new RuntimeException("Child VM: abnormal termination");
            case CODE_CONSUMER_TEST_FAILED:
                throw new RuntimeException("test failed: images in some " +
                    "native formats are not transferred properly: " +
                    "see output of child VM");
            default:
                boolean failed = false;
                String passedFormats = "";
                String failedFormats = "";

                for (int i = 0; i < imPr.passedArray.length; i++) {
                   if (imPr.passedArray[i]) passedFormats += imPr.formats[i] + " ";
                   else {
                       failed = true;
                       failedFormats += imPr.formats[i] + " ";
                   }
                }
                if (failed) {
                    throw new RuntimeException("test failed: images in following " +
                        "native formats are not transferred properly: " +
                        failedFormats);
                } else {
                    System.err.println("images in following native formats are " +
                        "transferred properly: " + passedFormats);
                }
        }
    }
}

abstract class ImageTransferer implements ClipboardOwner {

    static final String S_PASSED = "Y";
    static final String S_FAILED = "N";
    static final String S_BEGIN = "B";
    static final String S_BEGIN_ANSWER = "BA";
    static final String S_END = "E";

    Image image;

    Clipboard clipboard;

    String[] formats;
    int fi; // next format index


    ImageTransferer() {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        image = createImage();
    }

    abstract void notifyTransferSuccess(boolean status);

    private static Image createImage() {
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

        return Toolkit.getDefaultToolkit().
                   createImage(new MemoryImageSource(w, h, pix, 0, w));
    }

    static String[] retrieveFormatsToTest() {
        SystemFlavorMap sfm = (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();
        java.util.List ln = sfm.getNativesForFlavor(DataFlavor.imageFlavor);

        String osName = System.getProperty("os.name").toLowerCase();
        String sMETAFILEPICT = "METAFILEPICT";
        if (osName.indexOf("win") >= 0 && !ln.contains(sMETAFILEPICT)) {
            // for test failing on JDK without this fix
            ln.add(sMETAFILEPICT);
        }
        return (String[])ln.toArray(new String[ln.size()]);
    }

    static void leaveFormat(String format) {
        SystemFlavorMap sfm = (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();
        sfm.setFlavorsForNative(format,
                                new DataFlavor[] { DataFlavor.imageFlavor });
        sfm.setNativesForFlavor(DataFlavor.imageFlavor,
                                new String[] { format });
    }

    boolean areImagesIdentical(Image im1, Image im2) {
        if (formats[fi].equals("JFIF") || formats[fi].equals("image/jpeg") ||
                formats[fi].equals("GIF") || formats[fi].equals("image/gif")) {
            // JFIF and GIF are lossy formats
            return true;
        }
        int[] ib1 = getImageData(im1);
        int[] ib2 = getImageData(im2);

        if (ib1.length != ib2.length) {
            return false;
        }

        if (formats[fi].equals("PNG") ||
            formats[fi].equals("image/png") ||
            formats[fi].equals("image/x-png")) {
            // check alpha as well
            for (int i = 0; i < ib1.length; i++) {
               if (ib1[i] != ib2[i]) {
                   System.err.println("different pixels: " +
                       Integer.toHexString(ib1[i]) + " " +
                       Integer.toHexString(ib2[i]));
                   return false;
               }
            }
        } else {
            for (int i = 0; i < ib1.length; i++) {
               if ((ib1[i] & 0x00FFFFFF) != (ib2[i] & 0x00FFFFFF)) {
                   System.err.println("different pixels: " +
                       Integer.toHexString(ib1[i]) + " " +
                       Integer.toHexString(ib2[i]));
                   return false;
               }
            }
        }
        return true;
    }

    private static int[] getImageData(Image image) {
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bimage.createGraphics();
        try {
            g2d.drawImage(image, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }
        return bimage.getRGB(0, 0, width, height, null, 0, width);
    }

    static void setClipboardContents(Clipboard cb,
                                     Transferable contents,
                                     ClipboardOwner owner) {
        synchronized (cb) {
            boolean set = false;
            while (!set) {
                try {
                    cb.setContents(contents, owner);
                    set = true;
                } catch (IllegalStateException ise) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        }
    }

    static Transferable getClipboardContents(Clipboard cb,
                                             Object requestor) {
        synchronized (cb) {
            while (true) {
                try {
                    Transferable t = cb.getContents(requestor);
                    return t;
                } catch (IllegalStateException ise) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        }
    }
}

class TImageProducer extends ImageTransferer {

    boolean[] passedArray;

    private boolean isFirstCallOfLostOwnership = true;

    TImageProducer() {
        formats = retrieveFormatsToTest();
        passedArray = new boolean[formats.length];
    }

    void begin() {
        setClipboardContents(clipboard, new StringSelection(S_BEGIN), this);
    }

    public void lostOwnership(Clipboard cb, Transferable contents) {
        System.err.println("PRODUCER: lost clipboard ownership");

        Transferable t = getClipboardContents(cb, null);

        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            String msg = null;
            // for test going on if t.getTransferData() will throw an exception
            if (isFirstCallOfLostOwnership) {
                isFirstCallOfLostOwnership = false;
                msg = S_BEGIN_ANSWER;
            } else {
                msg = S_PASSED;
            }

            try {
                msg = (String)t.getTransferData(DataFlavor.stringFlavor);
                System.err.println("received message: " + msg);
            } catch (Exception e) {
                System.err.println("Can't getTransferData-message: " + e);
            }

            if (msg.equals(S_PASSED)) {
                notifyTransferSuccess(true);
            } else if (msg.equals(S_FAILED)) {
                notifyTransferSuccess(false);
            } else if (!msg.equals(S_BEGIN_ANSWER)) {
                throw new RuntimeException("wrong message in " +
                    "TImageProducer.lostOwnership(): " + msg +
                    "  (possibly due to bug 4683804)");
            }
        } else {
            throw new RuntimeException("DataFlavor.stringFlavor is not " +
                "supported by transferable in " +
                "TImageProducer.lostOwnership()");
        }

        if (fi < formats.length) {
            System.err.println("testing native image format " + formats[fi] +
                               "...");
            leaveFormat(formats[fi]);
            setClipboardContents(cb, new ImageSelection(image), this);
        } else {
            setClipboardContents(cb, new StringSelection(S_END), null);
        }
    }

    void notifyTransferSuccess(boolean status) {
        passedArray[fi] = status;
        fi++;
    }
}

class TImageConsumer extends ImageTransferer {

    private static final Object LOCK = new Object();

    private static boolean failed;

    public void lostOwnership(Clipboard cb, Transferable contents) {
        System.err.println("CONSUMER: lost clipboard ownership");

        Transferable t = getClipboardContents(cb, null);

        if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            Image im = null; //? image;
            try {
                im = (Image) t.getTransferData(DataFlavor.imageFlavor);
            } catch (Exception e) {
                System.err.println("Can't getTransferData-image: " + e);
                notifyTransferSuccess(false);
            }

            if (im == null) {
                System.err.println("getTransferData returned null");
                notifyTransferSuccess(false);
            } else if (areImagesIdentical(image, im)) {
                notifyTransferSuccess(true);
            } else {
                System.err.println("transferred image is different from " +
                                   "initial image");
                notifyTransferSuccess(false);
            }
        } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            // all image formats have been processed
            try {
                String msg = (String) t.getTransferData(DataFlavor.stringFlavor);
                System.err.println("received message: " + msg);
            } catch (Exception e) {
                System.err.println("Can't getTransferData-message: " + e);
            }
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
        } else {
            System.err.println("imageFlavor is not supported by transferable");
            notifyTransferSuccess(false);
        }
    }

    void notifyTransferSuccess(boolean status) {
        if (status) {
            System.err.println("format passed: " + formats[fi]);
            setClipboardContents(clipboard, new StringSelection(S_PASSED), this);
        } else {
            failed = true;
            System.err.println("format failed: " + formats[fi]);
            setClipboardContents(clipboard, new StringSelection(S_FAILED), this);
        }

        if (fi < formats.length - 1) {
            leaveFormat(formats[++fi]);
        }
    }

    public static void main(String[] args) {
        try {
            TImageConsumer ic = new TImageConsumer();

            ic.formats = args;

            leaveFormat(ic.formats[0]);
            synchronized (LOCK) {
                ic.setClipboardContents(ic.clipboard,
                    new StringSelection(S_BEGIN_ANSWER), ic);
                LOCK.wait();
            }
            if (failed) System.exit(ImageTransferTest.CODE_CONSUMER_TEST_FAILED);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(ImageTransferTest.CODE_FAILURE);
        }
    }
}

/**
 * A <code>Transferable</code> which implements the capability required
 * to transfer an <code>Image</code>.
 *
 * This <code>Transferable</code> properly supports
 * <code>DataFlavor.imageFlavor</code>.
 * and all equivalent flavors.
 * No other <code>DataFlavor</code>s are supported.
 *
 * @see java.awt.datatransfer.DataFlavor.imageFlavor
 */
class ImageSelection implements Transferable {

    private static final int IMAGE = 0;

    private static final DataFlavor[] flavors = { DataFlavor.imageFlavor };

    private Image data;

    /**
     * Creates a <code>Transferable</code> capable of transferring
     * the specified <code>String</code>.
     */
    public ImageSelection(Image data) {
        this.data = data;
    }

    /**
     * Returns an array of flavors in which this <code>Transferable</code>
     * can provide the data. <code>DataFlavor.stringFlavor</code>
     * is properly supported.
     * Support for <code>DataFlavor.plainTextFlavor</code> is
     * <b>deprecated</b>.
     *
     * @return an array of length one, whose element is <code>DataFlavor.
     *         imageFlavor</code>
     */
    public DataFlavor[] getTransferDataFlavors() {
        // returning flavors itself would allow client code to modify
        // our internal behavior
        return (DataFlavor[])flavors.clone();
    }

    /**
     * Returns whether the requested flavor is supported by this
     * <code>Transferable</code>.
     *
     * @param flavor the requested flavor for the data
     * @return true if <code>flavor</code> is equal to
     *   <code>DataFlavor.imageFlavor</code>;
     *   false if <code>flavor</code>
     *   is not one of the above flavors
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
     * @throws IOException if an IOException occurs while retrieving the data.
     *         By default, <code>ImageSelection</code> never throws
     *         this exception, but a subclass may.
     * @throws NullPointerException if flavor is <code>null</code>
     */
    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException, java.io.IOException
    {
        if (flavor.equals(flavors[IMAGE])) {
            return (Object)data;
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
