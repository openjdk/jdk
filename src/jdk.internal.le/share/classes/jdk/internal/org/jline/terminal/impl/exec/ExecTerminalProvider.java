/*
 * Copyright (c) 2022, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.exec;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.function.Function;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.ExecPty;
import jdk.internal.org.jline.terminal.impl.ExternalTerminal;
import jdk.internal.org.jline.terminal.impl.PosixSysTerminal;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.ExecHelper;
import jdk.internal.org.jline.utils.OSUtils;

public class ExecTerminalProvider implements TerminalProvider
{

    public String name() {
        return "exec";
    }

    public Pty current(Stream consoleStream) throws IOException {
        return ExecPty.current(consoleStream);
    }

    @Override
    public Terminal sysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                                boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
                                Stream consoleStream, Function<InputStream, InputStream> inputStreamWrapper) throws IOException {
        if (OSUtils.IS_WINDOWS) {
            return winSysTerminal(name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, consoleStream, inputStreamWrapper );
        } else {
            return posixSysTerminal(name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, consoleStream, inputStreamWrapper );
        }
    }

    public Terminal winSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                                    boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
                                    Stream consoleStream, Function<InputStream, InputStream> inputStreamWrapper ) throws IOException {
        if (OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM) {
            Pty pty = current(consoleStream);
            return new PosixSysTerminal(name, type, pty, encoding, nativeSignals, signalHandler, inputStreamWrapper);
        } else {
            return null;
        }
    }

    public Terminal posixSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                                     boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
                                     Stream consoleStream, Function<InputStream, InputStream> inputStreamWrapper) throws IOException {
        Pty pty = current(consoleStream);
        return new PosixSysTerminal(name, type, pty, encoding, nativeSignals, signalHandler, inputStreamWrapper);
    }

    @Override
    public Terminal newTerminal(String name, String type, InputStream in, OutputStream out,
                                Charset encoding, Terminal.SignalHandler signalHandler, boolean paused,
                                Attributes attributes, Size size) throws IOException
    {
        return new ExternalTerminal(name, type, in, out, encoding, signalHandler, paused, attributes, size);
    }

    @Override
    public boolean isSystemStream(Stream stream) {
        try {
            return isWindowsSystemStream(stream) || isPosixSystemStream(stream);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isWindowsSystemStream(Stream stream) {
        return systemStreamName( stream ) != null;
    }

    public boolean isPosixSystemStream(Stream stream) {
        try {
            Process p = new ProcessBuilder(OSUtils.TEST_COMMAND, "-t", Integer.toString(stream.ordinal()))
                    .inheritIO().start();
            return p.waitFor() == 0;
        } catch (Throwable t) {
            // ignore
        }
        return false;
    }

    @Override
    public String systemStreamName(Stream stream) {
        try {
            ProcessBuilder.Redirect input = stream == Stream.Input
                                ? ProcessBuilder.Redirect.INHERIT
                                : getRedirect(stream == Stream.Output ? FileDescriptor.out : FileDescriptor.err);
            Process p = new ProcessBuilder(OSUtils.TTY_COMMAND).redirectInput(input).start();
            String result = ExecHelper.waitAndCapture(p);
            if (p.exitValue() == 0) {
                return result.trim();
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private ProcessBuilder.Redirect getRedirect(FileDescriptor fd) throws ReflectiveOperationException {
        // This is not really allowed, but this is the only way to redirect the output or error stream
        // to the input.  This is definitely not something you'd usually want to do, but in the case of
        // the `tty` utility, it provides a way to get
        Class<?> rpi = Class.forName("java.lang.ProcessBuilder$RedirectPipeImpl");
        Constructor<?> cns = rpi.getDeclaredConstructor();
        cns.setAccessible(true);
        ProcessBuilder.Redirect input = (ProcessBuilder.Redirect) cns.newInstance();
        Field f = rpi.getDeclaredField("fd");
        f.setAccessible(true);
        f.set(input, fd);
        return input;
    }
}
