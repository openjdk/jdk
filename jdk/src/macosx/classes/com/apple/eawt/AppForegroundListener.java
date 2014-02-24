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

import com.apple.eawt.AppEvent.AppForegroundEvent;

/**
 * Implementors are notified when the app becomes the foreground app and when it resigns being the foreground app.
 * This notification is useful for hiding and showing transient UI like palette windows which should be hidden when the app is in the background.
 *
 * @see Application#addAppEventListener(AppEventListener)
 *
 * @since Java for Mac OS X 10.6 Update 3
 * @since Java for Mac OS X 10.5 Update 8
 */
public interface AppForegroundListener extends AppEventListener {
    /**
     * Called when the app becomes the foreground app.
     * @param e the app became foreground notification.
     */
    public void appRaisedToForeground(final AppForegroundEvent e);

    /**
     * Called when the app resigns to the background and another app becomes the foreground app.
     * @param e the app resigned foreground notification.
     */
    public void appMovedToBackground(final AppForegroundEvent e);
}
