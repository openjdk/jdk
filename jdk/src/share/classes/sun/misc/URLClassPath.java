/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.util.*;
import java.util.jar.JarFile;
import sun.misc.JarIndex;
import sun.misc.InvalidJarIndexException;
import sun.net.www.ParseUtil;
import java.util.zip.ZipEntry;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.io.*;
import java.security.AccessController;
import java.security.AccessControlException;
import java.security.CodeSigner;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import sun.misc.FileURLMapper;
import sun.net.util.URLUtil;

/**
 * This class is used to maintain a search path of URLs for loading classes
 * and resources from both JAR files and directories.
 *
 * @author  David Connelly
 */
public class URLClassPath {
    final static String USER_AGENT_JAVA_VERSION = "UA-Java-Version";
    final static String JAVA_VERSION;
    private static final boolean DEBUG;
    private static final boolean DISABLE_JAR_CHECKING;

    static {
        JAVA_VERSION = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("java.version"));
        DEBUG        = (java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.misc.URLClassPath.debug")) != null);
        String p = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.misc.URLClassPath.disableJarChecking"));
        DISABLE_JAR_CHECKING = p != null ? p.equals("true") || p.equals("") : false;
    }

    /* The original search path of URLs. */
    private ArrayList<URL> path = new ArrayList<URL>();

    /* The stack of unopened URLs */
    Stack<URL> urls = new Stack<URL>();

    /* The resulting search path of Loaders */
    ArrayList<Loader> loaders = new ArrayList<Loader>();

    /* Map of each URL opened to its corresponding Loader */
    HashMap<String, Loader> lmap = new HashMap<String, Loader>();

    /* The jar protocol handler to use when creating new URLs */
    private URLStreamHandler jarHandler;

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
    public URLClassPath(URL[] urls, URLStreamHandlerFactory factory) {
        for (int i = 0; i < urls.length; i++) {
            path.add(urls[i]);
        }
        push(urls);
        if (factory != null) {
            jarHandler = factory.createURLStreamHandler("jar");
        }
    }

    public URLClassPath(URL[] urls) {
        this(urls, null);
    }

