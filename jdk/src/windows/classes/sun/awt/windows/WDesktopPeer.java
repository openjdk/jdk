/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.awt.windows;


import java.awt.Desktop.Action;
import java.awt.peer.DesktopPeer;
import java.io.File;
import java.io.IOException;
import java.net.URI;


/**
 * Concrete implementation of the interface <code>DesktopPeer</code> for
 * the Windows platform.
 *
 * @see DesktopPeer
 */
public class WDesktopPeer implements DesktopPeer {
    /* Contants for the operation verbs */
    private static String ACTION_OPEN_VERB = "open";
    private static String ACTION_EDIT_VERB = "edit";
    private static String ACTION_PRINT_VERB = "print";

    public boolean isSupported(Action action) {
        // OPEN, EDIT, PRINT, MAIL, BROWSE all supported on windows.
        return true;
    }

    public void open(File file) throws IOException {
        this.ShellExecute(file, ACTION_OPEN_VERB);
    }

    public void edit(File file) throws IOException {
        this.ShellExecute(file, ACTION_EDIT_VERB);
    }

    public void print(File file) throws IOException {
        this.ShellExecute(file, ACTION_PRINT_VERB);
    }

    public void mail(URI uri) throws IOException {
        this.ShellExecute(uri, ACTION_OPEN_VERB);
    }

    public void browse(URI uri) throws IOException {
        this.ShellExecute(uri, ACTION_OPEN_VERB);
    }

    private void ShellExecute(File file, String verb) throws IOException {
        String errMsg = ShellExecute(file.getAbsolutePath(), verb);
        if (errMsg != null) {
            throw new IOException("Failed to " + verb + " " + file + ". Error message: " + errMsg);
        }
    }

    private void ShellExecute(URI uri, String verb) throws IOException {
        String errmsg = ShellExecute(uri.toString(), verb);

        if (errmsg != null) {
            throw new IOException("Failed to " + verb + " " + uri
                    + ". Error message: " + errmsg);
        }
    }

    private static native String ShellExecute(String fileOrUri, String verb);

}
