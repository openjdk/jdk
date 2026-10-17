/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.attach;

import com.sun.tools.attach.AttachOperationFailedException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.spi.AttachProvider;
import jdk.internal.misc.VM;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;

/*
 * The HotSpot implementation of com.sun.tools.attach.VirtualMachine.
 */
public abstract class HotSpotVirtualMachine extends VirtualMachine {

    HotSpotVirtualMachine(AttachProvider provider, String id)
        throws AttachNotSupportedException, IOException
    {
        super(provider, id);
    }

    // --- HotSpot specific methods ---

    // same as SIGQUIT
    public void localDataDump() throws IOException {
        executeCommand("datadump").close();
    }

    // Remote ctrl-break. The output of the ctrl-break actions can
    // be read from the input stream.
    public InputStream remoteDataDump(Object ... args) throws IOException {
        return executeCommand("threaddump", args);
    }

    // Remote heap dump. The output (error message) can be read from the
    // returned input stream.
    public InputStream dumpHeap(Object ... args) throws IOException {
        return executeCommand("dumpheap", args);
    }

    // Heap histogram (heap inspection in HotSpot)
    public InputStream heapHisto(Object ... args) throws IOException {
        return executeCommand("inspectheap", args);
    }

    // set JVM command line flag
    public InputStream setFlag(String name, String value) throws IOException {
        return executeCommand("setflag", name, value);
    }

    // print command line flag
    public InputStream printFlag(String name) throws IOException {
        return executeCommand("printflag", name);
    }

    public InputStream executeJCmd(String command) throws IOException {
        return executeCommand("jcmd", command);
    }

    /*
     * Execute the given command in the target VM - specific platform
     * implementation must implement this.
     */
    abstract InputStream execute(String cmd, Object ... args)
        throws AgentLoadException, IOException;

    /*
     * Convenience method for simple commands
     */
    public InputStream executeCommand(String cmd, Object ... args) throws IOException {
        try {
            return execute(cmd, args);
        } catch (AgentLoadException x) {
            throw new InternalError("Should not get here", x);
        }
    }
}
