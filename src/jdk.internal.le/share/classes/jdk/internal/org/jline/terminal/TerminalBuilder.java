/*
 * Copyright (c) 2002-2021, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.org.jline.terminal.impl.AbstractPosixTerminal;
import jdk.internal.org.jline.terminal.impl.AbstractTerminal;
import jdk.internal.org.jline.terminal.impl.DumbTerminal;
import jdk.internal.org.jline.terminal.impl.DumbTerminalProvider;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalExt;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.Log;
import jdk.internal.org.jline.utils.OSUtils;

/**
 * Builder class to create terminals.
 */
public final class TerminalBuilder {

    //
    // System properties
    //

    public static final String PROP_ENCODING = "org.jline.terminal.encoding";
    public static final String PROP_CODEPAGE = "org.jline.terminal.codepage";
    public static final String PROP_TYPE = "org.jline.terminal.type";
    public static final String PROP_PROVIDER = "org.jline.terminal.provider";
    public static final String PROP_PROVIDERS = "org.jline.terminal.providers";
    public static final String PROP_PROVIDER_FFM = "ffm";
    public static final String PROP_PROVIDER_JNI = "jni";
    public static final String PROP_PROVIDER_JANSI = "jansi";
    public static final String PROP_PROVIDER_JNA = "jna";
    public static final String PROP_PROVIDER_EXEC = "exec";
    public static final String PROP_PROVIDER_DUMB = "dumb";
    public static final String PROP_PROVIDERS_DEFAULT = String.join(
            ",", PROP_PROVIDER_FFM, PROP_PROVIDER_JNI, PROP_PROVIDER_JANSI, PROP_PROVIDER_JNA, PROP_PROVIDER_EXEC);
    public static final String PROP_FFM = "org.jline.terminal." + PROP_PROVIDER_FFM;
    public static final String PROP_JNI = "org.jline.terminal." + PROP_PROVIDER_JNI;
    public static final String PROP_JANSI = "org.jline.terminal." + PROP_PROVIDER_JANSI;
    public static final String PROP_JNA = "org.jline.terminal." + PROP_PROVIDER_JNA;
    public static final String PROP_EXEC = "org.jline.terminal." + PROP_PROVIDER_EXEC;
    public static final String PROP_DUMB = "org.jline.terminal." + PROP_PROVIDER_DUMB;
    public static final String PROP_DUMB_COLOR = "org.jline.terminal.dumb.color";
    public static final String PROP_OUTPUT = "org.jline.terminal.output";
    public static final String PROP_OUTPUT_OUT = "out";
    public static final String PROP_OUTPUT_ERR = "err";
    public static final String PROP_OUTPUT_OUT_ERR = "out-err";
    public static final String PROP_OUTPUT_ERR_OUT = "err-out";
    public static final String PROP_OUTPUT_FORCED_OUT = "forced-out";
    public static final String PROP_OUTPUT_FORCED_ERR = "forced-err";

    //
    // Other system properties controlling various jline parts
    //

    public static final String PROP_NON_BLOCKING_READS = "org.jline.terminal.pty.nonBlockingReads";
    public static final String PROP_COLOR_DISTANCE = "org.jline.utils.colorDistance";
    public static final String PROP_DISABLE_ALTERNATE_CHARSET = "org.jline.utils.disableAlternateCharset";

    //
    // System properties controlling how FileDescriptor are create.
    // The value can be a comma separated list of defined mechanisms.
    //
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE = "org.jline.terminal.pty.fileDescriptorCreationMode";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_NATIVE = "native";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION = "reflection";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_DEFAULT =
            String.join(",", PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION, PROP_FILE_DESCRIPTOR_CREATION_MODE_NATIVE);

    //
    // System properties controlling how RedirectPipe are created.
    // The value can be a comma separated list of defined mechanisms.
    //
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE = "org.jline.terminal.exec.redirectPipeCreationMode";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE = "native";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION = "reflection";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT =
            String.join(",", PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION, PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE);

