/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;

/**
 * Utility class that creates an instance of rmid with a policy
 * file of name <code>TestParams.defaultPolicy</code>.
 *
 * Activation groups should run with the same security manager as the
 * test.
 */
public class RMID extends JavaVM {

    public static String MANAGER_OPTION="-Djava.security.manager=";

    /** Test port for rmid */
    private final int port;

    /** Initial log name */
    protected static String log = "log";
    /** rmid's logfile directory; currently must be "." */
    protected static String LOGDIR = ".";

    private static void mesg(Object mesg) {
        System.err.println("RMID: " + mesg.toString());
    }

    /** make test options and arguments */
    private static String makeOptions(boolean debugExec) {

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

        return options;
    }

    private static String makeArgs(boolean includePortArg, int port) {
        String propagateManager = null;

        // rmid will run with a security manager set, but no policy
        // file - it should not need one.
        if (System.getSecurityManager() == null) {
            propagateManager = MANAGER_OPTION +
                TestParams.defaultSecurityManager;
        } else {
            propagateManager = MANAGER_OPTION +
                System.getSecurityManager().getClass().getName();
        }

        // getAbsolutePath requires permission to read user.dir
        String args =
            " -log " + (new File(LOGDIR, log)).getAbsolutePath();

        if (includePortArg) {
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
        args += " " + getCodeCoverageArgs();
        return args;
    }

    /**
     * Routine that creates an rmid that will run with or without a
     * policy file.
     */
    public static RMID createRMID() {
        return createRMID(System.out, System.err, true);
    }

    public static RMID createRMID(boolean debugExec) {
        return createRMID(System.out, System.err, debugExec);
    }

    public static RMID createRMID(OutputStream out, OutputStream err) {
        return createRMID(out, err, true);
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
        String options = makeOptions(debugExec);
        String args = makeArgs(includePortArg, port);
        RMID rmid = new RMID("sun.rmi.server.Activation", options, args,
                             out, err, port);
        rmid.setPolicyFile(TestParams.defaultRmidPolicy);

        return rmid;
    }


    /**
     * Test RMID should be created with the createRMID method.
     */
    protected RMID(String classname, String options, String args,
                   OutputStream out, OutputStream err, int port)
    {
        super(classname, options, args, out, err);
        this.port = port;
    }

    public static void removeLog() {
        /*
         * Remove previous log file directory before
         * starting up rmid.
         */
        File f = new File(LOGDIR, log);

        if (f.exists()) {
            mesg("removing rmid's old log file...");
            String[] files = f.list();

            if (files != null) {
                for (int i=0; i<files.length; i++) {
                    (new File(f, files[i])).delete();
                }
            }

            if (f.delete() != true) {
                mesg("\t" + " unable to delete old log file.");
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

    public void start() throws IOException {
        start(10000);
    }

    public void slowStart() throws IOException {
        start(60000);
    }

    public void start(long waitTime) throws IOException {

        if (getVM() != null) return;

        // if rmid is already running, then the test will fail with
        // a well recognized exception (port already in use...).

        mesg("starting rmid on port #" + port + "...");
        super.start();

        int slopFactor = 1;
        try {
            slopFactor = Integer.valueOf(
                TestLibrary.getExtraProperty("jcov.sleep.multiplier","1"));
        } catch (NumberFormatException ignore) {}
        waitTime = waitTime * slopFactor;

        // We check several times (as many as provides passed waitTime) to
        // see if Rmid is currently running. Waiting steps last 100 msecs.
        final long rmidStartSleepTime = 100;
        do {
            // Sleeping for another rmidStartSleepTime time slice.
            try {
                Thread.sleep(Math.min(waitTime, rmidStartSleepTime));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                mesg("Thread interrupted while checking for start of Activation System. Giving up check.");
                mesg("Activation System state unknown");
                return;
            }
            waitTime -= rmidStartSleepTime;

            // Checking if rmid is present
            if (ActivationLibrary.rmidRunning(port)) {
                /**
                 * We need to set the java.rmi.activation.port value as the
                 * activation system will use the property to determine the
                 * port #.  The activation system will use this value if set.
                 * If it isn't set, the activation system will set it to an
                 * incorrect value.
                 */
                System.setProperty("java.rmi.activation.port", Integer.toString(port));
                mesg("finished starting rmid.");
                return;
            }
            else {
                mesg("rmid still not started");
            }

        } while (waitTime > 0);
        TestLibrary.bomb("start rmid failed... giving up", null);
    }

    public void restart() throws IOException {
        destroy();
        start();
    }

    /**
     * Ask rmid to shutdown gracefully using a remote method call.
     * catch any errors that might occur from rmid not being present
     * at time of shutdown invocation.
     *
     * Shutdown does not nullify possible references to the rmid
     * process object (destroy does though).
     */
    public static void shutdown(int port) {

        try {
            ActivationSystem system = null;

            try {
                mesg("getting a reference to the activation system");
                system = (ActivationSystem) Naming.lookup("//:" +
                    port +
                    "/java.rmi.activation.ActivationSystem");
                mesg("obtained a reference to the activation system");
            } catch (RemoteException re) {
                mesg("could not contact registry while trying to shutdown activation system");
            } catch (java.net.MalformedURLException mue) {
            }

            if (system == null) {
                TestLibrary.bomb("reference to the activation system was null");
            }
            system.shutdown();

        } catch (RemoteException re) {
            mesg("shutting down the activation daemon failed");
        } catch (Exception e) {
            mesg("caught exception trying to shutdown rmid");
            mesg(e.getMessage());
            e.printStackTrace();
        }

        mesg("testlibrary finished shutting down rmid");
    }

    /**
     * Ask rmid to shutdown gracefully but then destroy the rmid
     * process if it does not exit by itself.  This method only works
     * if rmid is a child process of the current VM.
     */
    public void destroy() {
        // attempt graceful shutdown of the activation system
        shutdown(port);

        if (vm != null) {
            try {
                /* Waiting for distant RMID process to shutdown.
                 * Waiting is bounded at a hardcoded max of 60 secs (1 min).
                 * Waiting by steps of 200 msecs, thus at most 300 such attempts
                 * for termination of distant RMID process. If process is not
                 * known to be terminated properly after that time,
                 * we give up for a gracefull termination, and thus go for
                 * forcibly destroying the process.
                 */
                boolean vmEnded = false;
                int waitingTrials = 0;
                final int maxTrials = 300;
                final long vmProcessEndWaitInterval = 200;
                int vmExitValue;
                do {
                    try {
                        Thread.sleep(vmProcessEndWaitInterval);
                        waitingTrials++;
                        vmExitValue = vm.exitValue();
                        mesg("rmid exited on shutdown request");
                        vmEnded = true;
                    } catch (IllegalThreadStateException illegal) {
                        mesg("RMID's process still not terminated after more than " +
                             (waitingTrials * vmProcessEndWaitInterval) + " milliseconds");
                    }
                }
                while (!vmEnded &&
                       (waitingTrials < maxTrials));

                if (waitingTrials >= maxTrials) {
                    mesg("RMID's process still not terminated after more than " +
                         (waitingTrials * vmProcessEndWaitInterval) + " milliseconds." +
                         "Givinp up gracefull termination...");
                    mesg("destroying RMID's process using Process.destroy()");
                    super.destroy();
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                mesg("Thread interrupted while checking for termination of distant rmid vm. Giving up check.");
            } catch (Exception e) {
                mesg("caught unexpected exception trying to destroy rmid: " +
                     e.getMessage());
                e.printStackTrace();
            }

            // rmid will not restart if its process is not null
            vm = null;
        }
    }

    public int getPort() {return port;}
}
