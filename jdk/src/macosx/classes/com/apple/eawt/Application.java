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

import java.awt.*;
import java.awt.peer.*;
import java.beans.Beans;
import java.security.PrivilegedAction;

import javax.swing.JMenuBar;

import sun.lwawt.*;
import sun.lwawt.macosx.*;

/**
 * The <code>Application</code> class allows you to integrate your Java application with the native Mac OS X environment.
 * You can provide your Mac OS X users a greatly enhanced experience by implementing a few basic handlers for standard system events.
 *
 * For example:
 * <ul>
 * <li>Open an about dialog when a user chooses About from the application menu.</li>
 * <li>Open a preferences window when the users chooses Preferences from the application menu.</li>
 * <li>Create a new document when the user clicks on your Dock icon, and no windows are open.</li>
 * <li>Open a document that the user double-clicked on in the Finder.</li>
 * <li>Open a custom URL scheme when a user clicks on link in a web browser.</li>
 * <li>Reconnect to network services after the system has awoke from sleep.</li>
 * <li>Cleanly shutdown your application when the user chooses Quit from the application menu, Dock icon, or types Command-Q.</li>
 * <li>Cancel shutdown/logout if the user has unsaved changes in your application.</li>
 * </ul>
 *
 * @since 1.4
 */
public class Application {
    private static native void nativeInitializeApplicationDelegate();

    static Application sApplication = null;

    static {
        java.security.AccessController.doPrivileged((PrivilegedAction<?>)new sun.security.action.LoadLibraryAction("awt"));

        checkSecurity();
        if (!Beans.isDesignTime()) {
            nativeInitializeApplicationDelegate();
        }

        sApplication = new Application();
    }

    private static void checkSecurity() {
        final SecurityManager security = System.getSecurityManager();
        if (security == null) return;
        security.checkPermission(new RuntimePermission("canProcessApplicationEvents"));
    }

    /**
     * @return the singleton representing this Mac OS X Application
     *
     * @since 1.4
     */
    public static Application getApplication() {
        checkSecurity();
        return sApplication;
    }

    // some singletons, since they get called back into from native
    final _AppEventHandler eventHandler = _AppEventHandler.getInstance();
    final _AppMenuBarHandler menuBarHandler = _AppMenuBarHandler.getInstance();
    final _AppDockIconHandler iconHandler = new _AppDockIconHandler();

    /**
     * Creates an Application instance. Should only be used in JavaBean environments.
     * @deprecated use {@link #getApplication()}
     *
     * @since 1.4
     */
    @Deprecated
    public Application() {
        checkSecurity();
    }

