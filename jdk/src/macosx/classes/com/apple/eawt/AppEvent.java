/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.URI;
import java.util.*;
import java.awt.Window;

/**
 * AppEvents are sent to listeners and handlers installed on the {@link Application}.
 *
 * @since Java for Mac OS X 10.6 Update 3
 * @since Java for Mac OS X 10.5 Update 8
 */
public abstract class AppEvent extends EventObject {
    AppEvent() {
        super(Application.getApplication());
    }

    /**
     * Contains a list of files.
     */
    public abstract static class FilesEvent extends AppEvent {
        final List<File> files;

        FilesEvent(final List<File> files) {
            this.files = files;
        }

        /**
         * @return the list of files
         */
        public List<File> getFiles() {
            return files;
        }
    }

    /**
     * Event sent when the app is asked to open a list of files.
     *
     * @see OpenFilesHandler#openFiles(OpenFilesEvent)
     */
    public static class OpenFilesEvent extends FilesEvent {
        final String searchTerm;

        OpenFilesEvent(final List<File> files, final String searchTerm) {
            super(files);
            this.searchTerm = searchTerm;
        }

        /**
         * If the files were opened using the Spotlight search menu or a Finder search window, this method obtains the search term used to find the files.
         * This is useful for highlighting the search term in the documents when they are opened.
         * @return the search term used to find the files
         */
        public String getSearchTerm() {
            return searchTerm;
        }
    }

    /**
     * Event sent when the app is asked to print a list of files.
     *
     * @see PrintFilesHandler#printFiles(PrintFilesEvent)
     */
    public static class PrintFilesEvent extends FilesEvent {
        PrintFilesEvent(final List<File> files) {
            super(files);
        }
    }

    /**
     * Event sent when the app is asked to open a URI.
     *
     * @see OpenURIHandler#openURI(OpenURIEvent)
     */
    public static class OpenURIEvent extends AppEvent {
        final URI uri;

        OpenURIEvent(final URI uri) {
            this.uri = uri;
        }

        /**
         * @return the URI the app was asked to open
         */
        public URI getURI() {
            return uri;
        }
    }

    /**
     * Event sent when the application is asked to open it's about window.
     *
     * @see AboutHandler#handleAbout()
     */
    public static class AboutEvent extends AppEvent { AboutEvent() { } }

    /**
     * Event sent when the application is asked to open it's preferences window.
     *
     * @see PreferencesHandler#handlePreferences()
     */
    public static class PreferencesEvent extends AppEvent { PreferencesEvent() { } }

    /**
     * Event sent when the application is asked to quit.
     *
     * @see QuitHandler#handleQuitRequestWith(QuitEvent, QuitResponse)
     */
    public static class QuitEvent extends AppEvent { QuitEvent() { } }

    /**
     * Event sent when the application is asked to re-open itself.
     *
     * @see AppReOpenedListener#appReOpened(AppReOpenedEvent)
     */
    public static class AppReOpenedEvent extends AppEvent { AppReOpenedEvent() { } }

    /**
     * Event sent when the application has become the foreground app, and when it has resigned being the foreground app.
     *
     * @see AppForegroundListener#appRaisedToForeground(AppForegroundEvent)
     * @see AppForegroundListener#appMovedToBackground(AppForegroundEvent)
     */
    public static class AppForegroundEvent extends AppEvent { AppForegroundEvent() { } }

    /**
     * Event sent when the application has been hidden or shown.
     *
     * @see AppHiddenListener#appHidden(AppHiddenEvent)
     * @see AppHiddenListener#appUnhidden(AppHiddenEvent)
     */
    public static class AppHiddenEvent extends AppEvent { AppHiddenEvent() { } }

    /**
     * Event sent when the user session has been changed via Fast User Switching.
     *
     * @see UserSessionListener#userSessionActivated(UserSessionEvent)
     * @see UserSessionListener#userSessionDeactivated(UserSessionEvent)
     */
    public static class UserSessionEvent extends AppEvent { UserSessionEvent() { } }

    /**
     * Event sent when the displays attached to the system enter and exit power save sleep.
     *
     * @see ScreenSleepListener#screenAboutToSleep(ScreenSleepEvent)
     * @see ScreenSleepListener#screenAwoke(ScreenSleepEvent)
     */
    public static class ScreenSleepEvent extends AppEvent { ScreenSleepEvent() { } }

    /**
     * Event sent when the system enters and exits power save sleep.
     *
     * @see SystemSleepListener#systemAboutToSleep(SystemSleepEvent)
     * @see SystemSleepListener#systemAwoke(SystemSleepEvent)
     */
    public static class SystemSleepEvent extends AppEvent { SystemSleepEvent() { } }

    /**
     * Event sent when a window is entering/exiting or has entered/exited full screen state.
     *
     * @see FullScreenUtilities
     *
     * @since Java for Mac OS X 10.7 Update 1
     */
    public static class FullScreenEvent extends AppEvent {
        final Window window;

        FullScreenEvent(final Window window) {
            this.window = window;
        }

        /**
         * @return window transitioning between full screen states
         */
        public Window getWindow() {
            return window;
        }
    }
}
