/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.jdi;

import static jdk.internal.jshell.remote.RemoteCodes.*;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.EOFException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import static java.util.stream.Collectors.toList;
import jdk.jshell.JShellException;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;
import jdk.internal.jshell.jdi.ClassTracker.ClassInfo;
import static java.util.stream.Collectors.toMap;
import jdk.internal.jshell.debug.InternalDebugControl;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;

/**
 * Controls the remote execution environment.
 * Interfaces to the JShell-core by implementing ExecutionControl SPI.
 * Interfaces to RemoteAgent over a socket and via JDI.
 * Launches a remote process.
 */
public class JDIExecutionControl implements ExecutionControl {

    ExecutionEnv execEnv;
    private final boolean isLaunch;
    private JDIConnection connection;
    private ClassTracker classTracker;
    private Socket socket;
    private ObjectInputStream remoteIn;
    private ObjectOutputStream remoteOut;

    /**
     * Creates an ExecutionControl instance based on JDI.
     *
     * @param isLaunch true for LaunchingConnector; false for ListeningConnector
     */
    public JDIExecutionControl(boolean isLaunch) {
        this.isLaunch = isLaunch;
    }

    /**
     * Creates an ExecutionControl instance based on a JDI LaunchingConnector.
     */
    public JDIExecutionControl() {
        this.isLaunch = true;
    }

    /**
     * Initializes the launching JDI execution engine. Initialize JDI and use it
     * to launch the remote JVM. Set-up control and result communications socket
     * to the remote execution environment. This socket also transports the
     * input/output channels.
     *
     * @param execEnv the execution environment provided by the JShell-core
     * @throws IOException
     */
    @Override
    public void start(ExecutionEnv execEnv) throws IOException {
        this.execEnv = execEnv;
        StringBuilder sb = new StringBuilder();
        try (ServerSocket listener = new ServerSocket(0)) {
            // timeout after 60 seconds
            listener.setSoTimeout(60000);
            int port = listener.getLocalPort();
            connection = new JDIConnection(this, port, execEnv.extraRemoteVMOptions(), isLaunch);
            this.socket = listener.accept();
            // out before in -- match remote creation so we don't hang
            this.remoteOut = new ObjectOutputStream(socket.getOutputStream());
            PipeInputStream commandIn = new PipeInputStream();
            new DemultiplexInput(socket.getInputStream(), commandIn, execEnv.userOut(), execEnv.userErr()).start();
            this.remoteIn = new ObjectInputStream(commandIn);
        }
    }

