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
 * @bug 4558797
 * @summary Tests that there is no data conversion failure when two applications
 *          exchange data via system clipboard
 * @key headful
 * @run main NoDataConversionFailureTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class NoDataConversionFailureTest {

    public static void main(String[] args) throws Exception {
        SystemClipboardOwner.run();

        if (SystemClipboardOwner.failed) {
            throw new RuntimeException("test failed: can not get transfer data");
        } else {
            System.err.println("test passed");
        }
    }
}

class SystemClipboardOwner implements ClipboardOwner {
    static volatile boolean failed;

    private static final Object LOCK = new Object();

    private static final int CHAIN_LENGTH = 15;
    private final static Clipboard clipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();

    private int m;
    private final int id;

    public SystemClipboardOwner(int m) { this.m = m; id = m; }

    public void lostOwnership(Clipboard cb, Transferable contents) {
        System.err.println(id + " lost clipboard ownership");

        Transferable t = getClipboardContents(cb, null);
        // for test passing if t.getTransferData() will throw an exception
        String msg = String.valueOf(m + 1);
        try {
            msg = (String) t.getTransferData(DataFlavor.stringFlavor);
        } catch (IOException e) {
            failed = true;
            System.err.println(id + " can't getTransferData: " + e);
        } catch (Exception e) {
            System.err.println(id + " can't getTransferData: " + e);
        }

        System.err.println(id + " Clipboard.getContents(): " + msg);
        if (!msg.equals(String.valueOf(m + 1))) {
            System.err.println("Clipboard.getContents() returned incorrect contents!");
        }

        m += 2;
        if (m <= CHAIN_LENGTH) {
            System.err.println(id + " Clipboard.setContents(): " + m);
            setClipboardContents(cb, new StringSelection(m + ""), this);
        }
        if (m >= CHAIN_LENGTH) {
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
        }
    }

    public static void run() throws Exception {
        SystemClipboardOwner cbo1 = new SystemClipboardOwner(0);
        System.err.println(cbo1.m + " Clipboard.setContents(): " + cbo1.m);
        setClipboardContents(clipboard, new StringSelection(cbo1.m + ""),
                cbo1);


        String javaPath = System.getProperty("java.home", "");

        Process process = new ProcessBuilder(
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "SystemClipboardOwner"
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

        if (cbo1.m < CHAIN_LENGTH) {
            System.err.println("chain of calls of lostOwnership() broken!");
        }

        if (pres.exitValue != 0) {
            throw new Error("Unexpected exit value: " + pres.exitValue);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SystemClipboardOwner cbo2 = new SystemClipboardOwner(1);
        System.err.println(cbo2.m + " Clipboard.setContents(): " + cbo2.m);

        synchronized (LOCK) {
            setClipboardContents(clipboard, new StringSelection(cbo2.m + ""),
                cbo2);
            LOCK.wait();
        }
    }


    private static void setClipboardContents(Clipboard cb,
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

    private static Transferable getClipboardContents(Clipboard cb,
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
