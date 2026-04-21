/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.desktop.AppHiddenEvent;
import java.awt.desktop.AppHiddenListener;
import java.awt.desktop.AppReopenedEvent;
import java.awt.desktop.AppReopenedListener;
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.OpenURIEvent;
import java.awt.desktop.OpenURIHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.PrintFilesEvent;
import java.awt.desktop.PrintFilesHandler;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitStrategy;
import java.awt.desktop.ScreenSleepEvent;
import java.awt.desktop.ScreenSleepListener;
import java.awt.desktop.SystemEventListener;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;
import java.awt.desktop.UserSessionEvent;
import java.awt.desktop.UserSessionEvent.Reason;
import java.awt.desktop.UserSessionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import sun.awt.SunToolkit;

final class _AppEventHandler {
    private static final int NOTIFY_ABOUT = 1;
    private static final int NOTIFY_PREFS = 2;
    private static final int NOTIFY_OPEN_APP = 3;
    private static final int NOTIFY_REOPEN_APP = 4;
    private static final int NOTIFY_QUIT = 5;
    private static final int NOTIFY_SHUTDOWN = 6;
    private static final int NOTIFY_ACTIVE_APP_GAINED = 7;
    private static final int NOTIFY_ACTIVE_APP_LOST = 8;
    private static final int NOTIFY_APP_HIDDEN = 9;
    private static final int NOTIFY_APP_SHOWN = 10;
    private static final int NOTIFY_USER_SESSION_ACTIVE = 11;
    private static final int NOTIFY_USER_SESSION_INACTIVE = 12;
    private static final int NOTIFY_SCREEN_SLEEP = 13;
    private static final int NOTIFY_SCREEN_WAKE = 14;
    private static final int NOTIFY_SYSTEM_SLEEP = 15;
    private static final int NOTIFY_SYSTEM_WAKE = 16;

    private static final int REGISTER_USER_SESSION = 1;
    private static final int REGISTER_SCREEN_SLEEP = 2;
    private static final int REGISTER_SYSTEM_SLEEP = 3;

    private static native void nativeOpenCocoaAboutWindow();
    private static native void nativeReplyToAppShouldTerminate(final boolean shouldTerminate);
    private static native void nativeRegisterForNotification(final int notification);

    static final _AppEventHandler instance = new _AppEventHandler();
    static _AppEventHandler getInstance() {
        return instance;
    }

    // single shot dispatchers (some queuing, others not)
    final _AboutDispatcher aboutDispatcher = new _AboutDispatcher();
    final _PreferencesDispatcher preferencesDispatcher = new _PreferencesDispatcher();
    final _OpenFileDispatcher openFilesDispatcher = new _OpenFileDispatcher();
    final _PrintFileDispatcher printFilesDispatcher = new _PrintFileDispatcher();
    final _OpenURIDispatcher openURIDispatcher = new _OpenURIDispatcher();
    final _QuitDispatcher quitDispatcher = new _QuitDispatcher();
    final _OpenAppDispatcher openAppDispatcher = new _OpenAppDispatcher();

    // multiplexing dispatchers (contains listener lists)
    final _AppReOpenedDispatcher reOpenAppDispatcher = new _AppReOpenedDispatcher();
    final _AppForegroundDispatcher foregroundAppDispatcher = new _AppForegroundDispatcher();
    final _HiddenAppDispatcher hiddenAppDispatcher = new _HiddenAppDispatcher();
    final _UserSessionDispatcher userSessionDispatcher = new _UserSessionDispatcher();
    final _ScreenSleepDispatcher screenSleepDispatcher = new _ScreenSleepDispatcher();
    final _SystemSleepDispatcher systemSleepDispatcher = new _SystemSleepDispatcher();

    QuitStrategy defaultQuitAction = QuitStrategy.NORMAL_EXIT;

    _AppEventHandler() {
        final String strategyProp = System.getProperty("apple.eawt.quitStrategy");
        if (strategyProp == null) return;

        if ("CLOSE_ALL_WINDOWS".equals(strategyProp)) {
            setDefaultQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
        } else if ("SYSTEM_EXIT_O".equals(strategyProp)
                || "NORMAL_EXIT".equals(strategyProp)) {
            setDefaultQuitStrategy(QuitStrategy.NORMAL_EXIT);
        } else {
            System.err.println("unrecognized apple.eawt.quitStrategy: " + strategyProp);
        }
    }

