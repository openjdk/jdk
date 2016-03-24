/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.runtime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.WeakHashMap;
import jdk.nashorn.api.scripting.URLReader;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
/**
 * Source objects track the origin of JavaScript entities.
 */
@Logger(name="source")
public final class Source implements Loggable {
    private static final int BUF_SIZE = 8 * 1024;
    private static final Cache CACHE = new Cache();

    // Message digest to file name encoder
    private final static Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    /**
     * Descriptive name of the source as supplied by the user. Used for error
     * reporting to the user. For example, SyntaxError will use this to print message.
     * Used to implement __FILE__. Also used for SourceFile in .class for debugger usage.
     */
    private final String name;

    /**
     * Base directory the File or base part of the URL. Used to implement __DIR__.
     * Used to load scripts relative to the 'directory' or 'base' URL of current script.
     * This will be null when it can't be computed.
     */
    private final String base;

    /** Source content */
    private final Data data;

    /** Cached hash code */
    private int hash;

    /** Base64-encoded SHA1 digest of this source object */
    private volatile byte[] digest;

    /** source URL set via //@ sourceURL or //# sourceURL directive */
    private String explicitURL;

    // Do *not* make this public, ever! Trusts the URL and content.
    private Source(final String name, final String base, final Data data) {
        this.name = name;
        this.base = base;
        this.data = data;
    }

    private static synchronized Source sourceFor(final String name, final String base, final URLData data) throws IOException {
        try {
            final Source newSource = new Source(name, base, data);
            final Source existingSource = CACHE.get(newSource);
            if (existingSource != null) {
                // Force any access errors
                data.checkPermissionAndClose();
                return existingSource;
            }

            // All sources in cache must be fully loaded
            data.load();
            CACHE.put(newSource, newSource);

            return newSource;
        } catch (final RuntimeException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
    }

    private static class Cache extends WeakHashMap<Source, WeakReference<Source>> {
        public Source get(final Source key) {
            final WeakReference<Source> ref = super.get(key);
            return ref == null ? null : ref.get();
        }

        public void put(final Source key, final Source value) {
            assert !(value.data instanceof RawData);
            put(key, new WeakReference<>(value));
        }
    }

    /* package-private */
    DebuggerSupport.SourceInfo getSourceInfo() {
        return new DebuggerSupport.SourceInfo(getName(), data.hashCode(),  data.url(), data.array());
    }

    // Wrapper to manage lazy loading
    private static interface Data {

        URL url();

        int length();

        long lastModified();

        char[] array();

        boolean isEvalCode();
    }

    private static class RawData implements Data {
        private final char[] array;
        private final boolean evalCode;
        private int hash;

        private RawData(final char[] array, final boolean evalCode) {
            this.array = Objects.requireNonNull(array);
            this.evalCode = evalCode;
        }

        private RawData(final String source, final boolean evalCode) {
            this.array = Objects.requireNonNull(source).toCharArray();
            this.evalCode = evalCode;
        }

        private RawData(final Reader reader) throws IOException {
            this(readFully(reader), false);
        }

        @Override
        public int hashCode() {
            int h = hash;
            if (h == 0) {
                h = hash = Arrays.hashCode(array) ^ (evalCode? 1 : 0);
            }
            return h;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof RawData) {
                final RawData other = (RawData)obj;
                return Arrays.equals(array, other.array) && evalCode == other.evalCode;
            }
            return false;
        }

        @Override
        public String toString() {
            return new String(array());
        }

        @Override
        public URL url() {
            return null;
        }

        @Override
        public int length() {
            return array.length;
        }

        @Override
        public long lastModified() {
            return 0;
        }

        @Override
        public char[] array() {
            return array;
        }


        @Override
        public boolean isEvalCode() {
            return evalCode;
        }
    }

    private static class URLData implements Data {
        private final URL url;
        protected final Charset cs;
        private int hash;
        protected char[] array;
        protected int length;
        protected long lastModified;

        private URLData(final URL url, final Charset cs) {
            this.url = Objects.requireNonNull(url);
            this.cs = cs;
        }

        @Override
        public int hashCode() {
            int h = hash;
            if (h == 0) {
                h = hash = url.hashCode();
            }
            return h;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof URLData)) {
                return false;
            }

