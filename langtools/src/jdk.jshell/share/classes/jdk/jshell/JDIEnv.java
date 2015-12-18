/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.Map;

import com.sun.jdi.*;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;

/**
 * Representation of a Java Debug Interface environment
 * Select methods extracted from jdb Env; shutdown() adapted to JShell shutdown.
 */
class JDIEnv {

    private JDIConnection connection;
    private final JShell state;

    JDIEnv(JShell state) {
        this.state = state;
    }

    void init(String connectorName, Map<String, String> argumentName2Value, boolean openNow, int flags) {
        connection = new JDIConnection(this, connectorName, argumentName2Value, flags, state);
        if (!connection.isLaunch() || openNow) {
            connection.open();
        }
    }

    JDIConnection connection() {
        return connection;
    }

    VirtualMachine vm() {
        return connection.vm();
    }

    void shutdown() {
        if (connection != null) {
            try {
                connection.disposeVM();
            } catch (VMDisconnectedException e) {
                // Shutting down after the VM has gone away. This is
                // not an error, and we just ignore it.
            } catch (Throwable e) {
                state.debug(DBG_GEN, null, "disposeVM threw: " + e);
            }
        }
        if (state != null) { // If state has been set-up
            try {
                state.closeDown();
            } catch (Throwable e) {
                state.debug(DBG_GEN, null, "state().closeDown() threw: " + e);
            }
        }
    }
}
