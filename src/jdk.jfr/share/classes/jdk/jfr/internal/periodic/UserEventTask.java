/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.periodic;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

import jdk.internal.event.Event;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Class to be used with user-defined events that runs untrusted code.
 * <p>
 * This class can be removed once the Security Manager is no longer supported.
 */
final class UserEventTask extends JavaEventTask {
    @SuppressWarnings("removal")
    private final AccessControlContext controlContext;

    public UserEventTask(@SuppressWarnings("removal") AccessControlContext controlContext, Class<? extends Event> eventClass, Runnable runnable) {
        super(eventClass, runnable);
        this.controlContext = Objects.requireNonNull(controlContext);
    }

    @SuppressWarnings("removal")
    @Override
    public void execute(long timestamp, PeriodicType periodicType) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            execute();
            return null;
        }, controlContext);
    }

    private void execute() {
        try {
            getRunnable().run();
            if (Logger.shouldLog(LogTag.JFR_EVENT, LogLevel.DEBUG)) {
                Logger.log(LogTag.JFR_EVENT, LogLevel.DEBUG, "Executed periodic task for " + getEventType().getLogName());
            }
        } catch (Throwable t) {
            // Prevent malicious user to propagate exception callback in the wrong context
            Logger.log(LogTag.JFR_EVENT, LogLevel.WARN, "Exception occurred during execution of period task for " + getEventType().getLogName());
        }
    }
}
