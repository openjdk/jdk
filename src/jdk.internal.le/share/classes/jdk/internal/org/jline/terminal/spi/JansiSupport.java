package jdk.internal.org.jline.terminal.spi;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.charset.Charset;

public interface JansiSupport {

    Pty current() throws IOException;

    Pty open(Attributes attributes, Size size) throws IOException;

    Terminal winSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding, int codepage, boolean nativeSignals, Terminal.SignalHandler signalHandler) throws IOException;

    Terminal winSysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding, int codepage, boolean nativeSignals, Terminal.SignalHandler signalHandler, boolean paused) throws IOException;

}
