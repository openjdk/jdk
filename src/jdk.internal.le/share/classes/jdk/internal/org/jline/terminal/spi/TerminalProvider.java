/*
 * Copyright (c) 2022, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.function.Function;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.exec.ExecTerminalProvider;
import jdk.internal.org.jline.terminal.impl.ffm.FfmTerminalProvider;

public interface TerminalProvider {

    String name();

    Terminal sysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException;

    Terminal newTerminal(
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException;

    boolean isSystemStream(SystemStream stream);

    String systemStreamName(SystemStream stream);

    int systemStreamWidth(SystemStream stream);

    static TerminalProvider load(String name) throws IOException {
        switch (name) {
            case "exec": return new ExecTerminalProvider();
            case "ffm": return new FfmTerminalProvider();
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        InputStream is = cl.getResourceAsStream("META-INF/services/org/jline/terminal/provider/" + name);
        if (is != null) {
            Properties props = new Properties();
            try {
                props.load(is);
                String className = props.getProperty("class");
                if (className == null) {
                    throw new IOException("No class defined in terminal provider file " + name);
                }
                Class<?> clazz = cl.loadClass(className);
                return (TerminalProvider) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                throw new IOException("Unable to load terminal provider " + name + ": " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Unable to find terminal provider " + name);
        }
    }
}
