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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.FileNameMap;
import java.io.*;
import java.text.Collator;
import java.security.Permission;
import sun.net.www.*;
import java.util.*;
import java.text.SimpleDateFormat;

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

    /*
     * Note: the semantics of FileURLConnection object is that the
     * results of the various URLConnection calls, such as
     * getContentType, getInputStream or getContentLength reflect
     * whatever was true when connect was called.
     */
    public void connect() throws IOException {
        if (!connected) {

            isDirectory = file.isDirectory();
            if (isDirectory) {
                String[] fileList = file.list();
                if (fileList == null)
                    throw new FileNotFoundException(file.getPath() + " exists, but is not accessible");
                directoryListing = Arrays.asList(fileList);
            } else {
                is = new BufferedInputStream(new FileInputStream(file.getPath()));
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
                    properties.add(CONTENT_TYPE, contentType);
                }
                properties.add(CONTENT_LENGTH, Long.toString(length));

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
                    properties.add(LAST_MODIFIED, fo.format(date));
                }
            } else {
                properties.add(CONTENT_TYPE, TEXT_PLAIN);
            }
            initializedHeaders = true;
        }
    }

    public Map<String,List<String>> getHeaderFields() {
        initializeHeaders();
        return super.getHeaderFields();
    }

    public String getHeaderField(String name) {
        initializeHeaders();
        return super.getHeaderField(name);
    }

    public String getHeaderField(int n) {
        initializeHeaders();
        return super.getHeaderField(n);
    }

    public int getContentLength() {
        initializeHeaders();
        if (length > Integer.MAX_VALUE)
            return -1;
        return (int) length;
    }

    public long getContentLengthLong() {
        initializeHeaders();
        return length;
    }

    public String getHeaderFieldKey(int n) {
        initializeHeaders();
        return super.getHeaderFieldKey(n);
    }

    public MessageHeader getProperties() {
        initializeHeaders();
        return super.getProperties();
    }

    public long getLastModified() {
        initializeHeaders();
        return lastModified;
    }

    public synchronized InputStream getInputStream()
        throws IOException {

        connect();

        if (is == null) {
            if (isDirectory) {

                if (directoryListing == null) {
                    throw new FileNotFoundException(file.getPath());
                }

                directoryListing.sort(Collator.getInstance());

                StringBuilder sb = new StringBuilder();
                for (String fileName : directoryListing) {
                    sb.append(fileName);
                    sb.append("\n");
                }
                // Put it into a (default) locale-specific byte-stream.
                is = new ByteArrayInputStream(sb.toString().getBytes());
            } else {
                throw new FileNotFoundException(file.getPath());
            }
        }
        return is;
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
