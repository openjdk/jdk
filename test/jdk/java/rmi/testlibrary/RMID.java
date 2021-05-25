/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.registry.*;
import java.time.LocalTime;
import java.util.concurrent.TimeoutException;

/**
 * Utility class that creates an instance of rmid with a policy
 * file of name <code>TestParams.defaultPolicy</code>.
 *
 * Activation groups should run with the same security manager as the
 * test.
 */
public class RMID extends JavaVM {

    // TODO: adjust these based on the timeout factor
    // such as jcov.sleep.multiplier; see start(long) method.
    // Also consider the test.timeout.factor property (a float).
    private static final long TIMEOUT_SHUTDOWN_MS = 60_000L;
    private static final long TIMEOUT_DESTROY_MS  = 10_000L;
    private static final long STARTTIME_MS        = 15_000L;
    private static final long POLLTIME_MS         = 100L;
    private static final long TIMEOUT_BASE        = 240_000L;

    // when restart rmid, it may take more time than usual because of
    // "port in use" by a possible interloper (check JDK-8168975),
    // so need to set a longer timeout than STARTTIME_MS for restart.
    private static final long RESTART_TIMEOUT = (long)(TIMEOUT_BASE * 0.9);
    // Same reason to inheritedChannel in RMIDSelectorProvider.
    // Put it here rather than in RMIDSelectorProvider to adjust
    // both timeout values together.
    private static long inheritedChannelTimeout;

    private static final String SYSTEM_NAME = ActivationSystem.class.getName();
        // "java.rmi.activation.ActivationSystem"

    public static String MANAGER_OPTION="-Djava.security.manager=";

    /**
     * Test port for rmid.
     *
     * May initially be 0, which means that the child rmid process will choose
     * an ephemeral port and report it back to the parent process. This field
     * will then be set to the child rmid's ephemeral port value.
     */
    private volatile int port;
    //private final boolean ephemeralPort

    /** Initial log name */
    protected static String log = "log";
    /** rmid's logfile directory; currently must be "." */
    protected static String LOGDIR = ".";

    /** The output message from the child rmid process that directly precedes
     * the ephemeral port number.*/
    public static final String EPHEMERAL_MSG = "RmidSelectorProvider-listening-On:";

    private static void mesg(Object mesg) {
        System.err.println("RMID: " + mesg.toString());
    }

    /** make test options and arguments */
    private static String makeOptions(int port, boolean debugExec,
                                      boolean enableSelectorProvider) {

        String options = " -Dsun.rmi.server.activation.debugExec=" +
            debugExec;
        // +
        //" -Djava.compiler= ";

        // if test params set, want to propagate them
        if (!TestParams.testSrc.equals("")) {
            options += " -Dtest.src=" + TestParams.testSrc + " ";
        }
        //if (!TestParams.testClasses.equals("")) {
        //    options += " -Dtest.classes=" + TestParams.testClasses + " ";
        //}
        options += " -Dtest.classes=" + TestParams.testClasses //;
         +
         " -Djava.rmi.server.logLevel=v ";

        // +
        // " -Djava.security.debug=all ";

        // Set execTimeout to 60 sec (default is 30 sec)
        // to avoid spurious timeouts on slow machines.
        options += " -Dsun.rmi.activation.execTimeout=60000";

        // It's important to set handshakeTimeout to small value, for example
        // 5 sec (default is 60 sec) to avoid wasting too much time when
        // calling lookupSystem(port) in restart(), because
        //   1. If use default value of this option, it will take about 2 minutes
        //     to finish lookupSystem(port) in 2 loops in restart();
        //   2. If set this option as 5 sec then lookupSystem(port) will return
        //     very quickly.
        options += " -Dsun.rmi.transport.tcp.handshakeTimeout=5000";

        if (port == 0 || enableSelectorProvider) {
            // Ephemeral port, so have the rmid child process create the
            // server socket channel and report its port number, over stdin.
            options += " -classpath " + TestParams.testClassPath;
            options += " --add-exports=java.base/sun.nio.ch=ALL-UNNAMED";
            options += " -Djava.nio.channels.spi.SelectorProvider=RMIDSelectorProvider";
            options += " -Dtest.java.rmi.testlibrary.RMIDSelectorProvider.port=" + port;
            options += " -Dtest.java.rmi.testlibrary.RMIDSelectorProvider.timeout="
                        + inheritedChannelTimeout;

            // Disable redirection of System.err to /tmp
            options += " -Dsun.rmi.server.activation.disableErrRedirect=true";
        }

        return options;
    }

