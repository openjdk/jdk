/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import jdk.internal.jline.internal.Configuration;
import jdk.internal.jline.internal.Log;
import jdk.internal.jline.internal.Preconditions;
import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Creates terminal instances.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.0
 */
public class TerminalFactory
{
    public static final String JLINE_TERMINAL = "jline.terminal";

    public static final String AUTO = "auto";

    public static final String UNIX = "unix";

    public static final String WIN = "win";

    public static final String WINDOWS = "windows";

    public static final String NONE = "none";

    public static final String OFF = "off";

    public static final String FALSE = "false";

    private static Terminal term = null;

    public static synchronized Terminal create() {
        if (Log.TRACE) {
            //noinspection ThrowableInstanceNeverThrown
            Log.trace(new Throwable("CREATE MARKER"));
        }

        String type = Configuration.getString(JLINE_TERMINAL, AUTO);
        if ("dumb".equals(System.getenv("TERM"))) {
            type = "none";
            Log.debug("$TERM=dumb; setting type=", type);
        }

        Log.debug("Creating terminal; type=", type);

        Terminal t;
        try {
            String tmp = type.toLowerCase();

            if (tmp.equals(UNIX)) {
                t = getFlavor(Flavor.UNIX);
            }
            else if (tmp.equals(WIN) | tmp.equals(WINDOWS)) {
                t = getFlavor(Flavor.WINDOWS);
            }
            else if (tmp.equals(NONE) || tmp.equals(OFF) || tmp.equals(FALSE)) {
                t = new UnsupportedTerminal();
            }
            else {
                if (tmp.equals(AUTO)) {
                    String os = Configuration.getOsName();
                    Flavor flavor = Flavor.UNIX;
                    if (os.contains(WINDOWS)) {
                        flavor = Flavor.WINDOWS;
                    }
                    t = getFlavor(flavor);
                }
                else {
                    try {
                        @SuppressWarnings("deprecation")
                        Object o = Thread.currentThread().getContextClassLoader().loadClass(type).newInstance();
                        t = (Terminal) o;
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException(MessageFormat.format("Invalid terminal type: {0}", type), e);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error("Failed to construct terminal; falling back to unsupported", e);
            t = new UnsupportedTerminal();
        }

        Log.debug("Created Terminal: ", t);

        try {
            t.init();
        }
        catch (Throwable e) {
            Log.error("Terminal initialization failed; falling back to unsupported", e);
            return new UnsupportedTerminal();
        }

        return t;
    }

    public static synchronized void reset() {
        term = null;
    }

    public static synchronized void resetIf(final Terminal t) {
        if(t == term) {
            reset();
        }
    }

    public static enum Type
    {
        AUTO,
        WINDOWS,
        UNIX,
        NONE
    }

    public static synchronized void configure(final String type) {
        checkNotNull(type);
        System.setProperty(JLINE_TERMINAL, type);
    }

    public static synchronized void configure(final Type type) {
        checkNotNull(type);
        configure(type.name().toLowerCase());
    }

    //
    // Flavor Support
    //

    public static enum Flavor
    {
        WINDOWS,
        UNIX
    }

    private static final Map<Flavor, Callable<? extends Terminal>> FLAVORS = new HashMap<>();

    static {
//        registerFlavor(Flavor.WINDOWS, AnsiWindowsTerminal.class);
//        registerFlavor(Flavor.UNIX, UnixTerminal.class);
        registerFlavor(Flavor.WINDOWS, WindowsTerminal :: new);
        registerFlavor(Flavor.UNIX, UnixTerminal :: new);
    }

    public static synchronized Terminal get() {
        if (term == null) {
            term = create();
        }
        return term;
    }

    public static Terminal getFlavor(final Flavor flavor) throws Exception {
        return FLAVORS.getOrDefault(flavor, () -> {throw new InternalError();}).call();
    }

    public static void registerFlavor(final Flavor flavor, final Callable<? extends Terminal> sup) {
        FLAVORS.put(flavor, sup);
    }

}
