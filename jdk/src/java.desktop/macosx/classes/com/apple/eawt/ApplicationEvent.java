/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EventObject;

/**
 * The class of events sent to the deprecated ApplicationListener callbacks.
 *
 * @deprecated replaced by {@link AboutHandler}, {@link PreferencesHandler}, {@link AppReOpenedListener}, {@link OpenFilesHandler}, {@link PrintFilesHandler}, {@link QuitHandler}, {@link QuitResponse}
 * @since 1.4
 */
@Deprecated
@SuppressWarnings("serial") // JDK implementation class
public class ApplicationEvent extends EventObject {
    private String fFilename = null;
    private boolean fHandled = false;

    ApplicationEvent(final Object source) {
        super(source);
    }

    ApplicationEvent(final Object source, final String filename) {
        super(source);
        fFilename = filename;
    }

    /**
     * Determines whether an ApplicationListener has acted on a particular event.
     * An event is marked as having been handled with {@code setHandled(true)}.
     *
     * @return {@code true} if the event has been handled, otherwise {@code false}
     *
     * @since 1.4
     * @deprecated
     */
    @Deprecated
    public boolean isHandled() {
        return fHandled;
    }

    /**
     * Marks the event as handled.
     * After this method handles an ApplicationEvent, it may be useful to specify that it has been handled.
     * This is usually used in conjunction with {@code getHandled()}.
     * Set to {@code true} to designate that this event has been handled. By default it is {@code false}.
     *
     * @param state {@code true} if the event has been handled, otherwise {@code false}.
     *
     * @since 1.4
     * @deprecated
     */
    @Deprecated
    public void setHandled(final boolean state) {
        fHandled = state;
    }

    /**
     * Provides the filename associated with a particular AppleEvent.
     * When the ApplicationEvent corresponds to an AppleEvent that needs to act on a particular file, the ApplicationEvent carries the name of the specific file with it.
     * For example, the Print and Open events refer to specific files.
     * For these cases, this returns the appropriate file name.
     *
     * @return the full path to the file associated with the event, if applicable, otherwise {@code null}
     *
     * @since 1.4
     * @deprecated use {@link OpenFilesHandler} or {@link PrintFilesHandler} instead
     */
    @Deprecated
    public String getFilename() {
        return fFilename;
    }
}
