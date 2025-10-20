/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4287795 4790833
 * @summary tests new Clipboard methods: getAvailableDataFlavors,
 *          isDataFlavorAvailable, getData
 * @key headful
 * @library /test/lib
 * @run main SystemClipboardTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class SystemClipboardTest {

    private static final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    private static final String contentsText = "contents text";

    public void start() throws Exception {
        Util.setClipboardContents(clipboard, new StringSelection(contentsText), new ClipboardOwner() {
                public void lostOwnership(Clipboard clpbrd, Transferable cntnts) {
                    check(); // clipboard data retrieved from the system clipboard
                    Util.setClipboardContents(clipboard, new StringSelection(contentsText), null);
                }
            });

        check(); // JVM-local clipboard data

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                SystemClipboardTest.class.getName(),
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

    private void check() {
        boolean failed = false;

        Transferable contents = Util.getClipboardContents(clipboard, null);
        Set<DataFlavor> flavorsT = new HashSet<>(Arrays.asList(contents.getTransferDataFlavors()));
        Set<DataFlavor> flavorsA = new HashSet<>(Arrays.asList(Util.getClipboardAvailableDataFlavors(clipboard)));
        System.err.println("getAvailableDataFlavors(): " + flavorsA);
        if (!flavorsA.equals(flavorsT)) {
            failed = true;
            System.err.println("FAILURE: getAvailableDataFlavors() returns incorrect " +
                    "DataFlavors: " + flavorsA + "\nwhile getContents()." +
                    "getTransferDataFlavors() return: " + flavorsT);
        }

        if (!Util.isClipboardDataFlavorAvailable(clipboard, DataFlavor.stringFlavor)) {
            failed = true;
            System.err.println("FAILURE: isDataFlavorAvailable(DataFlavor.stringFlavor) " +
                               "returns false");
        }

        Object data = null;
        try {
            data = Util.getClipboardData(clipboard, DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException exc) {
            failed = true;
            exc.printStackTrace();
        } catch (IOException exc) {
            failed = true;
            exc.printStackTrace();
        }
        System.err.println("getData(): " + data);
        if (!contentsText.equals(data)) {
            failed = true;
            System.err.println("FAILURE: getData() returns: " + data +
                               ", that is not equal to: \"" + contentsText + "\"");

        }

        if (failed) {
            throw new RuntimeException("test failed, for details see output above");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            SystemClipboardTest systemClipboardTest = new SystemClipboardTest();
            systemClipboardTest.start();
            return;
        }

        System.err.println("child VM: setting clipboard contents");

        CountDownLatch latch = new CountDownLatch(1);
        Util.setClipboardContents(clipboard, new StringSelection(contentsText),
                (clpbrd, cntnts) -> {
                    System.err.println("child VM: success");
                    latch.countDown();
                });

        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new RuntimeException("child VM failed");
        }
    }
}

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

    public static Transferable getClipboardContents(Clipboard cb,
                                                    Object requestor) {
        while (true) {
            try {
                return cb.getContents(requestor);
            } catch (IllegalStateException ise) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static Object getClipboardData(Clipboard cb, DataFlavor flavor)
            throws IOException, UnsupportedFlavorException {
        while (true) {
            try {
                return cb.getData(flavor);
            } catch (IllegalStateException ise) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static DataFlavor[] getClipboardAvailableDataFlavors(Clipboard cb) {
        while (true) {
            try {
                return cb.getAvailableDataFlavors();
            } catch (IllegalStateException ise) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static boolean isClipboardDataFlavorAvailable(Clipboard cb,
                                                         DataFlavor flavor) {
        while (true) {
            try {
                return cb.isDataFlavorAvailable(flavor);
            } catch (IllegalStateException ise) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }
}
