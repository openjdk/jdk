// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.security.AccessController;
import java.security.PrivilegedAction;


/**
 * This utility class is used for resolving a right ClassLoader from
 * a given class. getClassLoader always returns a non-null ClassLoader
 * even a class is loaded through the bootstrap class loader of JRE.
 */
public class ClassLoaderUtil {

    private static class BootstrapClassLoader extends ClassLoader {
        BootstrapClassLoader() {
            // Object.class.getClassLoader() may return null.
            //
            // On Android, the behavior of super(null) is not guaranteed,
            // but Object.class.getClassLoader() actually returns the
            // bootstrap class loader. Note that we probably do not reach
            // this constructor on Android, because ClassLoaderUtil.getClassLoader()
            // should get non-null ClassLoader before calling
            // ClassLoaderUtil.getBootstrapClassLoader().
            //
            // On other common JREs (such as Oracle, OpenJDK),
            // Object.class.getClassLoader() returns null, but
            // super(null) is commonly used for accessing the bootstrap
            // class loader.
            super(Object.class.getClassLoader());
        }
    }

    private static volatile ClassLoader BOOTSTRAP_CLASSLOADER;

    /**
     * Lazily create a singleton BootstrapClassLoader.
     * This class loader might be necessary when ICU4J classes are
     * initialized by bootstrap class loader.
     *
     * @return The BootStrapClassLoader singleton instance
     */
    private static ClassLoader getBootstrapClassLoader() {
        if (BOOTSTRAP_CLASSLOADER == null) {
            synchronized(ClassLoaderUtil.class) {
                if (BOOTSTRAP_CLASSLOADER == null) {
                    BOOTSTRAP_CLASSLOADER = new BootstrapClassLoader();
                }
            }
        }
        return BOOTSTRAP_CLASSLOADER;
    }


    /**
     * Returns the class loader used for loading the specified class.
     * @param cls The class
     * @return the class loader
     */
    public static ClassLoader getClassLoader(Class<?> cls) {
        ClassLoader cl = cls.getClassLoader();
        if (cl == null) {
            cl = getClassLoader();
        }
        return cl;
    }

    /**
     * Returns a fallback class loader.
     * @return A class loader
     */
    public static ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
            if (cl == null) {
                // When this method is called for initializing a ICU4J class
                // during bootstrap, cl might be still null (other than Android?).
                // In this case, we want to use the bootstrap class loader.
                cl = getBootstrapClassLoader();
            }
        }
        return cl;
    }
}
