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
 * @bug 4513976
 * @summary tests that inter-JVM image transfer doesn't cause crash
 * @key headful
 * @library /test/lib
 * @run main ImageTransferCrashTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ImageTransferCrashTest implements ClipboardOwner {
    static final Clipboard clipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();
    final Transferable textTransferable = new StringSelection("TEXT");
    public static final int CLIPBOARD_DELAY = 10;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ImageTransferCrashTest imageTransferCrashTest = new ImageTransferCrashTest();
            imageTransferCrashTest.initialize();
            return;
        }
        final ClipboardOwner clipboardOwner = (clip, contents) -> System.exit(0);
        final int width = 100;
        final int height = 100;
        final BufferedImage bufferedImage =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final WritableRaster writableRaster =
                bufferedImage.getWritableTile(0, 0);
        final int[] color = new int[]{0x80, 0x80, 0x80};
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                writableRaster.setPixel(i, j, color);
            }
        }
        bufferedImage.releaseWritableTile(0, 0);

        final Transferable imageTransferable = new Transferable() {
            final DataFlavor[] flavors = new DataFlavor[]{
                    DataFlavor.imageFlavor};

            public DataFlavor[] getTransferDataFlavors() {
                return flavors;
            }

            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.imageFlavor.equals(df);
            }

            public Object getTransferData(DataFlavor df)
                    throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(df)) {
                    throw new UnsupportedFlavorException(df);
                }
                return bufferedImage;
            }
        };
        clipboard.setContents(imageTransferable, clipboardOwner);
        final Object o = new Object();
        synchronized (o) {
            try {
                o.wait();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        System.out.println("Test Pass!");
    }

    public void initialize() throws Exception {
        clipboard.setContents(textTransferable, this);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                ImageTransferCrashTest.class.getName(),
                "child"
        );

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for Child");
        }

        outputAnalyzer.shouldHaveExitValue(0);
    }

    public void lostOwnership(Clipboard clip, Transferable contents) {
        final Runnable r = () -> {
            while (true) {
                try {
                    Thread.sleep(CLIPBOARD_DELAY);
                    Transferable t = clipboard.getContents(null);
                    t.getTransferData(DataFlavor.imageFlavor);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    System.err.println("clipboard is not prepared yet");
                    continue;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            clipboard.setContents(textTransferable, null);
        };
        final Thread t = new Thread(r);
        t.start();
    }
}
