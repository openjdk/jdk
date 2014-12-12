/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.AccessControlContext;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Permission;
import java.security.ProtectionDomain;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sun.net.www.ParseUtil;
import sun.security.util.SecurityConstants;

/**
 * This class is used by the system to launch the main application.
Launcher */
public class Launcher {

    // ensure URLClassPath for boot loader is initialized first
    static {
        URLClassPath ucp = BootClassPathHolder.bcp;
    }

    private static URLStreamHandlerFactory factory = new Factory();
    private static Launcher launcher = new Launcher();

    public static Launcher getLauncher() {
        return launcher;
    }

    private ClassLoader loader;

    public Launcher() {
        // Create the extension class loader
        ClassLoader extcl;
        try {
            extcl = ExtClassLoader.getExtClassLoader();
        } catch (IOException e) {
            throw new InternalError(
                "Could not create extension class loader", e);
        }

        // Now create the class loader to use to launch the application
        try {
            loader = AppClassLoader.getAppClassLoader(extcl);
        } catch (IOException e) {
            throw new InternalError(
                "Could not create application class loader", e);
        }

        // Also set the context class loader for the primordial thread.
        Thread.currentThread().setContextClassLoader(loader);

        // Finally, install a security manager if requested
        String s = System.getProperty("java.security.manager");
        if (s != null) {
            SecurityManager sm = null;
            if ("".equals(s) || "default".equals(s)) {
                sm = new java.lang.SecurityManager();
            } else {
                try {
                    sm = (SecurityManager)loader.loadClass(s).newInstance();
                } catch (IllegalAccessException e) {
                } catch (InstantiationException e) {
                } catch (ClassNotFoundException e) {
                } catch (ClassCastException e) {
                }
            }
            if (sm != null) {
                System.setSecurityManager(sm);
            } else {
                throw new InternalError(
                    "Could not create SecurityManager: " + s);
            }
        }
    }

    /*
     * Returns the class loader used to launch the main application.
     */
    public ClassLoader getClassLoader() {
        return loader;
    }

    /*
     * The class loader used for loading installed extensions.
     */
    static class ExtClassLoader extends URLClassLoader {

        static {
            ClassLoader.registerAsParallelCapable();
        }

        /**
         * create an ExtClassLoader. The ExtClassLoader is created
         * within a context that limits which files it can read
         */
        public static ExtClassLoader getExtClassLoader() throws IOException {
            try {
                // Prior implementations of this doPrivileged() block supplied
                // aa synthesized ACC via a call to the private method
                // ExtClassLoader.getContext().

                return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<ExtClassLoader>() {
                        public ExtClassLoader run() throws IOException {
                            // ext modules linked into image
                            String home = System.getProperty("java.home");
                            File dir = new File(new File(home, "lib"), "modules");
                            File jimage = new File(dir, "extmodules.jimage");

                            File jfxrt = new File(new File(home, "lib"), "jfxrt.jar");
                            File[] files = jfxrt.exists() ? new File[] {jimage, jfxrt}
                                                          : new File[] {jimage};
                            return new ExtClassLoader(files);
                        }
                    });
            } catch (java.security.PrivilegedActionException e) {
                throw (IOException) e.getException();
            }
        }

        void addExtURL(URL url) {
            super.addURL(url);
        }

        /*
         * Creates a new ExtClassLoader for the specified directories.
         */
        public ExtClassLoader(File[] files) throws IOException {
            super(getExtURLs(files), null, factory);
        }

        private static URL[] getExtURLs(File[] files) throws IOException {
            int len = files.length;
            URL[] urls = new URL[len];
            for (int i=0; i<len; i++) {
                urls[i] = getFileURL(files[i]);
            }
            return urls;
        }

