/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.Argument.value;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import java.io.*;
import javax.naming.directory.Attribute;
import java.util.*;

/**
 * Test for the control of
 *
 *      Interface:      com.sun.jdi.connect.Connector.Argument
 *      Method:         public java.lang.String value()
 *      Assertion:      "Returns the current value of the argument."
 *
 *
 *      Comments:       The test aims on the concrete Sun's JDI
 *                      reference implementations. It uses
 *                      com.sun.jdi.RawCommandLineLaunch connector and its
 *                      "command" argument.
 *                      The test sets up the new "command" argument
 *                      value and then checks that new value remains previously
 *                      after connection establishing with debugee VM and
 *                      after debugee VM finishing.
 *
 *      Warning:        Temporarily the test is prepared only for
 *                      Sparc.Solaris.dt_socket-transport of RawCommandLineLaunch
 *                      connector
 */

public class value004 {
    private static Log log;

    public static void main( String argv[] ) {
        System.exit(run(argv, System.out)+95); // JCK-compatible exit status
    }

    public static int run(String argv[],PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();

        String address = argHandler.getTransportPort();

        List lcl = vmm.launchingConnectors();
        if (lcl.size() > 0) {
            log.display("Number of all known JDI launching connectors: "
                        + lcl.size());
        } else {
            log.complain("FAILURE: no JDI launching connectors found!");
            return 2;
        }

        Iterator lci = lcl.iterator();
        for (int i = 1; lci.hasNext(); i++) {
            Connector c = (Connector) lci.next();
            if (c.name().compareTo("com.sun.jdi.RawCommandLineLaunch") != 0) {
                continue;
            }
            log.display("Connector's transport is: " + c.transport().name());
            if (c.transport().name().compareTo("dt_socket") != 0) {
                log.display("WARNING: Temporarily the test is prepared only "
                          + "for dt_socket-transport of "
                          + "RawCommandLineLaunch connector!");
                return 0;
            }
            if (System.getProperties().getProperty("os.arch")
                .compareTo("sparc") != 0 ||
                System.getProperties().getProperty("os.name")
                .compareTo("SunOS") != 0) {
                log.display("WARNING: Temporarily the test is prepared "
                          + "only for Sparc.Solaris-transport of "
                          + "RawCommandLineLaunch connector!");
                return 0;
            }
            Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> cdfltArgmnts = c.defaultArguments();
            int ksz = cdfltArgmnts.size();
            String av[] = new String[ksz + 1];
            Set ks = cdfltArgmnts.keySet();
            if (ks.isEmpty()) {
                log.complain("FAILURE: empty default arguments set is "
                           + "found for " + c.name() + " connector!");
                return 2;
            }

            log.display("Looking over " + c.name() + " connector arguments: ");

            boolean flg = false;
            Iterator argi = ks.iterator();
            String ovl = null;
            String nvl = null;
            for (int j = 1; argi.hasNext(); j++) {
                String argkey = (String) argi.next();
                Connector.Argument argval =
                    (Connector.Argument)cdfltArgmnts.get((Object) argkey);
                log.display("Connector.Argument argval = "+ argval);
                if (argkey.compareTo("command") != 0 &&
                    argkey.compareTo("address") != 0) {
                    continue;
                }
                if (argkey.compareTo("address") == 0) {
                    if (argval.isValid(address)) {
                        argval.setValue(address);
                    } else {
                        log.complain("FAILURE: Can't set up new value "
                                   + "for address-argument");
                        return 2;
                    }
                    continue;
                }
                flg = true;
                ovl = argHandler.getLaunchExecPath() + " "
                     + "-Xdebug -Xnoagent -Djava.compiler=NONE "
                     + "-Xrunjdwp:transport=dt_socket,suspend=y,"
                     + "address=" + address + " nsk.jdi.Argument.value.value004a";
                if (argval.isValid(ovl)) {
                    argval.setValue(ovl);
                } else {
                    log.complain("FAILURE: Can't set up new value for "
                               + "command-argument");
                    return 2;
                }

                nvl = argval.value();
                if (nvl.compareTo(ovl) != 0) {
                    log.complain("FAILURE: Can't set up argument value!");
                    return 2;
                }
                log.display("Changed " + argval.name() + " argument's "
                          + "value is: " + nvl);
            };

            Binder binder = new Binder(argHandler, log);
            Debugee debugee = null;

            try {
                if (flg) {
                    flg = false;
                    VirtualMachine vm =
                        ((LaunchingConnector)c).launch(cdfltArgmnts);
                    log.display("VM = (" + vm + ")");
                    debugee = binder.enwrapDebugee(vm, vm.process());

                    if (((Connector.Argument) cdfltArgmnts
                        .get((Object) "command")).value()
                        .compareTo(ovl) != 0) {
                        log.complain("FAILURE: Current 'command' argument "
                                   + "value is not coinsides with the "
                                   + "last setted up value.");
                        return 2;
                    }

                    debugee.resume();
                }
            } catch ( java.io.IOException exc) {
                log.complain("FAILURE: Unable to launch, so "
                           + "java.io.IOException is arisen.");
                log.complain(exc.getMessage());
                return 2;
            } catch ( com.sun.jdi.connect.IllegalConnectorArgumentsException
                exc) {
                log.complain("FAILURE: One of the connector arguments "
                           + "is invalid, so "
                           + "IllegalConnectorArgumentsException is arisen.");
                log.complain(exc.getMessage());
                return 2;
            } catch ( com.sun.jdi.connect.VMStartException exc) {
                log.complain("FAILURE: VM was terminated with error before "
                           + "a connection could be established, so "
                           + "VMStartException is arisen.");
                log.complain(exc.getMessage());
                log.complain(Binder.readVMStartExceptionOutput(exc, log.getOutStream()));
                return 2;
            } finally {
                if (debugee != null) {
                    try {
                        debugee.dispose();
                    } catch (VMDisconnectedException ignore) {
                    }

                    int extcd = debugee.waitFor();
                    if (extcd != 95) {
                        log.complain("FAILURE: Launching VM crushes "
                                   + "with " + extcd + " exit code.");
                        return 2;
                    }
                }
            }
        };
        log.display("Test PASSED!");
        return 0;
    }
}
