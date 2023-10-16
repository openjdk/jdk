/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4916420
  @requires os.family == "linux"
  @summary verifies that AWT_LOCK is properly taken during file transfer
  @key headful
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class FileTransferAWTLockTest {

    public static void main(String[] args) {
        if (!(System.getProperty("os.name").startsWith("Linux"))) {
            return;
        }
        FileTransferAWTLockTest parent = new FileTransferAWTLockTest();
        parent.start();
    }

    public void start() {
        String stderr = null;
        try {
            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " +
                System.getProperty("test.classes", ".") +
                " -Dawt.toolkit=sun.awt.X11.XToolkit" +
                " FileTransferAWTLockTestChild";

            Process process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);

            stderr = pres.stderr;

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

            System.err.println("Child VM return code: " + pres.exitValue);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (stderr != null && stderr.indexOf("InternalError") >= 0) {
            throw new RuntimeException("Test failed");
        }
    }
}

class FileTransferAWTLockTestChild {
    static final Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();
    static final Transferable transferable = new Transferable() {
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor };
        }
        public boolean isDataFlavorSupported(DataFlavor df) {
            return DataFlavor.javaFileListFlavor.equals(df);
        }
        public Object getTransferData(DataFlavor df)
            throws IOException, UnsupportedFlavorException {
            if (!isDataFlavorSupported(df)) {
                throw new UnsupportedFlavorException(df);
            }

            File file = new File("file.txt");
            ArrayList list = new ArrayList();
            list.add(file);
            return list;
        }
    };

    public static void main(String[] args) {
        Util.setClipboardContents(clipboard, transferable, null);
        FileTransferAWTLockTestChild test = new FileTransferAWTLockTestChild();
        test.run();
    }

    public void run() {
        Transferable t = Util.getClipboardContents(clipboard, null);
        try {
            t.getTransferData(DataFlavor.javaFileListFlavor);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Util {
    public static void setClipboardContents(Clipboard cb,
                                            Transferable contents,
                                            ClipboardOwner owner) {
        synchronized (cb) {
            while (true) {
                try {
                    cb.setContents(contents, owner);
                    return;
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
                    return cb.getContents(requestor);
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