        private static AccessControlContext getContext(File[] dirs)
            throws IOException
        {
            PathPermissions perms =
                new PathPermissions(dirs);

            ProtectionDomain domain = new ProtectionDomain(
                new CodeSource(perms.getCodeBase(),
                    (java.security.cert.Certificate[]) null),
                perms);

            AccessControlContext acc =
                new AccessControlContext(new ProtectionDomain[] { domain });

            return acc;
        }
    }

    /**
     * The class loader used for loading from java.class.path.
     * runs in a restricted security context.
     */
    static class AppClassLoader extends URLClassLoader {

        static {
            ClassLoader.registerAsParallelCapable();
        }

        public static ClassLoader getAppClassLoader(final ClassLoader extcl)
            throws IOException
        {
            // modules linked into image are prepended to class path
            String home = System.getProperty("java.home");
            File dir = new File(new File(home, "lib"), "modules");
            String jimage = new File(dir, "appmodules.jimage").getPath();

            String cp = System.getProperty("java.class.path");
            if (cp == null) {
                cp = jimage;
            } else {
                cp = jimage + File.pathSeparator + cp;
            }
            final File[] path = getClassPath(cp, true);

            // Note: on bugid 4256530
            // Prior implementations of this doPrivileged() block supplied
            // a rather restrictive ACC via a call to the private method
            // AppClassLoader.getContext(). This proved overly restrictive
            // when loading  classes. Specifically it prevent
            // accessClassInPackage.sun.* grants from being honored.
            //
            return AccessController.doPrivileged(
                new PrivilegedAction<AppClassLoader>() {
                    public AppClassLoader run() {
                        URL[] urls = pathToURLs(path);
                        return new AppClassLoader(urls, extcl);
                }
            });
        }

        /*
         * Creates a new AppClassLoader
         */
        AppClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent, factory);
        }

        /**
         * Override loadClass so we can checkPackageAccess.
         */
        public Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
        {
            int i = name.lastIndexOf('.');
            if (i != -1) {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    sm.checkPackageAccess(name.substring(0, i));
                }
            }
            return (super.loadClass(name, resolve));
        }

        /**
         * allow any classes loaded from classpath to exit the VM.
         */
        protected PermissionCollection getPermissions(CodeSource codesource) {
            PermissionCollection perms = super.getPermissions(codesource);
            perms.add(new RuntimePermission("exitVM"));
            return perms;
        }

        /**
         * This class loader supports dynamic additions to the class path
         * at runtime.
         *
         * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
         */
        private void appendToClassPathForInstrumentation(String path) {
            assert(Thread.holdsLock(this));

            // addURL is a no-op if path already contains the URL
            super.addURL( getFileURL(new File(path)) );
        }

        /**
         * create a context that can read any directories (recursively)
         * mentioned in the class path. In the case of a jar, it has to
         * be the directory containing the jar, not just the jar, as jar
         * files might refer to other jar files.
         */

        private static AccessControlContext getContext(File[] cp)
            throws java.net.MalformedURLException
        {
            PathPermissions perms =
                new PathPermissions(cp);

            ProtectionDomain domain =
                new ProtectionDomain(new CodeSource(perms.getCodeBase(),
                    (java.security.cert.Certificate[]) null),
                perms);

            AccessControlContext acc =
                new AccessControlContext(new ProtectionDomain[] { domain });

            return acc;
        }
    }

    private static class BootClassPathHolder {
        static final URLClassPath bcp;
        static {
            URL[] urls = AccessController.doPrivileged(
                    new PrivilegedAction<URL[]>() {
                        public URL[] run() {
                            String bootClassPath = System.getProperty("sun.boot.class.path");
                            if (bootClassPath == null)
                                return new URL[0];
                            // Skip empty path in boot class path i.e. not default to use CWD
                            File[] classPath = getClassPath(bootClassPath, false);
                            int len = classPath.length;
                            Set<File> seenDirs = new HashSet<File>();
                            for (int i = 0; i < len; i++) {
                                File curEntry = classPath[i];
                                // Negative test used to properly handle
                                // nonexistent jars on boot class path
                                if (!curEntry.isDirectory()) {
                                    curEntry = curEntry.getParentFile();
                                }
                                if (curEntry != null && seenDirs.add(curEntry)) {
                                    MetaIndex.registerDirectory(curEntry);
                                }
                            }
                            return pathToURLs(classPath);
                        }
                    }
                );
            bcp = new URLClassPath(urls, factory);
        }
    }

    public static URLClassPath getBootstrapClassPath() {
        return BootClassPathHolder.bcp;
    }

    private static URL[] pathToURLs(File[] path) {
        URL[] urls = new URL[path.length];
        for (int i = 0; i < path.length; i++) {
            urls[i] = getFileURL(path[i]);
        }
        // DEBUG
        //for (int i = 0; i < urls.length; i++) {
        //  System.out.println("urls[" + i + "] = " + '"' + urls[i] + '"');
        //}
        return urls;
    }

    private static File[] getClassPath(String cp, boolean defaultToCwd) {
        File[] path;
        if (cp != null) {
            int count = 0, maxCount = 1;
            int pos = 0, lastPos = 0;
            // Count the number of separators first
            while ((pos = cp.indexOf(File.pathSeparator, lastPos)) != -1) {
                maxCount++;
                lastPos = pos + 1;
            }
            path = new File[maxCount];
            lastPos = pos = 0;
            // Now scan for each path component
            while ((pos = cp.indexOf(File.pathSeparator, lastPos)) != -1) {
                if (pos > lastPos) {
                    path[count++] = new File(cp.substring(lastPos, pos));
                } else if (defaultToCwd) {
                    // empty path component translates to "."
                    path[count++] = new File(".");
                }
                lastPos = pos + 1;
            }
            // Make sure we include the last path component
            if (lastPos < cp.length()) {
                path[count++] = new File(cp.substring(lastPos));
            } else if (defaultToCwd) {
                path[count++] = new File(".");
            }
            // Trim array to correct size
            if (count != maxCount) {
                File[] tmp = new File[count];
                System.arraycopy(path, 0, tmp, 0, count);
                path = tmp;
            }
        } else {
            path = new File[0];
        }
        // DEBUG
        //for (int i = 0; i < path.length; i++) {
        //  System.out.println("path[" + i + "] = " + '"' + path[i] + '"');
        //}
        return path;
    }

    private static URLStreamHandler fileHandler;

    static URL getFileURL(File file) {
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {}

        try {
            return ParseUtil.fileToEncodedURL(file);
        } catch (MalformedURLException e) {
            // Should never happen since we specify the protocol...
            throw new InternalError(e);
        }
    }

    /*
     * The stream handler factory for loading system protocol handlers.
     */
    private static class Factory implements URLStreamHandlerFactory {
        private static String PREFIX = "sun.net.www.protocol";

        public URLStreamHandler createURLStreamHandler(String protocol) {
            String name = PREFIX + "." + protocol + ".Handler";
            try {
                Class<?> c = Class.forName(name);
                return (URLStreamHandler)c.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new InternalError("could not load " + protocol +
                                        "system protocol handler", e);
            }
        }
    }
}

