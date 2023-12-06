/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/*
 * @test
 * @key headful
 * @bug 6176679
 * @summary Tests that an application doesn't freeze when copying an animated
 * gif image to the system clipboard. We run the test two times. First with
 * image displayed on screen and second with it not displayed.
 * @run main CopyAnimatedGIFTest
 */
public class CopyAnimatedGIFTest {
    private static final long TIMEOUT = 10000;

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Image img = Toolkit.getDefaultToolkit().createImage(imageData);

    private static final byte[] imageData = {
            (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38, (byte) 0x39,
            (byte) 0x61, (byte) 0x04, (byte) 0x00, (byte) 0x04, (byte) 0x00,
            (byte) 0xa1, (byte) 0x03, (byte) 0x00, (byte) 0xff, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0x00, (byte) 0xff,
            (byte) 0xff, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0x21, (byte) 0xff, (byte) 0x0b, (byte) 0x4e, (byte) 0x45,
            (byte) 0x54, (byte) 0x53, (byte) 0x43, (byte) 0x41, (byte) 0x50,
            (byte) 0x45, (byte) 0x32, (byte) 0x2e, (byte) 0x30, (byte) 0x03,
            (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x21,
            (byte) 0xf9, (byte) 0x04, (byte) 0x00, (byte) 0x0a, (byte) 0x00,
            (byte) 0xff, (byte) 0x00, (byte) 0x2c, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x04,
            (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x04, (byte) 0x84,
            (byte) 0x8f, (byte) 0x09, (byte) 0x05, (byte) 0x00, (byte) 0x21,
            (byte) 0xf9, (byte) 0x04, (byte) 0x01, (byte) 0x0a, (byte) 0x00,
            (byte) 0x03, (byte) 0x00, (byte) 0x2c, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x04,
            (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x04, (byte) 0x94,
            (byte) 0x8f, (byte) 0x29, (byte) 0x05, (byte) 0x00, (byte) 0x21,
            (byte) 0xf9, (byte) 0x04, (byte) 0x01, (byte) 0x0a, (byte) 0x00,
            (byte) 0x03, (byte) 0x00, (byte) 0x2c, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x04,
            (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x04, (byte) 0x8c,
            (byte) 0x8f, (byte) 0x19, (byte) 0x05, (byte) 0x00, (byte) 0x3b
    };

    private void createGUI() {
        ImageCanvas canvas = new ImageCanvas(img);
        canvas.setBackground(Color.BLUE);

        Frame frame = new Frame("CopyAnimatedGIFTest");
        frame.setSize(400, 200);
        frame.add(canvas);
        frame.setVisible(true);
    }

    private void copyImage() {
        Clipboard sys = Toolkit.getDefaultToolkit().getSystemClipboard();
        sys.setContents(new MyTransferable(img), null);
    }

    private void runTest(boolean isImageDisplayed) throws Exception {

        if (isImageDisplayed) {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(this::createGUI);
            robot.waitForIdle();
            robot.delay(1000);
        }

        EventQueue.invokeLater(() -> {
            copyImage();
            latch.countDown();
        });

        if (!latch.await(TIMEOUT, MILLISECONDS)) {
            String str = isImageDisplayed ? " displayed":" not displayed";
            throw new RuntimeException("Image copying taking too long for image"
                                       + str + " case");
        }
    }

    public static void main(String[] args) throws Exception {
        // run test with Image showing up on screen
        new CopyAnimatedGIFTest().runTest(true);

        // run test without Image showing up
        new CopyAnimatedGIFTest().runTest(false);
    }

    private static class ImageCanvas extends Canvas {
        private final Image img;
        public ImageCanvas(Image img) {
            this.img = img;
        }

        @Override
        public void paint(Graphics g) {
            g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
        }
    }

    private static class MyTransferable implements Transferable {
        private final Image img;
        private final DataFlavor[] flavors = {DataFlavor.imageFlavor};

        public MyTransferable(Image img) {
            this.img = img;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavors[0].equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return img;
        }
    }

}
