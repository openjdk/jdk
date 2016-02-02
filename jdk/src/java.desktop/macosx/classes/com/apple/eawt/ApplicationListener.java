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

import java.util.EventListener;

/**
 * ApplicationEvents are deprecated. Use individual AppEvent listeners or handlers instead.
 *
 * @see Application#addAppEventListener(AppEventListener)
 *
 * @see AboutHandler
 * @see PreferencesHandler
 * @see OpenURIHandler
 * @see OpenFilesHandler
 * @see PrintFilesHandler
 * @see QuitHandler
 *
 * @see AppReOpenedListener
 * @see AppForegroundListener
 * @see AppHiddenListener
 * @see UserSessionListener
 * @see ScreenSleepListener
 * @see SystemSleepListener
 *
 * @since 1.4
 * @deprecated replaced by {@link AboutHandler}, {@link PreferencesHandler}, {@link AppReOpenedListener}, {@link OpenFilesHandler}, {@link PrintFilesHandler}, {@link QuitHandler}, {@link QuitResponse}
 */
@SuppressWarnings("deprecation")
@Deprecated
public interface ApplicationListener extends EventListener {
    /**
     * Called when the user selects the About item in the application menu. If {@code event} is not handled by
     * setting {@code isHandled(true)}, a default About window consisting of the application's name and icon is
     * displayed. To display a custom About window, designate the {@code event} as being handled and display the
     * appropriate About window.
     *
     * @param event an ApplicationEvent initiated by the user choosing About in the application menu
     * @deprecated use {@link AboutHandler}
     */
    @Deprecated
    public void handleAbout(ApplicationEvent event);

    /**
     * Called when the application receives an Open Application event from the Finder or another application. Usually
     * this will come from the Finder when a user double-clicks your application icon. If there is any special code
     * that you want to run when you user launches your application from the Finder or by sending an Open Application
     * event from another application, include that code as part of this handler. The Open Application event is sent
     * after AWT has been loaded.
     *
     * @param event the Open Application event
     * @deprecated no replacement
     */
    @Deprecated
    public void handleOpenApplication(ApplicationEvent event);

    /**
     * Called when the application receives an Open Document event from the Finder or another application. This event
     * is generated when a user double-clicks a document in the Finder. If the document is registered as belonging
     * to your application, this event is sent to your application. Documents are bound to a particular application based
     * primarily on their suffix. In the Finder, a user selects a document and then from the File Menu chooses Get Info.
     * The Info window allows users to bind a document to a particular application.
     *
     * These events are sent only if the bound application has file types listed in the Info.plist entries Document Types
     * or CFBundleDocumentTypes.
     *
     * The ApplicationEvent sent to this handler holds a reference to the file being opened.
     *
     * @param event an Open Document event with reference to the file to be opened
     * @deprecated use {@link OpenFilesHandler}
     */
    @Deprecated
    public void handleOpenFile(ApplicationEvent event);

    /**
     * Called when the Preference item in the application menu is selected. Native Mac OS X applications make their
     * Preferences window available through the application menu. Java applications are automatically given an application
     * menu in Mac OS X. By default, the Preferences item is disabled in that menu. If you are deploying an application
     * on Mac OS X, you should enable the preferences item with {@code setEnabledPreferencesMenu(true)} in the
     * Application object and then display your Preferences window in this handler.
     *
     * @param event triggered when the user selects Preferences from the application menu
     * @deprecated use {@link PreferencesHandler}
     */
    @Deprecated
    public void handlePreferences(ApplicationEvent event);

    /**
     * Called when the application is sent a request to print a particular file or files. You can allow other applications to
     * print files with your application by implementing this handler. If another application sends a Print Event along
     * with the name of a file that your application knows how to process, you can use this handler to determine what to
     * do with that request. You might open your entire application, or just invoke your printing classes.
     *
     * These events are sent only if the bound application has file types listed in the Info.plist entries Document Types
     * or CFBundleDocumentTypes.
     *
     * The ApplicationEvent sent to this handler holds a reference to the file being opened.
     *
     * @param event a Print Document event with a reference to the file(s) to be printed
     * @deprecated use {@link PrintFilesHandler}
     */
    @Deprecated
    public void handlePrintFile(ApplicationEvent event);

    /**
     * Called when the application is sent the Quit event. This event is generated when the user selects Quit from the
     * application menu, when the user types Command-Q, or when the user control clicks on your application icon in the
     * Dock and chooses Quit. You can either accept or reject the request to quit. You might want to reject the request
     * to quit if the user has unsaved work. Reject the request, move into your code to save changes, then quit your
     * application. To accept the request to quit, and terminate the application, set {@code isHandled(true)} for the
     * {@code event}. To reject the quit, set {@code isHandled(false)}.
     *
     * @param event a Quit Application event
     * @deprecated use {@link QuitHandler} and {@link QuitResponse}
     */
    @Deprecated
    public void handleQuit(ApplicationEvent event);

    /**
     * Called when the application receives a Reopen Application event from the Finder or another application. Usually
     * this will come when a user clicks on your application icon in the Dock. If there is any special code
     * that needs to run when your user clicks on your application icon in the Dock or when a Reopen Application
     * event is sent from another application, include that code as part of this handler.
     *
     * @param event the Reopen Application event
     * @deprecated use {@link AppReOpenedListener}
     */
    @Deprecated
    public void handleReOpenApplication(ApplicationEvent event);
}
