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
  @bug 4859006
  @summary tests that MIME formats are mapped to flavors properly on X11
  @requires (os.family == "linux")
  @key headful
  @run main MimeFormatsTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;


public class MimeFormatsTest implements ClipboardOwner {
    public static final DataFlavor TEST_FLAVOR =
        new DataFlavor(
                "text/test;charset=UTF-8;class=java.io.InputStream",
                null);

    public static class TextTransferable implements Transferable {
        private final String text;

        public TextTransferable(String text) {
            this.text = text;
        }

        public Object getTransferData(DataFlavor flavor)
          throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(TEST_FLAVOR)) {
                throw new UnsupportedFlavorException(flavor);
            }

            return new ByteArrayInputStream(
                    text.getBytes(StandardCharsets.UTF_8));
        }

        public DataFlavor[] getTransferDataFlavors(){
            return new DataFlavor[] { TEST_FLAVOR };
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return TEST_FLAVOR.equals(flavor);
        }
    }

    public static final String DATA =
        "\u0440\u0443\u0441\u0441\u043a\u0438\u0439";

    private String testData = null;

    private static final Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();

    public void childRun() {
        Transferable t = clipboard.getContents(null);
        String data = "";
        try {
            data = (String)t.getTransferData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("contents size=" + data.length());
        for (int i = 0; i < data.length(); i++) {
            System.err.println("     char[" + i + "]=" + (int) data.charAt(i));
        }
        ClipboardOwner owner = new ClipboardOwner() {
                public void lostOwnership(Clipboard clipboard,
                                          Transferable contents) {
                    System.err.println("%d exit".formatted(
                            System.currentTimeMillis()));
                    System.err.println("Exiting");
                    System.exit(0);
                }
            };
        clipboard.setContents(new StringSelection(data + data), owner);

        Object lock = new Object();
        synchronized (lock) {
            // Wait to let the parent retrieve the contents.
            try {
                System.err.println("%d wait".formatted(
                        System.currentTimeMillis()));
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() {
        FlavorMap fm = SystemFlavorMap.getDefaultFlavorMap();
        if (fm instanceof SystemFlavorMap) {
            SystemFlavorMap sfm = (SystemFlavorMap)fm;
            String mimeNative = "text/plain;charset=UTF-8";
            sfm.setNativesForFlavor(TEST_FLAVOR,
                                    new String[] { mimeNative });
            sfm.setFlavorsForNative(mimeNative,
                                    new DataFlavor[] { TEST_FLAVOR });
        } else {
            System.err.println("WARNING: system flavor map: " + fm);
            return;
        }

        clipboard.setContents(new TextTransferable(DATA), this);

        try {
            String javaPath = System.getProperty("java.home", "");
            String[] command = {
                    javaPath + File.separator + "bin" + File.separator + "java",
                    "-cp",
                    System.getProperty("test.classes", "."),
                    "Child"
            };

            Process process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);

            int returnCode = pres.exitValue;

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

        System.err.println("Received data size=" + testData.length());
        for (int i = 0; i < testData.length(); i++) {
            System.err.println("     char[" + i + "]=" + (int)testData.charAt(i));
        }

        if (!testData.equals(DATA + DATA)) {
            throw new RuntimeException();
        }
    }

    public void lostOwnership(Clipboard clip, Transferable contents) {
        Runnable r = new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Transferable t = clipboard.getContents(null);
                    try {
                        InputStream is =
                            (InputStream)t.getTransferData(TEST_FLAVOR);
                        Reader r = new InputStreamReader(is,
                                StandardCharsets.UTF_8);
                        StringBuffer sb = new StringBuffer();
                        int ch = 0;
                        while ((ch = r.read()) != -1) {
                            System.err.println("ch=" + ch);
                            sb.append((char)ch);
                        }
                        testData = sb.toString();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    clipboard.setContents(new TextTransferable(""), null);
                }
            };
        new Thread(r).start();
    }

    public static void main(String[] args) {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            return;
        }

        MimeFormatsTest mimeFormatsTest = new MimeFormatsTest();
        mimeFormatsTest.start();
    }
}

class Child {
    public static void main(String[] args) {
        MimeFormatsTest test = new MimeFormatsTest();
        test.childRun();
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
                    finished  = true;
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