    private static String makeArgs() {
        return makeArgs(false, 0);
    }

    private static String makeArgs(boolean includePortArg, int port) {
        // getAbsolutePath requires permission to read user.dir
        String args =
            " -log " + (new File(LOGDIR, log)).getAbsolutePath();

        // 0 = ephemeral port, do not include an explicit port number
        if (includePortArg && port != 0) {
            args += " -port " + port;
        }

        // +
        //      " -C-Djava.compiler= ";

        // if test params set, want to propagate them
        if (!TestParams.testSrc.equals("")) {
            args += " -C-Dtest.src=" + TestParams.testSrc;
        }
        if (!TestParams.testClasses.equals("")) {
            args += " -C-Dtest.classes=" + TestParams.testClasses;
        }

        if (!TestParams.testJavaOpts.equals("")) {
            for (String a : TestParams.testJavaOpts.split(" +")) {
                args += " -C" + a;
            }
        }

        if (!TestParams.testVmOpts.equals("")) {
            for (String a : TestParams.testVmOpts.split(" +")) {
                args += " -C" + a;
            }
        }

        args += " -C-Djava.rmi.server.useCodebaseOnly=false ";

        args += " " + getCodeCoverageArgs();
        return args;
    }

    /**
     * Routine that creates an rmid that will run with or without a
     * policy file.
     */
    public static RMID createRMID() {
        return createRMID(System.out, System.err, true, true,
                          TestLibrary.getUnusedRandomPort());
    }

    public static RMID createRMID(OutputStream out, OutputStream err,
                                  boolean debugExec)
    {
        return createRMID(out, err, debugExec, true,
                          TestLibrary.getUnusedRandomPort());
    }

    public static RMID createRMID(OutputStream out, OutputStream err,
                                  boolean debugExec, boolean includePortArg,
                                  int port)
    {
        return createRMIDWithOptions(out, err, debugExec, includePortArg, port, "");
    }

    /**
     * Create a RMID on a specified port capturing stdout and stderr
     * with additional command line options and whether to print out
     * debugging information that is used for spawning activation groups.
     *
     * @param out the OutputStream where the normal output of the
     *            rmid subprocess goes
     * @param err the OutputStream where the error output of the
     *            rmid subprocess goes
     * @param debugExec whether to print out debugging information
     * @param includePortArg whether to include port argument
     * @param port the port on which rmid accepts requests
     * @param additionalOptions additional command line options
     * @return a RMID instance
     */
    public static RMID createRMIDWithOptions(OutputStream out, OutputStream err,
                                  boolean debugExec, boolean includePortArg,
                                  int port, String additionalOptions)
    {
        String options = makeOptions(port, debugExec, false);
        options += " " + additionalOptions;
        String args = makeArgs(includePortArg, port);
        RMID rmid = new RMID("sun.rmi.server.Activation", options, args,
                             out, err, port);
        rmid.setPolicyFile(TestParams.defaultRmidPolicy);

        return rmid;
    }

    public static RMID createRMIDOnEphemeralPort() {
        return createRMID(System.out, System.err, true, false, 0);
    }

    /**
     * Create a RMID on an ephemeral port capturing stdout and stderr
     * with additional command line options.
     *
     * @param additionalOptions additional command line options
     * @return a RMID instance
     */
    public static RMID createRMIDOnEphemeralPortWithOptions(
                                            String additionalOptions) {
        return createRMIDWithOptions(System.out, System.err,
                                     true, false, 0, additionalOptions);
    }

