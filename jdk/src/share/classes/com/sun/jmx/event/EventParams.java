/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.event;

import com.sun.jmx.mbeanserver.GetPropertyAction;
import com.sun.jmx.remote.util.ClassLogger;
import java.security.AccessController;
import javax.management.event.EventClient;

/**
 *
 * @author sjiang
 */
public class EventParams {
    public static final String DEFAULT_LEASE_TIMEOUT =
            "com.sun.event.lease.time";


    @SuppressWarnings("cast") // cast for jdk 1.5
    public static long getLeaseTimeout() {
        long timeout = EventClient.DEFAULT_LEASE_TIMEOUT;
        try {
            final GetPropertyAction act =
                  new GetPropertyAction(DEFAULT_LEASE_TIMEOUT);
            final String s = (String)AccessController.doPrivileged(act);
            if (s != null) {
                timeout = Long.parseLong(s);
            }
        } catch (RuntimeException e) {
            logger.fine("getLeaseTimeout", "exception getting property", e);
        }

        return timeout;
    }

    /** Creates a new instance of EventParams */
    private EventParams() {
    }

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventParams");
}
