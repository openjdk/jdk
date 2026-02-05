/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.SA;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jtreg.SkippedException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class SATestUtils {
    /**
     * Creates a ProcessBuilder, adding privileges (sudo) if needed.
     */
    public static ProcessBuilder createProcessBuilder(JDKToolLauncher launcher) {
        List<String> cmdStringList = Arrays.asList(launcher.getCommand());
        if (needsPrivileges()) {
            cmdStringList = addPrivileges(cmdStringList);
        }
        return new ProcessBuilder(cmdStringList);
    }

    /**
     * Checks if SA Attach is expected to work.
     * @throws SkippedException if SA Attach is not expected to work.
    */
    public static void skipIfCannotAttach() {
        if (!Platform.hasSA()) {
            throw new SkippedException("SA not supported.");
        }
        try {
            if (Platform.isLinux()) {
                if (!canPtraceAttachLinux()) {
                    throw new SkippedException("SA Attach not expected to work. Ptrace attach not supported.");
                }
            } else if (Platform.isOSX()) {
                if (Platform.isHardenedOSX()) {
                    throw new SkippedException("SA Attach not expected to work. JDK is hardened.");
                }
                if (needsPrivileges() && !canAddPrivileges()) {
                    throw new SkippedException("SA Attach not expected to work. Insufficient privileges " +
                                               "(developer mode disabled, not root, and can't use sudo).");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("skipIfCannotAttach() failed due to IOException.", e);
        }
    }

    /**
     * Returns true if this platform is expected to require extra privileges (running using sudo).
     * If we are running as root or developer mode is enabled, then sudo is not needed.
     */
    public static boolean needsPrivileges() {
        if (!Platform.isOSX()) {
            return false;
        }
        if (Platform.isRoot()) {
            return false;
        }
        return !developerModeEnabled();
    }

    /*
     * Run "DevToolsSecurity --status" to see if developer mode is enabled.
     */
    private static boolean developerModeEnabled() {
        List<String> cmd = new ArrayList<String>();
        cmd.add("DevToolsSecurity");
        cmd.add("--status");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p;
        try {
            p = pb.start();
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException("DevToolsSecurity process interrupted", e);
            }

            String out = new String(p.getInputStream().readAllBytes());
            String err = new String(p.getErrorStream().readAllBytes());
            System.out.print("DevToolsSecurity stdout: " + out);
            if (out.equals("")) System.out.println();
            System.out.print("DevToolsSecurity stderr: " + err);
            if (err.equals("")) System.out.println();

            if (out.contains("Developer mode is currently enabled")) {
                return true;
            }
            if (out.contains("Developer mode is currently disabled")) {
                return false;
            }
            throw new RuntimeException("DevToolsSecurity failed to generate expected output: " + out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
  }

    /**
     * Returns true if a no-password sudo is expected to work properly.
     */
    private static boolean canAddPrivileges()  throws IOException {
       List<String> sudoList = new ArrayList<String>();
       sudoList.add("sudo");
       sudoList.add("-E"); // Preserve existing environment variables.
       sudoList.add("-n"); // non-interactive. Don't prompt for password. Must be cached or not required.
       sudoList.add("/bin/echo");
       sudoList.add("'Checking for sudo'");
       ProcessBuilder pb = new ProcessBuilder(sudoList);
       Process echoProcess = pb.start();
       try {
           if (echoProcess.waitFor(60, TimeUnit.SECONDS) == false) {
               // Due to using the "-n" option, sudo should complete almost immediately. 60 seconds
               // is more than generous. If it didn't complete in that time, something went very wrong.
               echoProcess.destroyForcibly();
               throw new RuntimeException("Timed out waiting for sudo to execute.");
           }
       } catch (InterruptedException e) {
           throw new RuntimeException("sudo process interrupted", e);
       }

       if (echoProcess.exitValue() == 0) {
           return true;
       }
       java.io.InputStream is = echoProcess.getErrorStream();
       String err = new String(is.readAllBytes());
       System.out.println(err);
       // 'sudo' has been run, but did not succeed, probably because the cached credentials
       //  have expired, or we don't have a no-password entry for the user in the /etc/sudoers list.
       // Check the sudo error message and skip the test.
       if (err.contains("no tty present") || err.contains("a password is required")) {
           return false;
       } else {
           throw new RuntimeException("Unknown error from 'sudo': " + err);
       }
    }

    /**
     * Adds privileges (sudo) to the command.
     */
    private static List<String> addPrivileges(List<String> cmdStringList) {
        if (!Platform.isOSX()) {
            throw new RuntimeException("Can only add privileges on OSX.");
        }

        System.out.println("Adding 'sudo -E -n' to the command.");
        List<String> outStringList = new ArrayList<String>();
        outStringList.add("sudo");
        outStringList.add("-E"); // Preserve existing environment variables.
        outStringList.add("-n"); // non-interactive. Don't prompt for password. Must be cached or not required.
        outStringList.addAll(cmdStringList);
        return outStringList;
    }

    /**
     * Adds privileges (sudo) to the command already setup for the ProcessBuilder.
     */
    public static void addPrivilegesIfNeeded(ProcessBuilder pb) {
        if (!Platform.isOSX()) {
            return;
        }

        if (needsPrivileges()) {
            List<String> cmdStringList = pb.command();
            cmdStringList = addPrivileges(cmdStringList);
            pb.command(cmdStringList);
        }
    }

    /**
     * On Linux, first check the SELinux boolean "deny_ptrace" and return false
     * as we expect to be denied if that is "1".  Then expect permission to attach
     * if we are root, so return true.  Then return false for an expected denial
     * if "ptrace_scope" is 1, and true otherwise.
     */
    private static boolean canPtraceAttachLinux() throws IOException {
        // SELinux deny_ptrace:
        var deny_ptrace = Paths.get("/sys/fs/selinux/booleans/deny_ptrace");
        if (Files.exists(deny_ptrace)) {
            var bb = Files.readAllBytes(deny_ptrace);
            if (bb.length == 0) {
                throw new Error("deny_ptrace is empty");
            }
            if (bb[0] != '0') {
                return false;
            }
        }

        // YAMA enhanced security ptrace_scope:
        // 0 - a process can PTRACE_ATTACH to any other process running under the same uid
        // 1 - restricted ptrace: a process must be a children of the inferior or user is root
        // 2 - only processes with CAP_SYS_PTRACE may use ptrace or user is root
        // 3 - no attach: no processes may use ptrace with PTRACE_ATTACH
        var ptrace_scope = Paths.get("/proc/sys/kernel/yama/ptrace_scope");
        if (Files.exists(ptrace_scope)) {
            var bb = Files.readAllBytes(ptrace_scope);
            if (bb.length == 0) {
                throw new Error("ptrace_scope is empty");
            }
            byte yama_scope = bb[0];
            if (yama_scope == '3') {
                return false;
            }

            if (!Platform.isRoot() && yama_scope != '0') {
                return false;
            }
        }
        // Otherwise expect to be permitted:
        return true;
    }

    /**
     * This tests has issues if you try adding privileges on OSX. The debugd process cannot
     * be killed if you do this (because it is a root process and the test is not), so the destroy()
     * call fails to do anything, and then waitFor() will time out. If you try to manually kill it with
     * a "sudo kill" command, that seems to work, but then leaves the LingeredApp it was
     * attached to in a stuck state for some unknown reason, causing the stopApp() call
     * to timeout. For that reason we don't run this test when privileges are needed. Note
     * it does appear to run fine as root, so we still allow it to run on OSX when privileges
     * are not required.
     *
     * Note that we also can't run these tests even if OSX developer mode is enabled (see
     * developerModeEnabled()). For the most part the test runs fine, but some reason you end up
     * needing to provide admin credentials as the test shuts down. If you don't, it just hangs forever.
     * And even after providing the credetials, the test still fails with a timeout.
     *
     * JDK-8314133 has been filed for these issues.
     */
    public static void validateSADebugDPrivileges() {
        if (Platform.isOSX() && !Platform.isRoot()) {
            throw new SkippedException("Cannot run this test on OSX if adding privileges is required.");
        }
    }

    /**
     * Find library file that provides strlen(3), then returns it as libc.
     * This method works on Linux only.
     * @return path to libc
     */
    @SuppressWarnings("restricted")
    public static String getLibCPath() {
        var linker = Linker.nativeLinker();
        var ptrStrlen = linker.defaultLookup()
                              .findOrThrow("strlen");
        var strlen = linker.downcallHandle(
            ptrStrlen,
            FunctionDescriptor.of(linker.canonicalLayouts().get("size_t"), ValueLayout.ADDRESS)
        );
        var dladdr = linker.downcallHandle(
            linker.defaultLookup().findOrThrow("dladdr"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        var structDLInfo = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("dli_fname"),
            ValueLayout.ADDRESS.withName("dli_fbase"),
            ValueLayout.ADDRESS.withName("dli_sname"),
            ValueLayout.ADDRESS.withName("dli_saddr")
        ).withName("Dl_info");
        var hndDliFname = structDLInfo.varHandle(MemoryLayout.PathElement.groupElement("dli_fname"));

        try(var arena = Arena.ofConfined()){
            var info = arena.allocate(structDLInfo);
            int result = (int)dladdr.invoke(ptrStrlen, info);
            if (result == 0) {
                throw new RuntimeException("dladdr() returns zero");
            }

            var ptrDliFname = (MemorySegment)hndDliFname.get(info, 0);
            var libcPathLen = (long)strlen.invoke(ptrDliFname);
            return ptrDliFname.reinterpret(libcPathLen + 1) // +1 for NUL
                              .getString(0);
        } catch (Throwable t) {
            throw new RuntimeException("getLibCPath() failed due to Throwable.", t);
        }
    }

    /**
     * Find debuginfo file for the library.
     * This method will work on Linux only.
     * "readelf" has to be available.
     * @return null if debuginfo is not available.
     */
    public static String getDebugInfo(String lib) {
        try {
            // Attempt to find debuginfo in /usr/lib/debug
            Path debuginfoPath = Path.of("/usr/lib/debug", lib + ".debug");
            boolean exists = Files.exists(debuginfoPath);
            if (!exists) {
                // Attempt to find debuginfo with build ID
                var proc = (new ProcessBuilder("readelf", "-n", lib)).start();
                try (var reader = proc.inputReader()) {
                    var buildID =  reader.lines()
                                         .filter(l -> l.contains("Build ID:"))
                                         .findAny()
                                         .map(l -> l.replace("Build ID:", "").trim())
                                         .get();
                    String dir = buildID.substring(0, 2);
                    String file = buildID.substring(2);
                    debuginfoPath = Path.of("/usr/lib/debug/.build_id", dir, file + ".debug");
                    exists = Files.exists(debuginfoPath);
                }
            }
            return exists ? debuginfoPath.toString() : null;
        } catch (IOException e) {
            throw new RuntimeException("getDebugInfo() failed due to IOException.", e);
        }
    }

    private static boolean isSymbolAvailableInternal(String lib, String symbol) throws IOException {
        var proc = (new ProcessBuilder("nm", lib)).start();
        try (var reader = proc.inputReader()) {
            return reader.lines()
                         .anyMatch(l -> l.endsWith(" " + symbol));
        }
    }

    /**
     * This method will work on Linux only.
     * Both "readelf" and "nm" have to be available.
     * @return true if given symbol is available in given lib.
     */
    public static boolean isSymbolAvailable(String lib, String symbol) {
        try {
            // Attempt to find symbol from lib
            boolean result = isSymbolAvailableInternal(lib, symbol);
            if (!result) {
                // Attempt to find symbol from debuginfo
                String debuginfoPath = getDebugInfo(lib);
                if (debuginfoPath != null) {
                    result = isSymbolAvailableInternal(debuginfoPath, symbol);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("isSymbolAvailable() failed due to IOException.", e);
        }
    }
}
