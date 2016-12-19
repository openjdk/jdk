/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8160128 8159935
 * @summary Tests for Aux channel, custom remote agents, custom JDI implementations.
 * @build KullaTesting ExecutionControlTestBase
 * @run testng UserJdiUserRemoteTest
 */
import java.io.ByteArrayOutputStream;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import jdk.jshell.Snippet;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;
import static jdk.jshell.Snippet.Status.VALID;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import jdk.jshell.VarSnippet;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.JdiExecutionControl;
import jdk.jshell.execution.JdiInitiator;
import jdk.jshell.execution.Util;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.ExecutionControlException;
import jdk.jshell.spi.ExecutionEnv;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static jdk.jshell.execution.Util.forwardExecutionControlAndIO;
import static jdk.jshell.execution.Util.remoteInputOutput;

@Test
public class UserJdiUserRemoteTest extends ExecutionControlTestBase {

    ExecutionControl currentEC;
    ByteArrayOutputStream auxStream;

    @BeforeMethod
    @Override
    public void setUp() {
        auxStream = new ByteArrayOutputStream();
        setUp(builder -> builder.executionEngine(MyExecutionControl.create(this)));
    }

    public void testVarValue() {
        VarSnippet dv = varKey(assertEval("double aDouble = 1.5;"));
        String vd = getState().varValue(dv);
        assertEquals(vd, "1.5");
        assertEquals(auxStream.toString(), "aDouble");
    }

    public void testExtension() throws ExecutionControlException {
        assertEval("42;");
        Object res = currentEC.extensionCommand("FROG", "test");
        assertEquals(res, "ribbit");
    }

    public void testRedefine() {
        Snippet vx = varKey(assertEval("int x;"));
        Snippet mu = methodKey(assertEval("int mu() { return x * 4; }"));
        Snippet c = classKey(assertEval("class C { String v() { return \"#\" + mu(); } }"));
        assertEval("C c0  = new C();");
        assertEval("c0.v();", "\"#0\"");
        assertEval("int x = 10;", "10",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(vx, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c0.v();", "\"#40\"");
        assertEval("C c = new C();");
        assertEval("c.v();", "\"#40\"");
        assertEval("int mu() { return x * 3; }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(mu, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c.v();", "\"#30\"");
        assertEval("class C { String v() { return \"@\" + mu(); } }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(c, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c0.v();", "\"@30\"");
        assertEval("c = new C();");
        assertEval("c.v();", "\"@30\"");
        assertActiveKeys();
    }
}

class MyExecutionControl extends JdiExecutionControl {

    private static final String REMOTE_AGENT = MyRemoteExecutionControl.class.getName();
    private static final int TIMEOUT = 2000;

    private VirtualMachine vm;
    private Process process;

    /**
     * Creates an ExecutionControl instance based on a JDI
     * {@code LaunchingConnector}.
     *
     * @return the generator
     */
    public static ExecutionControl.Generator create(UserJdiUserRemoteTest test) {
        return env -> make(env, test);
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
    static ExecutionControl make(ExecutionEnv env, UserJdiUserRemoteTest test) throws IOException {
        try (final ServerSocket listener = new ServerSocket(0)) {
            // timeout for socket
            listener.setSoTimeout(TIMEOUT);
            int port = listener.getLocalPort();

            // Set-up the JDI connection
            List<String> opts = new ArrayList<>(env.extraRemoteVMOptions());
            opts.add("-classpath");
            opts.add(System.getProperty("java.class.path")
                    + System.getProperty("path.separator")
                    + System.getProperty("user.dir"));
            JdiInitiator jdii = new JdiInitiator(port,
                    opts, REMOTE_AGENT, true, null, TIMEOUT);
            VirtualMachine vm = jdii.vm();
            Process process = jdii.process();

            List<Consumer<String>> deathListeners = new ArrayList<>();
            deathListeners.add(s -> env.closeDown());
            Util.detectJdiExitEvent(vm, s -> {
                for (Consumer<String> h : deathListeners) {
                    h.accept(s);
                }
            });

            // Set-up the commands/reslts on the socket.  Piggy-back snippet
            // output.
            Socket socket = listener.accept();
            // out before in -- match remote creation so we don't hang
            OutputStream out = socket.getOutputStream();
            Map<String, OutputStream> outputs = new HashMap<>();
            outputs.put("out", env.userOut());
            outputs.put("err", env.userErr());
            outputs.put("aux", test.auxStream);
            Map<String, InputStream> input = new HashMap<>();
            input.put("in", env.userIn());
            ExecutionControl myec = remoteInputOutput(socket.getInputStream(), out, outputs, input, (objIn, objOut) -> new MyExecutionControl(objOut, objIn, vm, process, deathListeners));
            test.currentEC = myec;
            return myec;
        }
    }

    /**
     * Create an instance.
     *
     * @param out the output for commands
     * @param in the input for responses
     */
    private MyExecutionControl(ObjectOutput out, ObjectInput in,
            VirtualMachine vm, Process process,
            List<Consumer<String>> deathListeners) {
        super(out, in);
        this.vm = vm;
        this.process = process;
        deathListeners.add(s -> disposeVM());
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
        } catch (Throwable e) {
            fail("disposeVM threw: " + e);
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

}

class MyRemoteExecutionControl extends DirectExecutionControl implements ExecutionControl {

    static PrintStream auxPrint;

    /**
     * Launch the agent, connecting to the JShell-core over the socket specified
     * in the command-line argument.
     *
     * @param args standard command-line arguments, expectation is the socket
     * number is the only argument
     * @throws Exception any unexpected exception
     */
    public static void main(String[] args) throws Exception {
        try {
            String loopBack = null;
            Socket socket = new Socket(loopBack, Integer.parseInt(args[0]));
            InputStream inStream = socket.getInputStream();
            OutputStream outStream = socket.getOutputStream();
            Map<String, Consumer<OutputStream>> outputs = new HashMap<>();
            outputs.put("out", st -> System.setOut(new PrintStream(st, true)));
            outputs.put("err", st -> System.setErr(new PrintStream(st, true)));
            outputs.put("aux", st -> { auxPrint = new PrintStream(st, true); });
            Map<String, Consumer<InputStream>> input = new HashMap<>();
            input.put("in", st -> System.setIn(st));
            forwardExecutionControlAndIO(new MyRemoteExecutionControl(), inStream, outStream, outputs, input);
        } catch (Throwable ex) {
            throw ex;
        }
    }

    @Override
    public String varValue(String className, String varName)
            throws RunException, EngineTerminationException, InternalException {
        auxPrint.print(varName);
        return super.varValue(className, varName);
    }

    @Override
    public Object extensionCommand(String className, Object arg)
            throws RunException, EngineTerminationException, InternalException {
        if (!arg.equals("test")) {
            throw new InternalException("expected extensionCommand arg to be 'test' got: " + arg);
        }
        return "ribbit";
    }

}
