/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

public class JavaProcess {

    protected Process process = null;

    private String classname;
    private StringBuilder classArgs;
    private StringBuilder javaOptions;

    private static String java = System.getProperty("java.home")
                                 + File.separator + "bin"
                                 + File.separator + "java";

    public JavaProcess(String classname) {
        this(classname, "", "");
    }

    public JavaProcess(String classname, String classArgs) {
        this(classname, "", classArgs);
    }

    public JavaProcess(String classname, String javaOptions, String classArgs) {
        this.classname = classname;
        this.javaOptions = new StringBuilder(javaOptions);
        this.classArgs = new StringBuilder(classArgs);
    }

    /**
     * add java options to the java command
     */
    public void addOptions(String[] opts) {
        if (javaOptions != null && javaOptions.length() > 0) {
            javaOptions.append(" ");
        }

        for (int i = 0; i < opts.length; i++) {
            if (i != 0) {
                javaOptions.append(" ");
            }
            javaOptions.append(opts[i]);
        }
    }

    /**
     * add arguments to the class arguments
     */
    public void addArguments(String[] args) {
        if (classArgs != null && classArgs.length() > 0) {
            classArgs.append(" ");
        }

        for (int i = 0; i < args.length; i++) {
            if (i != 0) {
                classArgs.append(" ");
            }
            classArgs.append(args[i]);
        }
    }

    /**
     * start the java process
     */
    public void start() throws IOException {
        if (process != null) {
            return;
        }

        String javaCommand = java + " " + javaOptions + " "
                             + classname + " " + classArgs;

        System.out.println("exec'ing: " + javaCommand);

        process = Runtime.getRuntime().exec(javaCommand);
    }

    /**
     * destroy the java process
     */
    public void destroy() {
        if (process != null) {
            process.destroy();
        }
        process = null;
    }

    public int exitValue() {
        if (process != null) {
            return process.exitValue();
        }
        throw new RuntimeException("exitValue called with process == null");
    }

    public InputStream getErrorStream() {
        if (process != null) {
            return process.getErrorStream();
        }
        throw new RuntimeException(
                "getErrorStream() called with process == null");
    }

    public InputStream getInputStream() {
        if (process != null) {
            return process.getInputStream();
        }
        throw new RuntimeException(
                "getInputStream() called with process == null");
    }

    public OutputStream getOutputStream() {
        if (process != null) {
            return process.getOutputStream();
        }
        throw new RuntimeException(
                "getOutputStream() called with process == null");
    }

    public int waitFor() throws InterruptedException {
        if (process != null) {
            return process.waitFor();
        }
        throw new RuntimeException("waitFor() called with process == null");
    }
}
