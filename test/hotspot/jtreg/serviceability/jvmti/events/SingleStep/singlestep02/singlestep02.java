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

import jdk.test.lib.jvmti.DebugeeClass;
import java.io.*;

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/SingleStep/singlestep002.
 * VM Testbase keywords: [jpda, jvmti, onload_only_caps, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This test exercises the JVMTI event SingleStep.
 *     It verifies that this event s sent only during the live phase
 *     of VM execution.
 *     The test works as follows. The tested event is enabled in the
 *     'OnLoad' phase. Then all received SingleStep events is checked
 *     to be sent only during the live phase via the GetPhase() call.
 * COMMENTS
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:singlestep02 singlestep02
 */

public class singlestep02 {

    static {
        try {
            System.loadLibrary("singlestep02");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load \"singlestep02\" library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    public static void main(String[] args) {
        new singlestep02().runThis(args);
    }

    private int runThis(String argv[]) {
        return DebugeeClass.TEST_PASSED;
    }
}
