/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import java.awt.Desktop.Action;
import java.awt.peer.DesktopPeer;
import java.io.File;
import java.io.IOException;
import java.net.URI;


/**
 * Concrete implementation of the interface <code>DesktopPeer</code> for MacOS X
 *
 * @see DesktopPeer
 */
public class CDesktopPeer implements DesktopPeer {

    public boolean isSupported(Action action) {
        // OPEN, EDIT, PRINT, MAIL, BROWSE all supported.
        // Though we don't really differentiate between OPEN / EDIT
        return true;
    }

    public void open(File file) throws IOException {
        this.lsOpenFile(file, false);
    }

    public void edit(File file) throws IOException {
        this.lsOpenFile(file, false);
    }

    public void print(File file) throws IOException {
        this.lsOpenFile(file, true);
    }

    public void mail(URI uri) throws IOException {
        this.lsOpen(uri);
    }

    public void browse(URI uri) throws IOException {
        this.lsOpen(uri);
    }

    private void lsOpen(URI uri) throws IOException {
        int status = _lsOpenURI(uri.toString());

        if (status != 0 /* noErr */) {
            throw new IOException("Failed to mail or browse " + uri + ". Error code: " + status);
        }
    }

    private void lsOpenFile(File file, boolean print) throws IOException {
        int status = _lsOpenFile(file.getCanonicalPath(), print);

        if (status != 0 /* noErr */) {
            throw new IOException("Failed to open, edit or print " + file + ". Error code: " + status);
        }
    }

    private static native int _lsOpenURI(String uri);

    private static native int _lsOpenFile(String path, boolean print);

}