    /**
     * Closes the execution engine. Send an exit command to the remote agent.
     * Shuts down the JDI connection. Should this close the socket?
     */
    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.beginShutdown();
            }
            if (remoteOut != null) {
                remoteOut.writeInt(CMD_EXIT);
                remoteOut.flush();
            }
            if (connection != null) {
                connection.disposeVM();
            }
        } catch (IOException ex) {
            debug(DBG_GEN, "Exception on JDI exit: %s\n", ex);
        }
    }

    /**
     * Loads the list of classes specified. Sends a load command to the remote
     * agent with pairs of classname/bytes.
     *
     * @param classes the names of the wrapper classes to loaded
     * @return true if all classes loaded successfully
     */
    @Override
    public boolean load(Collection<String> classes) {
        try {
            // Create corresponding ClassInfo instances to track the classes.
            // Each ClassInfo has the current class bytes associated with it.
            List<ClassInfo> infos = withBytes(classes);
            // Send a load command to the remote agent.
            remoteOut.writeInt(CMD_LOAD);
            remoteOut.writeInt(classes.size());
            for (ClassInfo ci : infos) {
                remoteOut.writeUTF(ci.getClassName());
                remoteOut.writeObject(ci.getBytes());
            }
            remoteOut.flush();
            // Retrieve and report results from the remote agent.
            boolean result = readAndReportResult();
            // For each class that now has a JDI ReferenceType, mark the bytes
            // as loaded.
            infos.stream()
                    .filter(ci -> ci.getReferenceTypeOrNull() != null)
                    .forEach(ci -> ci.markLoaded());
            return result;
        } catch (IOException ex) {
            debug(DBG_GEN, "IOException on remote load operation: %s\n", ex);
            return false;
        }
    }

    /**
     * Invoke the doit method on the specified class.
     *
     * @param classname name of the wrapper class whose doit should be invoked
     * @return return the result value of the doit
     * @throws JShellException if a user exception was thrown (EvalException) or
     * an unresolved reference was encountered (UnresolvedReferenceException)
     */
    @Override
    public String invoke(String classname, String methodname) throws JShellException {
        try {
            synchronized (STOP_LOCK) {
                userCodeRunning = true;
            }
            // Send the invoke command to the remote agent.
            remoteOut.writeInt(CMD_INVOKE);
            remoteOut.writeUTF(classname);
            remoteOut.writeUTF(methodname);
            remoteOut.flush();
            // Retrieve and report results from the remote agent.
            if (readAndReportExecutionResult()) {
                String result = remoteIn.readUTF();
                return result;
            }
        } catch (IOException | RuntimeException ex) {
            if (!connection.isRunning()) {
                // The JDI connection is no longer live, shutdown.
                handleVMExit();
            } else {
                debug(DBG_GEN, "Exception on remote invoke: %s\n", ex);
                return "Execution failure: " + ex.getMessage();
            }
        } finally {
            synchronized (STOP_LOCK) {
                userCodeRunning = false;
            }
        }
        return "";
    }

    /**
     * Retrieves the value of a JShell variable.
     *
     * @param classname name of the wrapper class holding the variable
     * @param varname name of the variable
     * @return the value as a String
     */
    @Override
    public String varValue(String classname, String varname) {
        try {
            // Send the variable-value command to the remote agent.
            remoteOut.writeInt(CMD_VARVALUE);
            remoteOut.writeUTF(classname);
            remoteOut.writeUTF(varname);
            remoteOut.flush();
            // Retrieve and report results from the remote agent.
            if (readAndReportResult()) {
                String result = remoteIn.readUTF();
                return result;
            }
        } catch (EOFException ex) {
            handleVMExit();
        } catch (IOException ex) {
            debug(DBG_GEN, "Exception on remote var value: %s\n", ex);
            return "Execution failure: " + ex.getMessage();
        }
        return "";
    }

    /**
     * Adds a path to the remote classpath.
     *
     * @param cp the additional path element
     * @return true if succesful
     */
    @Override
    public boolean addToClasspath(String cp) {
        try {
            // Send the classpath addition command to the remote agent.
            remoteOut.writeInt(CMD_CLASSPATH);
            remoteOut.writeUTF(cp);
            remoteOut.flush();
            // Retrieve and report results from the remote agent.
            return readAndReportResult();
        } catch (IOException ex) {
            throw new InternalError("Classpath addition failed: " + cp, ex);
        }
    }

    /**
     * Redefine the specified classes. Where 'redefine' is, as in JDI and JVMTI,
     * an in-place replacement of the classes (preserving class identity) --
     * that is, existing references to the class do not need to be recompiled.
     * This implementation uses JDI redefineClasses. It will be unsuccessful if
     * the signature of the class has changed (see the JDI spec). The
     * JShell-core is designed to adapt to unsuccessful redefine.
     *
     * @param classes the names of the classes to redefine
     * @return true if all the classes were redefined
     */
    @Override
    public boolean redefine(Collection<String> classes) {
        try {
            // Create corresponding ClassInfo instances to track the classes.
            // Each ClassInfo has the current class bytes associated with it.
            List<ClassInfo> infos = withBytes(classes);
            // Convert to the JDI ReferenceType to class bytes map form needed
            // by JDI.
            Map<ReferenceType, byte[]> rmp = infos.stream()
                    .collect(toMap(
                            ci -> ci.getReferenceTypeOrNull(),
                            ci -> ci.getBytes()));
            // Attempt redefine.  Throws exceptions on failure.
            connection.vm().redefineClasses(rmp);
            // Successful: mark the bytes as loaded.
            infos.stream()
                    .forEach(ci -> ci.markLoaded());
            return true;
        } catch (UnsupportedOperationException ex) {
            // A form of class transformation not supported by JDI
            return false;
        } catch (Exception ex) {
            debug(DBG_GEN, "Exception on JDI redefine: %s\n", ex);
            return false;
        }
    }

    // the VM has gone down in flames or because user evaled System.exit() or the like
    void handleVMExit() {
        if (connection != null) {
            // If there is anything left dispose of it
            connection.disposeVM();
        }
        // Tell JShell-core that the VM has died
        execEnv.closeDown();
    }

    // Lazy init class tracker
    private ClassTracker classTracker() {
        if (classTracker == null) {
            classTracker = new ClassTracker(connection.vm());
        }
        return classTracker;
    }

    /**
     * Converts a collection of class names into ClassInfo instances associated
     * with the most recently compiled class bytes.
     *
     * @param classes names of the classes
     * @return a list of corresponding ClassInfo instances
     */
    private List<ClassInfo> withBytes(Collection<String> classes) {
        return classes.stream()
                .map(cn -> classTracker().classInfo(cn, execEnv.getClassBytes(cn)))
                .collect(toList());
    }

    /**
     * Reports the status of the named class. UNKNOWN if not loaded. CURRENT if
     * the most recent successfully loaded/redefined bytes match the current
     * compiled bytes.
     *
     * @param classname the name of the class to test
     * @return the status
     */
    @Override
    public ClassStatus getClassStatus(String classname) {
        ClassInfo ci = classTracker().get(classname);
        if (ci.getReferenceTypeOrNull() == null) {
            // If the class does not have a JDI ReferenceType it has not been loaded
            return ClassStatus.UNKNOWN;
        }
        // Compare successfully loaded with last compiled bytes.
        return (Arrays.equals(execEnv.getClassBytes(classname), ci.getLoadedBytes()))
                ? ClassStatus.CURRENT
                : ClassStatus.NOT_CURRENT;
    }

    /**
     * Reports results from a remote agent command that does not expect
     * exceptions.
     *
     * @return true if successful
     * @throws IOException if the connection has dropped
     */
    private boolean readAndReportResult() throws IOException {
        int ok = remoteIn.readInt();
        switch (ok) {
            case RESULT_SUCCESS:
                return true;
            case RESULT_FAIL: {
                String ex = remoteIn.readUTF();
                debug(DBG_GEN, "Exception on remote operation: %s\n", ex);
                return false;
            }
            default: {
                debug(DBG_GEN, "Bad remote result code: %s\n", ok);
                return false;
            }
        }
    }

    /**
     * Reports results from a remote agent command that expects runtime
     * exceptions.
     *
     * @return true if successful
     * @throws IOException if the connection has dropped
     * @throws EvalException if a user exception was encountered on invoke
     * @throws UnresolvedReferenceException if an unresolved reference was
     * encountered
     */
    private boolean readAndReportExecutionResult() throws IOException, JShellException {
        int ok = remoteIn.readInt();
        switch (ok) {
            case RESULT_SUCCESS:
                return true;
            case RESULT_FAIL: {
                // An internal error has occurred.
                String ex = remoteIn.readUTF();
                return false;
            }
            case RESULT_EXCEPTION: {
                // A user exception was encountered.
                String exceptionClassName = remoteIn.readUTF();
                String message = remoteIn.readUTF();
                StackTraceElement[] elems = readStackTrace();
                throw execEnv.createEvalException(message, exceptionClassName, elems);
            }
            case RESULT_CORRALLED: {
                // An unresolved reference was encountered.
                int id = remoteIn.readInt();
                StackTraceElement[] elems = readStackTrace();
                throw execEnv.createUnresolvedReferenceException(id, elems);
            }
            case RESULT_KILLED: {
                // Execution was aborted by the stop()
                debug(DBG_GEN, "Killed.");
                return false;
            }
            default: {
                debug(DBG_GEN, "Bad remote result code: %s\n", ok);
                return false;
            }
        }
    }

    private StackTraceElement[] readStackTrace() throws IOException {
        int elemCount = remoteIn.readInt();
        StackTraceElement[] elems = new StackTraceElement[elemCount];
        for (int i = 0; i < elemCount; ++i) {
            String className = remoteIn.readUTF();
            String methodName = remoteIn.readUTF();
            String fileName = remoteIn.readUTF();
            int line = remoteIn.readInt();
            elems[i] = new StackTraceElement(className, methodName, fileName, line);
        }
        return elems;
    }

    private final Object STOP_LOCK = new Object();
    private boolean userCodeRunning = false;

    /**
     * Interrupt a running invoke.
     */
    @Override
    public void stop() {
        synchronized (STOP_LOCK) {
            if (!userCodeRunning) {
                return;
            }

            VirtualMachine vm = connection.vm();
            vm.suspend();
            try {
                OUTER:
                for (ThreadReference thread : vm.allThreads()) {
                    // could also tag the thread (e.g. using name), to find it easier
                    for (StackFrame frame : thread.frames()) {
                        String remoteAgentName = "jdk.internal.jshell.remote.RemoteAgent";
                        if (remoteAgentName.equals(frame.location().declaringType().name())
                                && "commandLoop".equals(frame.location().method().name())) {
                            ObjectReference thiz = frame.thisObject();
                            if (((BooleanValue) thiz.getValue(thiz.referenceType().fieldByName("inClientCode"))).value()) {
                                thiz.setValue(thiz.referenceType().fieldByName("expectingStop"), vm.mirrorOf(true));
                                ObjectReference stopInstance = (ObjectReference) thiz.getValue(thiz.referenceType().fieldByName("stopException"));

                                vm.resume();
                                debug(DBG_GEN, "Attempting to stop the client code...\n");
                                thread.stop(stopInstance);
                                thiz.setValue(thiz.referenceType().fieldByName("expectingStop"), vm.mirrorOf(false));
                            }

                            break OUTER;
                        }
                    }
                }
            } catch (ClassNotLoadedException | IncompatibleThreadStateException | InvalidTypeException ex) {
                debug(DBG_GEN, "Exception on remote stop: %s\n", ex);
            } finally {
                vm.resume();
            }
        }
    }

    void debug(int flags, String format, Object... args) {
        InternalDebugControl.debug(execEnv.state(), execEnv.userErr(), flags, format, args);
    }

    void debug(Exception ex, String where) {
        InternalDebugControl.debug(execEnv.state(), execEnv.userErr(), ex, where);
    }

    private final class DemultiplexInput extends Thread {

        private final DataInputStream delegate;
        private final PipeInputStream command;
        private final PrintStream out;
        private final PrintStream err;

        public DemultiplexInput(InputStream input,
                PipeInputStream command,
                PrintStream out,
                PrintStream err) {
            super("output reader");
            this.delegate = new DataInputStream(input);
            this.command = command;
            this.out = out;
            this.err = err;
        }

        public void run() {
            try {
                while (true) {
                    int nameLen = delegate.read();
                    if (nameLen == (-1))
                        break;
                    byte[] name = new byte[nameLen];
                    DemultiplexInput.this.delegate.readFully(name);
                    int dataLen = delegate.read();
                    byte[] data = new byte[dataLen];
                    DemultiplexInput.this.delegate.readFully(data);
                    switch (new String(name, "UTF-8")) {
                        case "err":
                            err.write(data);
                            break;
                        case "out":
                            out.write(data);
                            break;
                        case "command":
                            for (byte b : data) {
                                command.write(Byte.toUnsignedInt(b));
                            }
                            break;
                    }
                }
            } catch (IOException ex) {
                debug(ex, "Failed reading output");
            } finally {
                command.close();
            }
        }

    }

    public static final class PipeInputStream extends InputStream {
        public static final int INITIAL_SIZE = 128;

        private int[] buffer = new int[INITIAL_SIZE];
        private int start;
        private int end;
        private boolean closed;

        @Override
        public synchronized int read() {
            while (start == end) {
                if (closed) {
                    return -1;
                }
                try {
                    wait();
                } catch (InterruptedException ex) {
                    //ignore
                }
            }
            try {
                return buffer[start];
            } finally {
                start = (start + 1) % buffer.length;
            }
        }

        public synchronized void write(int b) {
            if (closed)
                throw new IllegalStateException("Already closed.");
            int newEnd = (end + 1) % buffer.length;
            if (newEnd == start) {
                //overflow:
                int[] newBuffer = new int[buffer.length * 2];
                int rightPart = (end > start ? end : buffer.length) - start;
                int leftPart = end > start ? 0 : start - 1;
                System.arraycopy(buffer, start, newBuffer, 0, rightPart);
                System.arraycopy(buffer, 0, newBuffer, rightPart, leftPart);
                buffer = newBuffer;
                start = 0;
                end = rightPart + leftPart;
                newEnd = end + 1;
            }
            buffer[end] = b;
            end = newEnd;
            notifyAll();
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }

    }
}
