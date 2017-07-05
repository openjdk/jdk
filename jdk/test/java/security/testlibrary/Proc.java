/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This is a test library that makes writing a Java test that spawns multiple
 * Java processes easily.
 *
 * Usage:
 *
 *    Proc.create("Clazz")      // The class to launch
 *        .args("x")            // with args
 *        .env("env", "value")  // and an environment variable
 *        .prop("key","value")  // and a system property
 *        .perm(perm)           // with granted permissions
 *        .start();             // and start
 *
 * create/start must be called, args/env/prop/perm can be called zero or
 * multiple times between create and start.
 *
 * The controller can call inheritIO to share its I/O to the process.
 * Otherwise, it can send data into a proc's stdin with write/println, and
 * read its stdout with readLine. stderr is always redirected to DFILE
 * unless nodump() is called. A protocol is designed to make
 * data exchange among the controller and the processes super easy, in which
 * useful data are always printed with a special prefix ("PROCISFUN:").
 * If the data is binary, make it BASE64.
 *
 * For example:
 *
 * - A producer Proc calls Proc.binOut() or Proc.textOut() to send out data.
 *   This method would prints to the stdout something like
 *
 *      PROCISFUN:[raw text or base64 binary]
 *
 * - The controller calls producer.readData() to get the content. This method
 *   ignores all other output and only reads lines starting with "PROCISFUN:".
 *
 * - The controller does not care if the context is text or base64, it simply
 *   feeds the data to a consumer Proc by calling consumer.println(data).
 *   This will be printed into System.in of the consumer process.
 *
 * - The consumer Proc calls Proc.binIn() or Proc.textIn() to read the data.
 *   The first method de-base64 the input and return a byte[] block.
 *
 * Please note only plain ASCII is supported in raw text at the moment.
 *
 * As the Proc objects are hidden so deeply, two static methods, d(String) and
 * d(Throwable) are provided to output info into stderr, where they will
 * normally be appended messages to DFILE (unless nodump() is called).
 * Developers can view the messages in real time by calling
 *
 *    tail -f proc.debug
 *
 * TODO:
 *
 * . launch java tools, say, keytool
 * . launch another version of java
 * . start in another directory
 * . start and finish using one method
 *
 * This is not a test, but is the core of
 * JDK-8009977: A test library to launch multiple Java processes
 */
public class Proc {
    private Process p;
    private BufferedReader br;      // the stdout of a process
    private String launcher;        // Optional: the java program

    private List<Permission> perms = new ArrayList<>();
    private List<String> args = new ArrayList<>();
    private Map<String,String> env = new HashMap<>();
    private Map<String,String> prop = new HashMap();
    private boolean inheritIO = false;
    private boolean noDump = false;

    private String clazz;           // Class to launch
    private String debug;           // debug flag, controller will show data
                                    // transfer between procs

    final private static String PREFIX = "PROCISFUN:";
    final private static String DFILE = "proc.debug";

    // The following methods are called by controllers