    public static RMID createRMIDOnEphemeralPort(OutputStream out,
                                                 OutputStream err,
                                                 boolean debugExec)
    {
        return createRMID(out, err, debugExec, false, 0);
    }


    /**
     * Private constructor. RMID instances should be created
     * using the static factory methods.
     */
    private RMID(String classname, String options, String args,
                   OutputStream out, OutputStream err, int port)
    {
        super(classname, options, args, out, err);
        this.port = port;
        long waitTime = (long)(TIMEOUT_BASE * TestLibrary.getTimeoutFactor());
        inheritedChannelTimeout = (long)(waitTime * 0.8);
    }

    /**
     * Removes rmid's log file directory.
     */
    public static void removeLog() {
        File f = new File(LOGDIR, log);

        if (f.exists()) {
            mesg("Removing rmid's old log file.");
            String[] files = f.list();

            if (files != null) {
                for (int i=0; i<files.length; i++) {
                    (new File(f, files[i])).delete();
                }
            }

            if (! f.delete()) {
                mesg("Warning: unable to delete old log file.");
            }
        }
    }

    /**
     * This method is used for adding arguments to rmid (not its VM)
     * for passing as VM options to its child group VMs.
     * Returns the extra command line arguments required
     * to turn on jcov code coverage analysis for rmid child VMs.
     */
    protected static String getCodeCoverageArgs() {
        return TestLibrary.getExtraProperty("rmid.jcov.args","");
    }

    /**
     * Looks up the activation system in the registry on the given port,
     * returning its stub, or null if it's not present. This method differs from
     * ActivationGroup.getSystem() because this method looks on a specific port
     * instead of using the java.rmi.activation.port property like
     * ActivationGroup.getSystem() does. This method also returns null instead
     * of throwing exceptions.
     */
    public static ActivationSystem lookupSystem(int port) {
        try {
            return (ActivationSystem)LocateRegistry.getRegistry(port).lookup(SYSTEM_NAME);
        } catch (RemoteException | NotBoundException ex) {
            return null;
        }
    }

    /**
     * Starts rmid and waits up to the default timeout period
     * to confirm that it's running.
     */
    public void start() throws IOException {
        start(STARTTIME_MS);
    }

