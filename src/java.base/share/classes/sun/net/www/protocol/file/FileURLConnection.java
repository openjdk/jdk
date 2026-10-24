/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.file;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sun.net.www.MessageHeader;
import sun.net.www.ParseUtil;
import sun.net.www.URLConnection;

/**
 * Open a file input stream given a URL.
 * @author      James Gosling
 * @author      Steven B. Byrne
 */
public class FileURLConnection extends URLConnection {

    private static final String CONTENT_LENGTH = "content-length";
    private static final String CONTENT_TYPE = "content-type";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String LAST_MODIFIED = "last-modified";

    // The feature of falling back to FTP for non-local file URLs is disabled
    // by default and can be re-enabled by setting a system property
    private static final boolean FTP_FALLBACK_ENABLED =
            Boolean.getBoolean("jdk.net.file.ftpfallback");

    private final File file;
    private InputStream is;
    private List<String> directoryListing;

    private boolean isDirectory = false;
    private boolean exists = false;

    private long length = -1;
    private long lastModified = 0;

    protected FileURLConnection(URL u, File file) {
        super(u);
        this.file = file;
    }

    /**
     * If already connected, then this method is a no-op.
     * If not already connected, then this method does
     * readability checks for the File.
     * <p>
     * If the File is a directory then the readability check
     * is done by verifying that File.list() does not return
     * null. On the other hand, if the File is not a directory,
     * then this method constructs a temporary FileInputStream
     * for the File and lets the FileInputStream's constructor
     * implementation do the necessary readability checks.
     * That temporary FileInputStream is closed before returning
     * from this method.
     * <p>
     * In either case, if the readability checks fail, then
     * an IOException is thrown from this method and the
     * FileURLConnection stays unconnected.
     * <p>
     * A normal return from this method implies that the
     * FileURLConnection is connected and the readability
     * checks have passed for the File.
     * <p>
     * Note: the semantics of FileURLConnection object is that the
     * results of the various URLConnection calls, such as
     * getContentType, getInputStream or getContentLength reflect
     * whatever was true when connect was called.
     */
    @Override
    public void connect() throws IOException {
        if (!connected) {
            isDirectory = file.isDirectory();
            // verify readability of the directory or the regular file
            if (isDirectory) {
                String[] fileList = file.list();
                if (fileList == null) {
                    throw new FileNotFoundException(file.getPath() + " exists, but is not accessible");
                }
                directoryListing = Arrays.asList(fileList);
            } else {
                // let FileInputStream constructor do the necessary readability checks
                // and propagate any failures
                new FileInputStream(file.getPath()).close();
            }
            connected = true;
        }
    }

    public synchronized void closeInputStream() throws IOException {
        if (is != null) {
            is.close();
        }
    }

    private boolean initializedHeaders = false;

    private void initializeHeaders() {
        try {
            connect();
            exists = file.exists();
        } catch (IOException e) {
        }
        if (!initializedHeaders || !exists) {
            length = file.length();
            lastModified = file.lastModified();

            if (!isDirectory) {
                FileNameMap map = java.net.URLConnection.getFileNameMap();
                String contentType = map.getContentTypeFor(file.getPath());
                if (contentType != null) {
                    properties.set(CONTENT_TYPE, contentType);
                }
                properties.set(CONTENT_LENGTH, Long.toString(length));

                /*
                 * Format the last-modified field into the preferred
                 * Internet standard - ie: fixed-length subset of that
                 * defined by RFC 1123
                 */
                if (lastModified != 0) {
                    Date date = new Date(lastModified);
                    SimpleDateFormat fo =
                        new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
                    fo.setTimeZone(TimeZone.getTimeZone("GMT"));
                    properties.set(LAST_MODIFIED, fo.format(date));
                }
            } else {
                properties.set(CONTENT_TYPE, TEXT_PLAIN);
            }
            initializedHeaders = true;
        }
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        initializeHeaders();
        return super.getHeaderFields();
    }

    @Override
    public String getHeaderField(String name) {
        initializeHeaders();
        return super.getHeaderField(name);
    }

    @Override
    public String getHeaderField(int n) {
        initializeHeaders();
        return super.getHeaderField(n);
    }

    @Override
    public int getContentLength() {
        initializeHeaders();
        if (length > Integer.MAX_VALUE)
            return -1;
        return (int) length;
    }

    @Override
    public long getContentLengthLong() {
        initializeHeaders();
        return length;
    }

    @Override
    public String getHeaderFieldKey(int n) {
        initializeHeaders();
        return super.getHeaderFieldKey(n);
    }

    @Override
    public MessageHeader getProperties() {
        initializeHeaders();
        return super.getProperties();
    }

    @Override
    public long getLastModified() {
        initializeHeaders();
        return lastModified;
    }

    @Override
    public synchronized InputStream getInputStream()
        throws IOException {

        connect();
        // connect() does the necessary readability checks and is expected to
        // throw IOException if any of those checks fail. A normal completion of connect()
        // must mean that connect succeeded.
        assert connected : "not connected";

        // a FileURLConnection only ever creates and provides a single InputStream
        if (is != null) {
            return is;
        }

        if (isDirectory) {
            // a successful connect() implies the directoryListing is non-null
            // if the file is a directory
            assert directoryListing != null : "missing directory listing";

            directoryListing.sort(Collator.getInstance());

            StringBuilder sb = new StringBuilder();
            for (String fileName : directoryListing) {
                sb.append(fileName);
                sb.append("\n");
            }
            // Put it into a (default) locale-specific byte-stream.
            is = new ByteArrayInputStream(sb.toString().getBytes());
        } else {
            is = new BufferedInputStream(new FileInputStream(file.getPath()));
        }
        return is;
    }

    @Override
    protected synchronized void ensureCanServeHeaders() throws IOException {
        // connect() (if not already connected) does the readability checks
        // and throws an IOException if those checks fail. A successful
        // completion from connect() implies the File is readable.
        connect();
    }


    Permission permission;

    /* since getOutputStream isn't supported, only read permission is
     * relevant
     */
    @Override
    @Deprecated(since = "25", forRemoval = true)
    @SuppressWarnings("removal")
    public Permission getPermission() throws IOException {
        if (permission == null) {
            String decodedPath = ParseUtil.decode(url.getPath());
            if (File.separatorChar == '/') {
                permission = new FilePermission(decodedPath, "read");
            } else {
                // decode could return /c:/x/y/z.
                if (decodedPath.length() > 2 && decodedPath.charAt(0) == '/'
                        && decodedPath.charAt(2) == ':') {
                    decodedPath = decodedPath.substring(1);
                }
                permission = new FilePermission(
                        decodedPath.replace('/', File.separatorChar), "read");
            }
        }
        return permission;
    }

    /**
     * Throw {@link MalformedURLException} if the FTP fallback feature for non-local
     * file URLs is not explicitly enabled via system property.
     *
     * @see #FTP_FALLBACK_ENABLED
     * @throws MalformedURLException if FTP fallback is not enabled
     */
     static void requireFtpFallbackEnabled() throws MalformedURLException {
        if (!FTP_FALLBACK_ENABLED) {
            throw new MalformedURLException("Unsupported non-local file URL");
        }
    }
}
