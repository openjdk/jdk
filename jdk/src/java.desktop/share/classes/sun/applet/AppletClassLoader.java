/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.lang.NullPointerException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.SocketPermission;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.EOFException;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import sun.awt.AppContext;
import sun.awt.SunToolkit;
import sun.misc.ManagedLocalsThread;
import sun.net.www.ParseUtil;
import sun.security.util.SecurityConstants;

/**
 * This class defines the class loader for loading applet classes and
 * resources. It extends URLClassLoader to search the applet code base
 * for the class or resource after checking any loaded JAR files.
 */
public class AppletClassLoader extends URLClassLoader {
    private URL base;   /* applet code base URL */
    private CodeSource codesource; /* codesource for the base URL */
    private AccessControlContext acc;
    private boolean exceptionStatus = false;

    private final Object threadGroupSynchronizer = new Object();
    private final Object grabReleaseSynchronizer = new Object();

    private boolean codebaseLookup = true;
    private volatile boolean allowRecursiveDirectoryRead = true;

    /*
     * Creates a new AppletClassLoader for the specified base URL.
     */
    protected AppletClassLoader(URL base) {
        super(new URL[0]);
        this.base = base;
        this.codesource =
            new CodeSource(base, (java.security.cert.Certificate[]) null);
        acc = AccessController.getContext();
    }

    public void disableRecursiveDirectoryRead() {
        allowRecursiveDirectoryRead = false;
    }


    /**
     * Set the codebase lookup flag.
     */
    void setCodebaseLookup(boolean codebaseLookup)  {
        this.codebaseLookup = codebaseLookup;
    }

    /*
     * Returns the applet code base URL.
     */
    URL getBaseURL() {
        return base;
    }

    /*
     * Returns the URLs used for loading classes and resources.
     */
    public URL[] getURLs() {
        URL[] jars = super.getURLs();
        URL[] urls = new URL[jars.length + 1];
        System.arraycopy(jars, 0, urls, 0, jars.length);
        urls[urls.length - 1] = base;
        return urls;
    }

    /*
     * Adds the specified JAR file to the search path of loaded JAR files.
     * Changed modifier to protected in order to be able to overwrite addJar()
     * in PluginClassLoader.java
     */
    protected void addJar(String name) throws IOException {
        URL url;
        try {
            url = new URL(base, name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }
        addURL(url);
        // DEBUG
        //URL[] urls = getURLs();
        //for (int i = 0; i < urls.length; i++) {
        //    System.out.println("url[" + i + "] = " + urls[i]);
        //}
    }

    /*
     * Override loadClass so that class loading errors can be caught in
     * order to print better error messages.
     */
    public synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        // First check if we have permission to access the package. This
        // should go away once we've added support for exported packages.
        int i = name.lastIndexOf('.');
        if (i != -1) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkPackageAccess(name.substring(0, i));
        }
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            //printError(name, e.getException());
            throw e;
        } catch (RuntimeException e) {
            //printError(name, e);
            throw e;
        } catch (Error e) {
            //printError(name, e);
            throw e;
        }
    }

    /*
     * Finds the applet class with the specified name. First searches
     * loaded JAR files then the applet code base for the class.
     */
    protected Class<?> findClass(String name) throws ClassNotFoundException {

        int index = name.indexOf(';');
        String cookie = "";
        if(index != -1) {
                cookie = name.substring(index, name.length());
                name = name.substring(0, index);
        }

        // check loaded JAR files
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
        }

        // Otherwise, try loading the class from the code base URL

        // 4668479: Option to turn off codebase lookup in AppletClassLoader
        // during resource requests. [stanley.ho]
        if (codebaseLookup == false)
            throw new ClassNotFoundException(name);

