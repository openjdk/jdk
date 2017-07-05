/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.sound;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.sound.sampled.AudioPermission;

/** Managing security in the Java Sound implementation.
 * This class contains all code that uses and is used by
 * SecurityManager.doPrivileged().
 *
 * @author Matthias Pfisterer
 */
final class JSSecurityManager {

    /** Prevent instantiation.
     */
    private JSSecurityManager() {
    }

    /** Checks if the VM currently has a SecurityManager installed.
     * Note that this may change over time. So the result of this method
     * should not be cached.
     *
     * @return true if a SecurityManger is installed, false otherwise.
     */
    private static boolean hasSecurityManager() {
        return (System.getSecurityManager() != null);
    }


    static void checkRecordPermission() throws SecurityException {
        if(Printer.trace) Printer.trace("JSSecurityManager.checkRecordPermission()");
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AudioPermission("record"));
        }
    }

    static String getProperty(final String propertyName) {
        String propertyValue;
        if (hasSecurityManager()) {
            if(Printer.debug) Printer.debug("using JDK 1.2 security to get property");
            try{
                PrivilegedAction<String> action = new PrivilegedAction<String>() {
                        public String run() {
                            try {
                                return System.getProperty(propertyName);
                            } catch (Throwable t) {
                                return null;
                            }
                        }
                    };
                propertyValue = AccessController.doPrivileged(action);
            } catch( Exception e ) {
                if(Printer.debug) Printer.debug("not using JDK 1.2 security to get properties");
                propertyValue = System.getProperty(propertyName);
            }
        } else {
            if(Printer.debug) Printer.debug("not using JDK 1.2 security to get properties");
            propertyValue = System.getProperty(propertyName);
        }
        return propertyValue;
    }


    /** Load properties from a file.
        This method tries to load properties from the filename give into
        the passed properties object.
        If the file cannot be found or something else goes wrong,
        the method silently fails.
        @param properties The properties bundle to store the values of the
        properties file.
        @param filename The filename of the properties file to load. This
        filename is interpreted as relative to the subdirectory "lib" in
        the JRE directory.
     */
    static void loadProperties(final Properties properties,
                               final String filename) {
        if(hasSecurityManager()) {
            try {
                // invoke the privileged action using 1.2 security
                PrivilegedAction<Void> action = new PrivilegedAction<Void>() {
                        public Void run() {
                            loadPropertiesImpl(properties, filename);
                            return null;
                        }
                    };
                AccessController.doPrivileged(action);
                if(Printer.debug)Printer.debug("Loaded properties with JDK 1.2 security");
            } catch (Exception e) {
                if(Printer.debug)Printer.debug("Exception loading properties with JDK 1.2 security");
                // try without using JDK 1.2 security
                loadPropertiesImpl(properties, filename);
            }
        } else {
            // not JDK 1.2 security, assume we already have permission
            loadPropertiesImpl(properties, filename);
        }
    }


    private static void loadPropertiesImpl(Properties properties,
                                           String filename) {
        if(Printer.trace)Printer.trace(">> JSSecurityManager: loadPropertiesImpl()");
        String fname = System.getProperty("java.home");
        try {
            if (fname == null) {
                throw new Error("Can't find java.home ??");
            }
            File f = new File(fname, "lib");
            f = new File(f, filename);
            fname = f.getCanonicalPath();
            InputStream in = new FileInputStream(fname);
            BufferedInputStream bin = new BufferedInputStream(in);
            try {
                properties.load(bin);
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } catch (Throwable t) {
            if (Printer.trace) {
                System.err.println("Could not load properties file \"" + fname + "\"");
                t.printStackTrace();
            }
        }
        if(Printer.trace)Printer.trace("<< JSSecurityManager: loadPropertiesImpl() completed");
    }

    /** Create a Thread in the current ThreadGroup.
     */
    static Thread createThread(final Runnable runnable,
                               final String threadName,
                               final boolean isDaemon, final int priority,
                               final boolean doStart) {
        Thread thread = new Thread(runnable);
        if (threadName != null) {
            thread.setName(threadName);
        }
        thread.setDaemon(isDaemon);
        if (priority >= 0) {
            thread.setPriority(priority);
        }
        if (doStart) {
            thread.start();
        }
        return thread;
    }

    static synchronized <T> List<T> getProviders(final Class<T> providerClass) {
        List<T> p = new ArrayList<>(7);
        // ServiceLoader creates "lazy" iterator instance, but it ensures that
        // next/hasNext run with permissions that are restricted by whatever
        // creates the ServiceLoader instance, so it requires to be called from
        // privileged section
        final PrivilegedAction<Iterator<T>> psAction =
                new PrivilegedAction<Iterator<T>>() {
                    @Override
                    public Iterator<T> run() {
                        return ServiceLoader.load(providerClass).iterator();
                    }
                };
        final Iterator<T> ps = AccessController.doPrivileged(psAction);

        // the iterator's hasNext() method looks through classpath for
        // the provider class names, so it requires read permissions
        PrivilegedAction<Boolean> hasNextAction = new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return ps.hasNext();
            }
        };

        while (AccessController.doPrivileged(hasNextAction)) {
            try {
                // the iterator's next() method creates instances of the
                // providers and it should be called in the current security
                // context
                T provider = ps.next();
                if (providerClass.isInstance(provider)) {
                    // $$mp 2003-08-22
                    // Always adding at the beginning reverses the
                    // order of the providers. So we no longer have
                    // to do this in AudioSystem and MidiSystem.
                    p.add(0, provider);
                }
            } catch (Throwable t) {
                //$$fb 2002-11-07: do not fail on SPI not found
                if (Printer.err) t.printStackTrace();
            }
        }
        return p;
    }
}
