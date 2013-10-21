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
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import jdk.nashorn.internal.parser.Token;

/**
 * Source objects track the origin of JavaScript entities.
 *
 */
public final class Source {
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

    /** Cached source content. */
    private final char[] content;

    /** Length of source content. */
    private final int length;

    /** Cached hash code */
    private int hash;

    /** Source URL if available */
    private final URL url;

    private static final int BUFSIZE = 8 * 1024;

    // Do *not* make this public ever! Trusts the URL and content. So has to be called
    // from other public constructors. Note that this can not be some init method as
    // we initialize final fields from here.
    private Source(final String name, final String base, final char[] content, final URL url) {
        this.name    = name;
        this.base    = base;
        this.content = content;
        this.length  = content.length;
        this.url     = url;
    }

    /**
     * Constructor
     *
     * @param name    source name
     * @param content contents as char array
     */
    public Source(final String name, final char[] content) {
        this(name, baseName(name, null), content, null);
    }

    /**
     * Constructor
     *
     * @param name    source name
     * @param content contents as string
     */
    public Source(final String name, final String content) {
        this(name, content.toCharArray());
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param url   url from which source can be loaded
     *
     * @throws IOException if source cannot be loaded
     */
    public Source(final String name, final URL url) throws IOException {
        this(name, baseURL(url, null), readFully(url), url);
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param url   url from which source can be loaded
     * @param cs    Charset used to convert bytes to chars
     *
     * @throws IOException if source cannot be loaded
     */
    public Source(final String name, final URL url, final Charset cs) throws IOException {
        this(name, baseURL(url, null), readFully(url, cs), url);
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param file  file from which source can be loaded
     *
     * @throws IOException if source cannot be loaded
     */
    public Source(final String name, final File file) throws IOException {
        this(name, dirName(file, null), readFully(file), getURLFromFile(file));
    }

    /**
     * Constructor
     *
     * @param name  source name
     * @param file  file from which source can be loaded
     * @param cs    Charset used to convert bytes to chars
     *
     * @throws IOException if source cannot be loaded
     */
    public Source(final String name, final File file, final Charset cs) throws IOException {
        this(name, dirName(file, null), readFully(file, cs), getURLFromFile(file));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Source)) {
            return false;
        }

        final Source src = (Source)obj;
        // Only compare content as a last resort measure
        return length == src.length && Objects.equals(url, src.url) && Objects.equals(name, src.name) && Arrays.equals(content, src.content);
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = hash = Arrays.hashCode(content) ^ Objects.hashCode(name);
        }
        return h;
    }

    /**
     * Fetch source content.
     * @return Source content.
     */
    public String getString() {
        return new String(content, 0, length);
    }

    /**
     * Get the user supplied name of this script.
     * @return User supplied source name.
     */
    public String getName() {
        return name;
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
        return new String(content, start, len);
    }

    /**
     * Fetch a portion of source content associated with a token.
     * @param token Token descriptor.
     * @return Source content portion.
     */
    public String getString(final long token) {
        final int start = Token.descPosition(token);
        final int len = Token.descLength(token);
        return new String(content, start, len);
    }

    /**
     * Returns the source URL of this script Source. Can be null if Source
     * was created from a String or a char[].
     *
     * @return URL source or null
     */
    public URL getURL() {
        return url;
    }

