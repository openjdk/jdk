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

package jdk.jshell;

import static jdk.internal.jshell.remote.RemoteCodes.*;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import com.sun.jdi.*;
import java.io.EOFException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.jshell.ClassTracker.ClassInfo;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;

/**
 * Controls the remote execution environment.
 *
 * @author Robert Field
 */
class ExecutionControl {

    private final JDIEnv env;
    private final SnippetMaps maps;
    private JDIEventHandler handler;
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final JShell proc;

    ExecutionControl(JDIEnv env, SnippetMaps maps, JShell proc) {
        this.env = env;
        this.maps = maps;
        this.proc = proc;
    }

    void launch() throws IOException {
        try (ServerSocket listener = new ServerSocket(0)) {
            // timeout after 60 seconds
            listener.setSoTimeout(60000);
            int port = listener.getLocalPort();
            jdiGo(port);
            socket = listener.accept();
            // out before in -- match remote creation so we don't hang
            out = new ObjectOutputStream(socket.getOutputStream());
            PipeInputStream commandIn = new PipeInputStream();
            new DemultiplexInput(socket.getInputStream(), commandIn, proc.out, proc.err).start();
            in = new ObjectInputStream(commandIn);
        }
    }

    void commandExit() {
        try {
            if (out != null) {
                out.writeInt(CMD_EXIT);
                out.flush();
            }
            JDIConnection c = env.connection();
            if (c != null) {
                c.disposeVM();
            }
        } catch (IOException ex) {
            proc.debug(DBG_GEN, "Exception on JDI exit: %s\n", ex);
        }
    }


    boolean commandLoad(Collection<ClassInfo> cil) {
        try {
            out.writeInt(CMD_LOAD);
            out.writeInt(cil.size());
            for (ClassInfo ci : cil) {
                out.writeUTF(ci.getClassName());
                out.writeObject(ci.getBytes());
            }
            out.flush();
            return readAndReportResult();
        } catch (IOException ex) {
            proc.debug(DBG_GEN, "IOException on remote load operation: %s\n", ex);
            return false;
        }
    }

    String commandInvoke(String classname) throws EvalException, UnresolvedReferenceException {
        try {
            synchronized (STOP_LOCK) {
                userCodeRunning = true;
            }
            out.writeInt(CMD_INVOKE);
            out.writeUTF(classname);
            out.flush();
            if (readAndReportExecutionResult()) {
                String result = in.readUTF();
                return result;
            }
        } catch (IOException | RuntimeException ex) {
            if (!env.connection().isRunning()) {
                env.shutdown();
            } else {
                proc.debug(DBG_GEN, "Exception on remote invoke: %s\n", ex);
                return "Execution failure: " + ex.getMessage();
            }
        } finally {
            synchronized (STOP_LOCK) {
                userCodeRunning = false;
            }
        }
        return "";
    }

    String commandVarValue(String classname, String varname) {
        try {
            out.writeInt(CMD_VARVALUE);
            out.writeUTF(classname);
            out.writeUTF(varname);
            out.flush();
            if (readAndReportResult()) {
                String result = in.readUTF();
                return result;
            }
        } catch (EOFException ex) {
            env.shutdown();
        } catch (IOException ex) {
            proc.debug(DBG_GEN, "Exception on remote var value: %s\n", ex);
            return "Execution failure: " + ex.getMessage();
        }
        return "";
    }

    boolean commandAddToClasspath(String cp) {
        try {
            out.writeInt(CMD_CLASSPATH);
            out.writeUTF(cp);
            out.flush();
            return readAndReportResult();
        } catch (IOException ex) {
            throw new InternalError("Classpath addition failed: " + cp, ex);
        }
    }

    boolean commandRedefine(Map<ReferenceType, byte[]> mp) {
        try {
            env.vm().redefineClasses(mp);
            return true;
        } catch (UnsupportedOperationException ex) {
            return false;
        } catch (Exception ex) {
            proc.debug(DBG_GEN, "Exception on JDI redefine: %s\n", ex);
            return false;
        }
    }

    ReferenceType nameToRef(String name) {
        List<ReferenceType> rtl = env.vm().classesByName(name);
        if (rtl.size() != 1) {
            return null;
        }
        return rtl.get(0);
    }

    private boolean readAndReportResult() throws IOException {
        int ok = in.readInt();
        switch (ok) {
            case RESULT_SUCCESS:
                return true;
            case RESULT_FAIL: {
                String ex = in.readUTF();
                proc.debug(DBG_GEN, "Exception on remote operation: %s\n", ex);
                return false;
            }
            default: {
                proc.debug(DBG_GEN, "Bad remote result code: %s\n", ok);
                return false;
            }
        }
    }

