/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Encapsulates terminal attributes and settings that control terminal behavior.
 *
 * <p>
 * The Attributes class represents the terminal settings similar to the POSIX termios structure,
 * providing control over terminal input/output behavior, control characters, and various flags.
 * These attributes determine how the terminal processes input and output, handles special characters,
 * and behaves in response to various conditions.
 * </p>
 *
 * <p>
 * Terminal attributes are organized into several categories:
 * </p>
 * <ul>
 *   <li><b>Input Flags</b> - Control input processing (e.g., character mapping, parity checking)</li>
 *   <li><b>Output Flags</b> - Control output processing (e.g., newline translation)</li>
 *   <li><b>Control Flags</b> - Control hardware settings (e.g., baud rate, character size)</li>
 *   <li><b>Local Flags</b> - Control various terminal behaviors (e.g., echo, canonical mode)</li>
 *   <li><b>Control Characters</b> - Define special characters (e.g., EOF, interrupt, erase)</li>
 * </ul>
 *
 * <p>
 * Attributes objects are typically obtained from a {@link Terminal} using {@link Terminal#getAttributes()},
 * modified as needed, and then applied back to the terminal using {@link Terminal#setAttributes(Attributes)}.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.terminal();
 *
 * // Get current attributes
 * Attributes attrs = terminal.getAttributes();
 *
 * // Modify attributes
 * attrs.setLocalFlag(LocalFlag.ECHO, false);  // Disable echo
 * attrs.setInputFlag(InputFlag.ICRNL, false); // Disable CR to NL mapping
 * attrs.setControlChar(ControlChar.VMIN, 1);   // Set minimum input to 1 character
 *
 * // Apply modified attributes
 * terminal.setAttributes(attrs);
 * </pre>
 *
 * @see Terminal#getAttributes()
 * @see Terminal#setAttributes(Attributes)
 */
public class Attributes {

    /**
     * Control characters used for special terminal functions.
     *
     * <p>
     * Control characters are special characters that trigger specific terminal behaviors
     * when encountered in the input stream. These characters control various aspects of
     * terminal operation, such as signaling end-of-file, interrupting processes, or
     * erasing characters.
     * </p>
     *
     * <p>
     * The most commonly used control characters include:
     * </p>
     * <ul>
     *   <li>{@link #VEOF} - End-of-file character (typically Ctrl+D)</li>
     *   <li>{@link #VINTR} - Interrupt character (typically Ctrl+C)</li>
     *   <li>{@link #VQUIT} - Quit character (typically Ctrl+\)</li>
     *   <li>{@link #VERASE} - Erase character (typically Backspace)</li>
     *   <li>{@link #VKILL} - Kill line character (typically Ctrl+U)</li>
     *   <li>{@link #VMIN} - Minimum number of characters for non-canonical read</li>
     *   <li>{@link #VTIME} - Timeout in deciseconds for non-canonical read</li>
     * </ul>
     *
     * <p>
     * Control characters can be accessed and modified using {@link #getControlChar(ControlChar)}
     * and {@link #setControlChar(ControlChar, int)}.
     * </p>
     *
     * @see #getControlChar(ControlChar)
     * @see #setControlChar(ControlChar, int)
     */
    public enum ControlChar {
        /** End-of-file character (typically Ctrl+D) */
        VEOF,
        /** End-of-line character */
        VEOL,
        /** Secondary end-of-line character */
        VEOL2,
        /** Erase character (typically Backspace) */
        VERASE,
        /** Word erase character (typically Ctrl+W) */
        VWERASE,
        /** Kill line character (typically Ctrl+U) */
        VKILL,
        /** Reprint line character (typically Ctrl+R) */
        VREPRINT,
        /** Interrupt character (typically Ctrl+C) */
        VINTR,
        /** Quit character (typically Ctrl+\) */
        VQUIT,
        /** Suspend character (typically Ctrl+Z) */
        VSUSP,
        /** Delayed suspend character */
        VDSUSP,
        /** Start output character (typically Ctrl+Q) */
        VSTART,
        /** Stop output character (typically Ctrl+S) */
        VSTOP,
        /** Literal next character (typically Ctrl+V) */
        VLNEXT,
        /** Discard output character (typically Ctrl+O) */
        VDISCARD,
        /** Minimum number of characters for non-canonical read */
        VMIN,
        /** Timeout in deciseconds for non-canonical read */
        VTIME,
        /** Status request character (typically Ctrl+T) */
        VSTATUS
    }

    /**
     * Input flags that control how terminal input is processed.
     *
     * <p>
     * Input flags determine how the terminal processes input characters before they are
     * made available to the application. These flags control aspects such as character
     * mapping, parity checking, and flow control for input.
     * </p>
     *
     * <p>
     * Common input flags include:
     * </p>
     * <ul>
     *   <li>{@link #ICRNL} - Map CR to NL on input (convert carriage returns to newlines)</li>
     *   <li>{@link #INLCR} - Map NL to CR on input (convert newlines to carriage returns)</li>
     *   <li>{@link #IGNCR} - Ignore carriage returns on input</li>
     *   <li>{@link #IXON} - Enable XON/XOFF flow control on output</li>
     *   <li>{@link #IXOFF} - Enable XON/XOFF flow control on input</li>
     * </ul>
     *
     * <p>
     * Input flags can be accessed and modified using methods like {@link #getInputFlag(InputFlag)},
     * {@link #setInputFlag(InputFlag, boolean)}, and {@link #setInputFlags(EnumSet)}.
     * </p>
     *
     * @see #getInputFlag(InputFlag)
     * @see #setInputFlag(InputFlag, boolean)
     * @see #getInputFlags()
     * @see #setInputFlags(EnumSet)
     */
    public enum InputFlag {
        IGNBRK, /* ignore BREAK condition */
        BRKINT, /* map BREAK to SIGINTR */
        IGNPAR, /* ignore (discard) parity errors */
        PARMRK, /* mark parity and framing errors */
        INPCK, /* enable checking of parity errors */
        ISTRIP, /* strip 8th bit off chars */
        INLCR, /* map NL into CR */
        IGNCR, /* ignore CR */
        ICRNL, /* map CR to NL (ala CRMOD) */
        IXON, /* enable output flow control */
        IXOFF, /* enable input flow control */
        IXANY, /* any char will restart after stop */
        IMAXBEL, /* ring bell on input queue full */
        IUTF8, /* maintain state for UTF-8 VERASE */

        INORMEOL /* normalize end-of-line */
    }

    /**
     * Output flags that control how terminal output is processed.
     *
     * <p>
     * Output flags determine how the terminal processes output characters before they are
     * sent to the terminal device. These flags control aspects such as newline translation,
     * tab expansion, and other output processing features.
     * </p>
     *
     * <p>
     * Common output flags include:
     * </p>
     * <ul>
     *   <li>{@link #OPOST} - Enable output processing (required for other output flags to take effect)</li>
     *   <li>{@link #ONLCR} - Map NL to CR-NL on output (convert newlines to carriage return + newline)</li>
     *   <li>{@link #OCRNL} - Map CR to NL on output (convert carriage returns to newlines)</li>
     *   <li>{@link #OXTABS} - Expand tabs to spaces on output</li>
     * </ul>
     *
     * <p>
     * Output flags can be accessed and modified using methods like {@link #getOutputFlag(OutputFlag)},
     * {@link #setOutputFlag(OutputFlag, boolean)}, and {@link #setOutputFlags(EnumSet)}.
     * </p>
     *
     * @see #getOutputFlag(OutputFlag)
     * @see #setOutputFlag(OutputFlag, boolean)
     * @see #getOutputFlags()
     * @see #setOutputFlags(EnumSet)
     */
    public enum OutputFlag {
        OPOST, /* enable following output processing */
        ONLCR, /* map NL to CR-NL (ala CRMOD) */
        OXTABS, /* expand tabs to spaces */
        ONOEOT, /* discard EOT's (^D) on output) */
        OCRNL, /* map CR to NL on output */
        ONOCR, /* no CR output at column 0 */
        ONLRET, /* NL performs CR function */
        OFILL, /* use fill characters for delay */
        NLDLY, /* \n delay */
        TABDLY, /* horizontal tab delay */
        CRDLY, /* \r delay */
        FFDLY, /* form feed delay */
        BSDLY, /* \b delay */
        VTDLY, /* vertical tab delay */
        OFDEL /* fill is DEL, else NUL */
    }

    /**
     * Control flags that manage hardware aspects of the terminal.
     *
     * <p>
     * Control flags determine how the terminal hardware operates. These flags control
     * aspects such as baud rate, character size, parity, and hardware flow control.
     * </p>
     *
     * <p>
     * Common control flags include:
     * </p>
     * <ul>
     *   <li>{@link #CS5}, {@link #CS6}, {@link #CS7}, {@link #CS8} - Character size (5-8 bits)</li>
     *   <li>{@link #CSTOPB} - Use two stop bits instead of one</li>
     *   <li>{@link #PARENB} - Enable parity generation and detection</li>
     *   <li>{@link #PARODD} - Use odd parity instead of even</li>
     *   <li>{@link #CLOCAL} - Ignore modem control lines</li>
     * </ul>
     *
     * <p>
     * Control flags can be accessed and modified using methods like {@link #getControlFlag(ControlFlag)},
     * {@link #setControlFlag(ControlFlag, boolean)}, and {@link #setControlFlags(EnumSet)}.
     * </p>
     *
     * @see #getControlFlag(ControlFlag)
     * @see #setControlFlag(ControlFlag, boolean)
     * @see #getControlFlags()
     * @see #setControlFlags(EnumSet)
     */
    public enum ControlFlag {
        CIGNORE, /* ignore control flags */
        CS5, /* 5 bits    (pseudo) */
        CS6, /* 6 bits */
        CS7, /* 7 bits */
        CS8, /* 8 bits */
        CSTOPB, /* send 2 stop bits */
        CREAD, /* enable receiver */
        PARENB, /* parity enable */
        PARODD, /* odd parity, else even */
        HUPCL, /* hang up on last close */
        CLOCAL, /* ignore modem status lines */
        CCTS_OFLOW, /* CTS flow control of output */
        CRTS_IFLOW, /* RTS flow control of input */
        CDTR_IFLOW, /* DTR flow control of input */
        CDSR_OFLOW, /* DSR flow control of output */
        CCAR_OFLOW /* DCD flow control of output */
    }

    /**
     * Local flags that control various terminal behaviors.
     *
     * <p>
     * Local flags control a variety of terminal behaviors that don't fit into the other
     * flag categories. These include echo control, canonical mode, signal generation,
     * and special character processing.
     * </p>
     *
     * <p>
     * Common local flags include:
     * </p>
     * <ul>
     *   <li>{@link #ECHO} - Echo input characters</li>
     *   <li>{@link #ICANON} - Enable canonical mode (line-by-line input)</li>
     *   <li>{@link #ISIG} - Enable signal generation (INTR, QUIT, SUSP)</li>
     *   <li>{@link #IEXTEN} - Enable extended input processing</li>
     *   <li>{@link #ECHOCTL} - Echo control characters as ^X</li>
     * </ul>
     *
     * <p>
     * Note: Some flags in this category begin with the letter "I" and might appear to
     * belong in the input flags category, but they are historically part of the local flags.
     * </p>
     *
     * <p>
     * Local flags can be accessed and modified using methods like {@link #getLocalFlag(LocalFlag)},
     * {@link #setLocalFlag(LocalFlag, boolean)}, and {@link #setLocalFlags(EnumSet)}.
     * </p>
     *
     * @see #getLocalFlag(LocalFlag)
     * @see #setLocalFlag(LocalFlag, boolean)
     * @see #getLocalFlags()
     * @see #setLocalFlags(EnumSet)
     */
    public enum LocalFlag {
        ECHOKE, /* visual erase for line kill */
        ECHOE, /* visually erase chars */
        ECHOK, /* echo NL after line kill */
        ECHO, /* enable echoing */
        ECHONL, /* echo NL even if ECHO is off */
        ECHOPRT, /* visual erase mode for hardcopy */
        ECHOCTL, /* echo control chars as ^(Char) */
        ISIG, /* enable signals INTR, QUIT, [D]SUSP */
        ICANON, /* canonicalize input lines */
        ALTWERASE, /* use alternate WERASE algorithm */
        IEXTEN, /* enable DISCARD and LNEXT */
        EXTPROC, /* external processing */
        TOSTOP, /* stop background jobs from output */
        FLUSHO, /* output being flushed (state) */
        NOKERNINFO, /* no kernel output from VSTATUS */
        PENDIN, /* XXX retype pending input (state) */
        NOFLSH /* don't flush after interrupt */
    }

    final EnumSet<InputFlag> iflag = EnumSet.noneOf(InputFlag.class);
    final EnumSet<OutputFlag> oflag = EnumSet.noneOf(OutputFlag.class);
    final EnumSet<ControlFlag> cflag = EnumSet.noneOf(ControlFlag.class);
    final EnumSet<LocalFlag> lflag = EnumSet.noneOf(LocalFlag.class);
    final EnumMap<ControlChar, Integer> cchars = new EnumMap<>(ControlChar.class);

    /**
     * Creates a new Attributes instance with default settings.
     *
     * <p>
     * This constructor creates an Attributes object with all flags unset and
     * all control characters undefined. The attributes can be modified using
     * the various setter methods.
     * </p>
     */
    public Attributes() {}

    /**
     * Creates a new Attributes instance by copying another Attributes object.
     *
     * <p>
     * This constructor creates a new Attributes object with the same settings
     * as the specified Attributes object. All flags and control characters are
     * copied from the source object.
     * </p>
     *
     * @param attr the Attributes object to copy
     * @see #copy(Attributes)
     */
    @SuppressWarnings("this-escape")
    public Attributes(Attributes attr) {
        copy(attr);
    }

    //
    // Input flags
    //

    /**
     * Returns the set of input flags currently enabled.
     *
     * <p>
     * This method returns a reference to the internal set of input flags.
     * Changes to the returned set will directly affect this Attributes object.
     * </p>
     *
     * @return the set of enabled input flags
     * @see InputFlag
     * @see #setInputFlags(EnumSet)
     */
    public EnumSet<InputFlag> getInputFlags() {
        return iflag;
    }

    /**
     * Sets the input flags to the specified set of flags.
     *
     * <p>
     * This method replaces all current input flags with the specified set.
     * Any previously enabled flags not in the new set will be disabled.
     * </p>
     *
     * @param flags the set of input flags to enable
     * @see InputFlag
     * @see #getInputFlags()
     */
    public void setInputFlags(EnumSet<InputFlag> flags) {
        iflag.clear();
        iflag.addAll(flags);
    }

    /**
     * Checks if a specific input flag is enabled.
     *
     * <p>
     * This method returns whether the specified input flag is currently enabled
     * in this Attributes object.
     * </p>
     *
     * @param flag the input flag to check
     * @return {@code true} if the flag is enabled, {@code false} otherwise
     * @see InputFlag
     * @see #setInputFlag(InputFlag, boolean)
     */
    public boolean getInputFlag(InputFlag flag) {
        return iflag.contains(flag);
    }

    /**
     * Sets multiple input flags to the same value.
     *
     * <p>
     * This method enables or disables all the specified input flags based on the
     * value parameter. If value is true, all flags in the set will be enabled.
     * If value is false, all flags in the set will be disabled.
     * </p>
     *
     * @param flags the set of input flags to modify
     * @param value {@code true} to enable the flags, {@code false} to disable them
     * @see InputFlag
     * @see #setInputFlag(InputFlag, boolean)
     */
    public void setInputFlags(EnumSet<InputFlag> flags, boolean value) {
        if (value) {
            iflag.addAll(flags);
        } else {
            iflag.removeAll(flags);
        }
    }

    /**
     * Sets a specific input flag to the specified value.
     *
     * <p>
     * This method enables or disables a single input flag based on the value parameter.
     * If value is true, the flag will be enabled. If value is false, the flag will be disabled.
     * </p>
     *
     * @param flag the input flag to modify
     * @param value {@code true} to enable the flag, {@code false} to disable it
     * @see InputFlag
     * @see #getInputFlag(InputFlag)
     */
    public void setInputFlag(InputFlag flag, boolean value) {
        if (value) {
            iflag.add(flag);
        } else {
            iflag.remove(flag);
        }
    }

    //
    // Output flags
    //

    public EnumSet<OutputFlag> getOutputFlags() {
        return oflag;
    }

    public void setOutputFlags(EnumSet<OutputFlag> flags) {
        oflag.clear();
        oflag.addAll(flags);
    }

    public boolean getOutputFlag(OutputFlag flag) {
        return oflag.contains(flag);
    }

    public void setOutputFlags(EnumSet<OutputFlag> flags, boolean value) {
        if (value) {
            oflag.addAll(flags);
        } else {
            oflag.removeAll(flags);
        }
    }

    public void setOutputFlag(OutputFlag flag, boolean value) {
        if (value) {
            oflag.add(flag);
        } else {
            oflag.remove(flag);
        }
    }

    //
    // Control flags
    //

    public EnumSet<ControlFlag> getControlFlags() {
        return cflag;
    }

    public void setControlFlags(EnumSet<ControlFlag> flags) {
        cflag.clear();
        cflag.addAll(flags);
    }

    public boolean getControlFlag(ControlFlag flag) {
        return cflag.contains(flag);
    }

    public void setControlFlags(EnumSet<ControlFlag> flags, boolean value) {
        if (value) {
            cflag.addAll(flags);
        } else {
            cflag.removeAll(flags);
        }
    }

    public void setControlFlag(ControlFlag flag, boolean value) {
        if (value) {
            cflag.add(flag);
        } else {
            cflag.remove(flag);
        }
    }

    //
    // Local flags
    //

    public EnumSet<LocalFlag> getLocalFlags() {
        return lflag;
    }

    public void setLocalFlags(EnumSet<LocalFlag> flags) {
        lflag.clear();
        lflag.addAll(flags);
    }

    public boolean getLocalFlag(LocalFlag flag) {
        return lflag.contains(flag);
    }

    public void setLocalFlags(EnumSet<LocalFlag> flags, boolean value) {
        if (value) {
            lflag.addAll(flags);
        } else {
            lflag.removeAll(flags);
        }
    }

    public void setLocalFlag(LocalFlag flag, boolean value) {
        if (value) {
            lflag.add(flag);
        } else {
            lflag.remove(flag);
        }
    }

    //
    // Control chars
    //

    /**
     * Returns the map of control characters and their values.
     *
     * <p>
     * This method returns a reference to the internal map of control characters.
     * Changes to the returned map will directly affect this Attributes object.
     * </p>
     *
     * @return the map of control characters to their values
     * @see ControlChar
     * @see #setControlChars(EnumMap)
     */
    public EnumMap<ControlChar, Integer> getControlChars() {
        return cchars;
    }

    /**
     * Sets the control characters to the specified map of values.
     *
     * <p>
     * This method replaces all current control character settings with the
     * specified map. Any previously set control characters not in the new map
     * will be unset.
     * </p>
     *
     * @param chars the map of control characters to their values
     * @see ControlChar
     * @see #getControlChars()
     */
    public void setControlChars(EnumMap<ControlChar, Integer> chars) {
        cchars.clear();
        cchars.putAll(chars);
    }

    /**
     * Returns the value of a specific control character.
     *
     * <p>
     * This method returns the current value of the specified control character,
     * or -1 if the control character is not defined.
     * </p>
     *
     * <p>
     * For most control characters, the value represents the ASCII code of the
     * character. For {@link ControlChar#VMIN} and {@link ControlChar#VTIME},
     * the values have special meanings related to non-canonical input mode.
     * </p>
     *
     * @param c the control character to retrieve
     * @return the value of the control character, or -1 if not defined
     * @see ControlChar
     * @see #setControlChar(ControlChar, int)
     */
    public int getControlChar(ControlChar c) {
        Integer v = cchars.get(c);
        return v != null ? v : -1;
    }

    /**
     * Sets a specific control character to the specified value.
     *
     * <p>
     * This method sets the value of the specified control character.
     * </p>
     *
     * <p>
     * For most control characters, the value should be the ASCII code of the
     * character. For {@link ControlChar#VMIN} and {@link ControlChar#VTIME},
     * the values have special meanings:
     * </p>
     * <ul>
     *   <li>VMIN - Minimum number of characters for non-canonical read</li>
     *   <li>VTIME - Timeout in deciseconds for non-canonical read</li>
     * </ul>
     *
     * @param c the control character to set
     * @param value the value to set for the control character
     * @see ControlChar
     * @see #getControlChar(ControlChar)
     */
    public void setControlChar(ControlChar c, int value) {
        cchars.put(c, value);
    }

    //
    // Miscellaneous methods
    //

    /**
     * Copies all settings from another Attributes object to this one.
     *
     * <p>
     * This method copies all flags and control characters from the specified
     * Attributes object to this object. Any previous settings in this object
     * will be overwritten.
     * </p>
     *
     * @param attributes the Attributes object to copy from
     */
    public void copy(Attributes attributes) {
        setControlFlags(attributes.getControlFlags());
        setInputFlags(attributes.getInputFlags());
        setLocalFlags(attributes.getLocalFlags());
        setOutputFlags(attributes.getOutputFlags());
        setControlChars(attributes.getControlChars());
    }

    @Override
    public String toString() {
        return "Attributes[" + "lflags: "
                + append(lflag) + ", " + "iflags: "
                + append(iflag) + ", " + "oflags: "
                + append(oflag) + ", " + "cflags: "
                + append(cflag) + ", " + "cchars: "
                + append(EnumSet.allOf(ControlChar.class), this::display) + "]";
    }

    private String display(ControlChar c) {
        String value;
        int ch = getControlChar(c);
        if (c == ControlChar.VMIN || c == ControlChar.VTIME) {
            value = Integer.toString(ch);
        } else if (ch < 0) {
            value = "<undef>";
        } else if (ch < 32) {
            value = "^" + (char) (ch + 'A' - 1);
        } else if (ch == 127) {
            value = "^?";
        } else if (ch >= 128) {
            value = String.format("\\u%04x", ch);
        } else {
            value = String.valueOf((char) ch);
        }
        return c.name().toLowerCase().substring(1) + "=" + value;
    }

    private <T extends Enum<T>> String append(EnumSet<T> set) {
        return append(set, e -> e.name().toLowerCase());
    }

    private <T extends Enum<T>> String append(EnumSet<T> set, Function<T, String> toString) {
        return set.stream().map(toString).collect(Collectors.joining(" "));
    }
}
