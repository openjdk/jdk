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
  @bug 4683804
  @summary Tests that in ClipboardOwner.lostOwnership() Clipboard.getContents()
           returns actual contents of the clipboard and Clipboard.setContents()
           can set contents of the clipboard and its owner. The clipboard is
           the system clipboard.
  @key headful
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class SystemClipboardTest {

    public static void main(String[] args) {
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
    static boolean failed;

    private static final Object LOCK = new Object();

    private static final int CHAIN_LENGTH = 5;
    private final static Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();

    private int m, id;

    public SystemClipboardOwner(int m) { this.m = m; id = m; }

    public void lostOwnership(Clipboard cb, Transferable contents) {
        System.err.println(id + " lost clipboard ownership");

        Transferable t = getClipboardContents(cb, null);
        String msg = null;
        try {
            msg = (String)t.getTransferData(DataFlavor.stringFlavor);
        } catch (Exception e) {
             System.err.println(id + " can't getTransferData: " + e);
        }
        System.err.println(id + " Clipboard.getContents(): " + msg);
        if ( ! msg.equals( "" + (m+1) ) ) {
            failed = true;
            System.err.println(
                    "Clipboard.getContents() returned incorrect contents!");
        }

        m += 2;
        if (m <= CHAIN_LENGTH) {
            System.err.println(id + " Clipboard.setContents(): " + m);
            setClipboardContents(cb, new StringSelection(m + ""), this);
        }

        synchronized (LOCK) {
            if (m > CHAIN_LENGTH) {
                LOCK.notifyAll();
            }
        }
    }

    public static void run() {
        SystemClipboardOwner cbo1 = new SystemClipboardOwner(0);
        System.err.println(cbo1.m + " Clipboard.setContents(): " + cbo1.m);
        setClipboardContents(clipboard,
                new StringSelection(cbo1.m + ""), cbo1);

        SystemClipboardOwner cbo2 = new SystemClipboardOwner(1);
        synchronized (LOCK) {
            System.err.println(cbo2.m + " Clipboard.setContents(): " + cbo2.m);
            setClipboardContents(clipboard,
                    new StringSelection(cbo2.m + ""), cbo2);
            try {
                LOCK.wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }

        if (cbo1.m < CHAIN_LENGTH) {
            failed = true;
            System.err.println("chain of calls of lostOwnership() broken!");
        }
    }

    private static void setClipboardContents(Clipboard cb,
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

    private static Transferable getClipboardContents(Clipboard cb,
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