    private boolean readAndReportExecutionResult() throws IOException, EvalException, UnresolvedReferenceException {
        int ok = in.readInt();
        switch (ok) {
            case RESULT_SUCCESS:
                return true;
            case RESULT_FAIL: {
                String ex = in.readUTF();
                proc.debug(DBG_GEN, "Exception on remote operation: %s\n", ex);
                return false;
            }
            case RESULT_EXCEPTION: {
                String exceptionClassName = in.readUTF();
                String message = in.readUTF();
                StackTraceElement[] elems = readStackTrace();
                EvalException ee = new EvalException(message, exceptionClassName, elems);
                throw ee;
            }
            case RESULT_CORRALLED: {
                int id = in.readInt();
                StackTraceElement[] elems = readStackTrace();
                Snippet si = maps.getSnippetDeadOrAlive(id);
                throw new UnresolvedReferenceException((DeclarationSnippet) si, elems);
            }
            case RESULT_KILLED: {
                proc.out.println("Killed.");
                return false;
            }
            default: {
                proc.debug(DBG_GEN, "Bad remote result code: %s\n", ok);
                return false;
            }
        }
    }

    private StackTraceElement[] readStackTrace() throws IOException {
        int elemCount = in.readInt();
        StackTraceElement[] elems = new StackTraceElement[elemCount];
        for (int i = 0; i < elemCount; ++i) {
            String className = in.readUTF();
            String methodName = in.readUTF();
            String fileName = in.readUTF();
            int line = in.readInt();
            elems[i] = new StackTraceElement(className, methodName, fileName, line);
        }
        return elems;
    }

    private void jdiGo(int port) {
        //MessageOutput.textResources = ResourceBundle.getBundle("impl.TTYResources",
        //        Locale.getDefault());

        String connectorName = "com.sun.jdi.CommandLineLaunch";
        String classPath = System.getProperty("java.class.path");
        String javaArgs = "-classpath " + classPath;
        Map<String, String> argumentName2Value = new HashMap<>();
        argumentName2Value.put("main", "jdk.internal.jshell.remote.RemoteAgent " + port);
        argumentName2Value.put("options", javaArgs);

        boolean launchImmediately = true;
        int traceFlags = 0;// VirtualMachine.TRACE_SENDS | VirtualMachine.TRACE_EVENTS;

        env.init(connectorName, argumentName2Value, launchImmediately, traceFlags);

        if (env.connection().isOpen() && env.vm().canBeModified()) {
            /*
             * Connection opened on startup. Start event handler
             * immediately, telling it (through arg 2) to stop on the
             * VM start event.
             */
            handler = new JDIEventHandler(env);
        }
    }

    private final Object STOP_LOCK = new Object();
    private boolean userCodeRunning = false;

    void commandStop() {
        synchronized (STOP_LOCK) {
            if (!userCodeRunning)
                return ;

            VirtualMachine vm = handler.env.vm();
            vm.suspend();
            try {
                OUTER: for (ThreadReference thread : vm.allThreads()) {
                    // could also tag the thread (e.g. using name), to find it easier
                    for (StackFrame frame : thread.frames()) {
                        String remoteAgentName = "jdk.internal.jshell.remote.RemoteAgent";
                        if (remoteAgentName.equals(frame.location().declaringType().name()) &&
                            "commandLoop".equals(frame.location().method().name())) {
                            ObjectReference thiz = frame.thisObject();
                            if (((BooleanValue) thiz.getValue(thiz.referenceType().fieldByName("inClientCode"))).value()) {
                                thiz.setValue(thiz.referenceType().fieldByName("expectingStop"), vm.mirrorOf(true));
                                ObjectReference stopInstance = (ObjectReference) thiz.getValue(thiz.referenceType().fieldByName("stopException"));

                                vm.resume();
                                proc.debug(DBG_GEN, "Attempting to stop the client code...\n");
                                thread.stop(stopInstance);
                                thiz.setValue(thiz.referenceType().fieldByName("expectingStop"), vm.mirrorOf(false));
                            }

                            break OUTER;
                        }
                    }
                }
            } catch (ClassNotLoadedException | IncompatibleThreadStateException | InvalidTypeException  ex) {
                proc.debug(DBG_GEN, "Exception on remote stop: %s\n", ex);
            } finally {
                vm.resume();
            }
        }
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
                proc.debug(ex, "Failed reading output");
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
