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

package com.apple.eawt;

import com.apple.eawt.AppEvent.OpenFilesEvent;

/**
 * An implementor is notified when the application is asked to open a list of files.
 * This message is only sent if the application has registered that it handles CFBundleDocumentTypes in it's Info.plist.
 *
 * @see Application#setOpenFileHandler(OpenFilesHandler)
 *
 * @since Java for Mac OS X 10.6 Update 3
 * @since Java for Mac OS X 10.5 Update 8
 */
public interface OpenFilesHandler {
    /**
     * Called when the application is asked to open a list of files.
     * @param e the request to open a list of files, and the search term used to find them, if any.
     */
    public void openFiles(final OpenFilesEvent e);
}
