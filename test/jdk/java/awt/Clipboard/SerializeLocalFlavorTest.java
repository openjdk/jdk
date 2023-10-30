/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4696186
  @summary tests that NotSerializableException is not printed in the console if
           non-serializable object with DataFlavor.javaJVMLocalObjectMimeType
           is set into the clipboard
  @key headful
  @run main SerializeLocalFlavorTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;


public class SerializeLocalFlavorTest {
    private boolean failed = false;

    public static void main(String[] args) {
        new SerializeLocalFlavorTest().start();
    }

    public void start () {
        try {
            String[] command = {
                    System.getProperty("java.home", "")
                            + File.separator + "bin" + File.separator
                            + "java",
                    "-cp",
                    System.getProperty("test.classes", "."),
                    "Child"
            };

            Process process = Runtime.getRuntime().exec(command);
            ProcessResults pres = ProcessResults.doWaitFor(process);

            if (pres.stderr != null && pres.stderr.length() > 0) {
                System.err.println("========= Child err ========");
                System.err.print(pres.stderr);
                System.err.println("======================================");
            }

            if (pres.stdout != null && pres.stdout.length() > 0) {
                System.err.println("========= Child out ========");
                System.err.print(pres.stdout);
                System.err.println("======================================");
            }

            if (pres.stderr.indexOf("java.io.NotSerializableException") >= 0) {
                failed = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (failed) {
            throw new RuntimeException(
                    "The test failed: java.io.NotSerializableException printed!");
        } else {
            System.err.println("The test passed!");
        }
    }
}

class Child {
    public static void main (String [] args) throws Exception {
        NotSerializableLocalTransferable t =
                new NotSerializableLocalTransferable(new NotSer());
        Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(t, null);
    }
}

class NotSerializableLocalTransferable implements Transferable {
    public final DataFlavor flavor;

    private final DataFlavor[] flavors;

    private final Object data;


    public NotSerializableLocalTransferable(Object data) throws Exception {
        this.data = data;
        flavor = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType +
            ";class=" +  "\"" + data.getClass().getName() + "\"");
        this.flavors = new DataFlavor[] { flavor };
    }

    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return this.flavor.equals(flavor);
    }

    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException
    {
        if (this.flavor.equals(flavor)) {
            return (Object)data;
        }
        throw new UnsupportedFlavorException(flavor);
    }

}

class NotSer implements Serializable {
    private Object field = new Object(); // not serializable field
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
                    Thread.sleep(500);
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
