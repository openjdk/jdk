/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.exec;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Attributes.ControlChar;
import jdk.internal.org.jline.terminal.Attributes.ControlFlag;
import jdk.internal.org.jline.terminal.Attributes.InputFlag;
import jdk.internal.org.jline.terminal.Attributes.LocalFlag;
import jdk.internal.org.jline.terminal.Attributes.OutputFlag;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.AbstractPty;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.OSUtils;

import static jdk.internal.org.jline.utils.ExecHelper.exec;

/**
 * A pseudoterminal implementation that uses external commands to interact with the terminal.
 *
 * <p>
 * The ExecPty class provides a Pty implementation that uses external commands (such as
 * stty, tput, etc.) to interact with the terminal. This approach allows JLine to work
 * in environments where native libraries are not available or cannot be used, by relying
 * on standard command-line utilities that are typically available on Unix-like systems.
 * </p>
 *
 * <p>
 * This implementation executes external commands to perform operations such as:
 * </p>
 * <ul>
 *   <li>Getting and setting terminal attributes</li>
 *   <li>Getting and setting terminal size</li>
 *   <li>Determining the current terminal device</li>
 * </ul>
 *
 * <p>
 * The ExecPty is typically used as a fallback when more direct methods of terminal
 * interaction (such as JNI or JNA) are not available. While it provides good compatibility,
 * it may have higher overhead due to the need to spawn external processes for many operations.
 * </p>
 *
 * @see org.jline.terminal.impl.AbstractPty
 * @see org.jline.terminal.spi.Pty
 */
public class ExecPty extends AbstractPty implements Pty {

    private final String name;

    /**
     * Creates an ExecPty instance for the current terminal.
     *
     * <p>
     * This method creates an ExecPty instance for the current terminal by executing
     * the 'tty' command to determine the terminal device name. It is used to obtain
     * a Pty object that can interact with the current terminal using external commands.
     * </p>
     *
     * @param provider the terminal provider that will own this Pty
     * @param systemStream the system stream (must be Output or Error) associated with this Pty
     * @return a new ExecPty instance for the current terminal
     * @throws IOException if the current terminal is not a TTY or if an error occurs
     *                     while executing the 'tty' command
     * @throws IllegalArgumentException if systemStream is not Output or Error
     */
    public static Pty current(TerminalProvider provider, SystemStream systemStream) throws IOException {
        try {
            String result = exec(true, OSUtils.TTY_COMMAND);
            if (systemStream != SystemStream.Output && systemStream != SystemStream.Error) {
                throw new IllegalArgumentException("systemStream should be Output or Error: " + systemStream);
            }
            return new ExecPty(provider, systemStream, result.trim());
        } catch (IOException e) {
            throw new IOException("Not a tty", e);
        }
    }

    /**
     * Creates a new ExecPty instance.
     *
     * <p>
     * This constructor creates a new ExecPty instance with the specified provider,
     * system stream, and terminal device name. It is protected because instances should
     * typically be created using the {@link #current(TerminalProvider, SystemStream)} method.
     * </p>
     *
     * @param provider the terminal provider that will own this Pty
     * @param systemStream the system stream associated with this Pty
     * @param name the name of the terminal device (e.g., "/dev/tty")
     */
    protected ExecPty(TerminalProvider provider, SystemStream systemStream, String name) {
        super(provider, systemStream);
        this.name = name;
    }

    /**
     * Closes this Pty.
     *
     * <p>
     * This implementation does nothing, as there are no resources to release.
     * The terminal device is not actually opened by this class, so it does not
     * need to be closed.
     * </p>
     *
     * @throws IOException if an I/O error occurs (never thrown by this implementation)
     */
    @Override
    public void close() throws IOException {}

    /**
     * Returns the name of the terminal device.
     *
     * <p>
     * This method returns the name of the terminal device associated with this Pty,
     * which was determined when the Pty was created. This is typically a device path
     * such as "/dev/tty" or "/dev/pts/0".
     * </p>
     *
     * @return the name of the terminal device
     */
    public String getName() {
        return name;
    }

    @Override
    public InputStream getMasterInput() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getMasterOutput() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected InputStream doGetSlaveInput() throws IOException {
        return systemStream != null ? new FileInputStream(FileDescriptor.in) : new FileInputStream(getName());
    }

    @Override
    public OutputStream getSlaveOutput() throws IOException {
        return systemStream == SystemStream.Output
                ? new FileOutputStream(FileDescriptor.out)
                : systemStream == SystemStream.Error
                        ? new FileOutputStream(FileDescriptor.err)
                        : new FileOutputStream(getName());
    }

    @Override
    public Attributes getAttr() throws IOException {
        String cfg = doGetConfig();
        return doGetAttr(cfg);
    }

