/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This is a framework to launch an app that could be synchronized with caller
 * to make further attach actions reliable across supported platforms

 * Caller example:
 *   SmartTestApp a = SmartTestApp.startApp(cmd);
 *     // do something
 *   a.stopApp();
 *
 *   or fine grained control
 *
 *   a = new SmartTestApp("MyLock.lck");
 *   a.createLock();
 *   a.runApp();
 *   a.waitAppReady();
 *     // do something
 *   a.deleteLock();
 *   a.waitAppTerminate();
 *
 *  Then you can work with app output and process object
 *
 *   output = a.getAppOutput();
 *   process = a.getProcess();
 *
 */
public class LingeredApp {

    private static final long spinDelay = 1000;

    private long lockCreationTime;
    private final ArrayList<String> storedAppOutput;

    protected Process appProcess;
    protected static final int appWaitTime = 100;
    protected final String lockFileName;

    /*
     * Drain child process output, store it into string array
     */
    class InputGobbler extends Thread {

        InputStream is;
        List<String> astr;

        InputGobbler(InputStream is, List<String> astr) {
            this.is = is;
            this.astr = astr;
        }

        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    astr.add(line);
                }
            } catch (IOException ex) {
                // pass
            }
        }
    }

    /**
     * Create LingeredApp object on caller side. Lock file have be a valid filename
     * at writable location
     *
     * @param lockFileName - the name of lock file
     */
    public LingeredApp(String lockFileName) {
        this.lockFileName = lockFileName;
        this.storedAppOutput = new ArrayList<String>();
    }

    public LingeredApp() {
        final String lockName = UUID.randomUUID().toString() + ".lck";
        this.lockFileName = lockName;
        this.storedAppOutput = new ArrayList<String>();
    }

    /**
     *
     * @return name of lock file
     */
    public String getLockFileName() {
        return this.lockFileName;
    }

    /**
     *
     * @return name of testapp
     */
    public String getAppName() {
        return this.getClass().getName();
    }

    /**
     *
     *  @return pid of java process running testapp
     */
    public long getPid() {
        if (appProcess == null) {
            throw new RuntimeException("Process is not alive");
        }
        return appProcess.pid();
    }

    /**
     *
     * @return process object
     */
    public Process getProcess() {
        return appProcess;
    }

    /**
     *
     * @return application output as string array. Empty array if application produced no output
     */
    public List<String> getAppOutput() {
        if (appProcess.isAlive()) {
            throw new RuntimeException("Process is still alive. Can't get its output.");
        }
        return storedAppOutput;
    }

    /* Make sure all part of the app use the same method to get dates,
     as different methods could produce different results
     */
    private static long epoch() {
        return new Date().getTime();
    }

    private static long lastModified(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        return attr.lastModifiedTime().toMillis();
    }

    private static void setLastModified(String fileName, long newTime) throws IOException {
        Path path = Paths.get(fileName);
        FileTime fileTime = FileTime.fromMillis(newTime);
        Files.setLastModifiedTime(path, fileTime);
    }

    /**
     * create lock
     *
     * @throws IOException
     */
    public void createLock() throws IOException {
        Path path = Paths.get(lockFileName);
        // Files.deleteIfExists(path);
        Files.createFile(path);
        lockCreationTime = lastModified(lockFileName);
    }

    /**
     * Delete lock
     *
     * @throws IOException
     */
    public void deleteLock() throws IOException {
        try {
            Path path = Paths.get(lockFileName);
            Files.delete(path);
        } catch (NoSuchFileException ex) {
            // Lock already deleted. Ignore error
        }
    }

    public void waitAppTerminate() {
        while (true) {
            try {
                appProcess.waitFor();
                break;
            } catch (InterruptedException ex) {
                // pass
            }
        }
    }

    /**
     * The app touches the lock file when it's started
     * wait while it happens. Caller have to delete lock on wait error.
     *
     * @param timeout
     * @throws java.io.IOException
     */
    public void waitAppReady(long timeout) throws IOException {
        long here = epoch();
        while (true) {
            long epoch = epoch();
            if (epoch - here > (timeout * 1000)) {
                throw new IOException("App waiting timeout");
            }

            // Live process should touch lock file every second
            long lm = lastModified(lockFileName);
            if (lm > lockCreationTime) {
                break;
            }

            // Make sure process didn't already exit
            if (!appProcess.isAlive()) {
                throw new IOException("App exited unexpectedly with " + appProcess.exitValue());
            }

            try {
                Thread.sleep(spinDelay);
            } catch (InterruptedException ex) {
                // pass
            }
        }
    }

    /**
     * Analyze an environment and prepare a command line to
     * run the app, app name should be added explicitly
     */
    public List<String> runAppPrepare(List<String> vmArguments) {
        // We should always use testjava or throw an exception,
        // so we can't use JDKToolFinder.getJDKTool("java");
        // that falls back to compile java on error
        String jdkPath = System.getProperty("test.jdk");
        if (jdkPath == null) {
            // we are not under jtreg, try env
            Map<String, String> env = System.getenv();
            jdkPath = env.get("TESTJAVA");
        }

        if (jdkPath == null) {
            throw new RuntimeException("Can't determine jdk path neither test.jdk property no TESTJAVA env are set");
        }

        String osname = System.getProperty("os.name");
        String javapath = jdkPath + ((osname.startsWith("window")) ? "/bin/java.exe" : "/bin/java");

        List<String> cmd = new ArrayList<String>();
        cmd.add(javapath);


        if (vmArguments == null) {
            // Propagate test.vm.options to LingeredApp, filter out possible empty options
            String testVmOpts[] = System.getProperty("test.vm.opts","").split("\\s+");
            for (String s : testVmOpts) {
                if (!s.equals("")) {
                    cmd.add(s);
                }
            }
        }
        else{
            // Lets user manage LingeredApp options
            cmd.addAll(vmArguments);
        }

        // Make sure we set correct classpath to run the app
        cmd.add("-cp");
        String classpath = System.getProperty("test.class.path");
        cmd.add((classpath == null) ? "." : classpath);

        return cmd;
    }

    /**
     * Assemble command line to a printable string
     */
    public void printCommandLine(List<String> cmd) {
        // A bit of verbosity
        StringBuilder cmdLine = new StringBuilder();
        for (String strCmd : cmd) {
            cmdLine.append("'").append(strCmd).append("' ");
        }

        System.out.println("Command line: [" + cmdLine.toString() + "]");
    }

    public void startGobblerPipe() {
      // Create pipe reader for process, and read stdin and stderr to array of strings
      InputGobbler gb = new InputGobbler(appProcess.getInputStream(), storedAppOutput);
      gb.start();
    }

    /**
     * Run the app.
     *
     * @param vmArguments
     * @throws IOException
     */
    public void runApp(List<String> vmArguments)
            throws IOException {

        List<String> cmd = runAppPrepare(vmArguments);

        cmd.add(this.getAppName());
        cmd.add(lockFileName);

        printCommandLine(cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // we don't expect any error output but make sure we are not stuck on pipe
        // pb.redirectErrorStream(false);
        // ProcessBuilder.start can throw IOException
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        appProcess = pb.start();

        startGobblerPipe();
    }

    /**
     * Delete lock file that signals app to terminate, then
     * wait until app is actually terminated.
     * @throws IOException
     */
    public void stopApp() throws IOException {
        deleteLock();
        // The startApp() of the derived app can throw
        // an exception before the LA actually starts
        if (appProcess != null) {
            waitAppTerminate();
            int exitcode = appProcess.exitValue();
            if (exitcode != 0) {
                throw new IOException("LingeredApp terminated with non-zero exit code " + exitcode);
            }
        }
    }

    /**
     *  High level interface for test writers
     */
    /**
     * Factory method that creates LingeredApp object with ready to use application
     * lock name is autogenerated
     * @param cmd - vm options, could be null to auto add testvm.options
     * @return LingeredApp object
     * @throws IOException
     */
    public static LingeredApp startApp(List<String> cmd) throws IOException {
        LingeredApp a = new LingeredApp();
        a.createLock();
        try {
            a.runApp(cmd);
            a.waitAppReady(appWaitTime);
        } catch (Exception ex) {
            a.deleteLock();
            throw ex;
        }

        return a;
    }

    /**
     * Factory method that starts pre-created LingeredApp
     * lock name is autogenerated
     * @param cmd - vm options, could be null to auto add testvm.options
     * @param theApp - app to start
     * @return LingeredApp object
     * @throws IOException
     */

    public static void startApp(List<String> cmd, LingeredApp theApp) throws IOException {
        theApp.createLock();
        try {
            theApp.runApp(cmd);
            theApp.waitAppReady(appWaitTime);
        } catch (Exception ex) {
            theApp.deleteLock();
            throw ex;
        }
    }

    public static LingeredApp startApp() throws IOException {
        return startApp(null);
    }

    public static void stopApp(LingeredApp app) throws IOException {
        if (app != null) {
            // LingeredApp can throw an exception during the intialization,
            // make sure we don't have cascade NPE
            app.stopApp();
        }
    }

    /**
     * LastModified time might not work correctly in some cases it might
     * cause later failures
     */

    public static boolean isLastModifiedWorking() {
        boolean sane = true;
        try {
            long lm = lastModified(".");
            if (lm == 0) {
                System.err.println("SANITY Warning! The lastModifiedTime() doesn't work on this system, it returns 0");
                sane = false;
            }

            long now = epoch();
            if (lm > now) {
                System.err.println("SANITY Warning! The Clock is wrong on this system lastModifiedTime() > getTime()");
                sane = false;
            }

            setLastModified(".", epoch());
            long lm1 = lastModified(".");
            if (lm1 <= lm) {
                System.err.println("SANITY Warning! The setLastModified doesn't work on this system");
                sane = false;
            }
        }
        catch(IOException e) {
            System.err.println("SANITY Warning! IOException during sanity check " + e);
            sane = false;
        }

        return sane;
    }

    /**
     * This part is the application it self
     */
    public static void main(String args[]) {

        if (args.length != 1) {
            System.err.println("Lock file name is not specified");
            System.exit(7);
        }

        String theLockFileName = args[0];

        try {
            Path path = Paths.get(theLockFileName);

            while (Files.exists(path)) {
                // Touch the lock to indicate our readiness
                setLastModified(theLockFileName, epoch());
                Thread.sleep(spinDelay);
            }
        } catch (NoSuchFileException ex) {
            // Lock deleted while we are setting last modified time.
            // Ignore error and lets the app exits
        } catch (Exception ex) {
            System.err.println("LingeredApp ERROR: " + ex);
            // Leave exit_code = 1 to Java launcher
            System.exit(3);
        }

        System.exit(0);
    }
}
