/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.OSUtils;

/**
 * Diagnostic utility for JLine terminals.
 *
 * <p>
 * The Diag class provides diagnostic tools for analyzing and troubleshooting
 * JLine terminal configurations. It can be used to gather information about
 * the current environment, available terminal providers, system properties,
 * and other details relevant to terminal operation.
 * </p>
 *
 * <p>
 * This class can be run as a standalone application to generate a diagnostic
 * report, which is useful for debugging terminal-related issues. The report
 * includes information such as:
 * </p>
 * <ul>
 *   <li>Java version and system properties</li>
 *   <li>Operating system details</li>
 *   <li>Available terminal providers</li>
 *   <li>Terminal capabilities and attributes</li>
 *   <li>Console and TTY information</li>
 * </ul>
 *
 * <p>
 * The diagnostic information can help identify configuration issues, missing
 * dependencies, or platform-specific problems that might affect terminal
 * functionality.
 * </p>
 */
public class Diag {

    /**
     * Main entry point for running the diagnostic tool.
     *
     * <p>
     * This method runs the diagnostic tool and prints the results to standard output.
     * If the "--verbose" flag is provided as an argument, additional detailed
     * information will be included in the output.
     * </p>
     *
     * @param args command-line arguments (use "--verbose" for detailed output)
     */
    public static void main(String[] args) {
        diag(System.out, Arrays.asList(args).contains("--verbose"));
    }

    /**
     * Generates a diagnostic report with standard verbosity.
     *
     * <p>
     * This method generates a diagnostic report with standard verbosity and
     * writes it to the specified PrintStream. This is equivalent to calling
     * {@link #diag(PrintStream, boolean)} with {@code verbose=false}.
     * </p>
     *
     * @param out the PrintStream to write the diagnostic report to
     */
    public static void diag(PrintStream out) {
        diag(out, true);
    }

    public static void diag(PrintStream out, boolean verbose) {
        new Diag(out, verbose).run();
    }

    private final PrintStream out;
    private final boolean verbose;

    public Diag(PrintStream out, boolean verbose) {
        this.out = out;
        this.verbose = verbose;
    }

    public void run() {
        out.println("System properties");
        out.println("=================");
        out.println("os.name =         " + System.getProperty("os.name"));
        out.println("OSTYPE =          " + System.getenv("OSTYPE"));
        out.println("MSYSTEM =         " + System.getenv("MSYSTEM"));
        out.println("PWD =             " + System.getenv("PWD"));
        out.println("ConEmuPID =       " + System.getenv("ConEmuPID"));
        out.println("WSL_DISTRO_NAME = " + System.getenv("WSL_DISTRO_NAME"));
        out.println("WSL_INTEROP =     " + System.getenv("WSL_INTEROP"));
        out.println();

        out.println("OSUtils");
        out.println("=================");
        out.println("IS_WINDOWS = " + OSUtils.IS_WINDOWS);
        out.println("IS_CYGWIN =  " + OSUtils.IS_CYGWIN);
        out.println("IS_MSYSTEM = " + OSUtils.IS_MSYSTEM);
        out.println("IS_WSL =     " + OSUtils.IS_WSL);
        out.println("IS_WSL1 =    " + OSUtils.IS_WSL1);
        out.println("IS_WSL2 =    " + OSUtils.IS_WSL2);
        out.println("IS_CONEMU =  " + OSUtils.IS_CONEMU);
        out.println("IS_OSX =     " + OSUtils.IS_OSX);
        out.println();

        // FFM
        out.println("FFM Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("ffm");
            testProvider(provider);
        } catch (Throwable t) {
            error("FFM support not available", t);
        }
        out.println();

        out.println("JniSupport");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jni");
            testProvider(provider);
        } catch (Throwable t) {
            error("JNI support not available", t);
        }
        out.println();

        // Exec
        out.println("Exec Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("exec");
            testProvider(provider);
        } catch (Throwable t) {
            error("Exec support not available", t);
        }

        if (!verbose) {
            out.println();
            out.println("Run with --verbose argument to print stack traces");
        }
    }

    private void testProvider(TerminalProvider provider) {
        try {
            out.println("StdIn stream =    " + provider.isSystemStream(SystemStream.Input));
            out.println("StdOut stream =   " + provider.isSystemStream(SystemStream.Output));
            out.println("StdErr stream =   " + provider.isSystemStream(SystemStream.Error));
        } catch (Throwable t) {
            error("Unable to check stream", t);
        }
        try {
            out.println("StdIn stream name =     " + provider.systemStreamName(SystemStream.Input));
            out.println("StdOut stream name =    " + provider.systemStreamName(SystemStream.Output));
            out.println("StdErr stream name =    " + provider.systemStreamName(SystemStream.Error));
        } catch (Throwable t) {
            error("Unable to check stream names", t);
        }
        try (Terminal terminal = provider.sysTerminal(
                "diag",
                "xterm",
                false,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                false,
                Terminal.SignalHandler.SIG_DFL,
                false,
                SystemStream.Output,
                input -> input)) {
            if (terminal != null) {
                Attributes attr = terminal.enterRawMode();
                try {
                    out.println("Terminal size: " + terminal.getSize());
                    ForkJoinPool forkJoinPool = new ForkJoinPool(1);
                    try {
                        ForkJoinTask<Integer> t =
                                forkJoinPool.submit(() -> terminal.reader().read(1));
                        t.get(1000, TimeUnit.MILLISECONDS);
                    } finally {
                        forkJoinPool.shutdown();
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("The terminal seems to work: ");
                    sb.append("terminal ").append(terminal.getClass().getName());
                    if (terminal instanceof AbstractPosixTerminal) {
                        sb.append(" with pty ")
                                .append(((AbstractPosixTerminal) terminal)
                                        .getPty()
                                        .getClass()
                                        .getName());
                    }
                    out.println(sb);
                } catch (Throwable t2) {
                    error("Unable to read from terminal", t2);
                } finally {
                    terminal.setAttributes(attr);
                }
            } else {
                out.println("Not supported by provider");
            }
        } catch (Throwable t) {
            error("Unable to open terminal", t);
        }
    }

    private void error(String message, Throwable cause) {
        if (verbose) {
            out.println(message);
            cause.printStackTrace(out);
        } else {
            out.println(message + ": " + cause);
        }
    }
}