    @Override
    protected void doSetAttr(Attributes attr) throws IOException {
        List<String> commands = getFlagsToSet(attr, getAttr());
        if (!commands.isEmpty()) {
            commands.add(0, OSUtils.STTY_COMMAND);
            if (systemStream == null) {
                commands.add(1, OSUtils.STTY_F_OPTION);
                commands.add(2, getName());
            }
            try {
                exec(systemStream != null, commands.toArray(new String[0]));
            } catch (IOException e) {
                // Handle partial failures with GNU stty, see #97
                if (e.toString().contains("unable to perform all requested operations")) {
                    commands = getFlagsToSet(attr, getAttr());
                    if (!commands.isEmpty()) {
                        throw new IOException("Could not set the following flags: " + String.join(", ", commands), e);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    protected List<String> getFlagsToSet(Attributes attr, Attributes current) {
        List<String> commands = new ArrayList<>();
        for (InputFlag flag : InputFlag.values()) {
            if (attr.getInputFlag(flag) != current.getInputFlag(flag) && flag != InputFlag.INORMEOL) {
                commands.add((attr.getInputFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (OutputFlag flag : OutputFlag.values()) {
            if (attr.getOutputFlag(flag) != current.getOutputFlag(flag)) {
                commands.add((attr.getOutputFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (ControlFlag flag : ControlFlag.values()) {
            if (attr.getControlFlag(flag) != current.getControlFlag(flag)) {
                commands.add((attr.getControlFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (LocalFlag flag : LocalFlag.values()) {
            if (attr.getLocalFlag(flag) != current.getLocalFlag(flag)) {
                commands.add((attr.getLocalFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        String undef = System.getProperty("os.name").toLowerCase().startsWith("hp") ? "^-" : "undef";
        for (ControlChar cchar : ControlChar.values()) {
            int v = attr.getControlChar(cchar);
            if (v >= 0 && v != current.getControlChar(cchar)) {
                String str = "";
                commands.add(cchar.name().toLowerCase().substring(1));
                if (cchar == ControlChar.VMIN || cchar == ControlChar.VTIME) {
                    commands.add(Integer.toString(v));
                } else if (v == 0) {
                    commands.add(undef);
                } else {
                    if (v >= 128) {
                        v -= 128;
                        str += "M-";
                    }
                    if (v < 32 || v == 127) {
                        v ^= 0x40;
                        str += "^";
                    }
                    str += (char) v;
                    commands.add(str);
                }
            }
        }
        return commands;
    }

    @Override
    public Size getSize() throws IOException {
        String cfg = doGetConfig();
        return doGetSize(cfg);
    }

    protected String doGetConfig() throws IOException {
        return systemStream != null
                ? exec(true, OSUtils.STTY_COMMAND, "-a")
                : exec(false, OSUtils.STTY_COMMAND, OSUtils.STTY_F_OPTION, getName(), "-a");
    }

    public static Attributes doGetAttr(String cfg) throws IOException {
        Attributes attributes = new Attributes();
        for (InputFlag flag : InputFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setInputFlag(flag, value);
            }
        }
        for (OutputFlag flag : OutputFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setOutputFlag(flag, value);
            }
        }
        for (ControlFlag flag : ControlFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setControlFlag(flag, value);
            }
        }
        for (LocalFlag flag : LocalFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setLocalFlag(flag, value);
            }
        }
        for (ControlChar cchar : ControlChar.values()) {
            String name = cchar.name().toLowerCase().substring(1);
            if ("reprint".endsWith(name)) {
                name = "(?:reprint|rprnt)";
            }
            Matcher matcher =
                    Pattern.compile("[\\s;]" + name + "\\s*=\\s*(.+?)[\\s;]").matcher(cfg);
            if (matcher.find()) {
                attributes.setControlChar(
                        cchar, parseControlChar(matcher.group(1).toUpperCase()));
            }
        }
        return attributes;
    }

    private static Boolean doGetFlag(String cfg, Enum<?> flag) {
        Matcher matcher = Pattern.compile("(?:^|[\\s;])(\\-?" + flag.name().toLowerCase() + ")(?:[\\s;]|$)")
                .matcher(cfg);
        return matcher.find() ? !matcher.group(1).startsWith("-") : null;
    }

    static int parseControlChar(String str) {
        // undef
        if ("<UNDEF>".equals(str)) {
            return -1;
        }
        // del
        if ("DEL".equalsIgnoreCase(str)) {
            return 127;
        }
        // octal
        if (str.charAt(0) == '0') {
            return Integer.parseInt(str, 8);
        }
        // decimal
        if (str.charAt(0) >= '1' && str.charAt(0) <= '9') {
            return Integer.parseInt(str, 10);
        }
        // control char
        if (str.charAt(0) == '^') {
            if (str.charAt(1) == '?') {
                return 127;
            } else {
                return str.charAt(1) - 64;
            }
        } else if (str.charAt(0) == 'M' && str.charAt(1) == '-') {
            if (str.charAt(2) == '^') {
                if (str.charAt(3) == '?') {
                    return 127 + 128;
                } else {
                    return str.charAt(3) - 64 + 128;
                }
            } else {
                return str.charAt(2) + 128;
            }
        } else {
            return str.charAt(0);
        }
    }

    static Size doGetSize(String cfg) throws IOException {
        return new Size(doGetInt("columns", cfg), doGetInt("rows", cfg));
    }

    static int doGetInt(String name, String cfg) throws IOException {
        String[] patterns = new String[] {
            "\\b([0-9]+)\\s+" + name + "\\b", "\\b" + name + "\\s+([0-9]+)\\b", "\\b" + name + "\\s*=\\s*([0-9]+)\\b"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(cfg);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }

    @Override
    public void setSize(Size size) throws IOException {
        if (systemStream != null) {
            exec(
                    true,
                    OSUtils.STTY_COMMAND,
                    "columns",
                    Integer.toString(size.getColumns()),
                    "rows",
                    Integer.toString(size.getRows()));
        } else {
            exec(
                    false,
                    OSUtils.STTY_COMMAND,
                    OSUtils.STTY_F_OPTION,
                    getName(),
                    "columns",
                    Integer.toString(size.getColumns()),
                    "rows",
                    Integer.toString(size.getRows()));
        }
    }

    @Override
    public String toString() {
        return "ExecPty[" + getName() + (systemStream != null ? ", system]" : "]");
    }
}
