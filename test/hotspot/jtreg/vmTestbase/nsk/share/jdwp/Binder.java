/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.jdwp;

import nsk.share.*;
import nsk.share.jpda.*;

import java.io.*;

/**
 * This class provides debugger with connection to debugee VM
 * using JDWP protocol.
 * <p>
 * This class provides abilities to launch and bind to debugee VM
 * as described for base <code>DebugeeBinder</code> class,
 * using raw JDWP protocol.
 * <p>
 * When <code>Binder</code> is asked to bind to debugee by invoking
 * <code>bindToBebugee()</code> method it launches process
 * with debugee VM and makes connection to it using JDWP transport
 * corresponding to value of command line options <code>-connector</code>
 * and <code>-transport</code>.
 * After debugee is launched and connection is established
 * <code>Binder</code> constructs <code>Debugee</code> object,
 * that provides abilities to interact with debugee VM.
 *
 * @see Debugee
 * @see DebugeeBinder
 */
final public class Binder extends DebugeeBinder {

    /**
     * Default message prefix for <code>Binder</code> object.
     */
    public static final String LOG_PREFIX = "binder> ";

    /**
     * Get version string.
     */
    public static String getVersion () {
        return "@(#)Binder.java %I% %E%";
    }

    // -------------------------------------------------- //

    /**
     * Handler of command line arguments.
     */
    private ArgumentHandler argumentHandler = null;

    /**
     * Return <code>argumentHandler</code> of this binder.
     */
    public ArgumentHandler getArgumentHandler() {
        return argumentHandler;
    }

    // -------------------------------------------------- //

    /**
     * Make new <code>Binder</code> object with specified
     * <code>argumentHandler</code> and <code>log</code>.
     */
    public Binder (ArgumentHandler argumentHandler, Log log) {
        super(argumentHandler, log);
        this.argumentHandler = argumentHandler;
    }

    // -------------------------------------------------- //

    /**
     * Start debugee VM and establish JDWP connection to it.
     */
    public Debugee bindToDebugee (String classToExecute) {

        Debugee debugee = null;

        prepareForPipeConnection(argumentHandler);

        debugee = launchDebugee(classToExecute);
        debugee.redirectOutput(log);

        Transport transport = debugee.connect();

        return debugee;
    }

    /**
     * Launch debugee VM for specified class.
     */
    public Debugee launchDebugee (String classToExecute) {

        try {
            Debugee debugee = new Debugee(this);
            String address = debugee.prepareTransport(argumentHandler);
            if (address == null)
                address = makeTransportAddress();
            String[] argsArray = makeCommandLineArgs(classToExecute, address);
            debugee.launch(argsArray);
            return debugee;
        } catch (IOException e) {
            e.printStackTrace(log.getOutStream());
            throw new Failure("Caught exception while launching debugee:\n\t" + e);
        }
    }
}