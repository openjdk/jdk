/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 6286011 6330315
 * @summary Verify that appropriate SelectorProvider is selected.
 */

import java.nio.channels.spi.*;

public class SelProvider {
    public static void main(String[] args) throws Exception {
        String osname = System.getProperty("os.name");
        String osver = System.getProperty("os.version");
        String spName = SelectorProvider.provider().getClass().getName();
        String expected = null;
        if ("SunOS".equals(osname)) {
            expected = "sun.nio.ch.DevPollSelectorProvider";
        } else if ("Linux".equals(osname)) {
            String[] vers = osver.split("\\.", 0);
            if (vers.length >= 2) {
                int major = Integer.parseInt(vers[0]);
                int minor = Integer.parseInt(vers[1]);
                if (major > 2 || (major == 2 && minor >= 6)) {
                    expected = "sun.nio.ch.EPollSelectorProvider";
                } else {
                    expected = "sun.nio.ch.PollSelectorProvider";
                }
            } else {
                throw new RuntimeException("Test does not recognize this operating system");
            }
        } else if (osname.startsWith("Mac OS")) {
            expected = "sun.nio.ch.PollSelectorProvider";
        } else
            return;
        if (!spName.equals(expected))
            throw new Exception("failed");
    }
}
