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

/*
 * @test
 * @key headful
 * @bug 6176679
 * @summary Tests that an application doesn't freeze when copying an animated gif image to the system clipboard
 * @run main bug6176679
 */

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;


public class bug6176679 implements ClipboardOwner, FlavorListener {
    private static final long TIMEOUT = 10000;

    private static final String FILENAME = "cupanim.gif";

    private static final CountDownLatch latch = new CountDownLatch(1);

    volatile static Image img = null;

    private static Frame frame;

    volatile static bug6176679 test;

    private void createGUI() throws InterruptedException {

        frame = new Frame("bug6176679");
        frame.setSize(400, 200);

        imgCanvas canvas = new imgCanvas();
        Panel panel = new Panel(new GridLayout(1, 1));
        panel.add(canvas);
        frame.add(panel);
        img = frame.getToolkit().getImage(System.getProperty("test.src", ".") + File.separator + FILENAME);
        MediaTracker mt = new MediaTracker(frame);
        mt.addImage(img, 0);
        mt.waitForAll();
        canvas.setImage(img);
        canvas.setBackground(Color.blue);
        frame.setVisible(true);
    }

    private void copyImage() {
        Clipboard sys = Toolkit.getDefaultToolkit().getSystemClipboard();
        sys.addFlavorListener(this);
        sys.setContents(new MyTransferable(img), this);

    }

    public static void main(String[] args) throws Exception {
        test = new bug6176679();

        Robot robot = new Robot();

        EventQueue.invokeAndWait(() -> {
            try {
                test.createGUI();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });

        robot.waitForIdle();
        robot.delay(1000);

        EventQueue.invokeLater(() -> {
            try {
                test.copyImage();
                latch.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });

        if (!latch.await(TIMEOUT, MILLISECONDS)) {
            throw new RuntimeException("Image copying taking too long.");
        }
        else
        {
            EventQueue.invokeAndWait(bug6176679::dispose);
        }

    }

    private static void dispose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    @Override
    public void lostOwnership(Clipboard c, Transferable t) {
    }

    @Override
    public void flavorsChanged(FlavorEvent fe) {

    }

    static class imgCanvas extends Canvas {
        Image img = null;

        public imgCanvas() {
        }

        public void setImage(Image i) {
            img = i;
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            if (img != null) {
                g.drawImage(img, 0, 0, getSize().width, getSize().height, this);
            } else {
                g.setColor(getBackground());
                g.fillRect(0, 0, getSize().width, getSize().height);
            }
        }
    }

    static class MyTransferable implements Transferable {

        Image img = null;
        DataFlavor[] df = new DataFlavor[1];

        public MyTransferable(Object obj) {
            if (obj instanceof Image) {
                img = (Image) obj;
                df = new DataFlavor[1];
                df[0] = DataFlavor.imageFlavor;
            }
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return df;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return true;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return img;
        }
    }


}