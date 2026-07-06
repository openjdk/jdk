/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8382537
 * @summary
 *     Tests to make sure invalid commands and command sets return NOT_IMPLEMENTED.
 *     Test launches debuggee VM using support classes and sends bad command sets
 *     and commands to it. Then test receives replies for the bad command sets and
 *     command and expects them to contain the NOT_IMPLEMENTED error code.
 *
 * @library /vmTestbase /test/hotspot/jtreg/vmTestbase
 *          /test/lib
 * @build nsk.jdwp.Invalid.invalid001a
 * @run driver
 *      nsk.jdwp.Invalid.invalid001
 *      -arch=${os.family}-${os.simpleArch}
 *      -verbose
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdwp.Invalid;

import java.io.*;
import java.util.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

public class invalid001 {
    static final int JCK_STATUS_BASE = 95;
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final String PACKAGE_NAME = "nsk.jdwp.Invalid";
    static final String TEST_CLASS_NAME = PACKAGE_NAME + "." + "invalid001";
    static final String DEBUGEE_CLASS_NAME = TEST_CLASS_NAME + "a";

    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("Test failed");
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new invalid001().runIt(argv, out);
    }

    Transport transport;
    Log log;
    boolean success = true;

    public int runIt(String argv[], PrintStream out) {

        try {
            ArgumentHandler argumentHandler = new ArgumentHandler(argv);
            log = new Log(out, argumentHandler);

            try {

                Binder binder = new Binder(argumentHandler, log);
                log.display("Start debugee VM");
                Debugee debugee = binder.bindToDebugee(DEBUGEE_CLASS_NAME);
                transport = debugee.getTransport();
                IOPipe pipe = debugee.createIOPipe();

                log.display("Waiting for VM_INIT event");
                debugee.waitForVMInit();

                log.display("Querying for IDSizes");
                debugee.queryForIDSizes();

                log.display("Resume debugee VM");
                debugee.resume();

                log.display("Waiting for command: " + "ready");
                String cmd = pipe.readln();
                log.display("Received command: " + cmd);

                // begin test of invalid JDWP commands

                try {
                    // First byte is the command set and 2nd byte is the command
                    verifyCmd(0x0001, "0 cmd set");
                    verifyCmd(0xff01, "-1 cmd set");
                    verifyCmd(0x7f01, "127 cmd set");
                    verifyCmd(0x0100, "0 command");
                    verifyCmd(0x01ff, "-1 command");
                    verifyCmd(0x017f, "127 command");
                } catch (Exception e) {
                    log.complain("Exception caught: " + e);
                    success = false;
                }

                // end test of invalid JDWP commands

                log.display("Sending command: " + "quit");
                pipe.println("quit");

                log.display("Waiting for debugee exits");
                int code = debugee.waitFor();
                if (code == JCK_STATUS_BASE + PASSED) {
                    log.display("Debugee PASSED: " + code);
                } else {
                    log.complain("Debugee FAILED: " + code);
                    success = false;
                }

            } catch (Exception e) {
                log.complain("Unexpected exception: " + e);
                e.printStackTrace(out);
                success = false;
            }

            if (!success) {
                log.complain("TEST FAILED");
                return FAILED;
            }

        } catch (Exception e) {
            out.println("Unexpected exception: " + e);
            e.printStackTrace(out);
            out.println("TEST FAILED");
            return FAILED;
        }

        out.println("TEST PASSED");
        return PASSED;

    }

    public void verifyCmd(int cmd, String cmdDescription) throws Exception {
        CommandPacket command = new CommandPacket(cmd);

        log.display("Sending command packet with " + cmdDescription + ":\n" + command);
        transport.write(command);

        log.display("Waiting for reply packet");
        ReplyPacket reply = new ReplyPacket();
        transport.read(reply);
        log.display("Reply packet received:\n" + reply);

        log.display("Checking reply packet error code");
        int errorCode = reply.getErrorCode();
        if (errorCode == JDWP.Error.NOT_IMPLEMENTED) {
            log.display("Got expected NOT_IMPLEMENTED error code");
        } else {
            log.complain("Got unexpected error code: " + errorCode);
            success = false;
        }
    }
}
