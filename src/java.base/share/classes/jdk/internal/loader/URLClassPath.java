/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.loader;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.zip.ZipFile;

import jdk.internal.access.JavaNetURLAccess;
import jdk.internal.access.JavaUtilZipFileAccess;
import jdk.internal.access.SharedSecrets;
import sun.net.util.URLUtil;
import sun.net.www.ParseUtil;

/**
 * This class is used to maintain a search path of URLs for loading classes
 * and resources from both JAR files and directories.
 *
 * @author  David Connelly
 */
public class URLClassPath {
    private static final String USER_AGENT_JAVA_VERSION = "UA-Java-Version";
    private static final String JAVA_VERSION;
    private static final boolean DEBUG;
    private static final boolean JAR_CHECKING_ENABLED;
    private static final boolean DISABLE_CP_URL_CHECK;
    private static final boolean DEBUG_CP_URL_CHECK;

    static {
        Properties props = System.getProperties();
        JAVA_VERSION = props.getProperty("java.version");
        DEBUG = (props.getProperty("sun.misc.URLClassPath.debug") != null);
        String p = props.getProperty("sun.misc.URLClassPath.disableJarChecking");
        // JAR check is disabled by default and will be enabled only if the "disable JAR check"
        // system property has been set to "false".
        JAR_CHECKING_ENABLED = "false".equals(p);

        // This property will be removed in a later release
        p = props.getProperty("jdk.net.URLClassPath.disableClassPathURLCheck");
        DISABLE_CP_URL_CHECK = p != null ? p.equals("true") || p.isEmpty() : false;

        // Print a message for each Class-Path entry that is ignored (assuming
        // the check is not disabled).
        p = props.getProperty("jdk.net.URLClassPath.showIgnoredClassPathEntries");
        DEBUG_CP_URL_CHECK = p != null ? p.equals("true") || p.isEmpty() : false;
    }

    /* Search path of URLs passed to the constructor or by calls to addURL.
     * Access is guarded by a monitor on 'searchPath' itself
     */
    private final ArrayList<URL> searchPath;

    /* Index of the next URL in the search path to process.
     * Access is guarded by a monitor on 'searchPath'
     */
    private int nextURL = 0;

    /* List of URLs found during expansion of JAR 'Class-Path' attributes.
     * Access is guarded by a monitor on 'searchPath'
     */
    private final ArrayList<URL> manifestClassPath = new ArrayList<>();

    /* The resulting search path of Loaders */
    private final ArrayList<Loader> loaders = new ArrayList<>();

    /* Map of each URL opened to its corresponding Loader */
    private final HashMap<String, Loader> lmap = new HashMap<>();

    /* The jar protocol handler to use when creating new URLs */
    private final URLStreamHandler jarHandler;

    /* Whether this URLClassLoader has been closed yet */
    private boolean closed = false;

    /**
     * Creates a new URLClassPath for the given URLs. The URLs will be
     * searched in the order specified for classes and resources. A URL
     * ending with a '/' is assumed to refer to a directory. Otherwise,
     * the URL is assumed to refer to a JAR file.
     *
     * @param urls the directory and JAR file URLs to search for classes
     *        and resources
     * @param factory the URLStreamHandlerFactory to use when creating new URLs
     */
    public URLClassPath(URL[] urls,
                        URLStreamHandlerFactory factory) {
        // Reject null URL array or any null element in the array
        this.searchPath = new ArrayList<>(List.of(urls));

        if (factory != null) {
            jarHandler = factory.createURLStreamHandler("jar");
        } else {
            jarHandler = null;
        }
    }

    public URLClassPath(URL[] urls) {
        this(urls, null);
    }