//      final String path = name.replace('.', '/').concat(".class").concat(cookie);
        String encodedName = ParseUtil.encodePath(name.replace('.', '/'), false);
        final String path = (new StringBuffer(encodedName)).append(".class").append(cookie).toString();
        try {
            byte[] b = AccessController.doPrivileged(
                               new PrivilegedExceptionAction<byte[]>() {
                public byte[] run() throws IOException {
                   try {
                        URL finalURL = new URL(base, path);

                        // Make sure the codebase won't be modified
                        if (base.getProtocol().equals(finalURL.getProtocol()) &&
                            base.getHost().equals(finalURL.getHost()) &&
                            base.getPort() == finalURL.getPort()) {
                            return getBytes(finalURL);
                        }
                        else {
                            return null;
                        }
                    } catch (Exception e) {
                        return null;
                    }
                }
            }, acc);

            if (b != null) {
                return defineClass(name, b, 0, b.length, codesource);
            } else {
                throw new ClassNotFoundException(name);
            }
        } catch (PrivilegedActionException e) {
            throw new ClassNotFoundException(name, e.getException());
        }
    }

    /**
     * Returns the permissions for the given codesource object.
     * The implementation of this method first calls super.getPermissions,
     * to get the permissions
     * granted by the super class, and then adds additional permissions
     * based on the URL of the codesource.
     * <p>
     * If the protocol is "file"
     * and the path specifies a file, permission is granted to read all files
     * and (recursively) all files and subdirectories contained in
     * that directory. This is so applets with a codebase of
     * file:/blah/some.jar can read in file:/blah/, which is needed to
     * be backward compatible. We also add permission to connect back to
     * the "localhost".
     *
     * @param codesource the codesource
     * @throws NullPointerException if {@code codesource} is {@code null}.
     * @return the permissions granted to the codesource
     */
    protected PermissionCollection getPermissions(CodeSource codesource)
    {
        final PermissionCollection perms = super.getPermissions(codesource);

        URL url = codesource.getLocation();

        String path = null;
        Permission p;

        try {
            p = url.openConnection().getPermission();
        } catch (java.io.IOException ioe) {
            p = null;
        }

        if (p instanceof FilePermission) {
            path = p.getName();
        } else if ((p == null) && (url.getProtocol().equals("file"))) {
            path = url.getFile().replace('/', File.separatorChar);
            path = ParseUtil.decode(path);
        }

        if (path != null) {
            final String rawPath = path;
            if (!path.endsWith(File.separator)) {
                int endIndex = path.lastIndexOf(File.separatorChar);
                if (endIndex != -1) {
                        path = path.substring(0, endIndex + 1) + "-";
                        perms.add(new FilePermission(path,
                            SecurityConstants.FILE_READ_ACTION));
                }
            }
            final File f = new File(rawPath);
            final boolean isDirectory = f.isDirectory();
            // grant codebase recursive read permission
            // this should only be granted to non-UNC file URL codebase and
            // the codesource path must either be a directory, or a file
            // that ends with .jar or .zip
            if (allowRecursiveDirectoryRead && (isDirectory ||
                    rawPath.toLowerCase().endsWith(".jar") ||
                    rawPath.toLowerCase().endsWith(".zip"))) {

            Permission bperm;
                try {
                    bperm = base.openConnection().getPermission();
                } catch (java.io.IOException ioe) {
                    bperm = null;
                }
                if (bperm instanceof FilePermission) {
                    String bpath = bperm.getName();
                    if (bpath.endsWith(File.separator)) {
                        bpath += "-";
                    }
                    perms.add(new FilePermission(bpath,
                        SecurityConstants.FILE_READ_ACTION));
                } else if ((bperm == null) && (base.getProtocol().equals("file"))) {
                    String bpath = base.getFile().replace('/', File.separatorChar);
                    bpath = ParseUtil.decode(bpath);
                    if (bpath.endsWith(File.separator)) {
                        bpath += "-";
                    }
                    perms.add(new FilePermission(bpath, SecurityConstants.FILE_READ_ACTION));
                }

            }
        }
        return perms;
    }

    /*
     * Returns the contents of the specified URL as an array of bytes.
     */
    private static byte[] getBytes(URL url) throws IOException {
        URLConnection uc = url.openConnection();
        if (uc instanceof java.net.HttpURLConnection) {
            java.net.HttpURLConnection huc = (java.net.HttpURLConnection) uc;
            int code = huc.getResponseCode();
            if (code >= java.net.HttpURLConnection.HTTP_BAD_REQUEST) {
                throw new IOException("open HTTP connection failed.");
            }
        }
        int len = uc.getContentLength();

        // Fixed #4507227: Slow performance to load
        // class and resources. [stanleyh]
        //
        // Use buffered input stream [stanleyh]
        InputStream in = new BufferedInputStream(uc.getInputStream());

        byte[] b;
        try {
            b = in.readAllBytes();
            if (len != -1 && b.length != len)
                throw new EOFException("Expected:" + len + ", read:" + b.length);
        } finally {
            in.close();
        }
        return b;
    }

    // Object for synchronization around getResourceAsStream()
    private Object syncResourceAsStream = new Object();
    private Object syncResourceAsStreamFromJar = new Object();

    // Flag to indicate getResourceAsStream() is in call
    private boolean resourceAsStreamInCall = false;
    private boolean resourceAsStreamFromJarInCall = false;

    /**
     * Returns an input stream for reading the specified resource.
     *
     * The search order is described in the documentation for {@link
     * #getResource(String)}.<p>
     *
     * @param  name the resource name
     * @return an input stream for reading the resource, or <code>null</code>
     *         if the resource could not be found
     * @since  1.1
     */
    public InputStream getResourceAsStream(String name)
    {

        if (name == null) {
            throw new NullPointerException("name");
        }

        try
        {
            InputStream is = null;

            // Fixed #4507227: Slow performance to load
            // class and resources. [stanleyh]
            //
            // The following is used to avoid calling
            // AppletClassLoader.findResource() in
            // super.getResourceAsStream(). Otherwise,
            // unnecessary connection will be made.
            //
            synchronized(syncResourceAsStream)
            {
                resourceAsStreamInCall = true;

                // Call super class
                is = super.getResourceAsStream(name);

                resourceAsStreamInCall = false;
            }

            // 4668479: Option to turn off codebase lookup in AppletClassLoader
            // during resource requests. [stanley.ho]
            if (codebaseLookup == true && is == null)
            {
                // If resource cannot be obtained,
                // try to download it from codebase
                URL url = new URL(base, ParseUtil.encodePath(name, false));
                is = url.openStream();
            }

            return is;
        }
        catch (Exception e)
        {
            return null;
        }
    }


    /**
     * Returns an input stream for reading the specified resource from the
     * the loaded jar files.
     *
     * The search order is described in the documentation for {@link
     * #getResource(String)}.<p>
     *
     * @param  name the resource name
     * @return an input stream for reading the resource, or <code>null</code>
     *         if the resource could not be found
     * @since  1.1
     */
    public InputStream getResourceAsStreamFromJar(String name) {

        if (name == null) {
            throw new NullPointerException("name");
        }

        try {
            InputStream is = null;
            synchronized(syncResourceAsStreamFromJar) {
                resourceAsStreamFromJarInCall = true;
                // Call super class
                is = super.getResourceAsStream(name);
                resourceAsStreamFromJarInCall = false;
            }

            return is;
        } catch (Exception e) {
            return null;
        }
    }


    /*
     * Finds the applet resource with the specified name. First checks
     * loaded JAR files then the applet code base for the resource.
     */
    public URL findResource(String name) {
        // check loaded JAR files
        URL url = super.findResource(name);

        // 6215746:  Disable META-INF/* lookup from codebase in
        // applet/plugin classloader. [stanley.ho]
        if (name.startsWith("META-INF/"))
            return url;

        // 4668479: Option to turn off codebase lookup in AppletClassLoader
        // during resource requests. [stanley.ho]
        if (codebaseLookup == false)
            return url;

        if (url == null)
        {
            //#4805170, if it is a call from Applet.getImage()
            //we should check for the image only in the archives
            boolean insideGetResourceAsStreamFromJar = false;
                synchronized(syncResourceAsStreamFromJar) {
                insideGetResourceAsStreamFromJar = resourceAsStreamFromJarInCall;
            }

            if (insideGetResourceAsStreamFromJar) {
                return null;
            }

            // Fixed #4507227: Slow performance to load
            // class and resources. [stanleyh]
            //
            // Check if getResourceAsStream is called.
            //
            boolean insideGetResourceAsStream = false;

            synchronized(syncResourceAsStream)
            {
                insideGetResourceAsStream = resourceAsStreamInCall;
            }

            // If getResourceAsStream is called, don't
            // trigger the following code. Otherwise,
            // unnecessary connection will be made.
            //
            if (insideGetResourceAsStream == false)
            {
                // otherwise, try the code base
                try {
                    url = new URL(base, ParseUtil.encodePath(name, false));
                    // check if resource exists
                    if(!resourceExists(url))
                        url = null;
                } catch (Exception e) {
                    // all exceptions, including security exceptions, are caught
                    url = null;
                }
            }
        }
        return url;
    }


    private boolean resourceExists(URL url) {
        // Check if the resource exists.
        // It almost works to just try to do an openConnection() but
        // HttpURLConnection will return true on HTTP_BAD_REQUEST
        // when the requested name ends in ".html", ".htm", and ".txt"
        // and we want to be able to handle these
        //
        // Also, cannot just open a connection for things like FileURLConnection,
        // because they succeed when connecting to a nonexistent file.
        // So, in those cases we open and close an input stream.
        boolean ok = true;
        try {
            URLConnection conn = url.openConnection();
            if (conn instanceof java.net.HttpURLConnection) {
                java.net.HttpURLConnection hconn =
                    (java.net.HttpURLConnection) conn;

                // To reduce overhead, using http HEAD method instead of GET method
                hconn.setRequestMethod("HEAD");

                int code = hconn.getResponseCode();
                if (code == java.net.HttpURLConnection.HTTP_OK) {
                    return true;
                }
                if (code >= java.net.HttpURLConnection.HTTP_BAD_REQUEST) {
                    return false;
                }
            } else {
                /**
                 * Fix for #4182052 - stanleyh
                 *
                 * The same connection should be reused to avoid multiple
                 * HTTP connections
                 */

                // our best guess for the other cases
                InputStream is = conn.getInputStream();
                is.close();
            }
        } catch (Exception ex) {
            ok = false;
        }
        return ok;
    }

    /*
     * Returns an enumeration of all the applet resources with the specified
     * name. First checks loaded JAR files then the applet code base for all
     * available resources.
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {

        final Enumeration<URL> e = super.findResources(name);

        // 6215746:  Disable META-INF/* lookup from codebase in
        // applet/plugin classloader. [stanley.ho]
        if (name.startsWith("META-INF/"))
            return e;

        // 4668479: Option to turn off codebase lookup in AppletClassLoader
        // during resource requests. [stanley.ho]
        if (codebaseLookup == false)
            return e;

        URL u = new URL(base, ParseUtil.encodePath(name, false));
        if (!resourceExists(u)) {
            u = null;
        }

        final URL url = u;
        return new Enumeration<URL>() {
            private boolean done;
            public URL nextElement() {
                if (!done) {
                    if (e.hasMoreElements()) {
                        return e.nextElement();
                    }
                    done = true;
                    if (url != null) {
                        return url;
                    }
                }
                throw new NoSuchElementException();
            }
            public boolean hasMoreElements() {
                return !done && (e.hasMoreElements() || url != null);
            }
        };
    }

    /*
     * Load and resolve the file specified by the applet tag CODE
     * attribute. The argument can either be the relative path
     * of the class file itself or just the name of the class.
     */
    Class<?> loadCode(String name) throws ClassNotFoundException {
        // first convert any '/' or native file separator to .
        name = name.replace('/', '.');
        name = name.replace(File.separatorChar, '.');

        // deal with URL rewriting
        String cookie = null;
        int index = name.indexOf(';');
        if(index != -1) {
                cookie = name.substring(index, name.length());
                name = name.substring(0, index);
        }

        // save that name for later
        String fullName = name;
        // then strip off any suffixes
        if (name.endsWith(".class") || name.endsWith(".java")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        try {
                if(cookie != null)
                        name = (new StringBuffer(name)).append(cookie).toString();
            return loadClass(name);
        } catch (ClassNotFoundException e) {
        }
        // then if it didn't end with .java or .class, or in the
        // really pathological case of a class named class or java
        if(cookie != null)
                fullName = (new StringBuffer(fullName)).append(cookie).toString();

        return loadClass(fullName);
    }

    /*
     * The threadgroup that the applets loaded by this classloader live
     * in. In the sun.* implementation of applets, the security manager's
     * (AppletSecurity) getThreadGroup returns the thread group of the
     * first applet on the stack, which is the applet's thread group.
     */
    private AppletThreadGroup threadGroup;
    private AppContext appContext;

    public ThreadGroup getThreadGroup() {
      synchronized (threadGroupSynchronizer) {
        if (threadGroup == null || threadGroup.isDestroyed()) {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    threadGroup = new AppletThreadGroup(base + "-threadGroup");
                    // threadGroup.setDaemon(true);
                    // threadGroup is now destroyed by AppContext.dispose()

                    // Create the new AppContext from within a Thread belonging
                    // to the newly created ThreadGroup, and wait for the
                    // creation to complete before returning from this method.
                    AppContextCreator creatorThread = new AppContextCreator(threadGroup);

                    // Since this thread will later be used to launch the
                    // applet's AWT-event dispatch thread and we want the applet
                    // code executing the AWT callbacks to use their own class
                    // loader rather than the system class loader, explicitly
                    // set the context class loader to the AppletClassLoader.
                    creatorThread.setContextClassLoader(AppletClassLoader.this);

                    creatorThread.start();
                    try {
                        synchronized(creatorThread.syncObject) {
                            while (!creatorThread.created) {
                                creatorThread.syncObject.wait();
                            }
                        }
                    } catch (InterruptedException e) { }
                    appContext = creatorThread.appContext;
                    return null;
                }
            });
        }
        return threadGroup;
      }
    }

    /*
     * Get the AppContext, if any, corresponding to this AppletClassLoader.
     */
    public AppContext getAppContext()  {
        return appContext;
    }

    int usageCount = 0;

    /**
     * Grab this AppletClassLoader and its ThreadGroup/AppContext, so they
     * won't be destroyed.
     */
