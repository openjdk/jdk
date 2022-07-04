/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;


/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/NativeMethodBind/nativemethbind002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This test exercises the JVMTI event NativeMethodBind.
 *     It verifies that the events will be sent only during the start
 *     and live phase of VM execution.
 *     The test works as follows. The NativeMethodBind event is enabled
 *     on 'OnLoad' phase. Then the VM phase is checked from the
 *     NativeMethodBind callback to be start or live one. The java part
 *     calls the dummy native method 'nativeMethod' on exit in order to
 *     provoke the NativeMethodBind event near the dead phase.
 * COMMENTS
 *     Fixed the 4995867 bug.
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:nativemethbind02 nativemethbind02
 */

public class nativemethbind02 {
    static {
        System.loadLibrary("nativemethbind02");
    }

    native int nativeMethod();

    public static void main(String[] argv) {
        new nativemethbind02().runThis();
    }

    private void runThis() {

        System.out.println("\nCalling a native method ...\n");

        // dummy methods used to provoke the NativeMethodBind event
        // near the dead phase
        int result = nativeMethod();
        if (result != 0) {
            throw new RuntimeException("runThis() returned " + result);
        }
    }
}
