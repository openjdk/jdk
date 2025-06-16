/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

    private static final long CURRENT_PID = pid();

    @SuppressWarnings("removal")
    private static long pid() {
        return ProcessHandle.current().pid();
    }

    private static final boolean ALLOW_ATTACH_SELF;
    private static final boolean ALLOW_STREAMING_OUTPUT;
    static {
        String s = VM.getSavedProperty("jdk.attach.allowAttachSelf");
        ALLOW_ATTACH_SELF = "".equals(s) || Boolean.parseBoolean(s);
        String s2 = VM.getSavedProperty("jdk.attach.allowStreamingOutput");
        ALLOW_STREAMING_OUTPUT = !("false".equals(s2));
    }

    private final boolean selfAttach;

    HotSpotVirtualMachine(AttachProvider provider, String id)
        throws AttachNotSupportedException, IOException
    {
        super(provider, id);

        int pid;
        try {
            pid = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new AttachNotSupportedException("Invalid process identifier: " + id);
        }

        selfAttach = pid == 0 || pid == CURRENT_PID;
        // The tool should be a different VM to the target. This check will
        // eventually be enforced by the target VM.
        if (!ALLOW_ATTACH_SELF && selfAttach) {
            throw new IOException("Can not attach to current VM");
        }
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
        if (agentLibrary == null) {
            throw new NullPointerException("agentLibrary cannot be null");
        }

        String msgPrefix = "return code: ";
        String errorMsg = "Failed to load agent library";
        try {
            InputStream in = execute("load",
                                     agentLibrary,
                                     isAbsolute ? "true" : "false",
                                     options);
            String result = readMessage(in);
            if (result.isEmpty()) {
                throw new AgentLoadException("Target VM did not respond");
            } else if (result.startsWith(msgPrefix)) {
                int retCode = Integer.parseInt(result.substring(msgPrefix.length()));
                if (retCode != 0) {
                    throw new AgentInitializationException("Agent_OnAttach failed", retCode);
                }
            } else {
                if (!result.isEmpty()) {
                    errorMsg += ": " + result;
                }
                throw new AgentLoadException(errorMsg);
            }
        } catch (AttachOperationFailedException ex) {
            // execute() throws AttachOperationFailedException if attach agent reported error.
            // Convert it to AgentLoadException.
            throw new AgentLoadException(errorMsg + ": " + ex.getMessage());
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
        if (agent == null) {
            throw new NullPointerException("agent cannot be null");
        }

        String args = agent;
        if (options != null) {
            args = args + "=" + options;
        }
        try {
            loadAgentLibrary("instrument", args);
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
                    throw new AgentLoadException(
                        "Agent JAR not found or no Agent-Class attribute");
                case ATTACH_ERROR_NOTONCP:
                    throw new AgentLoadException(
                        "Unable to add JAR file to system class path");
                case ATTACH_ERROR_STARTFAIL:
                    throw new AgentInitializationException(
                        "Agent JAR loaded but agent failed to initialize");
                default :
                    throw new AgentLoadException("" +
                        "Failed to load agent - unknown reason: " + rc);
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

    // known error
    private static final int ATTACH_ERROR_BADVERSION = 101;

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

    private static final String MANAGEMENT_PREFIX = "com.sun.management.";

    private static boolean checkedKeyName(Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Invalid option (not a String): "+key);
        }
        if (!((String)key).startsWith(MANAGEMENT_PREFIX)) {
            throw new IllegalArgumentException("Invalid option: "+key);
        }
        return true;
    }

    private static String stripKeyName(Object key) {
        return ((String)key).substring(MANAGEMENT_PREFIX.length());
    }

    @Override
    public void startManagementAgent(Properties agentProperties) throws IOException {
        if (agentProperties == null) {
            throw new NullPointerException("agentProperties cannot be null");
        }
        // Convert the arguments into arguments suitable for the Diagnostic Command:
        // "ManagementAgent.start jmxremote.port=5555 jmxremote.authenticate=false"
        String args = agentProperties.entrySet().stream()
            .filter(entry -> checkedKeyName(entry.getKey()))
            .map(entry -> stripKeyName(entry.getKey()) + "=" + escape(entry.getValue()))
            .collect(Collectors.joining(" "));
        executeJCmd("ManagementAgent.start " + args).close();
    }

    private String escape(Object arg) {
        String value = arg.toString();
        if (value.contains(" ")) {
            return "'" + value + "'";
        }
        return value;
    }

    @Override
    public String startLocalManagementAgent() throws IOException {
        executeJCmd("ManagementAgent.start_local").close();
        String prop = MANAGEMENT_PREFIX + "jmxremote.localConnectorAddress";
        return getAgentProperties().getProperty(prop);
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
    public InputStream executeCommand(String cmd, Object ... args) throws IOException {
        try {
            return execute(cmd, args);
        } catch (AgentLoadException x) {
            throw new InternalError("Should not get here", x);
        }
    }

    // Attach API version support
    protected static final int VERSION_1 = 1;
    protected static final int VERSION_2 = 2;

    // Attach operation properties.
    protected static class OperationProperties {
        public final static String STREAMING = "streaming";

        private int ver;
        private Map<String, String> options = new HashMap<>();

        OperationProperties(int ver) {
            this.ver = ver;
        }

        int version() {
            return ver;
        }

        void setOption(String name, String value) {
            options.put(name, value);
        }

        String options() {
            return options.entrySet().stream()
                          .map(e -> e.getKey() + "=" + e.getValue())
                          .collect(Collectors.joining(","));
        }
    }

    /*
     * Detects Attach API properties supported by target VM.
     */
    protected OperationProperties getDefaultProps() throws IOException {
        try {
            InputStream reply = execute("getversion", "options");
            String message = readMessage(reply);
            reply.close();

            // Reply is "<ver> option1,option2...".
            int delimPos = message.indexOf(' ');
            String ver = delimPos < 0 ? message : message.substring(0, delimPos);

            int supportedVersion = Integer.parseUnsignedInt(ver);

            // VERSION_2 supports options.
            if (supportedVersion == VERSION_2) {
                OperationProperties result = new OperationProperties(supportedVersion);
                // Parse known options, ignore unknown.
                String options = delimPos < 0 ? "" : message.substring(delimPos + 1);
                String[] parts = options.split(",");
                for (String s: parts) {
                    if (OperationProperties.STREAMING.equals(s)) {
                        result.setOption(OperationProperties.STREAMING,
                                         (isStreamingEnabled() ? "1" : "0"));
                    }
                }
                return result;
            }
        } catch (AttachOperationFailedException | AgentLoadException ex) {
            // the command is not supported, the VM supports VERSION_1 only
        } catch (NumberFormatException nfe) {
            // bad version number - fallback to VERSION_1
        }
        return new OperationProperties(VERSION_1);
    }

    /*
     * For testing purposes Attach API v2 may be disabled.
     */
    protected boolean isAPIv2Enabled() {
        // if "jdk.attach.compat" property is set, only v1 is enabled.
        return !Boolean.getBoolean("jdk.attach.compat");
    }

    /*
     * Streaming output.
     */
    protected boolean isStreamingEnabled() {
        // Disable streaming for self-attach.
        if (selfAttach) {
            return false;
        }
        return ALLOW_STREAMING_OUTPUT;
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

    /*
     * Utility method to read data into a String.
     */
    String readMessage(InputStream in) throws IOException {
        String s;
        StringBuilder message = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        while ((s = br.readLine()) != null) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append(s);
        }
        return message.toString();
    }

    /*
     * Utility method to process the completion status after command execution.
     * If we get IOE during previous command execution, delay throwing it until
     * completion status has been read.
     */
    void processCompletionStatus(IOException ioe, String cmd, InputStream sis) throws AgentLoadException, IOException {
        // Read the command completion status
        int completionStatus;
        try {
            completionStatus = readInt(sis);
        } catch (IOException x) {
            sis.close();
            if (ioe != null) {
                throw ioe;
            } else {
                throw x;
            }
        }
        if (completionStatus != 0) {
            // read from the stream and use that as the error message
            String message = readMessage(sis);
            sis.close();

            // In the event of a protocol mismatch then the target VM
            // returns a known error so that we can throw a reasonable
            // error.
            if (completionStatus == ATTACH_ERROR_BADVERSION) {
                throw new IOException("Protocol mismatch with target VM");
            }

            if (message.isEmpty()) {
                message = "Command failed in target VM";
            }
            throw new AttachOperationFailedException(message);
        }
    }

    /*
     * Helper writer interface to send commands to the target VM.
     */
    public static interface AttachOutputStream {
        abstract void write(byte[] buffer, int offset, int length) throws IOException;
    }

    private int dataSize(Object obj) {
        return (obj == null ? 0 : obj.toString().getBytes(StandardCharsets.UTF_8).length) + 1;
    }

    /*
     * Writes object (usually String or Integer) to the attach writer.
     */
    private void writeString(AttachOutputStream writer, Object obj) throws IOException {
        if (obj != null) {
            String s = obj.toString();
            if (s.length() > 0) {
                byte[] b = s.getBytes(StandardCharsets.UTF_8);
                writer.write(b, 0, b.length);
            }
        }
        byte b[] = new byte[1];
        b[0] = 0;
        writer.write(b, 0, 1);
    }

    protected void writeCommand(AttachOutputStream writer, OperationProperties props,
                                String cmd, Object ... args) throws IOException {
        writeString(writer, props.version());
        if (props.version() == VERSION_2) {
            // add options to the command name (if specified)
            String options = props.options();
            if (!options.isEmpty()) {
                cmd += " " + options;
            }
            // for v2 write size of the data
            int size = dataSize(cmd);
            for (Object arg: args) {
                size += dataSize(arg);
            }
            writeString(writer, size);
        }
        writeString(writer, cmd);
        // v1 commands always write 3 arguments
        int argNumber = props.version() == VERSION_1 ? 3 : args.length;
        for (int i = 0; i < argNumber; i++) {
            writeString(writer, i < args.length ? args[i] : null);
        }
    }

    /*
     * InputStream for the socket connection to get target VM
     */
    abstract static class SocketInputStream extends InputStream {
        private long fd;

        public SocketInputStream(long fd) {
            this.fd = fd;
        }

        protected abstract int read(long fd, byte[] bs, int off, int len) throws IOException;
        protected abstract void close(long fd) throws IOException;

        public synchronized int read() throws IOException {
            byte b[] = new byte[1];
            int n = this.read(b, 0, 1);
            if (n == 1) {
                return b[0] & 0xff;
            } else {
                return -1;
            }
        }

        public synchronized int read(byte[] bs, int off, int len) throws IOException {
            if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            return read(fd, bs, off, len);
        }

        public synchronized void close() throws IOException {
            if (fd != -1) {
                long toClose = fd;
                fd = -1;
                close(toClose);
            }
        }
    }

    // -- attach timeout support

    private static long defaultAttachTimeout = 10000;
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

    protected static void checkNulls(Object... args) {
        for (Object arg : args) {
            if (arg instanceof String s) {
                if (s.indexOf(0) >= 0) {
                    throw new IllegalArgumentException("illegal null character in command");
                }
            }
        }
    }
}