    void addListener(final SystemEventListener listener) {
        if (listener instanceof AppReopenedListener) reOpenAppDispatcher.addListener((AppReopenedListener)listener);
        if (listener instanceof AppForegroundListener) foregroundAppDispatcher.addListener((AppForegroundListener)listener);
        if (listener instanceof AppHiddenListener) hiddenAppDispatcher.addListener((AppHiddenListener)listener);
        if (listener instanceof UserSessionListener) userSessionDispatcher.addListener((UserSessionListener)listener);
        if (listener instanceof ScreenSleepListener) screenSleepDispatcher.addListener((ScreenSleepListener)listener);
        if (listener instanceof SystemSleepListener) systemSleepDispatcher.addListener((SystemSleepListener)listener);
    }

    void removeListener(final SystemEventListener listener) {
        if (listener instanceof AppReopenedListener) reOpenAppDispatcher.removeListener((AppReopenedListener)listener);
        if (listener instanceof AppForegroundListener) foregroundAppDispatcher.removeListener((AppForegroundListener)listener);
        if (listener instanceof AppHiddenListener) hiddenAppDispatcher.removeListener((AppHiddenListener)listener);
        if (listener instanceof UserSessionListener) userSessionDispatcher.removeListener((UserSessionListener)listener);
        if (listener instanceof ScreenSleepListener) screenSleepDispatcher.removeListener((ScreenSleepListener)listener);
        if (listener instanceof SystemSleepListener) systemSleepDispatcher.removeListener((SystemSleepListener)listener);
    }

    void openCocoaAboutWindow() {
        nativeOpenCocoaAboutWindow();
    }

    void setDefaultQuitStrategy(final QuitStrategy defaultQuitAction) {
        this.defaultQuitAction = defaultQuitAction;
    }

    MacQuitResponse currentQuitResponse;
    synchronized MacQuitResponse obtainQuitResponse() {
        if (currentQuitResponse != null) return currentQuitResponse;
        return currentQuitResponse = new MacQuitResponse(this);
    }

    synchronized void cancelQuit() {
        currentQuitResponse = null;
        nativeReplyToAppShouldTerminate(false);
    }

