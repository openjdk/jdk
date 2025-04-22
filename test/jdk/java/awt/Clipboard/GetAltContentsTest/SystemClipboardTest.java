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
 * @run main SystemClipboardTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        String javaPath = System.getProperty("java.home", "");

        Process process = new ProcessBuilder(
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "SystemClipboardTest", "child"
                ).start();

        ProcessResults pres = ProcessResults.doWaitFor(process, 15);

        if (!pres.stderr.isEmpty()) {
            System.err.println("========= Child VM System.err ========");
            System.err.print(pres.stderr);
            System.err.println("======================================");
        }

        if (!pres.stdout.isEmpty()) {
            System.err.println("========= Child VM System.out ========");
            System.err.print(pres.stdout);
            System.err.println("======================================");
        }

        if (pres.exitValue != 0) {
            throw new RuntimeException("child VM failed with exit code " + pres.exitValue);
        }
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

class ProcessResults {
    public int exitValue = -1;
    public String stdout;
    public String stderr;

    public static ProcessResults doWaitFor(Process p, int timeoutSeconds)
            throws Exception {
        ProcessResults pres = new ProcessResults();

        InReader in = new InReader("I", p.inputReader());
        InReader err = new InReader("E", p.errorReader());

        in.start();
        err.start();

        try {
            if (p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                pres.exitValue = p.exitValue();
            } else {
                System.err.println("Process timed out");
                p.destroyForcibly();
            }
        } finally {
            in.join(500);
            err.join(500);
        }

        pres.stdout = in.output.toString();
        pres.stderr = err.output.toString();

        return pres;
    }

    static class InReader extends Thread {
        private final String prefix;
        private final BufferedReader reader;
        private final StringBuffer output = new StringBuffer();

        public InReader(String prefix, BufferedReader reader) {
            this.prefix = prefix;
            this.reader = reader;
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    System.out.printf("> %s: %s\n", prefix, line);
                    output.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                System.out.printf("> %s: %s\n", prefix, e);
                output.append("Error reading: ")
                      .append(e).append(System.lineSeparator());
            }
        }
    }
}
