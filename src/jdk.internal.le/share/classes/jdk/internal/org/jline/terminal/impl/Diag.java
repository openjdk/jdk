/*
 * Copyright (c) 2022, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.OSUtils;

public class Diag {

    public static void main(String[] args) {
        diag(System.out);
    }

    public static void diag(PrintStream out) {
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
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("FFM support not available: " + t);
        }
        out.println();

        out.println("JnaSupport");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jna");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("JNA support not available: " + t);
        }
        out.println();

        out.println("Jansi2Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jansi");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("Jansi 2 support not available: " + t);
        }
        out.println();

        out.println("JniSupport");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jni");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("JNI support not available: " + t);
        }
        out.println();

        // Exec
        out.println("Exec Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("exec");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("Exec support not available: " + t);
        }
    }

    private static void testProvider(PrintStream out, TerminalProvider provider) {
        try {
            out.println("StdIn stream =    " + provider.isSystemStream(SystemStream.Input));
            out.println("StdOut stream =   " + provider.isSystemStream(SystemStream.Output));
            out.println("StdErr stream =   " + provider.isSystemStream(SystemStream.Error));
        } catch (Throwable t2) {
            out.println("Unable to check stream: " + t2);
        }
        try {
            out.println("StdIn stream name =     " + provider.systemStreamName(SystemStream.Input));
            out.println("StdOut stream name =    " + provider.systemStreamName(SystemStream.Output));
            out.println("StdErr stream name =    " + provider.systemStreamName(SystemStream.Error));
        } catch (Throwable t2) {
            out.println("Unable to check stream names: " + t2);
        }
        try (Terminal terminal = provider.sysTerminal(
                "diag",
                "xterm",
                false,
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
                    ForkJoinTask<Integer> t =
                            new ForkJoinPool(1).submit(() -> terminal.reader().read(1));
                    int r = t.get(1000, TimeUnit.MILLISECONDS);
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
                } catch (Throwable t3) {
                    out.println("Unable to read from terminal: " + t3);
                    t3.printStackTrace();
                } finally {
                    terminal.setAttributes(attr);
                }
            } else {
                out.println("Not supported by provider");
            }
        } catch (Throwable t2) {
            out.println("Unable to open terminal: " + t2);
            t2.printStackTrace();
        }
    }

    static <S> S load(Class<S> clazz) {
        return ServiceLoader.load(clazz, clazz.getClassLoader()).iterator().next();
    }
}
