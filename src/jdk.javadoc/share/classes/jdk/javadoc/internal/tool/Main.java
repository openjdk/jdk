/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.javadoc.internal.tool;

import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import java.io.PrintWriter;
import java.util.Objects;

import com.sun.tools.javac.util.Context;

/**
 * Provides external entry points (tool and programmatic) for the javadoc program.
 */
public class Main {

    /**
     * The main entry point called by the launcher. This will call
     * System.exit with an appropriate return value.
     *
     * @param args the command-line parameters
     */
    public static void main(String... args) {
        System.exit(execute(args));
    }

    /**
     * Programmatic interface.
     *
     * @param args the command-line parameters
     * @return The return code.
     */
    public static int execute(String... args) {
        Start jdoc = new Start();
        return jdoc.begin(args).exitCode;
    }

    /**
     * Programmatic interface.
     *
     * @param writer a stream for all output
     * @param args the command-line parameters
     * @return The return code.
     */
    public static int execute(String[] args, PrintWriter writer) {
        Start jdoc = new Start(writer, writer);
        return jdoc.begin(args).exitCode;
    }

    /**
     * Programmatic interface.
     *
     * @param outWriter a stream for expected output
     * @param errWriter a stream for diagnostic output
     * @param args the command-line parameters
     * @return The return code.
     */
    public static int execute(String[] args, PrintWriter outWriter, PrintWriter errWriter) {
        Start jdoc = new Start(outWriter, errWriter);
        return jdoc.begin(args).exitCode;
    }


    // builder-style API to run javadoc

    private PrintWriter outWriter;
    private PrintWriter errWriter;
    private StandardJavaFileManager fileManager;

    /**
     * Creates a default builder to run javadoc.
     */
    public Main() { }

    /**
     * Sets the output and error streams to be used when running javadoc.
     * The streams may be the same; they must not be {@code null}.
     *
     * @param outWriter the output stream
     * @param errWriter the error stream
     *
     * @return this object
     */
    public Main setStreams(PrintWriter outWriter, PrintWriter errWriter) {
        this.outWriter = Objects.requireNonNull(outWriter);
        this.errWriter = Objects.requireNonNull(errWriter);
        return this;
    }

    /**
     * Sets the file manager to be used when running javadoc.
     * A value of {@code null} means to use the default file manager.
     *
     * @param fileManager the file manager to use
     *
     * @return this object
     */
    public Main setFileManager(StandardJavaFileManager fileManager) {
        this.fileManager = fileManager;
        return this;
    }

    /**
     * Runs javadoc with preconfigured values and a given set of arguments.
     * Any errors will be reported to the error stream, or to {@link System#err}
     * if no error stream has been specified with {@code setStreams}.
     *
     * @param args the arguments
     *
     * @return a value indicating the success or otherwise of the run
     */
    public Result run(String... args) {
        Context context = null;
        if (fileManager != null) {
            context = new Context();
            context.put(JavaFileManager.class, fileManager);
        }
        Start jdoc = new Start(context, null, outWriter, errWriter, null, null);
        return jdoc.begin(args);
    }

    public enum Result {
        /** completed with no errors */
        OK(0),
        /** Completed with reported errors */
        ERROR(1),
        /** Bad command-line arguments */
        CMDERR(2),
        /** System error or resource exhaustion */
        SYSERR(3),
        /** Terminated abnormally */
        ABNORMAL(4);

        Result(int exitCode) {
            this.exitCode = exitCode;
        }

        public boolean isOK() {
            return (exitCode == 0);
        }

        public final int exitCode;

        @Override
        public String toString() {
            return name() + '(' + exitCode + ')';
        }
    }
}
