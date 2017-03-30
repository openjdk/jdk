/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.desktop.AboutHandler;
import java.awt.desktop.AppForegroundListener;
import java.awt.desktop.AppHiddenListener;
import java.awt.desktop.AppReopenedListener;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.OpenURIHandler;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.PrintFilesHandler;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.ScreenSleepListener;
import java.awt.desktop.SystemEventListener;
import java.awt.desktop.SystemSleepListener;
import java.awt.desktop.UserSessionListener;

/**
 * An abstract adapter class for receiving {@code ApplicationEvents}.
 *
 * ApplicationEvents are deprecated. Use individual app event listeners or handlers instead.
 *
 * @see Application#addAppEventListener(SystemEventListener)
 *
 * @see AboutHandler
 * @see PreferencesHandler
 * @see OpenURIHandler
 * @see OpenFilesHandler
 * @see PrintFilesHandler
 * @see QuitHandler
 *
 * @see AppReopenedListener
 * @see AppForegroundListener
 * @see AppHiddenListener
 * @see UserSessionListener
 * @see ScreenSleepListener
 * @see SystemSleepListener
 *
 * @deprecated replaced by {@link AboutHandler}, {@link PreferencesHandler}, {@link AppReopenedListener}, {@link OpenFilesHandler}, {@link PrintFilesHandler}, {@link QuitHandler}, {@link MacQuitResponse}.
 * @since 1.4
 */
@SuppressWarnings("deprecation")
@Deprecated
public class ApplicationAdapter implements ApplicationListener {
    @Deprecated
    public void handleAbout(final ApplicationEvent event) { }

    @Deprecated
    public void handleOpenApplication(final ApplicationEvent event) { }

    @Deprecated
    public void handleOpenFile(final ApplicationEvent event) { }

    @Deprecated
    public void handlePreferences(final ApplicationEvent event) { }

    @Deprecated
    public void handlePrintFile(final ApplicationEvent event) { }

    @Deprecated
    public void handleQuit(final ApplicationEvent event) { }

    @Deprecated
    public void handleReOpenApplication(final ApplicationEvent event) { }
}
