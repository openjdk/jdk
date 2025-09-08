/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.jar;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.jar.JarFile;

import jdk.internal.util.OperatingSystem;
import sun.net.util.URLUtil;
import sun.net.www.ParseUtil;
import static jdk.internal.util.Exceptions.filterJarName;
import static jdk.internal.util.Exceptions.formatMsg;

/* A factory for cached JAR file. This class is used to both retrieve
 * and cache Jar files.
 *
 * @author Benjamin Renaud
 * @since 1.2
 */
class JarFileFactory implements URLJarFile.URLJarFileCloseController {

    /* the url to file cache */
    private static final HashMap<String, JarFile> fileCache = new HashMap<>();

    /* the file to url cache */
    private static final HashMap<JarFile, URL> urlCache = new HashMap<>();

    private static final JarFileFactory instance = new JarFileFactory();

    private JarFileFactory() { }

    public static JarFileFactory getInstance() {
        return instance;
    }

    URLConnection getConnection(JarFile jarFile) throws IOException {
        URL u;
        synchronized (instance) {
            u = urlCache.get(jarFile);
        }
        if (u != null)
            return u.openConnection();

        return null;
    }

    public JarFile get(URL url) throws IOException {
        return get(url, true);
    }

    /**
     * Get or create a {@code JarFile} for the given {@code url}.
     * If {@code useCaches} is true, this method attempts to find
     * a jar file in the cache, and if so, returns it.
     * If no jar file is found in the cache, or {@code useCaches}
     * is false, the method creates a new jar file.
     * If the URL points to a local file, the returned jar file
     * will not be put in the cache yet.
     * The caller should then call {@link #cacheIfAbsent(URL, JarFile)}
     * with the returned jar file, if updating the cache is desired.
     * @param url the jar file url
     * @param useCaches whether the cache should be used
     * @return a new or cached jar file.
     * @throws IOException if the jar file couldn't be created
     */
    JarFile getOrCreate(URL url, boolean useCaches) throws IOException {
        if (useCaches == false) {
            return get(url, false);
        }
        URL patched = urlFor(url);
        if (!ParseUtil.isLocalFileURL(patched)) {
            // A temporary file will be created, we can prepopulate
            // the cache in this case.
            return get(url, useCaches);
        }

        // We have a local file. Do not prepopulate the cache.
        JarFile result;
        synchronized (instance) {
            result = getCachedJarFile(patched);
        }
        if (result == null) {
            result = URLJarFile.getJarFile(patched, this);
        }
        if (result == null)
            throw new FileNotFoundException(formatMsg("%s", filterJarName(url.toString())));
        return result;
    }

    /**
     * Close the given jar file if it isn't present in the cache.
     * Otherwise, does nothing.
     * @param url the jar file URL
     * @param jarFile the jar file to close
     * @return true if the jar file has been closed, false otherwise.
     * @throws IOException if an error occurs while closing the jar file.
     */
    boolean closeIfNotCached(URL url, JarFile jarFile) throws IOException {
        url = urlFor(url);
        JarFile result;
        synchronized (instance) {
            result = getCachedJarFile(url);
        }
        if (result != jarFile) jarFile.close();
        return result != jarFile;
    }

    boolean cacheIfAbsent(URL url, JarFile jarFile) {
        try {
            url = urlFor(url);
        } catch (IOException x) {
            // should not happen
            return false;
        }
        JarFile cached;
        synchronized (instance) {
            String key = urlKey(url);
            cached = fileCache.get(key);
            if (cached == null) {
                fileCache.put(key, jarFile);
                urlCache.put(jarFile, url);
            }
        }
        return cached == null || cached == jarFile;
    }

    private URL urlFor(URL url) throws IOException {
        // for systems other than Windows we don't
        // do any special conversion
        if (!OperatingSystem.isWindows()) {
            return url;
        }
        if (url.getProtocol().equalsIgnoreCase("file")) {
            // Deal with UNC pathnames specially. See 4180841

            String host = url.getHost();
            // Subtly different from ParseUtil.isLocalFileURL, for historical reasons
            boolean isLocalFile = ParseUtil.isLocalFileURL(url) && !"~".equals(host);
            // For remote hosts, change 'file://host/folder/data.xml' to 'file:////host/folder/data.xml'
            if (!isLocalFile) {
                @SuppressWarnings("deprecation")
                var _unused = url = new URL("file", "", "//" + host + url.getPath());
            }
        }
        return url;
    }

    JarFile get(URL url, boolean useCaches) throws IOException {

        url = urlFor(url);

        JarFile result;
        JarFile local_result;

        if (useCaches) {
            synchronized (instance) {
                result = getCachedJarFile(url);
            }
            if (result == null) {
                local_result = URLJarFile.getJarFile(url, this);
                synchronized (instance) {
                    result = getCachedJarFile(url);
                    if (result == null) {
                        fileCache.put(urlKey(url), local_result);
                        urlCache.put(local_result, url);
                        result = local_result;
                    } else {
                        if (local_result != null) {
                            local_result.close();
                        }
                    }
                }
            }
        } else {
            result = URLJarFile.getJarFile(url, this);
        }
        if (result == null)
            throw new FileNotFoundException(formatMsg("%s", filterJarName(url.toString())));

        return result;
    }

    /**
     * Callback method of the URLJarFileCloseController to
     * indicate that the JarFile is closed. This way we can
     * remove the JarFile from the cache
     */
    public void close(JarFile jarFile) {
        synchronized (instance) {
            URL urlRemoved = urlCache.remove(jarFile);
            if (urlRemoved != null)
                fileCache.remove(urlKey(urlRemoved));
        }
    }

    private JarFile getCachedJarFile(URL url) {
        assert Thread.holdsLock(instance);
        return fileCache.get(urlKey(url));
    }

    private String urlKey(URL url) {
        String urlstr =  URLUtil.urlNoFragString(url);
        if ("runtime".equals(url.getRef())) urlstr += "#runtime";
        return urlstr;
    }
}
