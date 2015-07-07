/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Provides access to configuration values.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.4
 */
public class Configuration
{
    /**
     * System property which can point to a file or URL containing configuration properties to load.
     *
     * @since 2.7
     */
    public static final String JLINE_CONFIGURATION = "jline.configuration";

    /**
     * Default configuration file name loaded from user's home directory.
     */
    public static final String JLINE_RC = ".jline.rc";

    private static volatile Properties properties;

    private static Properties initProperties() {
        URL url = determineUrl();
        Properties props = new Properties();
        try {
            loadProperties(url, props);
        }
        catch (IOException e) {
            // debug here instead of warn, as this can happen normally if default jline.rc file is missing
            Log.debug("Unable to read configuration from: ", url, e);
        }
        return props;
    }

    private static void loadProperties(final URL url, final Properties props) throws IOException {
        Log.debug("Loading properties from: ", url);
        InputStream input = url.openStream();
        try {
            props.load(new BufferedInputStream(input));
        }
        finally {
            try {
                input.close();
            }
            catch (IOException e) {
                // ignore
            }
        }

        if (Log.DEBUG) {
            Log.debug("Loaded properties:");
            for (Map.Entry<Object,Object> entry : props.entrySet()) {
                Log.debug("  ", entry.getKey(), "=", entry.getValue());
            }
        }
    }

    private static URL determineUrl() {
        // See if user has customized the configuration location via sysprop
        String tmp = System.getProperty(JLINE_CONFIGURATION);
        if (tmp != null) {
            return Urls.create(tmp);
        }
        else {
            // Otherwise try the default
            File file = new File(getUserHome(), JLINE_RC);
            return Urls.create(file);
        }
    }

    /**
     * @since 2.7
     */
    public static void reset() {
        Log.debug("Resetting");
        properties = null;

        // force new properties to load
        getProperties();
    }

    /**
     * @since 2.7
     */
    public static Properties getProperties() {
        // Not sure its worth to guard this with any synchronization, volatile field probably sufficient
        if (properties == null) {
            properties = initProperties();
        }
        return properties;
    }

    public static String getString(final String name, final String defaultValue) {
        checkNotNull(name);

        String value;

        // Check sysprops first, it always wins
        value = System.getProperty(name);

        if (value == null) {
            // Next try userprops
            value = getProperties().getProperty(name);

            if (value == null) {
                // else use the default
                value = defaultValue;
            }
        }

        return value;
    }

    public static String getString(final String name) {
        return getString(name, null);
    }

    public static boolean getBoolean(final String name, final boolean defaultValue) {
        String value = getString(name);
        if (value == null) {
            return defaultValue;
        }
        return value.length() == 0
            || value.equalsIgnoreCase("1")
            || value.equalsIgnoreCase("on")
            || value.equalsIgnoreCase("true");
    }

    /**
     * @since 2.6
     */
    public static int getInteger(final String name, final int defaultValue) {
        String str = getString(name);
        if (str == null) {
            return defaultValue;
        }
        return Integer.parseInt(str);
    }

    /**
     * @since 2.6
     */
    public static long getLong(final String name, final long defaultValue) {
        String str = getString(name);
        if (str == null) {
            return defaultValue;
        }
        return Long.parseLong(str);
    }

    //
    // System property helpers
    //

    /**
     * @since 2.7
     */
    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    public static File getUserHome() {
        return new File(System.getProperty("user.home"));
    }

    public static String getOsName() {
        return System.getProperty("os.name").toLowerCase();
    }

    /**
     * @since 2.7
     */
    public static boolean isWindows() {
        return getOsName().startsWith("windows");
    }

    // FIXME: Sort out use of property access of file.encoding in InputStreamReader, consolidate should configuration access here

    public static String getFileEncoding() {
        return System.getProperty("file.encoding");
    }

    /**
     * Get the default encoding.  Will first look at the LC_CTYPE environment variable, then the input.encoding
     * system property, then the default charset according to the JVM.
     *
     * @return The default encoding to use when none is specified.
     */
    public static String getEncoding() {
        // LC_CTYPE is usually in the form en_US.UTF-8
        String envEncoding = extractEncodingFromCtype(System.getenv("LC_CTYPE"));
        if (envEncoding != null) {
            return envEncoding;
        }
        return System.getProperty("input.encoding", Charset.defaultCharset().name());
    }

    /**
     * Parses the LC_CTYPE value to extract the encoding according to the POSIX standard, which says that the LC_CTYPE
     * environment variable may be of the format <code>[language[_territory][.codeset][@modifier]]</code>
     *
     * @param ctype The ctype to parse, may be null
     * @return The encoding, if one was present, otherwise null
     */
    static String extractEncodingFromCtype(String ctype) {
        if (ctype != null && ctype.indexOf('.') > 0) {
            String encodingAndModifier = ctype.substring(ctype.indexOf('.') + 1);
            if (encodingAndModifier.indexOf('@') > 0) {
                return encodingAndModifier.substring(0, encodingAndModifier.indexOf('@'));
            } else {
                return encodingAndModifier;
            }
        }
        return null;
    }
}
