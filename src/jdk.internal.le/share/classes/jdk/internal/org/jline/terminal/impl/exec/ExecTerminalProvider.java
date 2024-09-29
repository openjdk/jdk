/*
 * Copyright (c) 2022, the original author(s).
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

//import jdk.internal.org.jline.nativ.JLineLibrary;
//import jdk.internal.org.jline.nativ.JLineNativeLoader;
import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.internal.org.jline.terminal.impl.ExternalTerminal;
import jdk.internal.org.jline.terminal.impl.PosixSysTerminal;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.ExecHelper;
import jdk.internal.org.jline.utils.Log;
import jdk.internal.org.jline.utils.OSUtils;

import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION;

public class ExecTerminalProvider implements TerminalProvider {

    private static boolean warned;

    public String name() {
        return TerminalBuilder.PROP_PROVIDER_EXEC;
    }

    public Pty current(SystemStream systemStream) throws IOException {
        return ExecPty.current(this, systemStream);
    }

    @Override
    public Terminal sysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        if (OSUtils.IS_WINDOWS) {
            return winSysTerminal(
                    name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, systemStream, inputStreamWrapper);
        } else {
            return posixSysTerminal(
                    name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, systemStream, inputStreamWrapper);
        }
    }

    public Terminal winSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        if (OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM) {
            Pty pty = current(systemStream);
            return new PosixSysTerminal(name, type, pty, encoding, nativeSignals, signalHandler, inputStreamWrapper);
        } else {
            return null;
        }
    }

    public Terminal posixSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        Pty pty = current(systemStream);
        return new PosixSysTerminal(name, type, pty, encoding, nativeSignals, signalHandler, inputStreamWrapper);
    }

    @Override
    public Terminal newTerminal(
            String name,
            String type,
            InputStream in,
            OutputStream out,
            Charset encoding,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException {
        return new ExternalTerminal(this, name, type, in, out, encoding, signalHandler, paused, attributes, size);
    }

    @Override
    public boolean isSystemStream(SystemStream stream) {
        try {
            return isPosixSystemStream(stream) || isWindowsSystemStream(stream);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isWindowsSystemStream(SystemStream stream) {
        return systemStreamName(stream) != null;
    }

    public boolean isPosixSystemStream(SystemStream stream) {
        try {
            Process p = new ProcessBuilder(OSUtils.TEST_COMMAND, "-t", Integer.toString(stream.ordinal()))
                    .inheritIO()
                    .start();
            return p.waitFor() == 0;
        } catch (Throwable t) {
            Log.debug("ExecTerminalProvider failed 'test -t' for " + stream, t);
            // ignore
        }
        return false;
    }

    @Override
    public String systemStreamName(SystemStream stream) {
        try {
            ProcessBuilder.Redirect input = stream == SystemStream.Input
                    ? ProcessBuilder.Redirect.INHERIT
                    : newDescriptor(stream == SystemStream.Output ? FileDescriptor.out : FileDescriptor.err);
            Process p =
                    new ProcessBuilder(OSUtils.TTY_COMMAND).redirectInput(input).start();
            String result = ExecHelper.waitAndCapture(p);
            if (p.exitValue() == 0) {
                return result.trim();
            }
        } catch (Throwable t) {
            if ("java.lang.reflect.InaccessibleObjectException"
                            .equals(t.getClass().getName())
                    && !warned) {
                Log.warn(
                        "The ExecTerminalProvider requires the JVM options: '--add-opens java.base/java.lang=ALL-UNNAMED'");
                warned = true;
            }
            // ignore
        }
        return null;
    }

    @Override
    public int systemStreamWidth(SystemStream stream) {
        try (ExecPty pty = new ExecPty(this, stream, null)) {
            return pty.getSize().getColumns();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static RedirectPipeCreator redirectPipeCreator;

    protected static ProcessBuilder.Redirect newDescriptor(FileDescriptor fd) {
        if (redirectPipeCreator == null) {
            String str = System.getProperty(PROP_REDIRECT_PIPE_CREATION_MODE, PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT);
            String[] modes = str.split(",");
            IllegalStateException ise = new IllegalStateException("Unable to create RedirectPipe");
            for (String mode : modes) {
                try {
                    switch (mode) {
                        case PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE:
                            redirectPipeCreator = null;//new NativeRedirectPipeCreator();
                            break;
                        case PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION:
                            redirectPipeCreator = new ReflectionRedirectPipeCreator();
                            break;
                    }
                } catch (Throwable t) {
                    // ignore
                    ise.addSuppressed(t);
                }
                if (redirectPipeCreator != null) {
                    break;
                }
            }
            if (redirectPipeCreator == null) {
                throw ise;
            }
        }
        return redirectPipeCreator.newRedirectPipe(fd);
    }

    interface RedirectPipeCreator {
        ProcessBuilder.Redirect newRedirectPipe(FileDescriptor fd);
    }

    /**
     * Reflection based file descriptor creator.
     * This requires the following option
     *   --add-opens java.base/java.lang=ALL-UNNAMED
     */
    static class ReflectionRedirectPipeCreator implements RedirectPipeCreator {
        private final Constructor<ProcessBuilder.Redirect> constructor;
        private final Field fdField;

        @SuppressWarnings("unchecked")
        ReflectionRedirectPipeCreator() throws Exception {
            Class<?> rpi = Class.forName("java.lang.ProcessBuilder$RedirectPipeImpl");
            constructor = (Constructor<ProcessBuilder.Redirect>) rpi.getDeclaredConstructor();
            constructor.setAccessible(true);
            fdField = rpi.getDeclaredField("fd");
            fdField.setAccessible(true);
        }

        @Override
        public ProcessBuilder.Redirect newRedirectPipe(FileDescriptor fd) {
            try {
                ProcessBuilder.Redirect input = constructor.newInstance();
                fdField.set(input, fd);
                return input;
            } catch (ReflectiveOperationException e) {
                // This should not happen as the field has been set accessible
                throw new IllegalStateException(e);
            }
        }
    }

//    static class NativeRedirectPipeCreator implements RedirectPipeCreator {
//        public NativeRedirectPipeCreator() {
//            // Force load the library
//            JLineNativeLoader.initialize();
//        }
//
//        @Override
//        public ProcessBuilder.Redirect newRedirectPipe(FileDescriptor fd) {
//            return JLineLibrary.newRedirectPipe(fd);
//        }
//    }

    @Override
    public String toString() {
        return "TerminalProvider[" + name() + "]";
    }
}
