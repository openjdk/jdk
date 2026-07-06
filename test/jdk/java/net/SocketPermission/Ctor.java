/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4391898 8230407
 * @summary SocketPermission(":",...) throws ArrayIndexOutOfBoundsException
 *          SocketPermission constructor argument checks
 * @run junit ${test.main.class}
 */

import java.net.SocketPermission;
import static java.lang.System.err;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Ctor {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    @Test
    public void positive() {
        // ArrayIndexOutOfBoundsException is the bug, 4391898, exists
        SocketPermission sp1 =  new SocketPermission(":", "connect");
    }

    @Test
    public void npe() {
        NullPointerException e;
        e = assertThrows(NPE, () -> new SocketPermission(null, null));
        err.println("caught expected NPE: " + e);
        e = assertThrows(NPE, () -> new SocketPermission("foo", null));
        err.println("caught expected NPE: " + e);
        e = assertThrows(NPE, () -> new SocketPermission(null, "connect"));
        err.println("caught expected NPE: " + e);
    }

    @Test
    public void iae() {
        IllegalArgumentException e;
        // host
        e = assertThrows(IAE, () -> new SocketPermission("1:2:3:4", "connect"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo:5-4", "connect"));
        err.println("caught expected IAE: " + e);

        // actions
        e = assertThrows(IAE, () -> new SocketPermission("foo", ""));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", "badAction"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", "badAction,connect"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", "badAction,,connect"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", ",connect"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", ",,connect"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", "connect,"));
        err.println("caught expected IAE: " + e);
        e = assertThrows(IAE, () -> new SocketPermission("foo", "connect,,"));
        err.println("caught expected IAE: " + e);
    }
}
