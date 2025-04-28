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
 * @library /test/lib
 * @run main ClipboardSecurity
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

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

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                ClipboardSecurity.class.getName(),
                "child"
        );

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for Child");
        }
        System.out.println("WAIT COMPLETE");

        outputAnalyzer.shouldHaveExitValue(0);

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
