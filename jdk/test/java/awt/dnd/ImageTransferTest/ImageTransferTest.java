/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4397404 4720930
  @summary tests that images of all supported native image formats are transfered properly
  @library ../../../../lib/testlibrary
  @library ../../regtesthelpers/process/
  @build jdk.testlibrary.OSInfo ProcessResults ProcessCommunicator
  @author gas@sparc.spb.su area=Clipboard
  @run main ImageTransferTest
*/

import test.java.awt.regtesthelpers.process.ProcessCommunicator;
import test.java.awt.regtesthelpers.process.ProcessResults;
import jdk.testlibrary.OSInfo;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;
import java.util.stream.Stream;

public class ImageTransferTest {
    public static void main(String[] arg) throws Exception {
        ImageDragSource ids = new ImageDragSource();
        ids.frame.setLocation(100, 100);
        ids.frame.setVisible(true);
        Util.sync();
        String classpath = System.getProperty("java.class.path");
        String[] args = new String[ids.formats.length + 4];
        args[0] = "200";
        args[1] = "100";
        args[2] = args[3] = "150";

        System.arraycopy(ids.formats, 0, args, 4, ids.formats.length);
        ProcessResults pres = ProcessCommunicator.executeChildProcess(ImageDropTarget.class, classpath, args);

        if (pres.getStdErr() != null && pres.getStdErr().length() > 0) {
            System.err.println("========= Child VM System.err ========");
            System.err.print(pres.getStdErr());
            System.err.println("======================================");
        }

        if (pres.getStdOut() != null && pres.getStdOut().length() > 0) {
            System.err.println("========= Child VM System.out ========");
            System.err.print(pres.getStdOut());
            System.err.println("======================================");
        }

        boolean failed = false;
        String passedFormats = "";
        String failedFormats = "";

        for (int i = 0; i < ids.passedArray.length; i++) {
            if (ids.passedArray[i]) passedFormats += ids.formats[i] + " ";
            else {
                failed = true;
                failedFormats += ids.formats[i] + " ";
            }
        }

        if (failed) {
            throw new RuntimeException("test failed: images in following " +
                    "native formats are not transferred properly: " + failedFormats);
        } else {
            System.err.println("images in following " +
                    "native formats are transferred properly: " + passedFormats);
        }
    }
}


class Util {
    private static Robot srobot = null;
    public static void sync() {
        try {
            if(srobot == null) {
                srobot = new Robot();
            }
            srobot.waitForIdle();
            Thread.sleep(500);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

abstract class ImageTransferer {
    Image image;
    String[] formats;
    int fi; // current format index
    Frame frame = new Frame();


    ImageTransferer() {
        image = createImage();
        frame.setSize(100, 100);
    }

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
        return Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(w, h, pix, 0, w));
    }


    static String[] retrieveFormatsToTest() {
        SystemFlavorMap sfm = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
        java.util.List<String> ln = sfm.getNativesForFlavor(DataFlavor.imageFlavor);
        if (OSInfo.OSType.WINDOWS.equals(OSInfo.getOSType()) && !ln.contains("METAFILEPICT")) {
            // for test failing on JDK without this fix
            ln.add("METAFILEPICT");
        }
        return ln.toArray(new String[ln.size()]);
    }

    static void leaveFormat(String format) {
        SystemFlavorMap sfm = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
        sfm.setFlavorsForNative(format, new DataFlavor[]{DataFlavor.imageFlavor});
        sfm.setNativesForFlavor(DataFlavor.imageFlavor, new String[]{format});
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

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

}


class ImageDragSource extends ImageTransferer {
    boolean[] passedArray;

    ImageDragSource() {
        formats = retrieveFormatsToTest();
        passedArray = new boolean[formats.length];
        final DragSourceListener dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent e) {
                System.err.println("Drop was successful=" + e.getDropSuccess());
                notifyTransferSuccess(e.getDropSuccess());
                if (++fi < formats.length) {
                    leaveFormat(formats[fi]);
                }
            }
        };

        new DragSource().createDefaultDragGestureRecognizer(frame,
                DnDConstants.ACTION_COPY,
                dge -> dge.startDrag(null, new ImageSelection(image), dsl));
        leaveFormat(formats[fi]);
    }


    void notifyTransferSuccess(boolean status) {
        passedArray[fi] = status;
    }
}


class ImageDropTarget extends ImageTransferer {
    private final Robot robot;
    private static Point startPoint, endPoint = new Point(250, 150);

    ImageDropTarget() throws AWTException {
        DropTargetAdapter dropTargetAdapter = new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                checkImage(dtde);
                startImageDrag();
            }
        };
        new DropTarget(frame, dropTargetAdapter);
        robot = new Robot();
    }


    void checkImage(DropTargetDropEvent dtde) {
        final Transferable t = dtde.getTransferable();
        if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Image im;
            try {
                im = (Image) t.getTransferData(DataFlavor.imageFlavor);
                System.err.println("getTransferData was successful");
            } catch (Exception e) {
                System.err.println("Can't getTransferData: " + e);
                dtde.dropComplete(false);
                notifyTransferSuccess(false);
                return;
            }

            if (im == null) {
                System.err.println("getTransferData returned null");
                dtde.dropComplete(false);
                notifyTransferSuccess(false);
            } else if (areImagesIdentical(image, im)) {
                dtde.dropComplete(true);
                notifyTransferSuccess(true);
            } else {
                System.err.println("transferred image is different from initial image");
                dtde.dropComplete(false);
                notifyTransferSuccess(false);
            }

        } else {
            System.err.println("imageFlavor is not supported by Transferable");
            dtde.rejectDrop();
            notifyTransferSuccess(false);
        }
    }

    void startImageDrag() {
        leaveFormat(formats[fi]);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                // Exit from the child process
                System.exit(1);
            }
            robot.mouseMove(startPoint.x, startPoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (Point p = new Point(startPoint); !p.equals(endPoint);
                 p.translate(sign(endPoint.x - p.x), sign(endPoint.y - p.y))) {
                robot.mouseMove(p.x, p.y);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }).start();
    }

    void notifyTransferSuccess(boolean status) {
        if (status) {
            System.err.println("format passed: " + formats[fi]);
        } else {
            System.err.println("format failed: " + formats[fi]);
            System.exit(1);
        }
        if (fi < formats.length - 1) {
            leaveFormat(formats[++fi]);
        } else {
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }).start();
        }
    }


    public static void main(String[] args) {
        try {
            ImageDropTarget idt = new ImageDropTarget();

            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            startPoint = new Point(Integer.parseInt(args[2]), Integer.parseInt(args[3]));

            idt.formats = new String[args.length - 4];
            System.arraycopy(args, 4, idt.formats, 0, args.length - 4);
            leaveFormat(idt.formats[0]);

            idt.frame.setLocation(x, y);
            idt.frame.setVisible(true);
            Util.sync();

            idt.startImageDrag();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}


class ImageSelection implements Transferable {
    private static final int IMAGE = 0;
    private static final DataFlavor[] flavors = {DataFlavor.imageFlavor};
    private Image data;

    public ImageSelection(Image data) {
        this.data = data;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        // returning flavors itself would allow client code to modify
        // our internal behavior
        return flavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return Stream.of(flavor).anyMatch(flavor::equals);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (flavor.equals(flavors[IMAGE])) {
            return data;
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
