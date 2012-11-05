/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;

/*
 * The HotSpot implementation of com.sun.tools.attach.VirtualMachine.
 */

public abstract class HotSpotVirtualMachine extends VirtualMachine {

    HotSpotVirtualMachine(AttachProvider provider, String id) {
        super(provider, id);
    }

    /*
     * Load agent library
     * If isAbsolute is true then the agent library is the absolute path
     * to the library and thus will not be expanded in the target VM.
     * if isAbsolute is false then the agent library is just a library
     * name and it will be expended in the target VM.
     */
    private void loadAgentLibrary(String agentLibrary, boolean isAbsolute, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        InputStream in = execute("load",
                                 agentLibrary,
                                 isAbsolute ? "true" : "false",
                                 options);
        try {
            int result = readInt(in);
            if (result != 0) {
                throw new AgentInitializationException("Agent_OnAttach failed", result);
            }
        } finally {
            in.close();

        }
    }

    /*
     * Load agent library - library name will be expanded in target VM
     */
    public void loadAgentLibrary(String agentLibrary, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentLibrary(agentLibrary, false, options);
    }

    /*
     * Load agent - absolute path of library provided to target VM
     */
    public void loadAgentPath(String agentLibrary, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        loadAgentLibrary(agentLibrary, true, options);
    }

    /*
     * Load JPLIS agent which will load the agent JAR file and invoke
     * the agentmain method.
     */
    public void loadAgent(String agent, String options)
        throws AgentLoadException, AgentInitializationException, IOException
    {
        String args = agent;
        if (options != null) {
            args = args + "=" + options;
        }
        try {
            loadAgentLibrary("instrument", args);
        } catch (AgentLoadException x) {
            throw new InternalError("instrument library is missing in target VM", x);
        } catch (AgentInitializationException x) {
            /*
             * Translate interesting errors into the right exception and
             * message (FIXME: create a better interface to the instrument
             * implementation so this isn't necessary)
             */
            int rc = x.returnValue();
            switch (rc) {
                case JNI_ENOMEM:
                    throw new AgentLoadException("Insuffient memory");
                case ATTACH_ERROR_BADJAR:
                    throw new AgentLoadException("Agent JAR not found or no Agent-Class attribute");
                case ATTACH_ERROR_NOTONCP:
                    throw new AgentLoadException("Unable to add JAR file to system class path");
                case ATTACH_ERROR_STARTFAIL:
                    throw new AgentInitializationException("Agent JAR loaded but agent failed to initialize");
                default :
                    throw new AgentLoadException("Failed to load agent - unknown reason: " + rc);
            }
        }
    }

    /*
     * The possible errors returned by JPLIS's agentmain
     */
    private static final int JNI_ENOMEM                 = -4;
    private static final int ATTACH_ERROR_BADJAR        = 100;
    private static final int ATTACH_ERROR_NOTONCP       = 101;
    private static final int ATTACH_ERROR_STARTFAIL     = 102;


    /*
     * Send "properties" command to target VM
     */
    public Properties getSystemProperties() throws IOException {
        InputStream in = null;
        Properties props = new Properties();
        try {
            in = executeCommand("properties");
            props.load(in);
        } finally {
            if (in != null) in.close();
        }
        return props;
    }

    public Properties getAgentProperties() throws IOException {
        InputStream in = null;
        Properties props = new Properties();
        try {
            in = executeCommand("agentProperties");
            props.load(in);
        } finally {
            if (in != null) in.close();
        }
        return props;
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

    // -- Supporting methods


    /*
     * Execute the given command in the target VM - specific platform
     * implementation must implement this.
     */
    abstract InputStream execute(String cmd, Object ... args)
        throws AgentLoadException, IOException;

    /*
     * Convenience method for simple commands
     */
    private InputStream executeCommand(String cmd, Object ... args) throws IOException {
        try {
            return execute(cmd, args);
        } catch (AgentLoadException x) {
            throw new InternalError("Should not get here", x);
        }
    }


    /*
     * Utility method to read an 'int' from the input stream. Ideally
     * we should be using java.util.Scanner here but this implementation
     * guarantees not to read ahead.
     */
    int readInt(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();

        // read to \n or EOF
        int n;
        byte buf[] = new byte[1];
        do {
            n = in.read(buf, 0, 1);
            if (n > 0) {
                char c = (char)buf[0];
                if (c == '\n') {
                    break;                  // EOL found
                } else {
                    sb.append(c);
                }
            }
        } while (n > 0);

        if (sb.length() == 0) {
            throw new IOException("Premature EOF");
        }

        int value;
        try {
            value = Integer.parseInt(sb.toString());
        } catch (NumberFormatException x) {
            throw new IOException("Non-numeric value found - int expected");
        }
        return value;
    }

    // -- attach timeout support

    private static long defaultAttachTimeout = 5000;
    private volatile long attachTimeout;

    /*
     * Return attach timeout based on the value of the sun.tools.attach.attachTimeout
     * property, or the default timeout if the property is not set to a positive
     * value.
     */
    long attachTimeout() {
        if (attachTimeout == 0) {
            synchronized(this) {
                if (attachTimeout == 0) {
                    try {
                        String s =
                            System.getProperty("sun.tools.attach.attachTimeout");
                        attachTimeout = Long.parseLong(s);
                    } catch (SecurityException se) {
                    } catch (NumberFormatException ne) {
                    }
                    if (attachTimeout <= 0) {
                       attachTimeout = defaultAttachTimeout;
                    }
                }
            }
        }
        return attachTimeout;
    }
}
