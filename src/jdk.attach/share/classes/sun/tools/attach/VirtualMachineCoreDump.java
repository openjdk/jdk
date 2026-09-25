/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.AttachOperationFailedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.concurrent.*;

import jdk.internal.util.OperatingSystem;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Common class for implementation of VirtualMachine for a core dump or MiniDump.
 */
@SuppressWarnings("restricted")
public abstract class VirtualMachineCoreDump extends VirtualMachine {

    protected boolean attached;
    protected String libDirs;
    protected String revivalCachePath;

    /**
     * Attaches to a core file or minidump.
     */
    VirtualMachineCoreDump(AttachProvider provider, String vmid, Map<String, ?> env)
            throws AttachNotSupportedException, IllegalArgumentException, IOException {

        super(provider, vmid);

        if (env != null) {
            libDirs = (String) env.get("libDirs"); // May be a list using File.pathSeparator
            revivalCachePath = (String) env.get("revivalCachePath");
        }
        attach();
    }

    protected void attach() throws AttachNotSupportedException, IllegalArgumentException, IOException {
        if (!new File(id()).exists()) {
            throw new IOException("No such file '" + id() + "'");
        }
        checkCoreFile(id());
        if (revivalCachePath != null) {
            File f = new File(revivalCachePath);
            if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                throw new IllegalArgumentException("Bad path for revival cache data");
            }
        }
        attached = true;
    }

    /**
     * Platform-specific check whether the file is of the correct type.
     */
    protected abstract void checkCoreFile(String filename) throws AttachNotSupportedException, IOException;

    /**
     * Name of the helper app to run.
     */
    protected abstract String helperName(String jdkLibDir);

    /**
     * Add any platform-specific settings in the environment for the helper process.
     */
    protected abstract void setEnv(Map<String, String> env, String jdkLibDir);

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
        attached = false;
    }

    private void checkAttached() throws IOException {
        if (!attached) {
            throw new AttachOperationFailedException("Not attached");
        }
    }

    private static final int HELPER_TRIES = 100; // Default attempts to run helper
    private static final int HELPER_RETRY = 7;   // revivalhelper exit value hint to retry due to e.g. address space clash


/*    public InputStream executeCommand(String cmd, Object ... args) throws IOException {
        return execute(cmd, args);
    } */

    public InputStream executeJCmd(String command) throws IOException {
        return execute("jcmd", command);
    }

    /**
     * Execute the given command in the target VM.
     */
    @SuppressWarnings("deprecation")
    InputStream execute(String cmd, Object ... args) throws IOException {
        checkAttached();
        // Only the 'jcmd' operation is implemented on a core/minidump.
        if (!cmd.equals("jcmd")) {
            throw new AttachOperationFailedException("Command '" + cmd + "' not implemented");
        }
        String jdkLibDir = Path.of(System.getProperty("java.home"), "lib").toString();

        // Invoke "JDK/lib/revivalhelper corefilename jcmd command..."
        String helper = helperName(jdkLibDir);
        if (!(new File(helper).exists())) {
            throw new AttachOperationFailedException("jcmd helper '" + helper + "' not found");
        }
        List<String> pargs = new ArrayList<String>();
        pargs.add(helper);

        if (libDirs != null) {
            pargs.add("-L" + libDirs); // Pass library directory list as -Lpath
        }
        if (revivalCachePath != null) {
            pargs.add("-R" + revivalCachePath); // Set alternate cache location with -Rpath
        }

        pargs.add(id());
        pargs.add(cmd);
        for (Object o : args) {
            pargs.add((String) o);
        }

        ProcessBuilder pb = new ProcessBuilder(pargs);
        pb.redirectErrorStream(true); // merge error with output

        // Some System Properties are passed on to the native revival helper tool in the environment:
        Map<String, String> newEnv = pb.environment();
        String logString = System.getProperty("jdk.attach.core.log");
        boolean verbose = false;
        if (logString != null) {
            logString = logString.toLowerCase();
            newEnv.put("REVIVAL_LOG", logString); // Logging in native helper
            verbose = logString.equals("verbose") || logString.equals("debug"); // Logging in this method
        }

        if (Boolean.getBoolean("jdk.attach.core.skipVersionCheck")) {
            newEnv.put("REVIVAL_SKIPVERSIONCHECK", "1");
        }

        setEnv(newEnv, jdkLibDir); // Add any platform-specific settings

        // Run the helper. This may fail, e.g. address clash, which Address Space Layout Randomization both causes and fixes.
        // Recognise such a clash from the process return value, and retry.
        int maxTries = Integer.getInteger("jdk.attach.core.tries", HELPER_TRIES);
        String out = null;
        boolean ok = false;
        int i;
        for (i = 1; i < maxTries; i++) {
            Process p = pb.start();
            long pid = p.pid();
            if (verbose) System.err.println("Run revivalhelper: " + i + "  pid = " + pid);

            try {
                ExecutorService executor = Executors.newFixedThreadPool(2);
                Future<String> stdout = executor.submit(() -> drain(p.getInputStream()));
                int e = p.waitFor();
                out = stdout.get(10, TimeUnit.SECONDS);

                if (e == HELPER_RETRY) {
                    if (verbose) {
                        System.out.println(out);
                        System.out.println("(Retrying process revival.)");
                    }
                    continue; // ...and retry.
                } else if (e != 0) {
                    // e=1: Actual error from JCmd, e.g. Exception thrown by command implementation.
                    // Other non-zero values possible for other failures.
                    System.out.println(out);
                    if (i > 1 || verbose) {
                        System.err.println("jcmd via revival: failed, exit with: " + e + ". tries=" + i);
                    }
                    throw new AttachOperationFailedException("jcmd returned an error");  // JCmd caller will call System.exit(1)
                } else {
                    // Success.
                    ok = true;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                System.err.println("VirtualMachineCoreDump.execute: " + ex);
                if (verbose) {
                    ex.printStackTrace();
                }
            }
            break; // No retry except for explicit continue above.
        }
        if (i > 1 || verbose) {
            System.err.println("jcmd via revival " + (ok ? "OK" : "failed") + ": tries=" + i);
        }
        return new StringBufferInputStream(out);
    }

    private static String drain(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    @Override
    public void startManagementAgent(Properties agentProperties) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public String startLocalManagementAgent() throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public Properties getAgentProperties() throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public Properties getSystemProperties() throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgentLibrary(String agentLibrary, String options) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgentLibrary(String agentLibrary) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgentPath(String path) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgentPath(String path, String p2) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgent(String agentLibrary, String options) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }

    @Override
    public void loadAgent(String agentLibrary) throws IOException {
        throw new AttachOperationFailedException("not implemented");
    }
}