    /**
     * Constructs a URLClassPath from a class path string.
     *
     * @param cp the class path string
     * @param skipEmptyElements indicates if empty elements are ignored or
     *        treated as the current working directory
     *
     * @apiNote Used to create the application class path.
     */
    URLClassPath(String cp, boolean skipEmptyElements) {
        ArrayList<URL> path = new ArrayList<>();
        if (cp != null) {
            // map each element of class path to a file URL
            int off = 0, next;
            do {
                next = cp.indexOf(File.pathSeparator, off);
                String element = (next == -1)
                    ? cp.substring(off)
                    : cp.substring(off, next);
                if (!element.isEmpty() || !skipEmptyElements) {
                    URL url = toFileURL(element);
                    if (url != null) path.add(url);
                }
                off = next + 1;
            } while (next != -1);
        }
        this.searchPath = path;
        // the application class loader uses the built-in protocol handler to avoid protocol
        // handler lookup when opening JAR files on the class path.
        this.jarHandler = new sun.net.www.protocol.jar.Handler();
    }

    public synchronized List<IOException> closeLoaders() {
        if (closed) {
            return Collections.emptyList();
        }
        List<IOException> result = new ArrayList<>();
        for (Loader loader : loaders) {
            try {
                loader.close();
            } catch (IOException e) {
                result.add(e);
            }
        }
        closed = true;
        return result;
    }

    /**
     * Appends the specified URL to the search path of directory and JAR
     * file URLs from which to load classes and resources.
     * <p>
     * If the URL specified is null or is already in the list of
     * URLs, then invoking this method has no effect.
     */
    public synchronized void addURL(URL url) {
        if (closed || url == null)
            return;
        synchronized (searchPath) {
            if (! searchPath.contains(url)) {
                searchPath.add(url);
            }
        }
    }

    /**
     * Appends the specified file path as a file URL to the search path.
     */
    public void addFile(String s) {
        URL url = toFileURL(s);
        if (url != null) {
            addURL(url);
        }
    }