class PathPermissions extends PermissionCollection {
    // use serialVersionUID from JDK 1.2.2 for interoperability
    private static final long serialVersionUID = 8133287259134945693L;

    private File path[];
    private Permissions perms;

    URL codeBase;

    PathPermissions(File path[])
    {
        this.path = path;
        this.perms = null;
        this.codeBase = null;
    }

    URL getCodeBase()
    {
        return codeBase;
    }

    public void add(java.security.Permission permission) {
        throw new SecurityException("attempt to add a permission");
    }

    private synchronized void init()
    {
        if (perms != null)
            return;

        perms = new Permissions();

        // this is needed to be able to create the classloader itself!
        perms.add(SecurityConstants.CREATE_CLASSLOADER_PERMISSION);

        // add permission to read any "java.*" property
        perms.add(new java.util.PropertyPermission("java.*",
            SecurityConstants.PROPERTY_READ_ACTION));

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                for (int i=0; i < path.length; i++) {
                    File f = path[i];
                    String path;
                    try {
                        path = f.getCanonicalPath();
                    } catch (IOException ioe) {
                        path = f.getAbsolutePath();
                    }
                    if (i == 0) {
                        codeBase = Launcher.getFileURL(new File(path));
                    }
                    if (f.isDirectory()) {
                        if (path.endsWith(File.separator)) {
                            perms.add(new FilePermission(path+"-",
                                SecurityConstants.FILE_READ_ACTION));
                        } else {
                            perms.add(new FilePermission(
                                path + File.separator+"-",
                                SecurityConstants.FILE_READ_ACTION));
                        }
                    } else {
                        int endIndex = path.lastIndexOf(File.separatorChar);
                        if (endIndex != -1) {
                            path = path.substring(0, endIndex+1) + "-";
                            perms.add(new FilePermission(path,
                                SecurityConstants.FILE_READ_ACTION));
                        } else {
                            // XXX?
                        }
                    }
                }
                return null;
            }
        });
    }

    public boolean implies(java.security.Permission permission) {
        if (perms == null)
            init();
        return perms.implies(permission);
    }

    public java.util.Enumeration<Permission> elements() {
        if (perms == null)
            init();
        synchronized (perms) {
            return perms.elements();
        }
    }

    public String toString() {
        if (perms == null)
            init();
        return perms.toString();
    }
}
