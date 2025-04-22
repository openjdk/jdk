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
 * @bug 4655996
 * @summary tests that getting the system clipboard contents doesn't cause
 *          IOException if there is no clipboard owner or the owner doesn't
 *          export any target types
 * @key headful
 * @run main NoOwnerNoTargetsTest
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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class NoOwnerNoTargetsTest implements ClipboardOwner {

    final Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();
    public static final int CLIPBOARD_DELAY = 1000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            NoOwnerNoTargetsTest test = new NoOwnerNoTargetsTest();
            test.execute();
            return;
        }

        new NoOwnerNoTargetsTest().start();
    }

    public void execute() {
        final ClipboardOwner clipboardOwner = new ClipboardOwner() {
                public void lostOwnership(Clipboard clip,
                                          Transferable contents) {
                    System.exit(0);
                }
            };
        final Transferable emptyTransferable = new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[0];
                }
                public boolean isDataFlavorSupported(DataFlavor df) {
                    return false;
                }
                public Object getTransferData(DataFlavor df)
                  throws UnsupportedFlavorException {
                    throw new UnsupportedFlavorException(df);
                }
            };

        clipboard.setContents(emptyTransferable, clipboardOwner);
        final Object o = new Object();
        synchronized (o) {
            try {
                o.wait();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    public void start() throws Exception {
        clipboard.getContents(null);

        String javaPath = System.getProperty("java.home", "");

        Transferable transferable = new StringSelection("TEXT");
        clipboard.setContents(transferable, this);

        Process process = new ProcessBuilder(
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "NoOwnerNoTargetsTest", "child"
        ).start();

        ProcessResults pres = ProcessResults.doWaitFor(process, 15);
        System.out.println("Child returned: " + pres.exitValue);

        InputStream errorStream = process.getErrorStream();
        System.err.println("========= Child process stderr ========");
        try {
            dumpStream(errorStream, System.err);
            System.err.println("=======================================");
        } catch (IOException ioe) {
            System.err.println("=======================================");
            ioe.printStackTrace();
        }

        InputStream outputStream = process.getInputStream();
        System.out.println("========= Child process stdout ========");
        try {
            dumpStream(outputStream, System.out);
            System.out.println("=======================================");
        } catch (IOException ioe) {
            System.out.println("=======================================");
            ioe.printStackTrace();
        }

        if (pres.exitValue != 0) {
            throw new RuntimeException("Child process returned non-zero exit value "
                    + pres.exitValue);
        }
    }

    public void lostOwnership(Clipboard clip, Transferable contents) {
        final Transferable transferable = new StringSelection("TEXT");
        final Runnable r = () -> {
            try {
                Thread.sleep(CLIPBOARD_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            clipboard.getContents(null);
            clipboard.setContents(transferable, null);
        };
        final Thread t = new Thread(r);
        t.start();
    }

    public void dumpStream(InputStream in, OutputStream out)
      throws IOException {
        int count = in.available();
        while (count > 0) {
            byte[] b = new byte[count];
            in.read(b);
            out.write(b);
            count = in.available();
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

