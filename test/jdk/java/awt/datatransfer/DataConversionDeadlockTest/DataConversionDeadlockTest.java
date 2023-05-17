/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4760364
  @summary Tests that the deadlock doesn't happen when two apps request
           selection data from each other.
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class DataConversionDeadlockTest {

    public static void main(String[] args) {
        DataConversionDeadlockTest parent = new DataConversionDeadlockTest();
        parent.start();
    }

    public void start() {
        try {
            String javaPath = System.getProperty("java.home", "");
            String cmd = javaPath + File.separator + "bin" +
                File.separator + "java -cp " +
                System.getProperty("test.classes", ".") +
                " DataConversionDeadlockTestChild";

            Process process = Runtime.getRuntime().exec(cmd);
            ProcessResults pres = ProcessResults.doWaitFor(process);

            if (pres.stderr != null && pres.stderr.length() > 0) {
                System.err.println("========= Child VM System.err ========");
                System.err.print(pres.stderr);
                System.err.println("======================================");
            }

            if (pres.stdout != null && pres.stdout.length() > 0) {
                System.err.println("========= Child VM System.out ========");
                System.err.print(pres.stdout);
                System.err.println("======================================");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class DataConversionDeadlockTestChild implements ClipboardOwner, Runnable {
    private static final Toolkit toolkit = Toolkit.getDefaultToolkit();
    private static final Clipboard clipboard = toolkit.getSystemClipboard();
    private static final Clipboard selection = toolkit.getSystemSelection();
    private static final Transferable t = new StringSelection("TEXT");

    public void lostOwnership(Clipboard cb, Transferable contents) {
        ClipboardUtil.setClipboardContents(selection, t, this);
        new Thread(this).start();
    }

    public void run() {
        for (int i = 0; i < 100; i++) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    ClipboardUtil.getClipboardContents(clipboard, null);
                }
            });
        }
    }

    public static void main(String[] args) {
        if (clipboard == null || selection == null) {
            return;
        }
        ClipboardUtil.setClipboardContents(clipboard, t, null);
        for (int i = 0; i < 100; i++) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    ClipboardUtil.getClipboardContents(selection, null);
                }
            });
        }
    }
}

class ClipboardUtil {
    public static void setClipboardContents(Clipboard cb,
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

    public static Transferable getClipboardContents(Clipboard cb,
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
    public int exitValue;
    public String stdout;
    public String stderr;

    public ProcessResults() {
        exitValue = -1;
        stdout = "";
        stderr = "";
    }

    /**
     * Method to perform a "wait" for a process and return its exit value.
     * This is a workaround for <code>Process.waitFor()</code> never returning.
     */
    public static ProcessResults doWaitFor(Process p) {
        ProcessResults pres = new ProcessResults();

        InputStream in = null;
        InputStream err = null;

        try {
            in = p.getInputStream();
            err = p.getErrorStream();

            boolean finished = false;

            while (!finished) {
                try {
                    while (in.available() > 0) {
                        pres.stdout += (char)in.read();
                    }
                    while (err.available() > 0) {
                        pres.stderr += (char)err.read();
                    }
                    // Ask the process for its exitValue. If the process
                    // is not finished, an IllegalThreadStateException
                    // is thrown. If it is finished, we fall through and
                    // the variable finished is set to true.
                    pres.exitValue = p.exitValue();
                    finished = true;
                }
                catch (IllegalThreadStateException e) {
                    // Process is not finished yet;
                    // Sleep a little to save on CPU cycles
                    Thread.currentThread().sleep(500);
                }
            }
            if (in != null) in.close();
            if (err != null) err.close();
        }
        catch (Throwable e) {
            System.err.println("doWaitFor(): unexpected exception");
            e.printStackTrace();
        }
        return pres;
    }
}