            final URLData otherData = (URLData) other;

            if (url.equals(otherData.url)) {
                // Make sure both have meta data loaded
                try {
                    if (isDeferred()) {
                        // Data in cache is always loaded, and we only compare to cached data.
                        assert !otherData.isDeferred();
                        loadMeta();
                    } else if (otherData.isDeferred()) {
                        otherData.loadMeta();
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }

                // Compare meta data
                return this.length == otherData.length && this.lastModified == otherData.lastModified;
            }
            return false;
        }

        @Override
        public String toString() {
            return new String(array());
        }

        @Override
        public URL url() {
            return url;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

        @Override
        public char[] array() {
            assert !isDeferred();
            return array;
        }

        @Override
        public boolean isEvalCode() {
            return false;
        }

        boolean isDeferred() {
            return array == null;
        }

        @SuppressWarnings("try")
        protected void checkPermissionAndClose() throws IOException {
            try (InputStream in = url.openStream()) {
                // empty
            }
            debug("permission checked for ", url);
        }

        protected void load() throws IOException {
            if (array == null) {
                final URLConnection c = url.openConnection();
                try (InputStream in = c.getInputStream()) {
                    array = cs == null ? readFully(in) : readFully(in, cs);
                    length = array.length;
                    lastModified = c.getLastModified();
                    debug("loaded content for ", url);
                }
            }
        }

        protected void loadMeta() throws IOException {
            if (length == 0 && lastModified == 0) {
                final URLConnection c = url.openConnection();
                length = c.getContentLength();
                lastModified = c.getLastModified();
                debug("loaded metadata for ", url);
            }
        }
    }

    private static class FileData extends URLData {
        private final File file;

        private FileData(final File file, final Charset cs) {
            super(getURLFromFile(file), cs);
            this.file = file;

        }

        @Override
        protected void checkPermissionAndClose() throws IOException {
            if (!file.canRead()) {
                throw new FileNotFoundException(file + " (Permission Denied)");
            }
            debug("permission checked for ", file);
        }

        @Override
        protected void loadMeta() {
            if (length == 0 && lastModified == 0) {
                length = (int) file.length();
                lastModified = file.lastModified();
                debug("loaded metadata for ", file);
            }
        }

        @Override
        protected void load() throws IOException {
            if (array == null) {
                array = cs == null ? readFully(file) : readFully(file, cs);
                length = array.length;
                lastModified = file.lastModified();
                debug("loaded content for ", file);
            }
        }
    }

    private static void debug(final Object... msg) {
        final DebugLogger logger = getLoggerStatic();
        if (logger != null) {
            logger.info(msg);
        }
    }

    private char[] data() {
        return data.array();
    }

    /**
     * Returns a Source instance
     *
     * @param name    source name
     * @param content contents as char array
     * @param isEval does this represent code from 'eval' call?
     * @return source instance
     */
    public static Source sourceFor(final String name, final char[] content, final boolean isEval) {
        return new Source(name, baseName(name), new RawData(content, isEval));
    }

    /**
     * Returns a Source instance
     *
     * @param name    source name
     * @param content contents as char array
     *
     * @return source instance
     */
    public static Source sourceFor(final String name, final char[] content) {
        return sourceFor(name, content, false);
    }

    /**
     * Returns a Source instance
     *
     * @param name    source name
     * @param content contents as string
     * @param isEval does this represent code from 'eval' call?
     * @return source instance
     */
    public static Source sourceFor(final String name, final String content, final boolean isEval) {
        return new Source(name, baseName(name), new RawData(content, isEval));
    }

    /**
     * Returns a Source instance
     *
     * @param name    source name
     * @param content contents as string
     * @return source instance
     */
    public static Source sourceFor(final String name, final String content) {
        return sourceFor(name, content, false);
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param url   url from which source can be loaded
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final URL url) throws IOException {
        return sourceFor(name, url, null);
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param url   url from which source can be loaded
     * @param cs    Charset used to convert bytes to chars
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final URL url, final Charset cs) throws IOException {
        return sourceFor(name, baseURL(url), new URLData(url, cs));
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param file  file from which source can be loaded
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final File file) throws IOException {
        return sourceFor(name, file, null);
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param path  path from which source can be loaded
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final Path path) throws IOException {
        File file = null;
        try {
            file = path.toFile();
        } catch (final UnsupportedOperationException uoe) {
        }

        if (file != null) {
            return sourceFor(name, file);
        } else {
            return sourceFor(name, Files.newBufferedReader(path));
        }
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param file  file from which source can be loaded
     * @param cs    Charset used to convert bytes to chars
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final File file, final Charset cs) throws IOException {
        final File absFile = file.getAbsoluteFile();
        return sourceFor(name, dirName(absFile, null), new FileData(file, cs));
    }

