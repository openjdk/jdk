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

import java.awt.Toolkit;
import java.io.File;
import java.util.*;

import com.apple.eawt.AppEvent.*;

@SuppressWarnings("deprecation")
class _AppEventLegacyHandler implements AboutHandler, PreferencesHandler, _OpenAppHandler, AppReOpenedListener, OpenFilesHandler, PrintFilesHandler, QuitHandler {
    final _AppEventHandler parent;
    final Vector<ApplicationListener> legacyAppListeners = new Vector<ApplicationListener>();
    boolean blockLegacyAPI;
    boolean initializedParentDispatchers;

    _AppEventLegacyHandler(final _AppEventHandler parent) {
        this.parent = parent;
    }

    void blockLegacyAPI() {
        blockLegacyAPI = true;
    }

    void checkIfLegacyAPIBlocked() {
        if (!blockLegacyAPI) return;
        throw new IllegalStateException("Cannot add com.apple.eawt.ApplicationListener after installing an app event handler");
    }

    void addLegacyAppListener(final ApplicationListener listener) {
        checkIfLegacyAPIBlocked();

        if (!initializedParentDispatchers) {
            final _AppMenuBarHandler menuBarHandler = Application.getApplication().menuBarHandler;
            final boolean prefsMenuAlreadyExplicitlySet = menuBarHandler.prefsMenuItemExplicitlySet;

            parent.aboutDispatcher.setHandler(this);
            parent.preferencesDispatcher.setHandler(this);
            if (!prefsMenuAlreadyExplicitlySet) {
                menuBarHandler.setPreferencesMenuItemVisible(false); // default behavior is not to have a preferences item
            }
            parent.openAppDispatcher.setHandler(this);
            parent.reOpenAppDispatcher.addListener(this);
            parent.openFilesDispatcher.setHandler(this);
            parent.printFilesDispatcher.setHandler(this);
            parent.quitDispatcher.setHandler(this);

            initializedParentDispatchers = true;
        }

        synchronized (legacyAppListeners) {
            legacyAppListeners.addElement(listener);
        }
    }

    public void removeLegacyAppListener(final ApplicationListener listener) {
        checkIfLegacyAPIBlocked();

        synchronized (legacyAppListeners) {
            legacyAppListeners.removeElement(listener);
        }
    }

    @Override
    public void handleAbout(final AboutEvent e) {
        final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit());
        sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
            public void dispatchEvent(final ApplicationListener listener) {
                listener.handleAbout(ae);
            }
        });

        if (ae.isHandled()) return;
        parent.openCocoaAboutWindow();
    }

    @Override
    public void handlePreferences(final PreferencesEvent e) {
        final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit());
        sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
            public void dispatchEvent(final ApplicationListener listener) {
                listener.handlePreferences(ae);
            }
        });
    }

    @Override
    public void handleOpenApp() {
        final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit());
        sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
            public void dispatchEvent(final ApplicationListener listener) {
                listener.handleOpenApplication(ae);
            }
        });
    }

    @Override
    public void appReOpened(final AppReOpenedEvent e) {
        final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit());
        sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
            public void dispatchEvent(final ApplicationListener listener) {
                listener.handleReOpenApplication(ae);
            }
        });
    }

    @Override
    public void openFiles(final OpenFilesEvent e) {
        final List<File> files = e.getFiles();
        for (final File file : files) { // legacy ApplicationListeners only understood one file at a time
            final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit(), file.getAbsolutePath());
            sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
                public void dispatchEvent(final ApplicationListener listener) {
                    listener.handleOpenFile(ae);
                }
            });
        }
    }

    @Override
    public void printFiles(PrintFilesEvent e) {
        final List<File> files = e.getFiles();
        for (final File file : files) { // legacy ApplicationListeners only understood one file at a time
            final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit(), file.getAbsolutePath());
            sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
                public void dispatchEvent(final ApplicationListener listener) {
                    listener.handlePrintFile(ae);
                }
            });
        }
    }

    @Override
    public void handleQuitRequestWith(final QuitEvent e, final QuitResponse response) {
        final ApplicationEvent ae = new ApplicationEvent(Toolkit.getDefaultToolkit());
        sendEventToEachListenerUntilHandled(ae, new EventDispatcher() {
            public void dispatchEvent(final ApplicationListener listener) {
                listener.handleQuit(ae);
            }
        });

        if (ae.isHandled()) {
            parent.performQuit();
        } else {
            parent.cancelQuit();
        }
    }

    interface EventDispatcher {
        void dispatchEvent(final ApplicationListener listener);
    }

    // helper that cycles through the loop and aborts if the event is handled, or there are no listeners
    void sendEventToEachListenerUntilHandled(final ApplicationEvent event, final EventDispatcher dispatcher) {
        synchronized (legacyAppListeners) {
            if (legacyAppListeners.size() == 0) return;

            final Enumeration<ApplicationListener> e = legacyAppListeners.elements();
            while (e.hasMoreElements() && !event.isHandled()) {
                dispatcher.dispatchEvent(e.nextElement());
            }
        }
    }
}