    public static final Set<String> DEPRECATED_PROVIDERS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(PROP_PROVIDER_JNA, PROP_PROVIDER_JANSI)));

    public static final String PROP_DISABLE_DEPRECATED_PROVIDER_WARNING =
            "org.jline.terminal.disableDeprecatedProviderWarning";

    //
    // Terminal output control
    //
    public enum SystemOutput {
        SysOut,
        SysErr,
        SysOutOrSysErr,
        SysErrOrSysOut,
        ForcedSysOut,
        ForcedSysErr
    }

    /**
     * Returns the default system terminal.
     * Terminals should be closed properly using the {@link Terminal#close()}
     * method in order to restore the original terminal state.
     *
     * <p>
     * This call is equivalent to:
     * <code>builder().build()</code>
     * </p>
     *
     * @return the default system terminal
     * @throws IOException if an error occurs
     */
    public static Terminal terminal() throws IOException {
        return builder().build();
    }

    /**
     * Creates a new terminal builder instance.
     *
     * @return a builder
     */
    public static TerminalBuilder builder() {
        return new TerminalBuilder();
    }

    private static final AtomicReference<Terminal> SYSTEM_TERMINAL = new AtomicReference<>();
    private static final AtomicReference<Terminal> TERMINAL_OVERRIDE = new AtomicReference<>();

    private String name;
    private InputStream in;
    private OutputStream out;
    private String type;
    private Charset encoding;
    private int codepage;
    private Boolean system;
    private SystemOutput systemOutput;
    private String provider;
    private String providers;
    private Boolean jna;
    private Boolean jansi;
    private Boolean jni;
    private Boolean exec;
    private Boolean ffm;
    private Boolean dumb;
    private Boolean color;
    private Attributes attributes;
    private Size size;
    private boolean nativeSignals = true;
    private Function<InputStream, InputStream> inputStreamWrapper = in -> in;
    private Terminal.SignalHandler signalHandler = Terminal.SignalHandler.SIG_DFL;
    private boolean paused = false;

    private TerminalBuilder() {}

    public TerminalBuilder name(String name) {
        this.name = name;
        return this;
    }

    public TerminalBuilder streams(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        return this;
    }

    public TerminalBuilder system(boolean system) {
        this.system = system;
        return this;
    }

    /**
     * Indicates which standard stream should be used when displaying to the terminal.
     * The default is to use the system output stream.
     * Building a system terminal will fail if one of the stream specified is not linked
     * to the controlling terminal.
     *
     * @param systemOutput The mode to choose the output stream.
     * @return The builder.
     */
    public TerminalBuilder systemOutput(SystemOutput systemOutput) {
        this.systemOutput = systemOutput;
        return this;
    }

    /**
     * Forces the usage of the give terminal provider.
     *
     * @param provider The {@link TerminalProvider}'s name to use when creating the Terminal.
     * @return The builder.
     */
    public TerminalBuilder provider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Sets the list of providers to try when creating the terminal.
     * If not specified, the system property {@link #PROP_PROVIDERS} will be used if set.
     * Else, the value {@link #PROP_PROVIDERS_DEFAULT} will be used.
     *
     * @param providers The list of {@link TerminalProvider}'s names to check when creating the Terminal.
     * @return The builder.
     */
    public TerminalBuilder providers(String providers) {
        this.providers = providers;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_JNA}/{@code jna} terminal provider.
     * If not specified, the system property {@link #PROP_JNA} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder jna(boolean jna) {
        this.jna = jna;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_JANSI}/{@code jansi} terminal provider.
     * If not specified, the system property {@link #PROP_JANSI} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder jansi(boolean jansi) {
        this.jansi = jansi;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_JNI}/{@code jni} terminal provider.
     * If not specified, the system property {@link #PROP_JNI} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder jni(boolean jni) {
        this.jni = jni;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_EXEC}/{@code exec} terminal provider.
     * If not specified, the system property {@link #PROP_EXEC} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder exec(boolean exec) {
        this.exec = exec;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_FFM}/{@code ffm} terminal provider.
     * If not specified, the system property {@link #PROP_FFM} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder ffm(boolean ffm) {
        this.ffm = ffm;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_DUMB}/{@code dumb} terminal provider.
     * If not specified, the system property {@link #PROP_DUMB} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder dumb(boolean dumb) {
        this.dumb = dumb;
        return this;
    }

    public TerminalBuilder type(String type) {
        this.type = type;
        return this;
    }

    public TerminalBuilder color(boolean color) {
        this.color = color;
        return this;
    }

    /**
     * Set the encoding to use for reading/writing from the console.
     * If {@code null} (the default value), JLine will automatically select
     * a {@link Charset}, usually the default system encoding. However,
     * on some platforms (e.g. Windows) it may use a different one depending
     * on the {@link Terminal} implementation.
     *
     * <p>Use {@link Terminal#encoding()} to get the {@link Charset} that
     * should be used for a {@link Terminal}.</p>
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @throws UnsupportedCharsetException If the given encoding is not supported
     * @see Terminal#encoding()
     */
    public TerminalBuilder encoding(String encoding) throws UnsupportedCharsetException {
        return encoding(encoding != null ? Charset.forName(encoding) : null);
    }

    /**
     * Set the {@link Charset} to use for reading/writing from the console.
     * If {@code null} (the default value), JLine will automatically select
     * a {@link Charset}, usually the default system encoding. However,
     * on some platforms (e.g. Windows) it may use a different one depending
     * on the {@link Terminal} implementation.
     *
     * <p>Use {@link Terminal#encoding()} to get the {@link Charset} that
     * should be used to read/write from a {@link Terminal}.</p>
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @see Terminal#encoding()
     */
    public TerminalBuilder encoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * @param codepage the codepage
     * @return The builder
     * @deprecated JLine now writes Unicode output independently from the selected
     *   code page. Using this option will only make it emulate the selected code
     *   page for {@link Terminal#input()} and {@link Terminal#output()}.
     */
    @Deprecated
    public TerminalBuilder codepage(int codepage) {
        this.codepage = codepage;
        return this;
    }

    /**
     * Attributes to use when creating a non system terminal,
     * i.e. when the builder has been given the input and
     * output streams using the {@link #streams(InputStream, OutputStream)} method
     * or when {@link #system(boolean)} has been explicitly called with
     * <code>false</code>.
     *
     * @param attributes the attributes to use
     * @return The builder
     * @see #size(Size)
     * @see #system(boolean)
     */
    public TerminalBuilder attributes(Attributes attributes) {
        this.attributes = attributes;
        return this;
    }

    /**
     * Initial size to use when creating a non system terminal,
     * i.e. when the builder has been given the input and
     * output streams using the {@link #streams(InputStream, OutputStream)} method
     * or when {@link #system(boolean)} has been explicitly called with
     * <code>false</code>.
     *
     * @param size the initial size
     * @return The builder
     * @see #attributes(Attributes)
     * @see #system(boolean)
     */
    public TerminalBuilder size(Size size) {
        this.size = size;
        return this;
    }

    public TerminalBuilder nativeSignals(boolean nativeSignals) {
        this.nativeSignals = nativeSignals;
        return this;
    }

    /**
     * Determines the default value for signal handlers.
     * All signals will be mapped to the given handler.
     * @param signalHandler the default signal handler
     * @return The builder
     */
    public TerminalBuilder signalHandler(Terminal.SignalHandler signalHandler) {
        this.signalHandler = signalHandler;
        return this;
    }

    public TerminalBuilder inputStreamWrapper(Function<InputStream, InputStream> wrapper) {
        this.inputStreamWrapper = wrapper;
        return this;
    }

    /**
     * Initial paused state of the terminal (defaults to false).
     * By default, the terminal is started, but in some cases,
     * one might want to make sure the input stream is not consumed
     * before needed, in which case the terminal needs to be created
     * in a paused state.
     * @param paused the initial paused state
     * @return The builder
     * @see Terminal#pause()
     */
    public TerminalBuilder paused(boolean paused) {
        this.paused = paused;
        return this;
    }

    /**
     * Builds the terminal.
     * @return the newly created terminal, never {@code null}
     * @throws IOException if an error occurs
     */
    public Terminal build() throws IOException {
        Terminal override = TERMINAL_OVERRIDE.get();
        Terminal terminal = override != null ? override : doBuild();
        if (override != null) {
            Log.debug(() -> "Overriding terminal with global value set by TerminalBuilder.setTerminalOverride");
        }
        Log.debug(() -> "Using terminal " + terminal.getClass().getSimpleName());
        if (terminal instanceof AbstractPosixTerminal) {
            Log.debug(() -> "Using pty "
                    + ((AbstractPosixTerminal) terminal).getPty().getClass().getSimpleName());
        }
        return terminal;
    }

    private Terminal doBuild() throws IOException {
        String name = this.name;
        if (name == null) {
            name = "JLine terminal";
        }
        Charset encoding = computeEncoding();
        String type = computeType();

        String provider = this.provider;
        if (provider == null) {
            provider = System.getProperty(PROP_PROVIDER, null);
        }

        boolean forceDumb =
                (DumbTerminal.TYPE_DUMB.equals(type) || type != null && type.startsWith(DumbTerminal.TYPE_DUMB_COLOR))
                        || (provider != null && provider.equals(PROP_PROVIDER_DUMB));
        Boolean dumb = this.dumb;
        if (dumb == null) {
            dumb = getBoolean(PROP_DUMB, null);
        }
        IllegalStateException exception = new IllegalStateException("Unable to create a terminal");
        List<TerminalProvider> providers = getProviders(provider, exception);
        Terminal terminal = null;
        if ((system != null && system) || (system == null && in == null && out == null)) {
            if (system != null
                    && ((in != null && !in.equals(System.in))
                            || (out != null && !out.equals(System.out) && !out.equals(System.err)))) {
                throw new IllegalArgumentException("Cannot create a system terminal using non System streams");
            }
            if (attributes != null || size != null) {
                Log.warn("Attributes and size fields are ignored when creating a system terminal");
            }
            SystemOutput systemOutput = computeSystemOutput();
            Map<SystemStream, Boolean> system = Stream.of(SystemStream.values())
                    .collect(Collectors.toMap(
                            stream -> stream, stream -> providers.stream().anyMatch(p -> p.isSystemStream(stream))));
            SystemStream systemStream = select(system, systemOutput);

            if (!forceDumb && system.get(SystemStream.Input) && systemStream != null) {
                if (attributes != null || size != null) {
                    Log.warn("Attributes and size fields are ignored when creating a system terminal");
                }
                boolean ansiPassThrough = OSUtils.IS_CONEMU;
                // Cygwin defaults to XTERM, but actually supports 256 colors,
                // so if the value comes from the environment, change it to xterm-256color
                if ((OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM)
                        && "xterm".equals(type)
                        && this.type == null
                        && System.getProperty(PROP_TYPE) == null) {
                    type = "xterm-256color";
                }
                for (TerminalProvider prov : providers) {
                    if (terminal == null) {
                        try {
                            terminal = prov.sysTerminal(
                                    name,
                                    type,
                                    ansiPassThrough,
                                    encoding,
                                    nativeSignals,
                                    signalHandler,
                                    paused,
                                    systemStream,
                                    inputStreamWrapper);
                        } catch (Throwable t) {
                            Log.debug("Error creating " + prov.name() + " based terminal: ", t.getMessage(), t);
                            exception.addSuppressed(t);
                        }
                    }
                }
                if (terminal == null && OSUtils.IS_WINDOWS && providers.isEmpty() && (dumb == null || !dumb)) {
                    throw new IllegalStateException(
                            "Unable to create a system terminal. On Windows, either JLine's native libraries, JNA "
                                    + "or Jansi library is required.  Make sure to add one of those in the classpath.",
                            exception);
                }
            }
            if (terminal instanceof AbstractTerminal) {
                AbstractTerminal t = (AbstractTerminal) terminal;
                if (SYSTEM_TERMINAL.compareAndSet(null, t)) {
                    t.setOnClose(() -> SYSTEM_TERMINAL.compareAndSet(t, null));
                } else {
                    exception.addSuppressed(new IllegalStateException("A system terminal is already running. "
                            + "Make sure to use the created system Terminal on the LineReaderBuilder if you're using one "
                            + "or that previously created system Terminals have been correctly closed."));
                    terminal.close();
                    terminal = null;
                }
            }
            if (terminal == null && (forceDumb || dumb == null || dumb)) {
                if (!forceDumb && dumb == null) {
                    if (Log.isDebugEnabled()) {
                        Log.warn("input is tty: " + system.get(SystemStream.Input));
                        Log.warn("output is tty: " + system.get(SystemStream.Output));
                        Log.warn("error is tty: " + system.get(SystemStream.Error));
                        Log.warn("Creating a dumb terminal", exception);
                    } else {
                        Log.warn(
                                "Unable to create a system terminal, creating a dumb terminal (enable debug logging for more information)");
                    }
                }
                type = getDumbTerminalType(dumb, systemStream);
                terminal = new DumbTerminalProvider()
                        .sysTerminal(name, type, false, encoding, nativeSignals, signalHandler, paused, systemStream, inputStreamWrapper);
                if (OSUtils.IS_WINDOWS) {
                    Attributes attr = terminal.getAttributes();
                    attr.setInputFlag(Attributes.InputFlag.IGNCR, true);
                    terminal.setAttributes(attr);
                }
            }
        } else {
            for (TerminalProvider prov : providers) {
                if (terminal == null) {
                    try {
                        terminal = prov.newTerminal(
                                name, type, in, out, encoding, signalHandler, paused, attributes, size);
                    } catch (Throwable t) {
                        Log.debug("Error creating " + prov.name() + " based terminal: ", t.getMessage(), t);
                        exception.addSuppressed(t);
                    }
                }
            }
        }
        if (terminal == null) {
            throw exception;
        }
        if (terminal instanceof TerminalExt) {
            TerminalExt te = (TerminalExt) terminal;
            if (DEPRECATED_PROVIDERS.contains(te.getProvider().name())
                    && !getBoolean(PROP_DISABLE_DEPRECATED_PROVIDER_WARNING, false)) {
                Log.warn("The terminal provider " + te.getProvider().name()
                        + " has been deprecated, check your configuration. This warning can be disabled by setting the system property "
                        + PROP_DISABLE_DEPRECATED_PROVIDER_WARNING + " to true.");
            }
        }
        return terminal;
    }

    private String getDumbTerminalType(Boolean dumb, SystemStream systemStream) {
        // forced colored dumb terminal
        Boolean color = this.color;
        if (color == null) {
            color = getBoolean(PROP_DUMB_COLOR, null);
        }
        if (dumb == null) {
            // detect emacs using the env variable
            if (color == null) {
                String emacs = System.getenv("INSIDE_EMACS");
                if (emacs != null && emacs.contains("comint")) {
                    color = true;
                }
            }
            // detect Intellij Idea
            if (color == null) {
                // using the env variable on windows
                String ideHome = System.getenv("IDE_HOME");
                if (ideHome != null) {
                    color = true;
                } else {
                    // using the parent process command on unix/mac
                    String command = getParentProcessCommand();
                    if (command != null && command.endsWith("/idea")) {
                        color = true;
                    }
                }
            }
            if (color == null) {
                color = systemStream != null && System.getenv("TERM") != null;
            }
        } else {
            if (color == null) {
                color = false;
            }
        }
        return color ? Terminal.TYPE_DUMB_COLOR : Terminal.TYPE_DUMB;
    }

    public SystemOutput computeSystemOutput() {
        SystemOutput systemOutput = null;
        if (out != null) {
            if (out.equals(System.out)) {
                systemOutput = SystemOutput.SysOut;
            } else if (out.equals(System.err)) {
                systemOutput = SystemOutput.SysErr;
            }
        }
        if (systemOutput == null) {
            systemOutput = this.systemOutput;
        }
        if (systemOutput == null) {
            String str = System.getProperty(PROP_OUTPUT);
            if (str != null) {
                switch (str.trim().toLowerCase(Locale.ROOT)) {
                    case PROP_OUTPUT_OUT:
                        systemOutput = SystemOutput.SysOut;
                        break;
                    case PROP_OUTPUT_ERR:
                        systemOutput = SystemOutput.SysErr;
                        break;
                    case PROP_OUTPUT_OUT_ERR:
                        systemOutput = SystemOutput.SysOutOrSysErr;
                        break;
                    case PROP_OUTPUT_ERR_OUT:
                        systemOutput = SystemOutput.SysErrOrSysOut;
                        break;
                    case PROP_OUTPUT_FORCED_OUT:
                        systemOutput = SystemOutput.ForcedSysOut;
                        break;
                    case PROP_OUTPUT_FORCED_ERR:
                        systemOutput = SystemOutput.ForcedSysErr;
                        break;
                    default:
                        Log.debug("Unsupported value for " + PROP_OUTPUT + ": " + str + ". Supported values are: "
                                + String.join(
                                        ", ",
                                        PROP_OUTPUT_OUT,
                                        PROP_OUTPUT_ERR,
                                        PROP_OUTPUT_OUT_ERR,
                                        PROP_OUTPUT_ERR_OUT)
                                + ".");
                }
            }
        }
        if (systemOutput == null) {
            systemOutput = SystemOutput.SysOutOrSysErr;
        }
        return systemOutput;
    }

    public String computeType() {
        String type = this.type;
        if (type == null) {
            type = System.getProperty(PROP_TYPE);
        }
        if (type == null) {
            type = System.getenv("TERM");
        }
        return type;
    }

    public Charset computeEncoding() {
        Charset encoding = this.encoding;
        if (encoding == null) {
            String charsetName = System.getProperty(PROP_ENCODING);
            if (charsetName != null && Charset.isSupported(charsetName)) {
                encoding = Charset.forName(charsetName);
            }
        }
        if (encoding == null) {
            int codepage = this.codepage;
            if (codepage <= 0) {
                String str = System.getProperty(PROP_CODEPAGE);
                if (str != null) {
                    codepage = Integer.parseInt(str);
                }
            }
            if (codepage >= 0) {
                encoding = getCodepageCharset(codepage);
            } else {
                encoding = StandardCharsets.UTF_8;
            }
        }
        return encoding;
    }

    /**
     * Get the list of available terminal providers.
     * This list is sorted according to the {@link #PROP_PROVIDERS} system property.
     * @param provider if not {@code null}, only this provider will be checked
     * @param exception if a provider throws an exception, it will be added to this exception as a suppressed exception
     * @return a list of terminal providers
     */
    public List<TerminalProvider> getProviders(String provider, IllegalStateException exception) {
        List<TerminalProvider> providers = new ArrayList<>();
        // Check ffm provider
        checkProvider(provider, exception, providers, ffm, PROP_FFM, PROP_PROVIDER_FFM);
        // Check jni provider
        checkProvider(provider, exception, providers, jni, PROP_JNI, PROP_PROVIDER_JNI);
        // Check jansi provider
        checkProvider(provider, exception, providers, jansi, PROP_JANSI, PROP_PROVIDER_JANSI);
        // Check jna provider
        checkProvider(provider, exception, providers, jna, PROP_JNA, PROP_PROVIDER_JNA);
        // Check exec provider
        checkProvider(provider, exception, providers, exec, PROP_EXEC, PROP_PROVIDER_EXEC);
        // Order providers
        List<String> order = Arrays.asList(
                (this.providers != null ? this.providers : System.getProperty(PROP_PROVIDERS, PROP_PROVIDERS_DEFAULT))
                        .split(","));
        providers.sort(Comparator.comparing(l -> {
            int idx = order.indexOf(l.name());
            return idx >= 0 ? idx : Integer.MAX_VALUE;
        }));
        String names = providers.stream().map(TerminalProvider::name).collect(Collectors.joining(", "));
        Log.debug("Available providers: " + names);
        return providers;
    }

    private void checkProvider(
            String provider,
            IllegalStateException exception,
            List<TerminalProvider> providers,
            Boolean load,
            String property,
            String name) {
        Boolean doLoad = provider != null ? (Boolean) name.equals(provider) : load;
        if (doLoad == null) {
            doLoad = getBoolean(property, true);
        }
        if (doLoad) {
            try {
                TerminalProvider prov = TerminalProvider.load(name);
                prov.isSystemStream(SystemStream.Output);
                providers.add(prov);
            } catch (Throwable t) {
                Log.debug("Unable to load " + name + " provider: ", t);
                exception.addSuppressed(t);
            }
        }
    }

    private SystemStream select(Map<SystemStream, Boolean> system, SystemOutput systemOutput) {
        switch (systemOutput) {
            case SysOut:
                return select(system, SystemStream.Output);
            case SysErr:
                return select(system, SystemStream.Error);
            case SysOutOrSysErr:
                return select(system, SystemStream.Output, SystemStream.Error);
            case SysErrOrSysOut:
                return select(system, SystemStream.Error, SystemStream.Output);
            case ForcedSysOut:
                return SystemStream.Output;
            case ForcedSysErr:
                return SystemStream.Error;
        }
        return null;
    }

    private static SystemStream select(Map<SystemStream, Boolean> system, SystemStream... streams) {
        for (SystemStream s : streams) {
            if (system.get(s)) {
                return s;
            }
        }
        return null;
    }

    private static String getParentProcessCommand() {
        try {
            Class<?> phClass = Class.forName("java.lang.ProcessHandle");
            Object current = phClass.getMethod("current").invoke(null);
            Object parent = ((Optional<?>) phClass.getMethod("parent").invoke(current)).orElse(null);
            Method infoMethod = phClass.getMethod("info");
            Object info = infoMethod.invoke(parent);
            Object command = ((Optional<?>)
                            infoMethod.getReturnType().getMethod("command").invoke(info))
                    .orElse(null);
            return (String) command;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean getBoolean(String name, Boolean def) {
        try {
            String str = System.getProperty(name);
            if (str != null) {
                return Boolean.parseBoolean(str);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        return def;
    }

    private static <S> S load(Class<S> clazz) {
        return ServiceLoader.load(clazz, clazz.getClassLoader()).iterator().next();
    }

    private static final int UTF8_CODE_PAGE = 65001;

    private static Charset getCodepageCharset(int codepage) {
        // http://docs.oracle.com/javase/6/docs/technotes/guides/intl/encoding.doc.html
        if (codepage == UTF8_CODE_PAGE) {
            return StandardCharsets.UTF_8;
        }
        String charsetMS = "ms" + codepage;
        if (Charset.isSupported(charsetMS)) {
            return Charset.forName(charsetMS);
        }
        String charsetCP = "cp" + codepage;
        if (Charset.isSupported(charsetCP)) {
            return Charset.forName(charsetCP);
        }
        return Charset.defaultCharset();
    }

    /**
     * Allows an application to override the result of {@link #build()}. The
     * intended use case is to allow a container or server application to control
     * an embedded application that uses a LineReader that uses Terminal
     * constructed with TerminalBuilder.build but provides no public api for setting
     * the <code>LineReader</code> of the {@link Terminal}. For example, the sbt
     * build tool uses a <code>LineReader</code> to implement an interactive shell.
     * One of its supported commands is <code>console</code> which invokes
     * the scala REPL. The scala REPL also uses a <code>LineReader</code> and it
     * is necessary to override the {@link Terminal} used by the the REPL to
     * share the same {@link Terminal} instance used by sbt.
     *
     * <p>
     * When this method is called with a non-null {@link Terminal}, all subsequent
     * calls to {@link #build()} will return the provided {@link Terminal} regardless
     * of how the {@link TerminalBuilder} was constructed. The default behavior
     * of {@link TerminalBuilder} can be restored by calling setTerminalOverride
     * with a null {@link Terminal}
     * </p>
     *
     * <p>
     * Usage of setTerminalOverride should be restricted to cases where it
     * isn't possible to update the api of the nested application to accept
     * a {@link Terminal instance}.
     * </p>
     *
     * @param terminal the {@link Terminal} to globally override
     */
    @Deprecated
    public static void setTerminalOverride(final Terminal terminal) {
        TERMINAL_OVERRIDE.set(terminal);
    }
}