public     void grab() {
        synchronized(grabReleaseSynchronizer) {
            usageCount++;
        }
        getThreadGroup(); // Make sure ThreadGroup/AppContext exist
    }

    protected void setExceptionStatus()
    {
        exceptionStatus = true;
    }

    public boolean getExceptionStatus()
    {
        return exceptionStatus;
    }

    /**
     * Release this AppletClassLoader and its ThreadGroup/AppContext.
     * If nothing else has grabbed this AppletClassLoader, its ThreadGroup
     * and AppContext will be destroyed.
     *
     * Because this method may destroy the AppletClassLoader's ThreadGroup,
     * this method should NOT be called from within the AppletClassLoader's
     * ThreadGroup.
     *
     * Changed modifier to protected in order to be able to overwrite this
     * function in PluginClassLoader.java
     */
    protected void release() {

        AppContext tempAppContext = null;

        synchronized(grabReleaseSynchronizer) {
            if (usageCount > 1)  {
                --usageCount;
            } else {
                synchronized(threadGroupSynchronizer) {
                    tempAppContext = resetAppContext();
                }
            }
        }

        // Dispose appContext outside any sync block to
        // prevent potential deadlock.
        if (tempAppContext != null)  {
            try {
                tempAppContext.dispose(); // nuke the world!
            } catch (IllegalThreadStateException e) { }
        }
    }

    /*
     * reset classloader's AppContext and ThreadGroup
     * This method is for subclass PluginClassLoader to
     * reset superclass's AppContext and ThreadGroup but do
     * not dispose the AppContext. PluginClassLoader does not
     * use UsageCount to decide whether to dispose AppContext
     *
     * @return previous AppContext
     */
    protected AppContext resetAppContext() {
        AppContext tempAppContext = null;

        synchronized(threadGroupSynchronizer) {
            // Store app context in temp variable
            tempAppContext = appContext;
            usageCount = 0;
            appContext = null;
            threadGroup = null;
        }
        return tempAppContext;
    }


    // Hash map to store applet compatibility info
    private HashMap<String, Boolean> jdk11AppletInfo = new HashMap<>();
    private HashMap<String, Boolean> jdk12AppletInfo = new HashMap<>();

    /**
     * Set applet target level as JDK 1.1.
     *
     * @param clazz Applet class.
     * @param bool true if JDK is targeted for JDK 1.1;
     *             false otherwise.
     */
    void setJDK11Target(Class<?> clazz, boolean bool)
    {
         jdk11AppletInfo.put(clazz.toString(), Boolean.valueOf(bool));
    }

    /**
     * Set applet target level as JDK 1.2.
     *
     * @param clazz Applet class.
     * @param bool true if JDK is targeted for JDK 1.2;
     *             false otherwise.
     */
    void setJDK12Target(Class<?> clazz, boolean bool)
    {
        jdk12AppletInfo.put(clazz.toString(), Boolean.valueOf(bool));
    }

    /**
     * Determine if applet is targeted for JDK 1.1.
     *
     * @param  clazz Applet class.
     * @return TRUE if applet is targeted for JDK 1.1;
     *         FALSE if applet is not;
     *         null if applet is unknown.
     */
    Boolean isJDK11Target(Class<?> clazz)
    {
        return jdk11AppletInfo.get(clazz.toString());
    }

    /**
     * Determine if applet is targeted for JDK 1.2.
     *
     * @param  clazz Applet class.
     * @return TRUE if applet is targeted for JDK 1.2;
     *         FALSE if applet is not;
     *         null if applet is unknown.
     */
    Boolean isJDK12Target(Class<?> clazz)
    {
        return jdk12AppletInfo.get(clazz.toString());
    }

    private static AppletMessageHandler mh =
        new AppletMessageHandler("appletclassloader");

    /*
     * Prints a class loading error message.
     */
    private static void printError(String name, Throwable e) {
        String s = null;
        if (e == null) {
            s = mh.getMessage("filenotfound", name);
        } else if (e instanceof IOException) {
            s = mh.getMessage("fileioexception", name);
        } else if (e instanceof ClassFormatError) {
            s = mh.getMessage("fileformat", name);
        } else if (e instanceof ThreadDeath) {
            s = mh.getMessage("filedeath", name);
        } else if (e instanceof Error) {
            s = mh.getMessage("fileerror", e.toString(), name);
        }
        if (s != null) {
            System.err.println(s);
        }
    }
}

/*
 * The AppContextCreator class is used to create an AppContext from within
 * a Thread belonging to the new AppContext's ThreadGroup.  To wait for
 * this operation to complete before continuing, wait for the notifyAll()
 * operation on the syncObject to occur.
 */
class AppContextCreator extends ManagedLocalsThread {
    Object syncObject = new Object();
    AppContext appContext = null;
    volatile boolean created = false;

    AppContextCreator(ThreadGroup group)  {
        super(group, "AppContextCreator");
    }

    public void run()  {
        appContext = SunToolkit.createNewAppContext();
        created = true;
        synchronized(syncObject) {
            syncObject.notifyAll();
        }
    } // run()

} // class AppContextCreator
