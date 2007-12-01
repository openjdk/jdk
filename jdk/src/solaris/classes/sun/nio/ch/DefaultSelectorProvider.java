/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import sun.security.action.GetPropertyAction;

/**
 * Creates this platform's default SelectorProvider
 */

public class DefaultSelectorProvider {

    /**
     * Prevent instantiation.
     */
    private DefaultSelectorProvider() { }

    /**
     * Returns the default SelectorProvider.
     */
    public static SelectorProvider create() {
        String osname = AccessController.doPrivileged(
            new GetPropertyAction("os.name"));
        if ("SunOS".equals(osname)) {
            return new sun.nio.ch.DevPollSelectorProvider();
        }

        // use EPollSelectorProvider for Linux kernels >= 2.6
        if ("Linux".equals(osname)) {
            String osversion = AccessController.doPrivileged(
                new GetPropertyAction("os.version"));
            String[] vers = osversion.split("\\.", 0);
            if (vers.length >= 2) {
                try {
                    int major = Integer.parseInt(vers[0]);
                    int minor = Integer.parseInt(vers[1]);
                    if (major > 2 || (major == 2 && minor >= 6)) {
                        return new sun.nio.ch.EPollSelectorProvider();
                    }
                } catch (NumberFormatException x) {
                    // format not recognized
                }
            }
        }

        return new sun.nio.ch.PollSelectorProvider();
    }

}
