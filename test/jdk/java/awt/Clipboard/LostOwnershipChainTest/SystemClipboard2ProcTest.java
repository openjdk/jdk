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
 * @bug 4683804
 * @summary Tests that in ClipboardOwner.lostOwnership() Clipboard.getContents()
 *          returns actual contents of the clipboard and Clipboard.setContents()
 *          can set contents of the clipboard and its owner. The clipboard is
 *          the system clipboard and the owners of the clipboard are in
 *          2 different processes.
 * @key headful
 * @library /test/lib
 * @run main SystemClipboard2ProcTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SystemClipboard2ProcTest {

    public static void main(String[] args) throws Exception {
        SystemClipboardOwner.run();

        if (SystemClipboardOwner.failed) {
            throw new RuntimeException("test failed: can not get actual " +
                    "contents of the clipboard or set owner of the clipboard");
        } else {
            System.err.println("test passed");
        }
    }
}

class SystemClipboardOwner implements ClipboardOwner {
    static volatile boolean failed;

    private static final Object LOCK = new Object();

    private static final int CHAIN_LENGTH = 5;
    private final static Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();

    private int m, id;

    public SystemClipboardOwner(int m) { this.m = m; id = m; }

    public void lostOwnership(Clipboard cb, Transferable contents) {
        System.err.println(id + " lost clipboard ownership");

        Transferable t = getClipboardContents(cb, null);
        // for test passing if t.getTransferData() will throw an exception
        String msg = "" + (m + 1);
        try {
            msg = (String)t.getTransferData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            System.err.println(id + " can't getTransferData: " + e);
        }
        System.err.println(id + " Clipboard.getContents(): " + msg);
        if (!msg.equals("" + (m + 1))) {
            failed = true;
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

        ProcessBuilder pb = ProcessTools
                .createTestJavaProcessBuilder(SystemClipboardOwner.class.getName());

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for Child");
        }

        outputAnalyzer.shouldHaveExitValue(0);

        if (cbo1.m < CHAIN_LENGTH) {
            failed = true;
            System.err.println("chain of calls of lostOwnership() broken!");
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