    /**
     * Starts rmid and waits up to the given timeout period
     * to confirm that it's running.
     */
    public void start(long waitTime) throws IOException {

        // if rmid is already running, then the test will fail with
        // a well recognized exception (port already in use...).

        mesg("Starting rmid on port " + port + ", at " + LocalTime.now());
        if (!options.contains("-Djava.security.manager")) {
            options += " -Djava.security.manager=allow";
        }
        int p = super.startAndGetPort();
        if (p != -1)
            port = p;
        mesg("Started rmid on port " + port + ", at " + LocalTime.now());

        // int slopFactor = 1;
        // try {
        //     slopFactor = Integer.valueOf(
        //         TestLibrary.getExtraProperty("jcov.sleep.multiplier","1"));
        // } catch (NumberFormatException ignore) {}
        // waitTime = waitTime * slopFactor;

        long startTime = System.currentTimeMillis();
        long deadline = TestLibrary.computeDeadline(startTime, waitTime);

        while (true) {
            try {
                Thread.sleep(POLLTIME_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                mesg("Starting rmid interrupted, giving up at " +
                    (System.currentTimeMillis() - startTime) + "ms.");
                return;
            }

            try {
                int status = vm.exitValue();
                waitFor(TIMEOUT_SHUTDOWN_MS);
                TestLibrary.bomb("Rmid process exited with status " + status + " after " +
                    (System.currentTimeMillis() - startTime) + "ms.");
            } catch (InterruptedException | TimeoutException e) {
                mesg(e);
            } catch (IllegalThreadStateException ignore) { }

            // The rmid process is alive; check to see whether
            // it responds to a remote call.

            mesg("looking up activation system, at " + LocalTime.now());
            if (lookupSystem(port) != null) {
                /*
                 * We need to set the java.rmi.activation.port value as the
                 * activation system will use the property to determine the
                 * port #.  The activation system will use this value if set.
                 * If it isn't set, the activation system will set it to an
                 * incorrect value.
                 */
                System.setProperty("java.rmi.activation.port", Integer.toString(port));
                mesg("Started successfully after " +
                    (System.currentTimeMillis() - startTime) + "ms, at " + LocalTime.now());
                return;
            }

            mesg("after fail to looking up activation system, at " + LocalTime.now());
            if (System.currentTimeMillis() > deadline) {
                TestLibrary.bomb("Failed to start rmid, giving up after " +
                    (System.currentTimeMillis() - startTime) + "ms.", null);
            }
        }
    }

    /**
     * Destroys rmid and restarts it. Note that this does NOT clean up
     * the log file, because it stores information about restartable
     * and activatable objects that must be carried over to the new
     * rmid instance.
     */
    public void restart() throws IOException {
        destroy();
        options = makeOptions(port, true, true);
        args = makeArgs();

        start(RESTART_TIMEOUT);
    }

    /**
     * Ask rmid to shutdown gracefully using a remote method call.
     * catch any errors that might occur from rmid not being present
     * at time of shutdown invocation. If the remote call is
     * successful, wait for the process to terminate. Return true
     * if the process terminated, otherwise return false.
     */
    private boolean shutdown() throws InterruptedException {
        mesg("shutdown()");
        long startTime = System.currentTimeMillis();
        ActivationSystem system = lookupSystem(port);
        if (system == null) {
            mesg("lookupSystem() returned null after " +
                (System.currentTimeMillis() - startTime) + "ms.");
            return false;
        }

        try {
            mesg("ActivationSystem.shutdown()");
            system.shutdown();
        } catch (Exception e) {
            mesg("Caught exception from ActivationSystem.shutdown():");
            e.printStackTrace();
        }

        try {
            waitFor(TIMEOUT_SHUTDOWN_MS);
            mesg("Shutdown successful after " +
                (System.currentTimeMillis() - startTime) + "ms.");
            return true;
        } catch (TimeoutException ex) {
            mesg("Shutdown timed out after " +
                (System.currentTimeMillis() - startTime) + "ms:");
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Ask rmid to shutdown gracefully but then destroy the rmid
     * process if it does not exit by itself.  This method only works
     * if rmid is a child process of the current VM.
     */
    public void destroy() {
        if (vm == null) {
            throw new IllegalStateException("can't wait for RMID that isn't running");
        }

        long startTime = System.currentTimeMillis();

        // First, attempt graceful shutdown of the activation system.
        try {
            if (! shutdown()) {
                // Graceful shutdown failed, use Process.destroy().
                mesg("Destroying RMID process.");
                vm.destroy();
                try {
                    waitFor(TIMEOUT_DESTROY_MS);
                    mesg("Destroy successful after " +
                        (System.currentTimeMillis() - startTime) + "ms.");
                } catch (TimeoutException ex) {
                    mesg("Destroy timed out, giving up after " +
                        (System.currentTimeMillis() - startTime) + "ms:");
                    ex.printStackTrace();
                }
            }
        } catch (InterruptedException ie) {
            mesg("Shutdown/destroy interrupted, giving up at " +
                (System.currentTimeMillis() - startTime) + "ms.");
            ie.printStackTrace();
            Thread.currentThread().interrupt();
            return;
        }

        vm = null;
    }

    /**
     * Shuts down rmid and then removes its log file.
     */
    public void cleanup() {
        destroy();
        RMID.removeLog();
    }

    /**
     * Gets the port on which this rmid is listening.
     */
    public int getPort() {
        return port;
    }
}
