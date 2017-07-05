/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;

/*
 * @test
 * @bug 8134434
 * @summary JVM_DoPrivileged() fires assert(_exception_caught == false) failed: _exception_caught is out of phase
 * @run main/othervm -agentlib:jdwp=transport=dt_socket,address=9000,server=y,suspend=n -Xbatch ExceptionCaughtOutOfPhaseTest
 */

public class ExceptionCaughtOutOfPhaseTest {
    public static void main(String[] args) {
        PrivilegedAction action = new HotThrowingAction();
        System.out.println("### Warm-up");
        for(int i=0; i<11000; i++) {
            try {
                action.run(); // call run() to get it compiled
            } catch(Throwable t) {
                // ignored
            }
        }

        System.out.println("### Warm-up done");
        System.out.println("### Executing privileged action");

        try {
            AccessController.doPrivileged(action);
        } catch (Error e) {
            // ignored
        }
    }

    public static class HotThrowingAction implements PrivilegedAction {
        public Object run() {
            throw new Error();
        }
    }
}
