/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4414825
 * @summary SocketPermission.implies seems wrong for unresolveable hosts
 */

import java.net.*;

public class B4414825 {
    public static void main(String[] args) throws Exception {
        SocketPermission p = new SocketPermission("invlidhost", "connect");
        if (!p.implies(p))
            throw new RuntimeException("Test failed: SocketPermission instance should imply itself.");

        SocketPermission p1 = new SocketPermission("invlidhost", "connect");
        if (!p.implies(p1))
            throw new RuntimeException("Test failed: Equaled SocketPermission instances should imply each other.");
    }
}