    /**
     * Adds sub-types of {@link AppEventListener} to listen for notifications from the native Mac OS X system.
     *
     * @see AppForegroundListener
     * @see AppHiddenListener
     * @see AppReOpenedListener
     * @see ScreenSleepListener
     * @see SystemSleepListener
     * @see UserSessionListener
     *
     * @param listener
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void addAppEventListener(final AppEventListener listener) {
        eventHandler.addListener(listener);
    }

    /**
     * Removes sub-types of {@link AppEventListener} from listening for notifications from the native Mac OS X system.
     *
     * @see AppForegroundListener
     * @see AppHiddenListener
     * @see AppReOpenedListener
     * @see ScreenSleepListener
     * @see SystemSleepListener
     * @see UserSessionListener
     *
     * @param listener
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void removeAppEventListener(final AppEventListener listener) {
        eventHandler.removeListener(listener);
    }

    /**
     * Installs a handler to show a custom About window for your application.
     *
     * Setting the {@link AboutHandler} to <code>null</code> reverts it to the default Cocoa About window.
     *
     * @param aboutHandler the handler to respond to the {@link AboutHandler#handleAbout()} message
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setAboutHandler(final AboutHandler aboutHandler) {
        eventHandler.aboutDispatcher.setHandler(aboutHandler);
    }

    /**
     * Installs a handler to create the Preferences menu item in your application's app menu.
     *
     * Setting the {@link PreferencesHandler} to <code>null</code> will remove the Preferences item from the app menu.
     *
     * @param preferencesHandler
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setPreferencesHandler(final PreferencesHandler preferencesHandler) {
        eventHandler.preferencesDispatcher.setHandler(preferencesHandler);
    }

    /**
     * Installs the handler which is notified when the application is asked to open a list of files.
     * The {@link OpenFilesHandler#openFiles(AppEvent.OpenFilesEvent)} notifications are only sent if the Java app is a bundled application, with a <code>CFBundleDocumentTypes</code> array present in it's Info.plist.
     * See the <a href="http://developer.apple.com/mac/library/documentation/General/Reference/InfoPlistKeyReference">Info.plist Key Reference</a> for more information about adding a <code>CFBundleDocumentTypes</code> key to your app's Info.plist.
     *
     * @param openFileHandler
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setOpenFileHandler(final OpenFilesHandler openFileHandler) {
        eventHandler.openFilesDispatcher.setHandler(openFileHandler);
    }

    /**
     * Installs the handler which is notified when the application is asked to print a list of files.
     * The {@link PrintFilesHandler#printFiles(AppEvent.PrintFilesEvent)} notifications are only sent if the Java app is a bundled application, with a <code>CFBundleDocumentTypes</code> array present in it's Info.plist.
     * See the <a href="http://developer.apple.com/mac/library/documentation/General/Reference/InfoPlistKeyReference">Info.plist Key Reference</a> for more information about adding a <code>CFBundleDocumentTypes</code> key to your app's Info.plist.
     *
     * @param printFileHandler
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setPrintFileHandler(final PrintFilesHandler printFileHandler) {
        eventHandler.printFilesDispatcher.setHandler(printFileHandler);
    }

    /**
     * Installs the handler which is notified when the application is asked to open a URL.
     * The {@link OpenURIHandler#openURI(AppEvent.OpenURIEvent)} notifications are only sent if the Java app is a bundled application, with a <code>CFBundleURLTypes</code> array present in it's Info.plist.
     * See the <a href="http://developer.apple.com/mac/library/documentation/General/Reference/InfoPlistKeyReference">Info.plist Key Reference</a> for more information about adding a <code>CFBundleURLTypes</code> key to your app's Info.plist.
     *
     * Setting the handler to <code>null</code> causes all {@link OpenURIHandler#openURI(AppEvent.OpenURIEvent)} requests to be enqueued until another handler is set.
     *
     * @param openURIHandler
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setOpenURIHandler(final OpenURIHandler openURIHandler) {
        eventHandler.openURIDispatcher.setHandler(openURIHandler);
    }

    /**
     * Installs the handler which determines if the application should quit.
     * The handler is passed a one-shot {@link QuitResponse} which can cancel or proceed with the quit.
     * Setting the handler to <code>null</code> causes all quit requests to directly perform the default {@link QuitStrategy}.
     *
     * @param quitHandler the handler that is called when the application is asked to quit
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setQuitHandler(final QuitHandler quitHandler) {
        eventHandler.quitDispatcher.setHandler(quitHandler);
    }

    /**
     * Sets the default strategy used to quit this application. The default is calling SYSTEM_EXIT_0.
     *
     * The quit strategy can also be set with the "apple.eawt.quitStrategy" system property.
     *
     * @param strategy the way this application should be shutdown
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void setQuitStrategy(final QuitStrategy strategy) {
        eventHandler.setDefaultQuitStrategy(strategy);
    }

    /**
     * Enables this application to be suddenly terminated.
     *
     * Call this method to indicate your application's state is saved, and requires no notification to be terminated.
     * Letting your application remain terminatable improves the user experience by avoiding re-paging in your application when it's asked to quit.
     *
     * <b>Note: enabling sudden termination will allow your application to be quit without notifying your QuitHandler, or running any shutdown hooks.</b>
     * User initiated Cmd-Q, logout, restart, or shutdown requests will effectively "kill -KILL" your application.
     *
     * This call has no effect on Mac OS X versions prior to 10.6.
     *
     * @see <a href="http://developer.apple.com/mac/library/documentation/cocoa/reference/foundation/Classes/NSProcessInfo_Class">NSProcessInfo class references</a> for more information about Sudden Termination.
     * @see Application#disableSuddenTermination()
     *
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void enableSuddenTermination() {
        _AppMiscHandlers.enableSuddenTermination();
    }

    /**
     * Prevents this application from being suddenly terminated.
     *
     * Call this method to indicate that your application has unsaved state, and may not be terminated without notification.
     *
     * This call has no effect on Mac OS X versions prior to 10.6.
     *
     * @see <a href="http://developer.apple.com/mac/library/documentation/cocoa/reference/foundation/Classes/NSProcessInfo_Class">NSProcessInfo class references</a> for more information about Sudden Termination.
     * @see Application#enableSuddenTermination()
     *
     * @since Java for Mac OS X 10.6 Update 3
     * @since Java for Mac OS X 10.5 Update 8
     */
    public void disableSuddenTermination() {
        _AppMiscHandlers.disableSuddenTermination();
    }

