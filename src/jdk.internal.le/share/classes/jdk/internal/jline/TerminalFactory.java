/*
 * Copyright (c) 2002-2016, the original author or authors.
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

import jdk.internal.jline.internal.Configuration;
import jdk.internal.jline.internal.Log;
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

    public static final String OSV = "osv";

    public static final String WIN = "win";

    public static final String WINDOWS = "windows";

    public static final String FREEBSD = "freebsd";

    public static final String NONE = "none";

    public static final String OFF = "off";

    public static final String FALSE = "false";

    private static Terminal term = null;

    public static synchronized Terminal create() {
        return create(null);
    }

    public static synchronized Terminal create(String ttyDevice) {
        if (Log.TRACE) {
            //noinspection ThrowableInstanceNeverThrown
            Log.trace(new Throwable("CREATE MARKER"));
        }

        String defaultType = "dumb".equals(System.getenv("TERM")) ? NONE : AUTO;
        String type  = Configuration.getString(JLINE_TERMINAL, defaultType);

        Log.debug("Creating terminal; type=", type);

        Terminal t;
        try {
            String tmp = type.toLowerCase();

            if (tmp.equals(UNIX)) {
                t = getFlavor(Flavor.UNIX);
            }
            else if (tmp.equals(OSV)) {
                t = getFlavor(Flavor.OSV);
            }
            else if (tmp.equals(WIN) || tmp.equals(WINDOWS)) {
                t = getFlavor(Flavor.WINDOWS);
            }
            else if (tmp.equals(NONE) || tmp.equals(OFF) || tmp.equals(FALSE)) {
                if (System.getenv("INSIDE_EMACS") != null) {
                    // emacs requires ansi on and echo off
                    t = new UnsupportedTerminal(true, false);
                } else  {
                    // others the other way round
                    t = new UnsupportedTerminal(false, true);
                }
            }
            else {
                if (tmp.equals(AUTO)) {
                    String os = Configuration.getOsName();
                    Flavor flavor = Flavor.UNIX;
                    if (os.contains(WINDOWS)) {
                        flavor = Flavor.WINDOWS;
                    } else if (System.getenv("OSV_CPUS") != null) {
                        flavor = Flavor.OSV;
                    }
                    t = getFlavor(flavor, ttyDevice);
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
        OSV,
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
        UNIX,
        OSV
    }

    private static final Map<Flavor, TerminalConstructor> FLAVORS = new HashMap<>();

    static {
        registerFlavor(Flavor.WINDOWS, ttyDevice -> new WindowsTerminal());
        registerFlavor(Flavor.UNIX, ttyDevice -> new UnixTerminal(ttyDevice));
        registerFlavor(Flavor.OSV, ttyDevice -> new OSvTerminal());
    }

    public static synchronized Terminal get(String ttyDevice) {
        // The code is assuming we've got only one terminal per process.
        // Continuing this assumption, if this terminal is already initialized,
        // we don't check if it's using the same tty line either. Both assumptions
        // are a bit crude. TODO: check single terminal assumption.
        if (term == null) {
            term = create(ttyDevice);
        }
        return term;
    }

    public static synchronized Terminal get() {
        return get(null);
    }

    public static Terminal getFlavor(final Flavor flavor) throws Exception {
        return getFlavor(flavor, null);
    }

    @SuppressWarnings("deprecation")
    public static Terminal getFlavor(final Flavor flavor, String ttyDevice) throws Exception {
        TerminalConstructor factory = FLAVORS.get(flavor);
        if (factory != null) {
            return factory.createTerminal(ttyDevice);
        } else {
            throw new InternalError();
        }
    }

    public static void registerFlavor(final Flavor flavor, final TerminalConstructor factory) {
        FLAVORS.put(flavor, factory);
    }

    public interface TerminalConstructor {
        public Terminal createTerminal(String str) throws Exception;
    }
}
