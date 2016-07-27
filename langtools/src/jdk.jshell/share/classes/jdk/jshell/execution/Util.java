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

import jdk.jshell.spi.ExecutionEnv;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import com.sun.jdi.VirtualMachine;
import jdk.jshell.spi.ExecutionControl;


/**
 * Miscellaneous utility methods for setting-up implementations of
 * {@link ExecutionControl}. Particularly implementations with remote
 * execution.
 *
 * @author Jan Lahoda
 * @author Robert Field
 */
public class Util {

    // never instanciated
    private Util() {}

    /**
     * Create a composite {@link ExecutionControl.Generator} instance that, when
     * generating, will try each specified generator until successfully creating
     * an {@link ExecutionControl} instance, or, if all fail, will re-throw the
     * first exception.
     *
     * @param gec0 the first instance to try
     * @param gecs the second through Nth instance to try
     * @return the fail-over generator
     */
    public static ExecutionControl.Generator failOverExecutionControlGenerator(
            ExecutionControl.Generator gec0, ExecutionControl.Generator... gecs) {
        return (ExecutionEnv env) -> {
            Throwable thrown;
            try {
                return gec0.generate(env);
            } catch (Throwable ex) {
                thrown = ex;
            }
            for (ExecutionControl.Generator gec : gecs) {
                try {
                    return gec.generate(env);
                } catch (Throwable ignore) {
                    // only care about the first, and only if they all fail
                }
            }
            throw thrown;
        };
    }

    /**
     * Forward commands from the input to the specified {@link ExecutionControl}
     * instance, then responses back on the output.
     * @param ec the direct instance of {@link ExecutionControl} to process commands
     * @param in the command input
     * @param out the command response output
     */
    public static void forwardExecutionControl(ExecutionControl ec,
            ObjectInput in, ObjectOutput out) {
        new ExecutionControlForwarder(ec, in, out).commandLoop();
    }

    /**
     * Forward commands from the input to the specified {@link ExecutionControl}
     * instance, then responses back on the output.
     * @param ec the direct instance of {@link ExecutionControl} to process commands
     * @param inStream the stream from which to create the command input
     * @param outStream the stream that will carry {@code System.out},
     * {@code System.err}, any specified auxiliary channels, and the
     * command response output.
     * @param streamMap a map between names of additional streams to carry and setters
     * for the stream
     * @throws IOException if there are errors using the passed streams
     */
    public static void forwardExecutionControlAndIO(ExecutionControl ec,
            InputStream inStream, OutputStream outStream,
            Map<String, Consumer<OutputStream>> streamMap) throws IOException {
        ObjectInputStream cmdIn = new ObjectInputStream(inStream);
        for (Entry<String, Consumer<OutputStream>> e : streamMap.entrySet()) {
            e.getValue().accept(multiplexingOutputStream(e.getKey(), outStream));
        }
        ObjectOutputStream cmdOut = new ObjectOutputStream(multiplexingOutputStream("command", outStream));
        forwardExecutionControl(ec, cmdIn, cmdOut);
    }

    static OutputStream multiplexingOutputStream(String label, OutputStream outputStream) {
        return new MultiplexingOutputStream(label, outputStream);
    }

    /**
     * Reads from an InputStream which has been packetized and write its contents
     * to the out and err OutputStreams; Copies the command stream.
     * @param input the packetized input stream
     * @param streamMap a map between stream names and the output streams to forward
     * @return the command stream
     * @throws IOException if setting up the streams raised an exception
     */
    public static ObjectInput remoteInput(InputStream input,
            Map<String, OutputStream> streamMap) throws IOException {
        PipeInputStream commandIn = new PipeInputStream();
        new DemultiplexInput(input, commandIn, streamMap).start();
        return new ObjectInputStream(commandIn);
    }

    /**
     * Monitor the JDI event stream for {@link com.sun.jdi.event.VMDeathEvent}
     * and {@link com.sun.jdi.event.VMDisconnectEvent}. If encountered, invokes
     * {@code unbiddenExitHandler}.
     *
     * @param vm the virtual machine to check
     * @param unbiddenExitHandler the handler, which will accept the exit
     * information
     */
    public static void detectJDIExitEvent(VirtualMachine vm, Consumer<String> unbiddenExitHandler) {
        if (vm.canBeModified()) {
            new JDIEventHandler(vm, unbiddenExitHandler).start();
        }
    }

    /**
     * Creates a Thread that will ship all input to the remote agent.
     *
     * @param inputStream the user input
     * @param outStream the input to the remote agent
     * @param handler a failure handler
     */
    public static void forwardInputToRemote(final InputStream inputStream,
            final OutputStream outStream, final Consumer<Exception> handler) {
        Thread thr = new Thread("input reader") {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[256];
                    int cnt;
                    while ((cnt = inputStream.read(buf)) != -1) {
                        outStream.write(buf, 0, cnt);
                        outStream.flush();
                    }
                } catch (Exception ex) {
                    handler.accept(ex);
                }
            }
        };
        thr.setPriority(Thread.MAX_PRIORITY - 1);
        thr.start();
    }

}
