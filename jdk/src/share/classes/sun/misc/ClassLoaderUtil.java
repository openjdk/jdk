/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;

/**
 * Provides utility functions related to URLClassLoaders or subclasses of it.
 *
 *                  W  A  R  N  I  N  G
 *
 * This class uses undocumented, unpublished, private data structures inside
 * java.net.URLClassLoader and sun.misc.URLClassPath.  Use with extreme caution.
 *
 * @author      tjquinn
 */


import java.io.IOException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarFile;

public class ClassLoaderUtil {

    /**
     * Releases resources held by a URLClassLoader. A new classloader must
     * be created before the underlying resources can be accessed again.
     * @param classLoader the instance of URLClassLoader (or a subclass)
     */
    public static void releaseLoader(URLClassLoader classLoader) {
        releaseLoader(classLoader, null);
    }

    /**
     * Releases resources held by a URLClassLoader.  Notably, close the jars
     * opened by the loader. Initializes and updates the List of
     * jars that have been successfully closed.
     * <p>
     * @param classLoader the instance of URLClassLoader (or a subclass)
     * @param jarsClosed a List of Strings that will contain the names of jars
     *  successfully closed; can be null if the caller does not need the information returned
     * @return a List of IOExceptions reporting jars that failed to close; null
     * indicates that an error other than an IOException occurred attempting to
     * release the loader; empty indicates a successful release; non-empty
     * indicates at least one error attempting to close an open jar.
     */
    public static List<IOException> releaseLoader(URLClassLoader classLoader, List<String> jarsClosed) {

        List<IOException> ioExceptions = new LinkedList<IOException>();

        try {
            /* Records all IOExceptions thrown while closing jar files. */

            if (jarsClosed != null) {
                jarsClosed.clear();
            }

            System.out.println ("classLoader = " + classLoader);
            System.out.println ("SharedSecrets.getJavaNetAccess()="+SharedSecrets.getJavaNetAccess());
            URLClassPath ucp = SharedSecrets.getJavaNetAccess()
                                                .getURLClassPath(classLoader);
            ArrayList loaders = ucp.loaders;
            Stack urls = ucp.urls;
            HashMap lmap = ucp.lmap;

            /*
             *The urls variable in the URLClassPath object holds URLs that have not yet
             *been used to resolve a resource or load a class and, therefore, do
             *not yet have a loader associated with them.  Clear the stack so any
             *future requests that might incorrectly reach the loader cannot be
             *resolved and cannot open a jar file after we think we've closed
             *them all.
             */
            synchronized(urls) {
                urls.clear();
            }

            /*
             *Also clear the map of URLs to loaders so the class loader cannot use
             *previously-opened jar files - they are about to be closed.
             */
            synchronized(lmap) {
                lmap.clear();
            }

            /*
             *The URLClassPath object's path variable records the list of all URLs that are on
             *the URLClassPath's class path.  Leave that unchanged.  This might
             *help someone trying to debug why a released class loader is still used.
             *Because the stack and lmap are now clear, code that incorrectly uses a
             *the released class loader will trigger an exception if the
             *class or resource would have been resolved by the class
             *loader (and no other) if it had not been released.
             *
             *The list of URLs might provide some hints to the person as to where
             *in the code the class loader was set up, which might in turn suggest
             *where in the code the class loader needs to stop being used.
             *The URLClassPath does not use the path variable to open new jar
             *files - it uses the urls Stack for that - so leaving the path variable
             *will not by itself allow the class loader to continue handling requests.
             */

            /*
             *For each loader, close the jar file associated with that loader.
             *
             *The URLClassPath's use of loaders is sync-ed on the entire URLClassPath
             *object.
             */
            synchronized (ucp) {
                for (Object o : loaders) {
                    if (o != null) {
                        /*
                         *If the loader is a JarLoader inner class and its jarFile
                         *field is non-null then try to close that jar file.  Add
                         *it to the list of closed files if successful.
                         */
                        if (o instanceof URLClassPath.JarLoader) {
                                URLClassPath.JarLoader jl = (URLClassPath.JarLoader)o;
                                JarFile jarFile = jl.getJarFile();
                                try {
                                    if (jarFile != null) {
                                        jarFile.close();
                                        if (jarsClosed != null) {
                                            jarsClosed.add(jarFile.getName());
                                        }
                                    }
                                } catch (IOException ioe) {
                                    /*
                                     *Wrap the IOException to identify which jar
                                     *could not be closed and add it to the list
                                     *of IOExceptions to be returned to the caller.
                                     */
                                    String jarFileName = (jarFile == null) ? "filename not available":jarFile.getName();
                                    String msg = "Error closing JAR file: " + jarFileName;
                                    IOException newIOE = new IOException(msg);
                                    newIOE.initCause(ioe);
                                    ioExceptions.add(newIOE);
                                }
                        }
                    }
                }
                /*
                 *Now clear the loaders ArrayList.
                 */
                loaders.clear();
            }
        } catch (Throwable t) {
            throw new RuntimeException (t);
        }
        return ioExceptions;
    }
}