    public synchronized List<IOException> closeLoaders() {
        if (closed) {
            return Collections.emptyList();
        }
        List<IOException> result = new LinkedList<IOException>();
        for (Loader loader : loaders) {
            try {
                loader.close();
            } catch (IOException e) {
                result.add (e);
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
        if (closed)
            return;
        synchronized (urls) {
            if (url == null || path.contains(url))
                return;

            urls.add(0, url);
            path.add(url);
        }
    }

    /**
     * Returns the original search path of URLs.
     */
    public URL[] getURLs() {
        synchronized (urls) {
            return path.toArray(new URL[path.size()]);
        }
    }

    /**
     * Finds the resource with the specified name on the URL search path
     * or null if not found or security check fails.
     *
     * @param name      the name of the resource
     * @param check     whether to perform a security check
     * @return a <code>URL</code> for the resource, or <code>null</code>
     * if the resource could not be found.
     */
    public URL findResource(String name, boolean check) {
        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            URL url = loader.findResource(name, check);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Finds the first Resource on the URL search path which has the specified
     * name. Returns null if no Resource could be found.
     *
     * @param name the name of the Resource
     * @param check     whether to perform a security check
     * @return the Resource, or null if not found
     */
    public Resource getResource(String name, boolean check) {
        if (DEBUG) {
            System.err.println("URLClassPath.getResource(\"" + name + "\")");
        }

        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            Resource res = loader.getResource(name, check);
            if (res != null) {
                return res;
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
    public Enumeration<URL> findResources(final String name,
                                     final boolean check) {
        return new Enumeration<URL>() {
            private int index = 0;
            private URL url = null;

            private boolean next() {
                if (url != null) {
                    return true;
                } else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        url = loader.findResource(name, check);
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

    public Resource getResource(String name) {
        return getResource(name, true);
    }

    /**
     * Finds all resources on the URL search path with the given name.
     * Returns an enumeration of the Resource objects.
     *
     * @param name the resource name
     * @return an Enumeration of all the resources having the specified name
     */
    public Enumeration<Resource> getResources(final String name,
                                    final boolean check) {
        return new Enumeration<Resource>() {
            private int index = 0;
            private Resource res = null;

            private boolean next() {
                if (res != null) {
                    return true;
                } else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        res = loader.getResource(name, check);
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

    public Enumeration<Resource> getResources(final String name) {
        return getResources(name, true);
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
         // or the URL stack is empty.
        while (loaders.size() < index + 1) {
            // Pop the next URL from the URL stack
            URL url;
            synchronized (urls) {
                if (urls.empty()) {
                    return null;
                } else {
                    url = urls.pop();
                }
            }
            // Skip this URL if it already has a Loader. (Loader
            // may be null in the case where URL has not been opened
            // but is referenced by a JAR index.)
            String urlNoFragString = URLUtil.urlNoFragString(url);
            if (lmap.containsKey(urlNoFragString)) {
                continue;
            }
            // Otherwise, create a new Loader for the URL.
            Loader loader;
            try {
                loader = getLoader(url);
                // If the loader defines a local class path then add the
                // URLs to the list of URLs to be opened.
                URL[] urls = loader.getClassPath();
                if (urls != null) {
                    push(urls);
                }
            } catch (IOException e) {
                // Silently ignore for now...
                continue;
            }
            // Finally, add the Loader to the search path.
            loaders.add(loader);
            lmap.put(urlNoFragString, loader);
        }
        return loaders.get(index);
    }

    /*
     * Returns the Loader for the specified base URL.
     */
    private Loader getLoader(final URL url) throws IOException {
        try {
            return java.security.AccessController.doPrivileged(
                new java.security.PrivilegedExceptionAction<Loader>() {
                public Loader run() throws IOException {
                    String file = url.getFile();
                    if (file != null && file.endsWith("/")) {
                        if ("file".equals(url.getProtocol())) {
                            return new FileLoader(url);
                        } else {
                            return new Loader(url);
                        }
                    } else {
                        return new JarLoader(url, jarHandler, lmap);
                    }
                }
            });
        } catch (java.security.PrivilegedActionException pae) {
            throw (IOException)pae.getException();
        }
    }

    /*
     * Pushes the specified URLs onto the list of unopened URLs.
     */
    private void push(URL[] us) {
        synchronized (urls) {
            for (int i = us.length - 1; i >= 0; --i) {
                urls.push(us[i]);
            }
        }
    }

    /**
     * Convert class path specification into an array of file URLs.
     *
     * The path of the file is encoded before conversion into URL
     * form so that reserved characters can safely appear in the path.
     */
    public static URL[] pathToURLs(String path) {
        StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
        URL[] urls = new URL[st.countTokens()];
        int count = 0;
        while (st.hasMoreTokens()) {
            File f = new File(st.nextToken());
            try {
                f = new File(f.getCanonicalPath());
            } catch (IOException x) {
                // use the non-canonicalized filename
            }
            try {
                urls[count++] = ParseUtil.fileToEncodedURL(f);
            } catch (IOException x) { }
        }

        if (urls.length != count) {
            URL[] tmp = new URL[count];
            System.arraycopy(urls, 0, tmp, 0, count);
            urls = tmp;
        }
        return urls;
    }

    /*
     * Check whether the resource URL should be returned.
     * Return null on security check failure.
     * Called by java.net.URLClassLoader.
     */
    public URL checkURL(URL url) {
        try {
            check(url);
        } catch (Exception e) {
            return null;
        }

        return url;
    }

    /*
     * Check whether the resource URL should be returned.
     * Throw exception on failure.
     * Called internally within this file.
     */
    static void check(URL url) throws IOException {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            URLConnection urlConnection = url.openConnection();
            Permission perm = urlConnection.getPermission();
            if (perm != null) {
                try {
                    security.checkPermission(perm);
                } catch (SecurityException se) {
                    // fallback to checkRead/checkConnect for pre 1.2
                    // security managers
                    if ((perm instanceof java.io.FilePermission) &&
                        perm.getActions().indexOf("read") != -1) {
                        security.checkRead(perm.getName());
                    } else if ((perm instanceof
                        java.net.SocketPermission) &&
                        perm.getActions().indexOf("connect") != -1) {
                        URL locUrl = url;
                        if (urlConnection instanceof JarURLConnection) {
                            locUrl = ((JarURLConnection)urlConnection).getJarFileURL();
                        }
                        security.checkConnect(locUrl.getHost(),
                                              locUrl.getPort());
                    } else {
                        throw se;
                    }
                }
            }
        }
    }

    /**
     * Inner class used to represent a loader of resources and classes
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
        URL getBaseURL() {
            return base;
        }

        URL findResource(final String name, boolean check) {
            URL url;
            try {
                url = new URL(base, ParseUtil.encodePath(name, false));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("name");
            }

            try {
                if (check) {
                    URLClassPath.check(url);
                }

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

        Resource getResource(final String name, boolean check) {
            final URL url;
            try {
                url = new URL(base, ParseUtil.encodePath(name, false));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("name");
            }
            final URLConnection uc;
            try {
                if (check) {
                    URLClassPath.check(url);
                }
                uc = url.openConnection();
                InputStream in = uc.getInputStream();
                if (uc instanceof JarURLConnection) {
                    /* Need to remember the jar file so it can be closed
                     * in a hurry.
                     */
                    JarURLConnection juc = (JarURLConnection)uc;
                    jarfile = JarLoader.checkJar(juc.getJarFile());
                }
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
         * Returns the Resource for the specified name, or null if not
         * found or the caller does not have the permission to get the
         * resource.
         */
        Resource getResource(final String name) {
            return getResource(name, true);
        }

        /*
         * close this loader and release all resources
         * method overridden in sub-classes
         */
        public void close () throws IOException {
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
     * Inner class used to represent a Loader of resources from a JAR URL.
     */
    static class JarLoader extends Loader {
        private JarFile jar;
        private URL csu;
        private JarIndex index;
        private MetaIndex metaIndex;
        private URLStreamHandler handler;
        private HashMap<String, Loader> lmap;
        private boolean closed = false;
        private static final sun.misc.JavaUtilZipFileAccess zipAccess =
                sun.misc.SharedSecrets.getJavaUtilZipFileAccess();

        /*
         * Creates a new JarLoader for the specified URL referring to
         * a JAR file.
         */
        JarLoader(URL url, URLStreamHandler jarHandler,
                  HashMap<String, Loader> loaderMap)
            throws IOException
        {
            super(new URL("jar", "", -1, url + "!/", jarHandler));
            csu = url;
            handler = jarHandler;
            lmap = loaderMap;

            if (!isOptimizable(url)) {
                ensureOpen();
            } else {
                 String fileName = url.getFile();
                if (fileName != null) {
                    fileName = ParseUtil.decode(fileName);
                    File f = new File(fileName);
                    metaIndex = MetaIndex.forJar(f);
                    // If the meta index is found but the file is not
                    // installed, set metaIndex to null. A typical
                    // senario is charsets.jar which won't be installed
                    // when the user is running in certain locale environment.
                    // The side effect of null metaIndex will cause
                    // ensureOpen get called so that IOException is thrown.
                    if (metaIndex != null && !f.exists()) {
                        metaIndex = null;
                    }
                }

                // metaIndex is null when either there is no such jar file
                // entry recorded in meta-index file or such jar file is
                // missing in JRE. See bug 6340399.
                if (metaIndex == null) {
                    ensureOpen();
                }
            }
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

        JarFile getJarFile () {
            return jar;
        }

        private boolean isOptimizable(URL url) {
            return "file".equals(url.getProtocol());
        }

        private void ensureOpen() throws IOException {
            if (jar == null) {
                try {
                    java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedExceptionAction<Void>() {
                            public Void run() throws IOException {
                                if (DEBUG) {
                                    System.err.println("Opening " + csu);
                                    Thread.dumpStack();
                                }

                                jar = getJarFile(csu);
                                index = JarIndex.getJarIndex(jar, metaIndex);
                                if (index != null) {
                                    String[] jarfiles = index.getJarFiles();
                                // Add all the dependent URLs to the lmap so that loaders
                                // will not be created for them by URLClassPath.getLoader(int)
                                // if the same URL occurs later on the main class path.  We set
                                // Loader to null here to avoid creating a Loader for each
                                // URL until we actually need to try to load something from them.
                                    for(int i = 0; i < jarfiles.length; i++) {
                                        try {
                                            URL jarURL = new URL(csu, jarfiles[i]);
                                            // If a non-null loader already exists, leave it alone.
                                            String urlNoFragString = URLUtil.urlNoFragString(jarURL);
                                            if (!lmap.containsKey(urlNoFragString)) {
                                                lmap.put(urlNoFragString, null);
                                            }
                                        } catch (MalformedURLException e) {
                                            continue;
                                        }
                                    }
                                }
                                return null;
                            }
                        }
                    );
                } catch (java.security.PrivilegedActionException pae) {
                    throw (IOException)pae.getException();
                }
            }
        }

        /* Throws if the given jar file is does not start with the correct LOC */
        static JarFile checkJar(JarFile jar) throws IOException {
            if (System.getSecurityManager() != null && !DISABLE_JAR_CHECKING
                && !zipAccess.startsWithLocHeader(jar)) {
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
                FileURLMapper p = new FileURLMapper (url);
                if (!p.exists()) {
                    throw new FileNotFoundException(p.getPath());
                }
                return checkJar(new JarFile(p.getPath()));
            }
            URLConnection uc = getBaseURL().openConnection();
            uc.setRequestProperty(USER_AGENT_JAVA_VERSION, JAVA_VERSION);
            JarFile jarFile = ((JarURLConnection)uc).getJarFile();
            return checkJar(jarFile);
        }

        /*
         * Returns the index of this JarLoader if it exists.
         */
        JarIndex getIndex() {
            try {
                ensureOpen();
            } catch (IOException e) {
                throw new InternalError(e);
            }
            return index;
        }

        /*
         * Creates the resource and if the check flag is set to true, checks if
         * is its okay to return the resource.
         */
        Resource checkResource(final String name, boolean check,
            final JarEntry entry) {

            final URL url;
            try {
                url = new URL(getBaseURL(), ParseUtil.encodePath(name, false));
                if (check) {
                    URLClassPath.check(url);
                }
            } catch (MalformedURLException e) {
                return null;
                // throw new IllegalArgumentException("name");
            } catch (IOException e) {
                return null;
            } catch (AccessControlException e) {
                return null;
            }

            return new Resource() {
                public String getName() { return name; }
                public URL getURL() { return url; }
                public URL getCodeSourceURL() { return csu; }
                public InputStream getInputStream() throws IOException
                    { return jar.getInputStream(entry); }
                public int getContentLength()
                    { return (int)entry.getSize(); }
                public Manifest getManifest() throws IOException
                    { return jar.getManifest(); };
                public Certificate[] getCertificates()
                    { return entry.getCertificates(); };
                public CodeSigner[] getCodeSigners()
                    { return entry.getCodeSigners(); };
            };
        }


        /*
         * Returns true iff atleast one resource in the jar file has the same
         * package name as that of the specified resource name.
         */
        boolean validIndex(final String name) {
            String packageName = name;
            int pos;
            if((pos = name.lastIndexOf("/")) != -1) {
                packageName = name.substring(0, pos);
            }

            String entryName;
            ZipEntry entry;
            Enumeration<JarEntry> enum_ = jar.entries();
            while (enum_.hasMoreElements()) {
                entry = enum_.nextElement();
                entryName = entry.getName();
                if((pos = entryName.lastIndexOf("/")) != -1)
                    entryName = entryName.substring(0, pos);
                if (entryName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

        /*
         * Returns the URL for a resource with the specified name
         */
        URL findResource(final String name, boolean check) {
            Resource rsc = getResource(name, check);
            if (rsc != null) {
                return rsc.getURL();
            }
            return null;
        }

        /*
         * Returns the JAR Resource for the specified name.
         */
        Resource getResource(final String name, boolean check) {
            if (metaIndex != null) {
                if (!metaIndex.mayContain(name)) {
                    return null;
                }
            }

            try {
                ensureOpen();
            } catch (IOException e) {
                throw new InternalError(e);
            }
            final JarEntry entry = jar.getJarEntry(name);
            if (entry != null)
                return checkResource(name, check, entry);

            if (index == null)
                return null;

            HashSet<String> visited = new HashSet<String>();
            return getResource(name, check, visited);
        }

        /*
         * Version of getResource() that tracks the jar files that have been
         * visited by linking through the index files. This helper method uses
         * a HashSet to store the URLs of jar files that have been searched and
         * uses it to avoid going into an infinite loop, looking for a
         * non-existent resource
         */
        Resource getResource(final String name, boolean check,
                             Set<String> visited) {

            Resource res;
            String[] jarFiles;
            int count = 0;
            LinkedList<String> jarFilesList = null;

            /* If there no jar files in the index that can potential contain
             * this resource then return immediately.
             */
            if((jarFilesList = index.get(name)) == null)
                return null;

            do {
                int size = jarFilesList.size();
                jarFiles = jarFilesList.toArray(new String[size]);
                /* loop through the mapped jar file list */
                while(count < size) {
                    String jarName = jarFiles[count++];
                    JarLoader newLoader;
                    final URL url;

                    try{
                        url = new URL(csu, jarName);
                        String urlNoFragString = URLUtil.urlNoFragString(url);
                        if ((newLoader = (JarLoader)lmap.get(urlNoFragString)) == null) {
                            /* no loader has been set up for this jar file
                             * before
                             */
                            newLoader = AccessController.doPrivileged(
                                new PrivilegedExceptionAction<JarLoader>() {
                                    public JarLoader run() throws IOException {
                                        return new JarLoader(url, handler,
                                            lmap);
                                    }
                                });

                            /* this newly opened jar file has its own index,
                             * merge it into the parent's index, taking into
                             * account the relative path.
                             */
                            JarIndex newIndex = newLoader.getIndex();
                            if(newIndex != null) {
                                int pos = jarName.lastIndexOf("/");
                                newIndex.merge(this.index, (pos == -1 ?
                                    null : jarName.substring(0, pos + 1)));
                            }

                            /* put it in the global hashtable */
                            lmap.put(urlNoFragString, newLoader);
                        }
                    } catch (java.security.PrivilegedActionException pae) {
                        continue;
                    } catch (MalformedURLException e) {
                        continue;
                    }


                    /* Note that the addition of the url to the list of visited
                     * jars incorporates a check for presence in the hashmap
                     */
                    boolean visitedURL = !visited.add(URLUtil.urlNoFragString(url));
                    if (!visitedURL) {
                        try {
                            newLoader.ensureOpen();
                        } catch (IOException e) {
                            throw new InternalError(e);
                        }
                        final JarEntry entry = newLoader.jar.getJarEntry(name);
                        if (entry != null) {
                            return newLoader.checkResource(name, check, entry);
                        }

                        /* Verify that at least one other resource with the
                         * same package name as the lookedup resource is
                         * present in the new jar
                         */
                        if (!newLoader.validIndex(name)) {
                            /* the mapping is wrong */
                            throw new InvalidJarIndexException("Invalid index");
                        }
                    }

                    /* If newLoader is the current loader or if it is a
                     * loader that has already been searched or if the new
                     * loader does not have an index then skip it
                     * and move on to the next loader.
                     */
                    if (visitedURL || newLoader == this ||
                            newLoader.getIndex() == null) {
                        continue;
                    }

                    /* Process the index of the new loader
                     */
                    if((res = newLoader.getResource(name, check, visited))
                            != null) {
                        return res;
                    }
                }
                // Get the list of jar files again as the list could have grown
                // due to merging of index files.
                jarFilesList = index.get(name);

            // If the count is unchanged, we are done.
            } while(count < jarFilesList.size());
            return null;
        }


        /*
         * Returns the JAR file local class path, or null if none.
         */
        URL[] getClassPath() throws IOException {
            if (index != null) {
                return null;
            }

            if (metaIndex != null) {
                return null;
            }

            ensureOpen();
            parseExtensionsDependencies();

            if (SharedSecrets.javaUtilJarAccess().jarFileHasClassPathAttribute(jar)) { // Only get manifest when necessary
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
         * parse the standard extension dependencies
         */
        private void  parseExtensionsDependencies() throws IOException {
            ExtensionDependency.checkExtensionsDependencies(jar);
        }

        /*
         * Parses value of the Class-Path manifest attribute and returns
         * an array of URLs relative to the specified base URL.
         */
        private URL[] parseClassPath(URL base, String value)
            throws MalformedURLException
        {
            StringTokenizer st = new StringTokenizer(value);
            URL[] urls = new URL[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                String path = st.nextToken();
                urls[i] = new URL(base, path);
                i++;
            }
            return urls;
        }
    }

    /*
     * Inner class used to represent a loader of classes and resources
     * from a file URL that refers to a directory.
     */
    private static class FileLoader extends Loader {
        /* Canonicalized File */
        private File dir;

        FileLoader(URL url) throws IOException {
            super(url);
            if (!"file".equals(url.getProtocol())) {
                throw new IllegalArgumentException("url");
            }
            String path = url.getFile().replace('/', File.separatorChar);
            path = ParseUtil.decode(path);
            dir = (new File(path)).getCanonicalFile();
        }

        /*
         * Returns the URL for a resource with the specified name
         */
        URL findResource(final String name, boolean check) {
            Resource rsc = getResource(name, check);
            if (rsc != null) {
                return rsc.getURL();
            }
            return null;
        }

        Resource getResource(final String name, boolean check) {
            final URL url;
            try {
                URL normalizedBase = new URL(getBaseURL(), ".");
                url = new URL(getBaseURL(), ParseUtil.encodePath(name, false));

                if (url.getFile().startsWith(normalizedBase.getFile()) == false) {
                    // requested resource had ../..'s in path
                    return null;
                }

                if (check)
                    URLClassPath.check(url);

                final File file;
                if (name.indexOf("..") != -1) {
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
