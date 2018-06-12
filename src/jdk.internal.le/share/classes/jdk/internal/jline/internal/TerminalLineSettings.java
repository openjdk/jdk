/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Provides access to terminal line settings via <tt>stty</tt>.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:dwkemp@gmail.com">Dale Kemp</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:jbonofre@apache.org">Jean-Baptiste Onofr\u00E9</a>
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.0
 */
public final class TerminalLineSettings
{
    public static final String JLINE_STTY = "jline.stty";

    public static final String DEFAULT_STTY = "stty";

    public static final String JLINE_SH = "jline.sh";

    public static final String DEFAULT_SH = "sh";

    private static final String UNDEFINED;

    public static final String DEFAULT_TTY = "/dev/tty";

    private static final boolean SUPPORTS_REDIRECT;

    private static final Object REDIRECT_INHERIT;
    private static final Method REDIRECT_INPUT_METHOD;

    private static final Map<String, TerminalLineSettings> SETTINGS = new HashMap<String, TerminalLineSettings>();

    static {
        if (Configuration.isHpux()) {
            UNDEFINED = "^-";
        } else {
            UNDEFINED = "undef";
        }

        boolean supportsRedirect;
        Object redirectInherit = null;
        Method redirectInputMethod = null;
        try {
            Class<?> redirect = Class.forName("java.lang.ProcessBuilder$Redirect");
            redirectInherit = redirect.getField("INHERIT").get(null);
            redirectInputMethod = ProcessBuilder.class.getMethod("redirectInput", redirect);
            supportsRedirect = System.class.getMethod("console").invoke(null) != null;
        } catch (Throwable t) {
            supportsRedirect = false;
        }
        SUPPORTS_REDIRECT = supportsRedirect;
        REDIRECT_INHERIT = redirectInherit;
        REDIRECT_INPUT_METHOD = redirectInputMethod;
    }

    private String sttyCommand;

    private String shCommand;

    private String ttyDevice;

    private String config;
    private String initialConfig;

    private long configLastFetched;

    private boolean useRedirect;

    @Deprecated
    public TerminalLineSettings() throws IOException, InterruptedException {
        this(DEFAULT_TTY);
    }

    @Deprecated
    public TerminalLineSettings(String ttyDevice) throws IOException, InterruptedException {
        this(ttyDevice, false);
    }

    private TerminalLineSettings(String ttyDevice, boolean unused) throws IOException, InterruptedException {
        checkNotNull(ttyDevice);
        this.sttyCommand = Configuration.getString(JLINE_STTY, DEFAULT_STTY);
        this.shCommand = Configuration.getString(JLINE_SH, DEFAULT_SH);
        this.ttyDevice = ttyDevice;
        this.useRedirect = SUPPORTS_REDIRECT && DEFAULT_TTY.equals(ttyDevice);
        this.initialConfig = get("-g").trim();
        this.config = get("-a");
        this.configLastFetched = System.currentTimeMillis();

        Log.debug("Config: ", config);

        // sanity check
        if (config.length() == 0) {
            throw new IOException(MessageFormat.format("Unrecognized stty code: {0}", config));
        }
    }

    public static synchronized TerminalLineSettings getSettings(String device) throws IOException, InterruptedException {
        TerminalLineSettings settings = SETTINGS.get(device);
        if (settings == null) {
            settings = new TerminalLineSettings(device, false);
            SETTINGS.put(device, settings);
        }
        return settings;
    }

    public String getTtyDevice() {
        return ttyDevice;
    }

    public String getConfig() {
        return config;
    }

    public void restore() throws IOException, InterruptedException {
        set(initialConfig);
    }

    public String get(final String args) throws IOException, InterruptedException {
        checkNotNull(args);
        return stty(args);
    }

    public void set(final String args) throws IOException, InterruptedException {
        checkNotNull(args);
        stty(args.split(" "));
    }

    public void set(final String... args) throws IOException, InterruptedException {
        checkNotNull(args);
        stty(args);
    }