    /**
     * Requests this application to move to the foreground.
     *
     * @param allWindows if all windows of this application should be moved to the foreground, or only the foremost one
     *
     * @since Java for Mac OS X 10.6 Update 1
     * @since Java for Mac OS X 10.5 Update 6 - 1.6, 1.5
     */
    public void requestForeground(final boolean allWindows) {
        _AppMiscHandlers.requestActivation(allWindows);
    }

    /**
     * Requests user attention to this application (usually through bouncing the Dock icon). Critical
     * requests will continue to bounce the Dock icon until the app is activated. An already active
     * application requesting attention does nothing.
     *
     * @param critical if this is an important request
     *
     * @since Java for Mac OS X 10.6 Update 1
     * @since Java for Mac OS X 10.5 Update 6 - 1.6, 1.5
     */
    public void requestUserAttention(final boolean critical) {
        _AppMiscHandlers.requestUserAttention(critical);
    }

    /**
     * Opens the native help viewer application if a Help Book has been added to the
     * application bundler and registered in the Info.plist with CFBundleHelpBookFolder.
     *
     * See http://developer.apple.com/qa/qa2001/qa1022.html for more information.
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public void openHelpViewer() {
        _AppMiscHandlers.openHelpViewer();
    }

    /**
     * Attaches the contents of the provided PopupMenu to the application's Dock icon.
     *
     * @param menu the PopupMenu to attach to this application's Dock icon
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public void setDockMenu(final PopupMenu menu) {
        iconHandler.setDockMenu(menu);
    }

    /**
     * @return the PopupMenu used to add items to this application's Dock icon
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public PopupMenu getDockMenu() {
        return iconHandler.getDockMenu();
    }

    /**
     * Changes this application's Dock icon to the provided image.
     *
     * @param image
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public void setDockIconImage(final Image image) {
        iconHandler.setDockIconImage(image);
    }

    /**
     * Obtains an image of this application's Dock icon.
     *
     * @return an image of this application's Dock icon
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public Image getDockIconImage() {
        return iconHandler.getDockIconImage();
    }

    /**
     * Affixes a small system provided badge to this application's Dock icon. Usually a number.
     *
     * @param badge textual label to affix to the Dock icon
     *
     * @since Java for Mac OS X 10.5 - 1.5
     * @since Java for Mac OS X 10.5 Update 1 - 1.6
     */
    public void setDockIconBadge(final String badge) {
        iconHandler.setDockIconBadge(badge);
    }

    /**
     * Sets the default menu bar to use when there are no active frames.
     * Only used when the system property "apple.laf.useScreenMenuBar" is "true", and
     * the Aqua Look and Feel is active.
     *
     * @param menuBar to use when no other frames are active
     *
     * @since Java for Mac OS X 10.6 Update 1
     * @since Java for Mac OS X 10.5 Update 6 - 1.6, 1.5
     */
    public void setDefaultMenuBar(final JMenuBar menuBar) {
        menuBarHandler.setDefaultMenuBar(menuBar);
    }

    /**
     * Requests that a {@link Window} should animate into or out of full screen mode.
     * Only {@link Window}s marked as full screenable by {@link FullScreenUtilities#setWindowCanFullScreen(Window, boolean)} can be toggled.
     *
     * @param window to animate into or out of full screen mode
     *
     * @since Java for Mac OS X 10.7 Update 1
     */
    @SuppressWarnings("deprecation")
    public void requestToggleFullScreen(final Window window) {
        final ComponentPeer peer = window.getPeer();

        if (!(peer instanceof LWWindowPeer)) return;
        Object platformWindow = ((LWWindowPeer) peer).getPlatformWindow();
        if (!(platformWindow instanceof CPlatformWindow)) return;
        ((CPlatformWindow)platformWindow).toggleFullScreen();
    }


    // -- DEPRECATED API --

