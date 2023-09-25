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
  @bug 4914613
  @summary tests that "\r\n" is not converted to "\r\r\n"
  @key headful
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.InputStream;

public class CRLFTest {
    private int returnCode = 0;

    public static void main(String[] args) {
        CRLFTest parent = new CRLFTest();
        parent.start();
    }
    public void start() {

        try {
            String javaPath = System.getProperty("java.home", "");
            String command = javaPath + File.separator + "bin" +
                File.separator + "java -cp " +
                System.getProperty("test.classes", ".") +
                " CRLFTestClipboard";

            Process process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);
            returnCode = pres.exitValue;

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

            System.err.println("Child return code=" + returnCode);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}

class CRLFTestClipboard implements ClipboardOwner {
    private static final Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();

    public static void main(String[] args) {
        CRLFTestClipboard child = new CRLFTestClipboard();
        child.run();
    }

    public void run() {
        ClipboardOwner owner = new ClipboardOwner() {
            public void lostOwnership(Clipboard clipboard,
                                      Transferable contents) {
                System.exit(0);
            }
        };
        clipboard.setContents(new StringSelection("\r\n"), owner);

        // Wait to let the parent retrieve the contents.
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void lostOwnership(Clipboard clip, Transferable contents) {
        final DataFlavor df =
            new DataFlavor("text/test-subtype; class=java.io.InputStream",
                null);
        SystemFlavorMap sfm =
            (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();
        sfm.addUnencodedNativeForFlavor(df, "TEXT");
        sfm.addFlavorForUnencodedNative("TEXT", df);
        Runnable r = new Runnable() {
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Transferable t = clipboard.getContents(null);
                boolean passed = true;
                try {
                    InputStream is =
                        (InputStream)t.getTransferData(df);
                    int prev = 0;
                    int b = 0;
                    System.err.print("Bytes: ");
                    while ((b = is.read()) != -1) {
                        System.err.print(" " + Integer.
                            toHexString((int)b & 0xFF));
                        if (b == 0xD && prev == 0xD) {
                            passed = false;
                        }
                        prev = b;
                    }
                    System.err.println();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                clipboard.setContents(new StringSelection(""), null);

                if (!passed) {
                    throw new RuntimeException("Test failed");
                }
            }
        };
        new Thread(r).start();
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