    public void undef(final String name) throws IOException, InterruptedException {
        checkNotNull(name);
        stty(name, UNDEFINED);
    }

    /**
     * <p>
     * Get the value of a stty property, including the management of a cache.
     * </p>
     *
     * @param name the stty property.
     * @return the stty property value.
     */
    public int getProperty(String name) {
        checkNotNull(name);
        if (!fetchConfig(name)) {
            return -1;
        }
        return getProperty(name, config);
    }

    public String getPropertyAsString(String name) {
        checkNotNull(name);
        if (!fetchConfig(name)) {
            return null;
        }
        return getPropertyAsString(name, config);
    }

    private boolean fetchConfig(String name) {
        long currentTime = System.currentTimeMillis();
        try {
            // tty properties are cached so we don't have to worry too much about getting term width/height
            if (config == null || currentTime - configLastFetched > 1000) {
                config = get("-a");
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.debug("Failed to query stty ", name, "\n", e);
            if (config == null) {
                return false;
            }
        }

        // always update the last fetched time and try to parse the output
        if (currentTime - configLastFetched > 1000) {
            configLastFetched = currentTime;
        }
        return true;
    }

    /**
     * <p>
     * Parses a stty output (provided by stty -a) and return the value of a given property.
     * </p>
     *
     * @param name property name.
     * @param stty string resulting of stty -a execution.
     * @return value of the given property.
     */
    protected static String getPropertyAsString(String name, String stty) {
        // try the first kind of regex
        Pattern pattern = Pattern.compile(name + "\\s+=\\s+(.*?)[;\\n\\r]");
        Matcher matcher = pattern.matcher(stty);
        if (!matcher.find()) {
            // try a second kind of regex
            pattern = Pattern.compile(name + "\\s+([^;]*)[;\\n\\r]");
            matcher = pattern.matcher(stty);
            if (!matcher.find()) {
                // try a second try of regex
                pattern = Pattern.compile("(\\S*)\\s+" + name);
                matcher = pattern.matcher(stty);
                if (!matcher.find()) {
                    return null;
                }
            }
        }
        return matcher.group(1);
    }

    protected static int getProperty(String name, String stty) {
        String str = getPropertyAsString(name, stty);
        return str != null ? parseControlChar(str) : -1;
    }

    private static int parseControlChar(String str) {
        // under
        if ("<undef>".equals(str)) {
            return -1;
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

    private String stty(final String... args) throws IOException, InterruptedException {
        String[] s = new String[args.length + 1];
        s[0] = sttyCommand;
        System.arraycopy(args, 0, s, 1, args.length);
        return exec(s);
    }

    private String exec(final String... cmd) throws IOException, InterruptedException {
        checkNotNull(cmd);

        Log.trace("Running: ", cmd);

        Process p = null;
        if (useRedirect) {
            try {
                p = inheritInput(new ProcessBuilder(cmd)).start();
            } catch (Throwable t) {
                useRedirect = false;
            }
        }
        if (p == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cmd.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(cmd[i]);
            }
            sb.append(" < ");
            sb.append(ttyDevice);
            p = new ProcessBuilder(shCommand, "-c", sb.toString()).start();
        }

        String result = waitAndCapture(p);

        Log.trace("Result: ", result);

        return result;
    }

    private static ProcessBuilder inheritInput(ProcessBuilder pb) throws Exception {
        REDIRECT_INPUT_METHOD.invoke(pb, REDIRECT_INHERIT);
        return pb;
    }

    public static String waitAndCapture(Process p) throws IOException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        InputStream in = null;
        InputStream err = null;
        OutputStream out = null;
        try {
            int c;
            in = p.getInputStream();
            while ((c = in.read()) != -1) {
                bout.write(c);
            }
            err = p.getErrorStream();
            while ((c = err.read()) != -1) {
                bout.write(c);
            }
            out = p.getOutputStream();
            p.waitFor();
        }
        finally {
            close(in, out, err);
        }

        return bout.toString();
    }

    private static void close(final Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}

