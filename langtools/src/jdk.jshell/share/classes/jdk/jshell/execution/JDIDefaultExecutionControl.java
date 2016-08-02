/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell.execution;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;
import static jdk.jshell.execution.Util.remoteInput;

/**
 * The implementation of {@link jdk.jshell.spi.ExecutionControl} that the
 * JShell-core uses by default.
 * Launches a remote process -- the "remote agent".
 * Interfaces to the remote agent over a socket and via JDI.
 * Designed to work with {@link RemoteExecutionControl}.
 *
 * @author Robert Field
 * @author Jan Lahoda
 */
public class JDIDefaultExecutionControl extends JDIExecutionControl {

    private static final String REMOTE_AGENT = RemoteExecutionControl.class.getName();

    private VirtualMachine vm;
    private Process process;

    private final Object STOP_LOCK = new Object();
    private boolean userCodeRunning = false;

    /**
     * Creates an ExecutionControl instance based on a JDI
     * {@code LaunchingConnector}.
     *
     * @return the generator
     */
    public static ExecutionControl.Generator launch() {
        return env -> create(env, true);
    }

    /**
     * Creates an ExecutionControl instance based on a JDI
     * {@code ListeningConnector}.
     *
     * @return the generator
     */
    public static ExecutionControl.Generator listen() {
        return env -> create(env, false);
    }

    /**
     * Creates an ExecutionControl instance based on a JDI
     * {@code ListeningConnector} or {@code LaunchingConnector}.
     *
     * Initialize JDI and use it to launch the remote JVM. Set-up a socket for
     * commands and results. This socket also transports the user
     * input/output/error.
     *
     * @param env the context passed by
     * {@link jdk.jshell.spi.ExecutionControl#start(jdk.jshell.spi.ExecutionEnv) }
     * @return the channel
     * @throws IOException if there are errors in set-up
     */
    private static JDIDefaultExecutionControl create(ExecutionEnv env, boolean isLaunch) throws IOException {
        try (final ServerSocket listener = new ServerSocket(0)) {
            // timeout after 60 seconds
            listener.setSoTimeout(60000);
            int port = listener.getLocalPort();

            // Set-up the JDI connection
            JDIInitiator jdii = new JDIInitiator(port,
                    env.extraRemoteVMOptions(), REMOTE_AGENT, isLaunch);
            VirtualMachine vm = jdii.vm();
            Process process = jdii.process();

            // Forward input to the remote agent
            Util.forwardInputToRemote(env.userIn(), process.getOutputStream(),
                    ex -> debug(ex, "input forwarding failure"));

            List<Consumer<String>> deathListeners = new ArrayList<>();
            deathListeners.add(s -> env.closeDown());
            Util.detectJDIExitEvent(vm, s -> {
                for (Consumer<String> h : deathListeners) {
                    h.accept(s);
                }
            });

            // Set-up the commands/reslts on the socket.  Piggy-back snippet
            // output.
            Socket socket = listener.accept();
            // out before in -- match remote creation so we don't hang
            ObjectOutput cmdout = new ObjectOutputStream(socket.getOutputStream());
            Map<String, OutputStream> io = new HashMap<>();
            io.put("out", env.userOut());
            io.put("err", env.userErr());
            ObjectInput cmdin = remoteInput(socket.getInputStream(), io);
            return new JDIDefaultExecutionControl(cmdout, cmdin, vm, process, deathListeners);
        }
    }

    /**
     * Create an instance.
     *
     * @param cmdout the output for commands
     * @param cmdin the input for responses
     */
    private JDIDefaultExecutionControl(ObjectOutput cmdout, ObjectInput cmdin,
            VirtualMachine vm, Process process, List<Consumer<String>> deathListeners) {
        super(cmdout, cmdin);
        this.vm = vm;
        this.process = process;
        deathListeners.add(s -> disposeVM());
    }

    @Override
    public String invoke(String classname, String methodname)
            throws RunException,
            EngineTerminationException, InternalException {
        String res;
        synchronized (STOP_LOCK) {
            userCodeRunning = true;
        }
        try {
            res = super.invoke(classname, methodname);
        } finally {
            synchronized (STOP_LOCK) {
                userCodeRunning = false;
            }
        }
        return res;
    }

    /**
     * Interrupts a running remote invoke by manipulating remote variables
     * and sending a stop via JDI.
     *
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    @Override
    public void stop() throws EngineTerminationException, InternalException {
        synchronized (STOP_LOCK) {
            if (!userCodeRunning) {
                return;
            }

            vm().suspend();
            try {
                OUTER:
                for (ThreadReference thread : vm().allThreads()) {
                    // could also tag the thread (e.g. using name), to find it easier
                    for (StackFrame frame : thread.frames()) {
                        if (REMOTE_AGENT.equals(frame.location().declaringType().name()) &&
                                (    "invoke".equals(frame.location().method().name())
                                || "varValue".equals(frame.location().method().name()))) {
                            ObjectReference thiz = frame.thisObject();
                            Field inClientCode = thiz.referenceType().fieldByName("inClientCode");
                            Field expectingStop = thiz.referenceType().fieldByName("expectingStop");
                            Field stopException = thiz.referenceType().fieldByName("stopException");
                            if (((BooleanValue) thiz.getValue(inClientCode)).value()) {
                                thiz.setValue(expectingStop, vm().mirrorOf(true));
                                ObjectReference stopInstance = (ObjectReference) thiz.getValue(stopException);

                                vm().resume();
                                debug("Attempting to stop the client code...\n");
                                thread.stop(stopInstance);
                                thiz.setValue(expectingStop, vm().mirrorOf(false));
                            }

                            break OUTER;
                        }
                    }
                }
            } catch (ClassNotLoadedException | IncompatibleThreadStateException | InvalidTypeException ex) {
                throw new InternalException("Exception on remote stop: " + ex);
            } finally {
                vm().resume();
            }
        }
    }

    @Override
    public void close() {
        super.close();
        disposeVM();
    }

    private synchronized void disposeVM() {
        try {
            if (vm != null) {
                vm.dispose(); // This could NPE, so it is caught below
                vm = null;
            }
        } catch (VMDisconnectedException ex) {
            // Ignore if already closed
        } catch (Throwable ex) {
            debug(ex, "disposeVM");
        } finally {
            if (process != null) {
                process.destroy();
                process = null;
            }
        }
    }

    @Override
    protected synchronized VirtualMachine vm() throws EngineTerminationException {
        if (vm == null) {
            throw new EngineTerminationException("VM closed");
        } else {
            return vm;
        }
    }

    /**
     * Log debugging information. Arguments as for {@code printf}.
     *
     * @param format a format string as described in Format string syntax
     * @param args arguments referenced by the format specifiers in the format
     * string.
     */
    private static void debug(String format, Object... args) {
        // Reserved for future logging
    }

    /**
     * Log a serious unexpected internal exception.
     *
     * @param ex the exception
     * @param where a description of the context of the exception
     */
    private static void debug(Throwable ex, String where) {
        // Reserved for future logging
    }

}
