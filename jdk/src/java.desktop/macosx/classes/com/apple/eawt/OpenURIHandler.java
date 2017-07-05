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

import com.apple.eawt.AppEvent.OpenURIEvent;

/**
 * An implementor is notified when the application is asked to open a URI.
 * The application only sends {@link com.apple.eawt.EAWTEvent.OpenURIEvent}s when it has been launched as a bundled Mac application, and it's Info.plist claims URL schemes in it's {@code CFBundleURLTypes} entry.
 * See the <a href="http://developer.apple.com/mac/library/documentation/General/Reference/InfoPlistKeyReference">Info.plist Key Reference</a> for more information about adding a {@code CFBundleURLTypes} key to your app's Info.plist.
 *
 * @see Application#setOpenURIHandler(OpenURIHandler)
 *
 * @since Java for Mac OS X 10.6 Update 3
 * @since Java for Mac OS X 10.5 Update 8
 */
public interface OpenURIHandler {
    /**
     * Called when the application is asked to open a URI
     * @param e the request to open a URI
     */
    public void openURI(final OpenURIEvent e);
}