    synchronized void performQuit() {
        currentQuitResponse = null;

        try {
            if (defaultQuitAction == QuitStrategy.NORMAL_EXIT
                    || _AppMiscHandlers.isSuddenTerminationEnbaled()) System.exit(0);

            if (defaultQuitAction != QuitStrategy.CLOSE_ALL_WINDOWS) {
                throw new RuntimeException("Unknown quit action");
            }

            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    // walk frames from back to front
                    final Frame[] allFrames = Frame.getFrames();
                    for (int i = allFrames.length - 1; i >= 0; i--) {
                        final Frame frame = allFrames[i];
                        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                    }
                }
            });
        } finally {
            // Either we've just called System.exit(), or the app will call
            // it when processing a WINDOW_CLOSING event. Either way, we reply
            // to Cocoa that we don't want to exit the event loop yet.
            nativeReplyToAppShouldTerminate(false);
        }
    }

    /*
     * callbacks from native delegate
     */
    private static void handlePrintFiles(final List<String> filenames) {
        instance.printFilesDispatcher.dispatch(new _NativeEvent(filenames));
    }

    private static void handleOpenFiles(final List<String> filenames, final String searchTerm) {
        instance.openFilesDispatcher.dispatch(new _NativeEvent(filenames, searchTerm));
    }

    private static void handleOpenURI(final String uri) {
        instance.openURIDispatcher.dispatch(new _NativeEvent(uri));
    }

    // default funnel for non-complex events
    private static void handleNativeNotification(final int code) {
//        System.out.println(code);

        switch (code) {
            case NOTIFY_ABOUT:
                instance.aboutDispatcher.dispatch(new _NativeEvent());
                break;
            case NOTIFY_PREFS:
                instance.preferencesDispatcher.dispatch(new _NativeEvent());
                break;
            case NOTIFY_OPEN_APP:
                instance.openAppDispatcher.dispatch(new _NativeEvent());
                break;
            case NOTIFY_REOPEN_APP:
                instance.reOpenAppDispatcher.dispatch(new _NativeEvent());
                break;
            case NOTIFY_QUIT:
                instance.quitDispatcher.dispatch(new _NativeEvent());
                break;
            case NOTIFY_SHUTDOWN:
                // do nothing for now
                break;
            case NOTIFY_ACTIVE_APP_GAINED:
                instance.foregroundAppDispatcher.dispatch(new _NativeEvent(Boolean.TRUE));
                break;
            case NOTIFY_ACTIVE_APP_LOST:
                instance.foregroundAppDispatcher.dispatch(new _NativeEvent(Boolean.FALSE));
                break;
            case NOTIFY_APP_HIDDEN:
                instance.hiddenAppDispatcher.dispatch(new _NativeEvent(Boolean.TRUE));
                break;
            case NOTIFY_APP_SHOWN:
                instance.hiddenAppDispatcher.dispatch(new _NativeEvent(Boolean.FALSE));
                break;
            case NOTIFY_USER_SESSION_ACTIVE:
                instance.userSessionDispatcher.dispatch(new _NativeEvent(Boolean.TRUE));
                break;
            case NOTIFY_USER_SESSION_INACTIVE:
                instance.userSessionDispatcher.dispatch(new _NativeEvent(Boolean.FALSE));
                break;
            case NOTIFY_SCREEN_SLEEP:
                instance.screenSleepDispatcher.dispatch(new _NativeEvent(Boolean.TRUE));
                break;
            case NOTIFY_SCREEN_WAKE:
                instance.screenSleepDispatcher.dispatch(new _NativeEvent(Boolean.FALSE));
                break;
            case NOTIFY_SYSTEM_SLEEP:
                instance.systemSleepDispatcher.dispatch(new _NativeEvent(Boolean.TRUE));
                break;
            case NOTIFY_SYSTEM_WAKE:
                instance.systemSleepDispatcher.dispatch(new _NativeEvent(Boolean.FALSE));
                break;
            default:
                System.err.println("EAWT unknown native notification: " + code);
                break;
        }
    }


    final class _AboutDispatcher extends _AppEventDispatcher<AboutHandler> {
        @Override
        void performDefaultAction(final _NativeEvent event) {
            openCocoaAboutWindow(); // if the handler is null, fall back to showing the Cocoa default
        }

        @Override
        void performUsing(final AboutHandler handler, final _NativeEvent event) {
            handler.handleAbout(new AboutEvent());
        }
    }

    static final class _PreferencesDispatcher extends _AppEventDispatcher<PreferencesHandler> {
        @Override
        synchronized void setHandler(final PreferencesHandler handler) {
            super.setHandler(handler);

            _AppMenuBarHandler.getInstance().setPreferencesMenuItemVisible(handler != null);
            _AppMenuBarHandler.getInstance().setPreferencesMenuItemEnabled(handler != null);
        }

        @Override
        void performUsing(final PreferencesHandler handler, final _NativeEvent event) {
            handler.handlePreferences(new PreferencesEvent());
        }
    }

    static final class _OpenAppDispatcher extends _QueuingAppEventDispatcher<com.apple.eawt._OpenAppHandler> {
        @Override
        void performUsing(com.apple.eawt._OpenAppHandler handler, _NativeEvent event) {
            handler.handleOpenApp();
        }
    }

    static final class _AppReOpenedDispatcher extends _AppEventMultiplexor<AppReopenedListener> {
        @Override
        void performOnListener(AppReopenedListener listener, final _NativeEvent event) {
            final AppReopenedEvent e = new AppReopenedEvent();
            listener.appReopened(e);
        }
    }

    static final class _AppForegroundDispatcher extends _BooleanAppEventMultiplexor<AppForegroundListener, AppForegroundEvent> {
        @Override
        AppForegroundEvent createEvent(final boolean isTrue) { return new AppForegroundEvent(); }

        @Override
        void performFalseEventOn(final AppForegroundListener listener, final AppForegroundEvent e) {
            listener.appMovedToBackground(e);
        }

        @Override
        void performTrueEventOn(final AppForegroundListener listener, final AppForegroundEvent e) {
            listener.appRaisedToForeground(e);
        }
    }

    static final class _HiddenAppDispatcher extends _BooleanAppEventMultiplexor<AppHiddenListener, AppHiddenEvent> {
        @Override
        AppHiddenEvent createEvent(final boolean isTrue) { return new AppHiddenEvent(); }

        @Override
        void performFalseEventOn(final AppHiddenListener listener, final AppHiddenEvent e) {
            listener.appUnhidden(e);
        }

        @Override
        void performTrueEventOn(final AppHiddenListener listener, final AppHiddenEvent e) {
            listener.appHidden(e);
        }
    }

    static final class _UserSessionDispatcher extends _BooleanAppEventMultiplexor<UserSessionListener, UserSessionEvent> {
        @Override
        UserSessionEvent createEvent(final boolean isTrue) {
            return new UserSessionEvent(Reason.UNSPECIFIED);
        }

        @Override
        void performFalseEventOn(final UserSessionListener listener, final UserSessionEvent e) {
            listener.userSessionDeactivated(e);
        }

        @Override
        void performTrueEventOn(final UserSessionListener listener, final UserSessionEvent e) {
            listener.userSessionActivated(e);
        }

        @Override
        void registerNativeListener() {
            nativeRegisterForNotification(REGISTER_USER_SESSION);
        }
    }

    static final class _ScreenSleepDispatcher extends _BooleanAppEventMultiplexor<ScreenSleepListener, ScreenSleepEvent> {
        @Override
        ScreenSleepEvent createEvent(final boolean isTrue) { return new ScreenSleepEvent(); }

        @Override
        void performFalseEventOn(final ScreenSleepListener listener, final ScreenSleepEvent e) {
            listener.screenAwoke(e);
        }

        @Override
        void performTrueEventOn(final ScreenSleepListener listener, final ScreenSleepEvent e) {
            listener.screenAboutToSleep(e);
        }

        @Override
        void registerNativeListener() {
            nativeRegisterForNotification(REGISTER_SCREEN_SLEEP);
        }
    }

    static final class _SystemSleepDispatcher extends _BooleanAppEventMultiplexor<SystemSleepListener, SystemSleepEvent> {
        @Override
        SystemSleepEvent createEvent(final boolean isTrue) { return new SystemSleepEvent(); }

        @Override
        void performFalseEventOn(final SystemSleepListener listener, final SystemSleepEvent e) {
            listener.systemAwoke(e);
        }

        @Override
        void performTrueEventOn(final SystemSleepListener listener, final SystemSleepEvent e) {
            listener.systemAboutToSleep(e);
        }

        @Override
        void registerNativeListener() {
            nativeRegisterForNotification(REGISTER_SYSTEM_SLEEP);
        }
    }

    static final class _OpenFileDispatcher extends _QueuingAppEventDispatcher<OpenFilesHandler> {
        @Override
        void performUsing(final OpenFilesHandler handler, final _NativeEvent event) {
            // create file list from fileNames
            final List<String> fileNameList = event.get(0);
            final ArrayList<File> files = new ArrayList<File>(fileNameList.size());
            for (final String fileName : fileNameList) files.add(new File(fileName));

            // populate the properties map
            final String searchTerm = event.get(1);
            handler.openFiles(new OpenFilesEvent(files, searchTerm));
        }
    }

    static final class _PrintFileDispatcher extends _QueuingAppEventDispatcher<PrintFilesHandler> {
        @Override
        void performUsing(final PrintFilesHandler handler, final _NativeEvent event) {
            // create file list from fileNames
            final List<String> fileNameList = event.get(0);
            final ArrayList<File> files = new ArrayList<File>(fileNameList.size());
            for (final String fileName : fileNameList) files.add(new File(fileName));

            handler.printFiles(new PrintFilesEvent(files));
        }
    }

    // Java URLs can't handle unknown protocol types, which is why we use URIs
    static final class _OpenURIDispatcher extends _QueuingAppEventDispatcher<OpenURIHandler> {
        @Override
        void performUsing(final OpenURIHandler handler, final _NativeEvent event) {
            final String urlString = event.get(0);
            try {
                handler.openURI(new OpenURIEvent(new URI(urlString)));
            } catch (final URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class _QuitDispatcher extends _AppEventDispatcher<QuitHandler> {
        @Override
        void performDefaultAction(final _NativeEvent event) {
            obtainQuitResponse().performQuit();
        }

        @Override
        void performUsing(final QuitHandler handler, final _NativeEvent event) {
            if (_AppMiscHandlers.isSuddenTerminationEnbaled()) {
                performDefaultAction(event);
                return;
            }
            final MacQuitResponse response = obtainQuitResponse(); // obtains the "current" quit response
            handler.handleQuitRequestWith(new QuitEvent(), response);
        }
    }


// -- ABSTRACT QUEUE/EVENT/LISTENER HELPERS --

    // generic little "raw event" that's constructed easily from the native callbacks
    static final class _NativeEvent {
        Object[] args;

        public _NativeEvent(final Object... args) {
            this.args = args;
        }

        @SuppressWarnings("unchecked")
        <T> T get(final int i) {
            if (args == null) return null;
            return (T)args[i];
        }
    }

    abstract static class _AppEventMultiplexor<L> {
        private final Set<L> listeners = new HashSet<L>();
        boolean nativeListenerRegistered;

        // called from AppKit Thread-0
        void dispatch(final _NativeEvent event, final Object... args) {
            // grab a local ref to the listeners as an array
            final ArrayList<L> localList;
            synchronized (this) {
                if (listeners.size() == 0) {
                    return;
                }
                localList = new ArrayList<L>(listeners.size());
                localList.addAll(listeners);
            }
            for (final L listener : localList) {
                SunToolkit.invokeLater(new Runnable() {
                    public void run() {
                        performOnListener(listener, event);
                    }
                });
            }
        }

        synchronized void addListener(final L listener) {
            listeners.add(listener);

            if (!nativeListenerRegistered) {
                registerNativeListener();
                nativeListenerRegistered = true;
            }
        }

        synchronized void removeListener(final L listener) {
            listeners.remove(listener);
        }

        abstract void performOnListener(L listener, final _NativeEvent event);
        void registerNativeListener() { }
    }

    abstract static class _BooleanAppEventMultiplexor<L, E> extends _AppEventMultiplexor<L> {
        @Override
        void performOnListener(L listener, final _NativeEvent event) {
            final boolean isTrue = Boolean.TRUE.equals(event.get(0));
            final E e = createEvent(isTrue);
            if (isTrue) {
                performTrueEventOn(listener, e);
            } else {
                performFalseEventOn(listener, e);
            }
        }

        abstract E createEvent(final boolean isTrue);
        abstract void performTrueEventOn(final L listener, final E e);
        abstract void performFalseEventOn(final L listener, final E e);
    }

    /*
     * Ensures that setting and obtaining an app event handler is done in
     * both a thread-safe manner, and that user code is performed on the
     * AWT EventQueue thread.
     *
     * Allows native to blindly lob new events into the dispatcher,
     * knowing that they will only be dispatched once a handler is set.
     *
     * User code is not (and should not be) run under any synchronized lock.
     */
    abstract static class _AppEventDispatcher<H> {
        H _handler;

        // called from AppKit Thread-0
        void dispatch(final _NativeEvent event) {
            // grab a local ref to the handler
            final H localHandler;
            synchronized (_AppEventDispatcher.this) {
                localHandler = _handler;
            }

            if (localHandler == null) {
                performDefaultAction(event);
            } else {
                SunToolkit.invokeLater(new Runnable() {
                    public void run() {
                        performUsing(localHandler, event);
                    }
                });
            }
        }

        synchronized void setHandler(final H handler) {
            this._handler = handler;
        }

        void performDefaultAction(final _NativeEvent event) { } // by default, do nothing
        abstract void performUsing(final H handler, final _NativeEvent event);
    }

    abstract static class _QueuingAppEventDispatcher<H> extends _AppEventDispatcher<H> {
        List<_NativeEvent> queuedEvents = new LinkedList<_NativeEvent>();

        @Override
        void dispatch(final _NativeEvent event) {
            synchronized (this) {
                // dispatcher hasn't started yet
                if (queuedEvents != null) {
                    queuedEvents.add(event);
                    return;
                }
            }

            super.dispatch(event);
        }

        @Override
        synchronized void setHandler(final H handler) {
            this._handler = handler;

            // dispatch any events in the queue
            if (queuedEvents != null) {
                // grab a local ref to the queue, so the real one can be nulled out
                final java.util.List<_NativeEvent> localQueuedEvents = queuedEvents;
                queuedEvents = null;
                if (localQueuedEvents.size() != 0) {
                    for (final _NativeEvent arg : localQueuedEvents) {
                        dispatch(arg);
                    }
                }
            }
        }
    }
}