    /**
     * Returns a file URL for the given file path.
     */
    private static URL toFileURL(String s) {
        try {
            File f = new File(s).getCanonicalFile();
            return ParseUtil.fileToEncodedURL(f);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the original search path of URLs.
     */
    public URL[] getURLs() {
        synchronized (searchPath) {
            return searchPath.toArray(new URL[0]);
        }
    }

    /**
     * Finds the resource with the specified name on the URL search path
     * or null if not found.
     *
     * @param name      the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found.
     */
    public URL findResource(String name) {
        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            URL url = loader.findResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Finds all resources on the URL search path with the given name.
     * Returns an enumeration of the URL objects.
     *
     * @param name the resource name
     * @return an Enumeration of all the urls having the specified name
     */
    public Enumeration<URL> findResources(final String name) {
        return new Enumeration<>() {
            private int index = 0;
            private URL url = null;

            private boolean next() {
                if (url != null) {
                    return true;
                } else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        url = loader.findResource(name);
                        if (url != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            public boolean hasMoreElements() {
                return next();
            }

            public URL nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                URL u = url;
                url = null;
                return u;
            }
        };
    }

    /**
     * Finds the first Resource on the URL search path which has the specified
     * name. Returns null if no Resource could be found.
     *
     * @param name the name of the Resource
     * @return the Resource, or null if not found
     */
    public Resource getResource(String name) {
        if (DEBUG) {
            System.err.println("URLClassPath.getResource(\"" + name + "\")");
        }

        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            Resource res = loader.getResource(name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    /**
     * Finds all resources on the URL search path with the given name.
     * Returns an enumeration of the Resource objects.
     *
     * @param name the resource name
     * @return an Enumeration of all the resources having the specified name
     */
    public Enumeration<Resource> getResources(final String name) {
        return new Enumeration<>() {
            private int index = 0;
            private Resource res = null;

            private boolean next() {
                if (res != null) {
                    return true;
                } else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        res = loader.getResource(name);
                        if (res != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            public boolean hasMoreElements() {
                return next();
            }

            public Resource nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                Resource r = res;
                res = null;
                return r;
            }
        };
    }

    /*
     * Returns the next URL to process or null if finished
     */
    private URL nextURL() {
        synchronized (searchPath) {
            // Check paths discovered during 'Class-Path' expansion first
            if (!manifestClassPath.isEmpty()) {
                return manifestClassPath.removeLast();
            }
            // Check the regular search path
            if (nextURL < searchPath.size()) {
                return searchPath.get(nextURL++);
            }
            // All paths exhausted
            return null;
        }
    }
    /*
     * Returns the Loader at the specified position in the URL search
     * path. The URLs are opened and expanded as needed. Returns null
     * if the specified index is out of range.
     */
    private synchronized Loader getLoader(int index) {
        if (closed) {
            return null;
        }
        // Expand URL search path until the request can be satisfied
        // or all paths are exhausted.
        while (loaders.size() < index + 1) {
            final URL url = nextURL();
            if (url == null) {
                return null;
            }

            // Skip this URL if it already has a Loader.
            String urlNoFragString = URLUtil.urlNoFragString(url);
            if (lmap.containsKey(urlNoFragString)) {
                continue;
            }
            // Otherwise, create a new Loader for the URL.
            Loader loader = null;
            final URL[] loaderClassPathURLs;
            try {
                loader = getLoader(url);
                // If the loader defines a local class path then add the
                // URLs as the next URLs to be opened.
                loaderClassPathURLs = loader.getClassPath();
            } catch (IOException e) {
                // log the error and close the unusable loader (if any)
                if (DEBUG) {
                    System.err.println("Failed to construct a loader or construct its" +
                            " local classpath for " + url + ", cause:" + e);
                }
                if (loader != null) {
                    closeQuietly(loader);
                }
                continue;
            }
            if (loaderClassPathURLs != null) {
                addManifestClassPaths(loaderClassPathURLs);
            }
            // Finally, add the Loader to the search path.
            loaders.add(loader);
            lmap.put(urlNoFragString, loader);
        }
        return loaders.get(index);
    }

    // closes the given loader and ignores any IOException that may occur during close
    private static void closeQuietly(final Loader loader) {
        try {
            loader.close();
        } catch (IOException ioe) {
            if (DEBUG) {
                System.err.println("ignoring exception " + ioe + " while closing loader " + loader);
            }
        }
    }

    /*
     * Returns the Loader for the specified base URL.
     */
    private Loader getLoader(final URL url) throws IOException {
        String protocol = url.getProtocol();  // lower cased in URL
        String file = url.getFile();
        if (file != null && file.endsWith("/")) {
            if ("file".equals(protocol)) {
                return new FileLoader(url);
            } else if ("jar".equals(protocol) &&
                    isDefaultJarHandler(url) &&
                    file.endsWith("!/")) {
                // extract the nested URL
                @SuppressWarnings("deprecation")
                URL nestedUrl = new URL(file.substring(0, file.length() - 2));
                return new JarLoader(nestedUrl, jarHandler);
            } else {
                return new Loader(url);
            }
        } else {
            return new JarLoader(url, jarHandler);
        }
    }

    private static final JavaNetURLAccess JNUA
            = SharedSecrets.getJavaNetURLAccess();

    private static boolean isDefaultJarHandler(URL u) {
        URLStreamHandler h = JNUA.getHandler(u);
        return h instanceof sun.net.www.protocol.jar.Handler;
    }

    /*
     * Adds the specified URLs to the list of 'Class-Path' expanded URLs
     */
    private void addManifestClassPaths(URL[] urls) {
        synchronized (searchPath) {
            // Adding in reversed order since manifestClassPath is consumed tail-first
            manifestClassPath.addAll(Arrays.asList(urls).reversed());
        }
    }

    /**
     * Nested class used to represent a loader of resources and classes
     * from a base URL.
     */
    private static class Loader implements Closeable {
        private final URL base;
        private JarFile jarfile; // if this points to a jar file

        /*
         * Creates a new Loader for the specified URL.
         */
        Loader(URL url) {
            base = url;
        }

        /*
         * Returns the base URL for this Loader.
         */
        final URL getBaseURL() {
            return base;
        }

        URL findResource(final String name) {
            URL url;
            try {
                @SuppressWarnings("deprecation")
                var _unused = url = new URL(base, ParseUtil.encodePath(name, false));
            } catch (MalformedURLException e) {
                return null;
            }

            try {
                /*
                 * For a HTTP connection we use the HEAD method to
                 * check if the resource exists.
                 */
                URLConnection uc = url.openConnection();
                if (uc instanceof HttpURLConnection) {
                    HttpURLConnection hconn = (HttpURLConnection)uc;
                    hconn.setRequestMethod("HEAD");
                    if (hconn.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        return null;
                    }
                } else {
                    // our best guess for the other cases
                    uc.setUseCaches(false);
                    InputStream is = uc.getInputStream();
                    is.close();
                }
                return url;
            } catch (Exception e) {
                return null;
            }
        }

        /*
         * Returns the Resource for the specified name, or null if not
         * found.
         */
        Resource getResource(final String name) {
            final URL url;
            try {
                @SuppressWarnings("deprecation")
                var _unused = url = new URL(base, ParseUtil.encodePath(name, false));
            } catch (MalformedURLException e) {
                return null;
            }
            final URLConnection uc;
            try {
                uc = url.openConnection();

                if (uc instanceof JarURLConnection) {
                    /* Need to remember the jar file so it can be closed
                     * in a hurry.
                     */
                    JarURLConnection juc = (JarURLConnection)uc;
                    jarfile = JarLoader.checkJar(juc.getJarFile());
                }

                InputStream in = uc.getInputStream();
            } catch (Exception e) {
                return null;
            }
            return new Resource() {
                public String getName() { return name; }
                public URL getURL() { return url; }
                public URL getCodeSourceURL() { return base; }
                public InputStream getInputStream() throws IOException {
                    return uc.getInputStream();
                }
                public int getContentLength() throws IOException {
                    return uc.getContentLength();
                }
            };
        }

        /*
         * Closes this loader and release all resources.
         * Method overridden in sub-classes.
         */
        @Override
        public void close() throws IOException {
            if (jarfile != null) {
                jarfile.close();
            }
        }

        /*
         * Returns the local class path for this loader, or null if none.
         */
        URL[] getClassPath() throws IOException {
            return null;
        }
    }

    /*
     * Nested class used to represent a Loader of resources from a JAR URL.
     */
    private static class JarLoader extends Loader {
        private JarFile jar;
        private final URL csu;
        private boolean closed = false;
        private static final JavaUtilZipFileAccess zipAccess =
                SharedSecrets.getJavaUtilZipFileAccess();

        /*
         * Creates a new JarLoader for the specified URL referring to
         * a JAR file.
         */
        private JarLoader(URL url, URLStreamHandler jarHandler)
            throws IOException
        {
            super(newURL("jar", "", -1, url + "!/", jarHandler));
            csu = url;
            ensureOpen();
        }

        @SuppressWarnings("deprecation")
        private static URL newURL(String protocol, String host, int port, String file, URLStreamHandler handler)
                throws MalformedURLException
        {
            return new URL(protocol, host, port, file, handler);
        }

        @Override
        public void close () throws IOException {
            // closing is synchronized at higher level
            if (!closed) {
                closed = true;
                // in case not already open.
                ensureOpen();
                jar.close();
            }
        }

        private boolean isOptimizable(URL url) {
            return "file".equals(url.getProtocol());
        }

        private void ensureOpen() throws IOException {
            if (jar == null) {
                if (DEBUG) {
                    System.err.println("Opening " + csu);
                    Thread.dumpStack();
                }
                jar = getJarFile(csu);
            }
        }

        /*
         * Throws an IOException if the LOC file Header Signature (0x04034b50),
         * is not found starting at byte 0 of the given jar.
         */
        static JarFile checkJar(JarFile jar) throws IOException {
            if (JAR_CHECKING_ENABLED && !zipAccess.startsWithLocHeader(jar)) {
                IOException x = new IOException("Invalid Jar file");
                try {
                    jar.close();
                } catch (IOException ex) {
                    x.addSuppressed(ex);
                }
                throw x;
            }
            return jar;
        }

        private JarFile getJarFile(URL url) throws IOException {
            // Optimize case where url refers to a local jar file
            if (isOptimizable(url)) {
                FileURLMapper p = new FileURLMapper(url);
                if (!p.exists()) {
                    throw new FileNotFoundException(p.getPath());
                }
                return checkJar(new JarFile(new File(p.getPath()), true, ZipFile.OPEN_READ,
                        JarFile.runtimeVersion()));
            }
            @SuppressWarnings("deprecation")
            URLConnection uc = (new URL(getBaseURL(), "#runtime")).openConnection();
            uc.setRequestProperty(USER_AGENT_JAVA_VERSION, JAVA_VERSION);
            JarFile jarFile = ((JarURLConnection)uc).getJarFile();
            return checkJar(jarFile);
        }

        /*
         * Creates and returns the Resource. Returns null if the Resource couldn't be created.
         */
        Resource createResource(final String name, final JarEntry entry) {

            final URL url;
            try {
                String nm;
                if (jar.isMultiRelease()) {
                    nm = entry.getRealName();
                } else {
                    nm = name;
                }
                @SuppressWarnings("deprecation")
                var _unused = url = new URL(getBaseURL(), ParseUtil.encodePath(nm, false));
            } catch (IOException e) {
                return null;
            }

            return new Resource() {
                private Exception dataError = null;
                public String getName() { return name; }
                public URL getURL() { return url; }
                public URL getCodeSourceURL() { return csu; }
                public InputStream getInputStream() throws IOException
                    { return jar.getInputStream(entry); }
                public int getContentLength()
                    { return (int)entry.getSize(); }
                public Manifest getManifest() throws IOException {
                    SharedSecrets.javaUtilJarAccess().ensureInitialization(jar);
                    return jar.getManifest();
                }
                public Certificate[] getCertificates()
                    { return entry.getCertificates(); };
                public CodeSigner[] getCodeSigners()
                    { return entry.getCodeSigners(); };
                public Exception getDataError()
                    { return dataError; }
                public byte[] getBytes() throws IOException {
                    byte[] bytes = super.getBytes();
                    CRC32 crc32 = new CRC32();
                    crc32.update(bytes);
                    if (crc32.getValue() != entry.getCrc()) {
                        dataError = new IOException(
                                "CRC error while extracting entry from JAR file");
                    }
                    return bytes;
                }
            };
        }

        /*
         * Returns the URL for a resource with the specified name
         */
        @Override
        URL findResource(final String name) {
            Resource rsc = getResource(name);
            if (rsc != null) {
                return rsc.getURL();
            }
            return null;
        }

        /*
         * Returns the JAR Resource for the specified name.
         */
        @Override
        Resource getResource(final String name) {
            try {
                ensureOpen();
            } catch (IOException e) {
                throw new InternalError(e);
            }
            final JarEntry entry = jar.getJarEntry(name);
            if (entry != null) {
                return createResource(name, entry);
            }
            return null;
        }

        /*
         * Returns the JAR file local class path, or null if none.
         */
        @Override
        URL[] getClassPath() throws IOException {
            ensureOpen();

            // Only get manifest when necessary
            if (SharedSecrets.javaUtilJarAccess().jarFileHasClassPathAttribute(jar)) {
                Manifest man = jar.getManifest();
                if (man != null) {
                    Attributes attr = man.getMainAttributes();
                    if (attr != null) {
                        String value = attr.getValue(Name.CLASS_PATH);
                        if (value != null) {
                            return parseClassPath(csu, value);
                        }
                    }
                }
            }
            return null;
        }

        /*
         * Parses value of the Class-Path manifest attribute and returns
         * an array of URLs relative to the specified base URL.
         */
        private static URL[] parseClassPath(URL base, String value)
            throws MalformedURLException
        {
            StringTokenizer st = new StringTokenizer(value);
            URL[] urls = new URL[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                String path = st.nextToken();
                @SuppressWarnings("deprecation")
                URL url = DISABLE_CP_URL_CHECK ? new URL(base, path) : tryResolve(base, path);
                if (url != null) {
                    urls[i] = url;
                    i++;
                } else {
                    if (DEBUG_CP_URL_CHECK) {
                        System.err.println("Class-Path entry: \"" + path
                                           + "\" ignored in JAR file " + base);
                    }
                }
            }
            if (i == 0) {
                urls = null;
            } else if (i != urls.length) {
                // Truncate nulls from end of array
                urls = Arrays.copyOf(urls, i);
            }
            return urls;
        }

        static URL tryResolve(URL base, String input) throws MalformedURLException {
            if ("file".equalsIgnoreCase(base.getProtocol())) {
                return tryResolveFile(base, input);
            } else {
                return tryResolveNonFile(base, input);
            }
        }

        /**
         * Attempt to return a file URL by resolving input against a base file
         * URL.
         * @return the resolved URL or null if the input is an absolute URL with
         *         a scheme other than file (ignoring case)
         * @throws MalformedURLException
         */
        static URL tryResolveFile(URL base, String input) throws MalformedURLException {
            @SuppressWarnings("deprecation")
            URL retVal = new URL(base, input);
            if (input.indexOf(':') >= 0 &&
                    !"file".equalsIgnoreCase(retVal.getProtocol())) {
                // 'input' contains a ':', which might be a scheme, or might be
                // a Windows drive letter.  If the protocol for the resolved URL
                // isn't "file:", it should be ignored.
                return null;
            }
            return retVal;
        }

        /**
         * Attempt to return a URL by resolving input against a base URL. Returns
         * null if the resolved URL is not contained by the base URL.
         *
         * @return the resolved URL or null
         * @throws MalformedURLException
         */
        static URL tryResolveNonFile(URL base, String input) throws MalformedURLException {
            String child = input.replace(File.separatorChar, '/');
            if (isRelative(child)) {
                @SuppressWarnings("deprecation")
                URL url = new URL(base, child);
                String bp = base.getPath();
                String urlp = url.getPath();
                int pos = bp.lastIndexOf('/');
                if (pos == -1) {
                    pos = bp.length() - 1;
                }
                if (urlp.regionMatches(0, bp, 0, pos + 1)
                        && urlp.indexOf("..", pos) == -1) {
                    return url;
                }
            }
            return null;
        }

        /**
         * Returns true if the given input is a relative URI.
         */
        static boolean isRelative(String child) {
            try {
                return !URI.create(child).isAbsolute();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    /*
     * Nested class used to represent a loader of classes and resources
     * from a file URL that refers to a directory.
     */
    private static class FileLoader extends Loader {
        /* Canonicalized File */
        private final File dir;
        private final URL normalizedBase;

        /*
         * Creates a new FileLoader for the specified URL with a file protocol.
         */
        private FileLoader(URL url) throws IOException {
            super(url);
            String path = url.getFile().replace('/', File.separatorChar);
            try {
                path = ParseUtil.decode(path);
            } catch (IllegalArgumentException iae) {
                throw new IOException(iae);
            }
            dir = (new File(path)).getCanonicalFile();
            @SuppressWarnings("deprecation")
            var _unused = normalizedBase = new URL(getBaseURL(), ".");
        }

        /*
         * Returns the URL for a resource with the specified name
         */
        @Override
        URL findResource(final String name) {
            Resource rsc = getResource(name);
            if (rsc != null) {
                return rsc.getURL();
            }
            return null;
        }

        @Override
        Resource getResource(final String name) {
            final URL url;
            try {
                @SuppressWarnings("deprecation")
                var _unused = url = new URL(getBaseURL(), ParseUtil.encodePath(name, false));

                if (url.getFile().startsWith(normalizedBase.getFile()) == false) {
                    // requested resource had ../..'s in path
                    return null;
                }
                final File file;
                if (name.contains("..")) {
                    file = (new File(dir, name.replace('/', File.separatorChar)))
                          .getCanonicalFile();
                    if ( !((file.getPath()).startsWith(dir.getPath())) ) {
                        /* outside of base dir */
                        return null;
                    }
                } else {
                    file = new File(dir, name.replace('/', File.separatorChar));
                }

                if (file.exists()) {
                    return new Resource() {
                        public String getName() { return name; };
                        public URL getURL() { return url; };
                        public URL getCodeSourceURL() { return getBaseURL(); };
                        public InputStream getInputStream() throws IOException
                            { return new FileInputStream(file); };
                        public int getContentLength() throws IOException
                            { return (int)file.length(); };
                    };
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }
    }
}
