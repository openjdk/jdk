/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

/**/

public class TestSecurityManager extends SecurityManager {
    public TestSecurityManager() {
    }

    public void checkListen(int port) {
        // 4269910: ok, now rmid and the regsitry will *really* go
        // away...
        //
        // rmid and the registry need to listen on sockets so they
        // will exit when they try to do so... this is used as a sign
        // by the main test process to detect that the proper security
        // manager has been installed in the relevant VMs.
        //
        System.exit(1);
    }

    public void checkExit(int status) {
        // permit check exit for all code
    }
}