    // Creates a Proc by the Java class name, launcher is an optional
    // argument to specify the java program
    public static Proc create(String clazz, String... launcher) {
        Proc pc = new Proc();
        pc.clazz = clazz;
        if (launcher.length > 0) {
            pc.launcher = launcher[0];
        }
        return pc;
    }
    // Sets inheritIO flag to proc. If set, proc will same I/O channels as
    // teh controller. Otherwise, its stdin/stdout is untouched, and its
    // stderr is redirected to DFILE.
    public Proc inheritIO() {
        inheritIO = true;
        return this;
    }
    // When called, stderr inherits parent stderr, otherwise, append to a file
    public Proc nodump() {
        noDump = true;
        return this;
    }
    // Specifies some args. Can be called multiple times.
    public Proc args(String... args) {
        for (String c: args) {
            this.args.add(c);
        }
        return this;
    }
    // Returns debug prefix
    public String debug() {
        return debug;
    }
    // Enables debug with prefix
    public Proc debug(String title) {
        debug = title;
        return this;
    }
    // Specifies an env var. Can be called multiple times.
    public Proc env(String a, String b) {
        env.put(a, b);
        return this;
    }
    // Specifies a Java system property. Can be called multiple times.
    public Proc prop(String a, String b) {
        prop.put(a, b);
        return this;
    }
    // Adds a perm to policy. Can be called multiple times. In order to make it
    // effective, please also call prop("java.security.manager", "").
    public Proc perm(Permission p) {
        perms.add(p);
        return this;
    }
    // Starts the proc
    public Proc start() throws IOException {
        List<String> cmd = new ArrayList<>();
        if (launcher != null) {
            cmd.add(launcher);
        } else {
            cmd.add(new File(new File(System.getProperty("java.home"), "bin"),
                        "java").getPath());
        }

        int n = 0;
        String addexports;
        while ((addexports = System.getProperty("jdk.launcher.addexports." + n)) != null) {
            prop("jdk.launcher.addexports." + n, addexports);
            n++;
        }

        Collections.addAll(cmd, splitProperty("test.vm.opts"));
        Collections.addAll(cmd, splitProperty("test.java.opts"));

        cmd.add("-cp");
        cmd.add(System.getProperty("test.class.path") + File.pathSeparator +
                System.getProperty("test.src.path"));

        for (Entry<String,String> e: prop.entrySet()) {
            cmd.add("-D" + e.getKey() + "=" + e.getValue());
        }
        if (!perms.isEmpty()) {
            Path p = Files.createTempFile(
                    Paths.get(".").toAbsolutePath(), "policy", null);
            StringBuilder sb = new StringBuilder();
            sb.append("grant {\n");
            for (Permission perm: perms) {
                // Sometimes a permission has no name or actions.
                // but it's safe to use an empty string.
                String s = String.format("%s \"%s\", \"%s\"",
                        perm.getClass().getCanonicalName(),
                        perm.getName()
                                .replace("\\", "\\\\").replace("\"", "\\\""),
                        perm.getActions());
                sb.append("    permission ").append(s).append(";\n");
            }
            sb.append("};\n");
            Files.write(p, sb.toString().getBytes());
            cmd.add("-Djava.security.policy=" + p.toString());
        }
        cmd.add(clazz);
        for (String s: args) {
            cmd.add(s);
        }
        if (debug != null) {
            System.out.println("PROC: " + debug + " cmdline: " + cmd);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        for (Entry<String,String> e: env.entrySet()) {
            pb.environment().put(e.getKey(), e.getValue());
        }
        if (inheritIO) {
            pb.inheritIO();
        } else if (noDump) {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(DFILE)));
        }
        p = pb.start();
        br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        return this;
    }
    // Reads a line from stdout of proc
    public String readLine() throws IOException {
        String s = br.readLine();
        if (debug != null) {
            System.out.println("PROC: " + debug + " readline: " +
                    (s == null ? "<EOF>" : s));
        }
        return s;
    }
    // Reads a special line from stdout of proc
    public String readData() throws Exception {
        while (true) {
            String s = readLine();
            if (s == null) {
                if (p.waitFor() != 0) {
                    throw new Exception("Proc abnormal end");
                } else {
                    return s;
                }
            }
            if (s.startsWith(PREFIX)) {
                return s.substring(PREFIX.length());
            }
        }
    }
    // Writes text into stdin of proc
    public void println(String s) throws IOException {
        if (debug != null) {
            System.out.println("PROC: " + debug + " println: " + s);
        }
        write((s + "\n").getBytes());
    }
    // Writes data into stdin of proc
    public void write(byte[] b) throws IOException {
        p.getOutputStream().write(b);
        p.getOutputStream().flush();
    }
    // Reads all output and wait for process end
    public int waitFor() throws Exception {
        while (true) {
            String s = readLine();
            if (s == null) {
                break;
            }
        }
        return p.waitFor();
    }

    // The following methods are used inside a proc

    // Writes out a BASE64 binary with a prefix
    public static void binOut(byte[] data) {
        System.out.println(PREFIX + Base64.getEncoder().encodeToString(data));
    }
    // Reads in a line of BASE64 binary
    public static byte[] binIn() throws Exception {
        return Base64.getDecoder().decode(textIn());
    }
    // Writes out a text with a prefix
    public static void textOut(String data) {
        System.out.println(PREFIX + data);
    }
    // Reads in a line of text
    public static String textIn() throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean isEmpty = true;
        while (true) {
            int i = System.in.read();
            if (i == -1) break;
            isEmpty = false;
            if (i == '\n') break;
            if (i != 13) {
                // Force it to a char, so only simple ASCII works.
                sb.append((char)i);
            }
        }
        return isEmpty ? null : sb.toString();
    }
    // Sends string to stderr. If inheritIO is not called, they will
    // be collected into DFILE
    public static void d(String s) throws IOException {
        System.err.println(s);
    }
    // Sends an exception to stderr
    public static void d(Throwable e) throws IOException {
        e.printStackTrace();
    }

    private static String[] splitProperty(String prop) {
        String s = System.getProperty(prop);
        if (s == null || s.trim().isEmpty()) {
            return new String[] {};
        }
        return s.trim().split("\\s+");
    }
}