    /**
     * Returns an instance
     *
     * @param name source name
     * @param reader reader from which source can be loaded
     *
     * @return source instance
     *
     * @throws IOException if source cannot be loaded
     */
    public static Source sourceFor(final String name, final Reader reader) throws IOException {
        // Extract URL from URLReader to defer loading and reuse cached data if available.
        if (reader instanceof URLReader) {
            final URLReader urlReader = (URLReader) reader;
            return sourceFor(name, urlReader.getURL(), urlReader.getCharset());
        }
        return new Source(name, baseName(name), new RawData(reader));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Source)) {
            return false;
        }
        final Source other = (Source) obj;
        return Objects.equals(name, other.name) && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = hash = data.hashCode() ^ Objects.hashCode(name);
        }
        return h;
    }

    /**
     * Fetch source content.
     * @return Source content.
     */
    public String getString() {
        return data.toString();
    }

    /**
     * Get the user supplied name of this script.
     * @return User supplied source name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the last modified time of this script.
     * @return Last modified time.
     */
    public long getLastModified() {
        return data.lastModified();
    }

    /**
     * Get the "directory" part of the file or "base" of the URL.
     * @return base of file or URL.
     */
    public String getBase() {
        return base;
    }

    /**
     * Fetch a portion of source content.
     * @param start start index in source
     * @param len length of portion
     * @return Source content portion.
     */
    public String getString(final int start, final int len) {
        return new String(data(), start, len);
    }

    /**
     * Fetch a portion of source content associated with a token.
     * @param token Token descriptor.
     * @return Source content portion.
     */
    public String getString(final long token) {
        final int start = Token.descPosition(token);
        final int len = Token.descLength(token);
        return new String(data(), start, len);
    }

    /**
     * Returns the source URL of this script Source. Can be null if Source
     * was created from a String or a char[].
     *
     * @return URL source or null
     */
    public URL getURL() {
        return data.url();
    }

    /**
     * Get explicit source URL.
     * @return URL set via sourceURL directive
     */
    public String getExplicitURL() {
        return explicitURL;
    }

    /**
     * Set explicit source URL.
     * @param explicitURL URL set via sourceURL directive
     */
    public void setExplicitURL(final String explicitURL) {
        this.explicitURL = explicitURL;
    }

    /**
     * Returns whether this source was submitted via 'eval' call or not.
     *
     * @return true if this source represents code submitted via 'eval'
     */
    public boolean isEvalCode() {
        return data.isEvalCode();
    }

    /**
     * Find the beginning of the line containing position.
     * @param position Index to offending token.
     * @return Index of first character of line.
     */
    private int findBOLN(final int position) {
        final char[] d = data();
        for (int i = position - 1; i > 0; i--) {
            final char ch = d[i];

            if (ch == '\n' || ch == '\r') {
                return i + 1;
            }
        }

        return 0;
    }

    /**
     * Find the end of the line containing position.
     * @param position Index to offending token.
     * @return Index of last character of line.
     */
    private int findEOLN(final int position) {
        final char[] d = data();
        final int length = d.length;
        for (int i = position; i < length; i++) {
            final char ch = d[i];

            if (ch == '\n' || ch == '\r') {
                return i - 1;
            }
        }

        return length - 1;
    }

    /**
     * Return line number of character position.
     *
     * <p>This method can be expensive for large sources as it iterates through
     * all characters up to {@code position}.</p>
     *
     * @param position Position of character in source content.
     * @return Line number.
     */
    public int getLine(final int position) {
        final char[] d = data();
        // Line count starts at 1.
        int line = 1;

        for (int i = 0; i < position; i++) {
            final char ch = d[i];
            // Works for both \n and \r\n.
            if (ch == '\n') {
                line++;
            }
        }

        return line;
    }

    /**
     * Return column number of character position.
     * @param position Position of character in source content.
     * @return Column number.
     */
    public int getColumn(final int position) {
        // TODO - column needs to account for tabs.
        return position - findBOLN(position);
    }

    /**
     * Return line text including character position.
     * @param position Position of character in source content.
     * @return Line text.
     */
    public String getSourceLine(final int position) {
        // Find end of previous line.
        final int first = findBOLN(position);
        // Find end of this line.
        final int last = findEOLN(position);

        return new String(data(), first, last - first + 1);
    }

    /**
     * Get the content of this source as a char array. Note that the underlying array is returned instead of a
     * clone; modifying the char array will cause modification to the source; this should not be done. While
     * there is an apparent danger that we allow unfettered access to an underlying mutable array, the
     * {@code Source} class is in a restricted {@code jdk.nashorn.internal.*} package and as such it is
     * inaccessible by external actors in an environment with a security manager. Returning a clone would be
     * detrimental to performance.
     * @return content the content of this source as a char array
     */
    public char[] getContent() {
        return data();
    }

    /**
     * Get the length in chars for this source
     * @return length
     */
    public int getLength() {
        return data.length();
    }

    /**
     * Read all of the source until end of file. Return it as char array
     *
     * @param reader reader opened to source stream
     * @return source as content
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final Reader reader) throws IOException {
        final char[]        arr = new char[BUF_SIZE];
        final StringBuilder sb  = new StringBuilder();

        try {
            int numChars;
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                sb.append(arr, 0, numChars);
            }
        } finally {
            reader.close();
        }

        return sb.toString().toCharArray();
    }

    /**
     * Read all of the source until end of file. Return it as char array
     *
     * @param file source file
     * @return source as content
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException(file + " is not a file"); //TODO localize?
        }
        return byteToCharArray(Files.readAllBytes(file.toPath()));
    }

    /**
     * Read all of the source until end of file. Return it as char array
     *
     * @param file source file
     * @param cs Charset used to convert bytes to chars
     * @return source as content
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final File file, final Charset cs) throws IOException {
        if (!file.isFile()) {
            throw new IOException(file + " is not a file"); //TODO localize?
        }

        final byte[] buf = Files.readAllBytes(file.toPath());
        return (cs != null) ? new String(buf, cs).toCharArray() : byteToCharArray(buf);
    }

    /**
     * Read all of the source until end of stream from the given URL. Return it as char array
     *
     * @param url URL to read content from
     * @return source as content
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final URL url) throws IOException {
        return readFully(url.openStream());
    }

    /**
     * Read all of the source until end of file. Return it as char array
     *
     * @param url URL to read content from
     * @param cs Charset used to convert bytes to chars
     * @return source as content
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final URL url, final Charset cs) throws IOException {
        return readFully(url.openStream(), cs);
    }

    /**
     * Get a Base64-encoded SHA1 digest for this source.
     *
     * @return a Base64-encoded SHA1 digest for this source
     */
    public String getDigest() {
        return new String(getDigestBytes(), StandardCharsets.US_ASCII);
    }

    private byte[] getDigestBytes() {
        byte[] ldigest = digest;
        if (ldigest == null) {
            final char[] content = data();
            final byte[] bytes = new byte[content.length * 2];

            for (int i = 0; i < content.length; i++) {
                bytes[i * 2]     = (byte)  (content[i] & 0x00ff);
                bytes[i * 2 + 1] = (byte) ((content[i] & 0xff00) >> 8);
            }

            try {
                final MessageDigest md = MessageDigest.getInstance("SHA-1");
                if (name != null) {
                    md.update(name.getBytes(StandardCharsets.UTF_8));
                }
                if (base != null) {
                    md.update(base.getBytes(StandardCharsets.UTF_8));
                }
                if (getURL() != null) {
                    md.update(getURL().toString().getBytes(StandardCharsets.UTF_8));
                }
                digest = ldigest = BASE64.encode(md.digest(bytes));
            } catch (final NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return ldigest;
    }

    /**
     * Get the base url. This is currently used for testing only
     * @param url a URL
     * @return base URL for url
     */
    public static String baseURL(final URL url) {
        if (url.getProtocol().equals("file")) {
            try {
                final Path path = Paths.get(url.toURI());
                final Path parent = path.getParent();
                return (parent != null) ? (parent + File.separator) : null;
            } catch (final SecurityException | URISyntaxException | IOError e) {
                return null;
            }
        }

        // FIXME: is there a better way to find 'base' URL of a given URL?
        String path = url.getPath();
        if (path.isEmpty()) {
            return null;
        }
        path = path.substring(0, path.lastIndexOf('/') + 1);
        final int port = url.getPort();
        try {
            return new URL(url.getProtocol(), url.getHost(), port, path).toString();
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    private static String dirName(final File file, final String DEFAULT_BASE_NAME) {
        final String res = file.getParent();
        return (res != null) ? (res + File.separator) : DEFAULT_BASE_NAME;
    }

    // fake directory like name
    private static String baseName(final String name) {
        int idx = name.lastIndexOf('/');
        if (idx == -1) {
            idx = name.lastIndexOf('\\');
        }
        return (idx != -1) ? name.substring(0, idx + 1) : null;
    }

    private static char[] readFully(final InputStream is, final Charset cs) throws IOException {
        return (cs != null) ? new String(readBytes(is), cs).toCharArray() : readFully(is);
    }

    public static char[] readFully(final InputStream is) throws IOException {
        return byteToCharArray(readBytes(is));
    }

    private static char[] byteToCharArray(final byte[] bytes) {
        Charset cs = StandardCharsets.UTF_8;
        int start = 0;
        // BOM detection.
        if (bytes.length > 1 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            start = 2;
            cs = StandardCharsets.UTF_16BE;
        } else if (bytes.length > 1 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            if (bytes.length > 3 && bytes[2] == 0 && bytes[3] == 0) {
                start = 4;
                cs = Charset.forName("UTF-32LE");
            } else {
                start = 2;
                cs = StandardCharsets.UTF_16LE;
            }
        } else if (bytes.length > 2 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            start = 3;
            cs = StandardCharsets.UTF_8;
        } else if (bytes.length > 3 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
            start = 4;
            cs = Charset.forName("UTF-32BE");
        }

        return new String(bytes, start, bytes.length - start, cs).toCharArray();
    }

    static byte[] readBytes(final InputStream is) throws IOException {
        final byte[] arr = new byte[BUF_SIZE];
        try {
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                int numBytes;
                while ((numBytes = is.read(arr, 0, arr.length)) > 0) {
                    buf.write(arr, 0, numBytes);
                }
                return buf.toByteArray();
            }
        } finally {
            is.close();
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    private static URL getURLFromFile(final File file) {
        try {
            return file.toURI().toURL();
        } catch (final SecurityException | MalformedURLException ignored) {
            return null;
        }
    }

    private static DebugLogger getLoggerStatic() {
        final Context context = Context.getContextTrustedOrNull();
        return context == null ? null : context.getLogger(Source.class);
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    @Override
    public DebugLogger getLogger() {
        return initLogger(Context.getContextTrusted());
    }

    private File dumpFile(final File dirFile) {
        final URL u = getURL();
        final StringBuilder buf = new StringBuilder();
        // make it unique by prefixing current date & time
        buf.append(LocalDateTime.now().toString());
        buf.append('_');
        if (u != null) {
            // make it a safe file name
            buf.append(u.toString()
                    .replace('/', '_')
                    .replace('\\', '_'));
        } else {
            buf.append(getName());
        }

        return new File(dirFile, buf.toString());
    }

    void dump(final String dir) {
        final File dirFile = new File(dir);
        final File file = dumpFile(dirFile);
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            debug("Skipping source dump for " + name);
            return;
        }

        try (final FileOutputStream fos = new FileOutputStream(file)) {
            final PrintWriter pw = new PrintWriter(fos);
            pw.print(data.toString());
            pw.flush();
        } catch (final IOException ioExp) {
            debug("Skipping source dump for " +
                    name +
                    ": " +
                    ECMAErrors.getMessage(
                        "io.error.cant.write",
                        dir +
                        " : " + ioExp.toString()));
        }
    }
}
