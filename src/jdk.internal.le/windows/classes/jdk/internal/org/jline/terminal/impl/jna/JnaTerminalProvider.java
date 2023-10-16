/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.jna;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.PosixPtyTerminal;
import jdk.internal.org.jline.terminal.impl.PosixSysTerminal;
import jdk.internal.org.jline.terminal.impl.jna.win.JnaWinSysTerminal;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.utils.OSUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Function;

public class JnaTerminalProvider implements TerminalProvider
{
    @Override
    public String name() {
        return "jna";
    }

//    public Pty current(TerminalProvider.Stream console) throws IOException {
//        return JnaNativePty.current(console);
//    }
//
//    public Pty open(Attributes attributes, Size size) throws IOException {
//        return JnaNativePty.open(attributes, size);
//    }

    @Override
    public Terminal sysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                                boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
                                Stream consoleStream, Function<InputStream, InputStream> inputStreamWrapper) throws IOException {
        if (OSUtils.IS_WINDOWS) {
            return winSysTerminal(name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, consoleStream, inputStreamWrapper );
        } else {
            return null;
        }
    }

    public Terminal winSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                                   boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
                                   Stream console, Function<InputStream, InputStream> inputStreamWrapper) throws IOException {
        return JnaWinSysTerminal.createTerminal(name, type, ansiPassThrough, encoding, nativeSignals, signalHandler, paused, console, inputStreamWrapper);
    }

//    public Terminal posixSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
//                                     boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused,
//                                     Stream consoleStream) throws IOException {
//        Pty pty = current(consoleStream);
//        return new PosixSysTerminal(name, type, pty, encoding, nativeSignals, signalHandler);
//    }

    @Override
    public Terminal newTerminal(String name, String type, InputStream in, OutputStream out,
                                Charset encoding, Terminal.SignalHandler signalHandler, boolean paused,
                                Attributes attributes, Size size) throws IOException
    {
//        Pty pty = open(attributes, size);
//        return new PosixPtyTerminal(name, type, pty, in, out, encoding, signalHandler, paused);
        return null;
    }

    @Override
    public boolean isSystemStream(Stream stream) {
        try {
            if (OSUtils.IS_WINDOWS) {
                return isWindowsSystemStream(stream);
            } else {
//                return isPosixSystemStream(stream);
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isWindowsSystemStream(Stream stream) {
        return JnaWinSysTerminal.isWindowsSystemStream(stream);
    }

//    public boolean isPosixSystemStream(Stream stream) {
//        return JnaNativePty.isPosixSystemStream(stream);
//    }

    @Override
    public String systemStreamName(Stream stream) {
//        if (OSUtils.IS_WINDOWS) {
            return null;
//        } else {
//            return JnaNativePty.posixSystemStreamName(stream);
//        }
    }
}