    /**
     * Find the beginning of the line containing position.
     * @param position Index to offending token.
     * @return Index of first character of line.
     */
    private int findBOLN(final int position) {
        for (int i = position - 1; i > 0; i--) {
            final char ch = content[i];

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
         for (int i = position; i < length; i++) {
            final char ch = content[i];

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
        // Line count starts at 1.
        int line = 1;

        for (int i = 0; i < position; i++) {
            final char ch = content[i];
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

        return new String(content, first, last - first + 1);
    }

    /**
     * Get the content of this source as a char array
     * @return content
     */
    public char[] getContent() {
        return content.clone();
    }

    /**
     * Get the length in chars for this source
     * @return length
     */
    public int getLength() {
        return length;
    }

    /**
     * Read all of the source until end of file. Return it as char array
     *
     * @param reader  reader opened to source stream
     * @return source as content
     *
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final Reader reader) throws IOException {
        final char[]        arr = new char[BUFSIZE];
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
     * @param file  source file
     * @return source as content
     *
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
     * @param file  source file
     * @param cs Charset used to convert bytes to chars
     * @return source as content
     *
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final File file, final Charset cs) throws IOException {
        if (!file.isFile()) {
            throw new IOException(file + " is not a file"); //TODO localize?
        }

        final byte[] buf = Files.readAllBytes(file.toPath());
        return (cs != null)? new String(buf, cs).toCharArray() : byteToCharArray(buf);
    }

    /**
     * Read all of the source until end of stream from the given URL. Return it as char array
     *
     * @param url URL to read content from
     * @return source as content
     *
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
     *
     * @throws IOException if source could not be read
     */
    public static char[] readFully(final URL url, final Charset cs) throws IOException {
        return readFully(url.openStream(), cs);
    }

    /**
     * Get the base url. This is currently used for testing only
     * @param url a URL
     * @return base URL for url
     */
    public static String baseURL(final URL url) {
        return baseURL(url, null);
    }

    private static String baseURL(final URL url, final String defaultValue) {
        if (url.getProtocol().equals("file")) {
            try {
                final Path path = Paths.get(url.toURI());
                final Path parent = path.getParent();
                return (parent != null) ? (parent + File.separator) : defaultValue;
            } catch (final SecurityException | URISyntaxException | IOError e) {
                return defaultValue;
            }
        }

        // FIXME: is there a better way to find 'base' URL of a given URL?
        String path = url.getPath();
        if (path.isEmpty()) {
            return defaultValue;
        }
        path = path.substring(0, path.lastIndexOf('/') + 1);
        final int port = url.getPort();
        try {
            return new URL(url.getProtocol(), url.getHost(), port, path).toString();
        } catch (final MalformedURLException e) {
            return defaultValue;
        }
    }

    private static String dirName(final File file, final String defaultValue) {
        final String res = file.getParent();
        return (res != null)? (res + File.separator) : defaultValue;
    }

    // fake directory like name
    private static String baseName(final String name, final String defaultValue) {
        int idx = name.lastIndexOf('/');
        if (idx == -1) {
            idx = name.lastIndexOf('\\');
        }
        return (idx != -1)? name.substring(0, idx + 1) : defaultValue;
    }

    private static char[] readFully(final InputStream is, final Charset cs) throws IOException {
        return (cs != null)? new String(readBytes(is), cs).toCharArray() : readFully(is);
    }

    private static char[] readFully(final InputStream is) throws IOException {
        return byteToCharArray(readBytes(is));
    }

    private static char[] byteToCharArray(final byte[] bytes) {
        Charset cs = StandardCharsets.UTF_8;
        int start = 0;
        // BOM detection.
        if (bytes.length > 1 && bytes[0] == (byte)0xFE && bytes[1] == (byte)0xFF) {
            start = 2;
            cs = StandardCharsets.UTF_16BE;
        } else if (bytes.length > 1 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE) {
            start = 2;
            cs = StandardCharsets.UTF_16LE;
        } else if (bytes.length > 2 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
            start = 3;
            cs = StandardCharsets.UTF_8;
        } else if (bytes.length > 3 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE && bytes[2] == 0 && bytes[3] == 0) {
            start = 4;
            cs = Charset.forName("UTF-32LE");
        } else if (bytes.length > 3 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == (byte)0xFE && bytes[3] == (byte)0xFF) {
            start = 4;
            cs = Charset.forName("UTF-32BE");
        }

        return new String(bytes, start, bytes.length - start, cs).toCharArray();
    }

    static byte[] readBytes(final InputStream is) throws IOException {
        final byte[] arr = new byte[BUFSIZE];
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
}
