/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4274061
 * @summary Tests that Transferable.getTransferData() and
 *          SelectionOwner.lostOwnership is not called on Toolkit thread.
 * @key headful
 * @run main ClipboardSecurity
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClipboardSecurity {
    static Clipboard clip = null;
    public static final CountDownLatch latch = new CountDownLatch(1);
    public static volatile boolean hasError = false;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ClipboardSecurity clipboardSecurity = new ClipboardSecurity();
            clipboardSecurity.start();
            return;
        }

        try {
            clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            if ( clip == null ) {
                throw (new RuntimeException("Clipboard is null"));
            }
            Transferable data = clip.getContents(null);
            if ( data == null ) {
                throw (new RuntimeException("Data is null"));
            }
            System.out.println("Clipboard contents: " + data);
            // First check - getTransferData
            try {
                String contData =
                        (String) data.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException exc) {
                throw(new RuntimeException("Couldn't get transfer data - "
                        + exc.getMessage()));
            }
            // Second check - lostOwnership
            MyClass clipData = new MyClass("clipbard test data");
            clip.setContents(clipData, clipData);
            System.out.println("exit 0");
            System.exit(0);
        } catch (RuntimeException exc) {
            System.err.println(exc.getMessage());
            System.out.println("exit 2");
            System.exit(2);
        }
    }

    public void start() throws Exception {
        clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (clip == null) {
            throw (new RuntimeException("Clipboard is null"));
        }
        MyClass clipData = new MyClass("clipboard test data");
        clip.setContents(clipData, clipData);

        String javaPath = System.getProperty("java.home", "");

        Process proc = new ProcessBuilder(
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "ClipboardSecurity", "client"
        ).start();

        ProcessResults pres = ProcessResults.doWaitFor(proc, 15);

        System.out.println("WAIT COMPLETE");

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
            throw new RuntimeException("child VM failed with exit code "
                    + pres.exitValue);
        }

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("timed out");
        }

        if (hasError) {
            throw new RuntimeException("Detected call on Toolkit thread");
        }

        System.out.println("Passed.");
    }
}

class MyClass extends StringSelection implements ClipboardOwner {
    MyClass(String title) {
        super(title);
    }

    private void checkIsCorrectThread(String reason) {
        System.out.println("Checking " + reason + " for thread "
                + Thread.currentThread().getName());
        String name = Thread.currentThread().getName();
        if (name.equals("AWT-Windows") || name.equals("AWT-Motif")) {
            ClipboardSecurity.hasError = true;
            System.err.println(reason + " is called on Toolkit thread!");
        }
    }

    public void lostOwnership(Clipboard clip, Transferable cont) {
        checkIsCorrectThread("lostOwnership");
        ClipboardSecurity.latch.countDown();
        System.out.println("lost ownership on "
                + Thread.currentThread().getName() + " thread");
    }

    public Object getTransferData(DataFlavor flav)
            throws UnsupportedFlavorException, IOException {
        System.out.println("getTransferData on "
                + Thread.currentThread().getName() + " thread");
        checkIsCorrectThread("getTransferData");
        return super.getTransferData(flav);
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