    /**
     * Adds the specified ApplicationListener as a receiver of callbacks from this class.
     * This method throws a RuntimeException if the newer About, Preferences, Quit, etc handlers are installed.
     *
     * @param listener an implementation of ApplicationListener that handles ApplicationEvents
     *
     * @deprecated register individual handlers for each task (About, Preferences, Open, Print, Quit, etc)
     * @since 1.4
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void addApplicationListener(final ApplicationListener listener) {
        eventHandler.legacyHandler.addLegacyAppListener(listener);
    }

    /**
     * Removes the specified ApplicationListener from being a receiver of callbacks from this class.
     * This method throws a RuntimeException if the newer About, Preferences, Quit, etc handlers are installed.
     *
     * @param listener an implementation of ApplicationListener that had previously been registered to handle ApplicationEvents
     *
     * @deprecated unregister individual handlers for each task (About, Preferences, Open, Print, Quit, etc)
     * @since 1.4
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public void removeApplicationListener(final ApplicationListener listener) {
        eventHandler.legacyHandler.removeLegacyAppListener(listener);
    }

    /**
     * Enables the Preferences item in the application menu. The ApplicationListener receives a callback for
     * selection of the Preferences item in the application menu only if this is set to <code>true</code>.
     *
     * If a Preferences item isn't present, this method adds and enables it.
     *
     * @param enable specifies whether the Preferences item in the application menu should be enabled (<code>true</code>) or not (<code>false</code>)
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public void setEnabledPreferencesMenu(final boolean enable) {
        menuBarHandler.setPreferencesMenuItemVisible(true);
        menuBarHandler.setPreferencesMenuItemEnabled(enable);
    }

    /**
     * Enables the About item in the application menu. The ApplicationListener receives a callback for
     * selection of the About item in the application menu only if this is set to <code>true</code>. Because AWT supplies
     * a standard About window when an application may not, by default this is set to <code>true</code>.
     *
     * If the About item isn't present, this method adds and enables it.
     *
     * @param enable specifies whether the About item in the application menu should be enabled (<code>true</code>) or not (<code>false</code>)
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public void setEnabledAboutMenu(final boolean enable) {
        menuBarHandler.setAboutMenuItemEnabled(enable);
    }

    /**
     * Determines if the Preferences item of the application menu is enabled.
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public boolean getEnabledPreferencesMenu() {
        return menuBarHandler.isPreferencesMenuItemEnabled();
    }

    /**
     * Determines if the About item of the application menu is enabled.
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public boolean getEnabledAboutMenu() {
        return menuBarHandler.isAboutMenuItemEnabled();
    }

    /**
     * Determines if the About item of the application menu is present.
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public boolean isAboutMenuItemPresent() {
        return menuBarHandler.isAboutMenuItemVisible();
    }

    /**
     * Adds the About item to the application menu if the item is not already present.
     *
     * @deprecated use {@link #setAboutHandler(AboutHandler)} with a non-null {@link AboutHandler} parameter
     * @since 1.4
     */
    @Deprecated
    public void addAboutMenuItem() {
        menuBarHandler.setAboutMenuItemVisible(true);
    }

    /**
     * Removes the About item from the application menu if  the item is present.
     *
     * @deprecated use {@link #setAboutHandler(AboutHandler)} with a null parameter
     * @since 1.4
     */
    @Deprecated
    public void removeAboutMenuItem() {
        menuBarHandler.setAboutMenuItemVisible(false);
    }

    /**
     * Determines if the About Preferences of the application menu is present. By default there is no Preferences menu item.
     *
     * @deprecated no replacement
     * @since 1.4
     */
    @Deprecated
    public boolean isPreferencesMenuItemPresent() {
        return menuBarHandler.isPreferencesMenuItemVisible();
    }

    /**
     * Adds the Preferences item to the application menu if the item is not already present.
     *
     * @deprecated use {@link #setPreferencesHandler(PreferencesHandler)} with a non-null {@link PreferencesHandler} parameter
     * @since 1.4
     */
    @Deprecated
    public void addPreferencesMenuItem() {
        menuBarHandler.setPreferencesMenuItemVisible(true);
    }

    /**
     * Removes the Preferences item from the application menu if that item is present.
     *
     * @deprecated use {@link #setPreferencesHandler(PreferencesHandler)} with a null parameter
     * @since 1.4
     */
    @Deprecated
    public void removePreferencesMenuItem() {
        menuBarHandler.setPreferencesMenuItemVisible(false);
    }

    /**
     * @deprecated Use <code>java.awt.MouseInfo.getPointerInfo().getLocation()</code>.
     *
     * @since 1.4
     */
    @Deprecated
    public static Point getMouseLocationOnScreen() {
        return java.awt.MouseInfo.getPointerInfo().getLocation();
    }
}
