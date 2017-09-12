/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper utility to support jdk <= jdk1.6. After jdk1.6 EOL reflection can be removed and API can be used directly.
 */
class TempFiles {

    private static final Logger LOGGER = Logger.getLogger(TempFiles.class.getName());

    private static final Class<?> CLASS_FILES;
    private static final Class<?> CLASS_PATH;
    private static final Class<?> CLASS_FILE_ATTRIBUTE;
    private static final Class<?> CLASS_FILE_ATTRIBUTES;
    private static final Method METHOD_FILE_TO_PATH;
    private static final Method METHOD_FILES_CREATE_TEMP_FILE;
    private static final Method METHOD_FILES_CREATE_TEMP_FILE_WITHPATH;

    private static final Method METHOD_PATH_TO_FILE;

    private static boolean useJdk6API;

    static {
        useJdk6API = isJdk6();

        CLASS_FILES = safeGetClass("java.nio.file.Files");
        CLASS_PATH = safeGetClass("java.nio.file.Path");
        CLASS_FILE_ATTRIBUTE = safeGetClass("java.nio.file.attribute.FileAttribute");
        CLASS_FILE_ATTRIBUTES = safeGetClass("[Ljava.nio.file.attribute.FileAttribute;");
        METHOD_FILE_TO_PATH = safeGetMethod(File.class, "toPath");
        METHOD_FILES_CREATE_TEMP_FILE = safeGetMethod(CLASS_FILES, "createTempFile", String.class, String.class, CLASS_FILE_ATTRIBUTES);
        METHOD_FILES_CREATE_TEMP_FILE_WITHPATH = safeGetMethod(CLASS_FILES, "createTempFile", CLASS_PATH, String.class, String.class, CLASS_FILE_ATTRIBUTES);
        METHOD_PATH_TO_FILE = safeGetMethod(CLASS_PATH, "toFile");
    }

    private static boolean isJdk6() {
        String javaVersion = System.getProperty("java.version");
        LOGGER.log(Level.FINEST, "Detected java version = {0}", javaVersion);
        return javaVersion.startsWith("1.6.");
    }

    private static Class<?> safeGetClass(String className) {
        // it is jdk 6 or something failed already before
        if (useJdk6API) return null;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Exception cought", e);
            LOGGER.log(Level.WARNING, "Class {0} not found. Temp files will be created using old java.io API.", className);
            useJdk6API = true;
            return null;
        }
    }

    private static Method safeGetMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        // it is jdk 6 or something failed already before
        if (useJdk6API) return null;
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.SEVERE, "Exception cought", e);
            LOGGER.log(Level.WARNING, "Method {0} not found. Temp files will be created using old java.io API.", methodName);
            useJdk6API = true;
            return null;
        }
    }


    static Object toPath(File f) throws InvocationTargetException, IllegalAccessException {
        return METHOD_FILE_TO_PATH.invoke(f);
    }

    static File toFile(Object path) throws InvocationTargetException, IllegalAccessException {
        return (File) METHOD_PATH_TO_FILE.invoke(path);
    }

    static File createTempFile(String prefix, String suffix, File dir) throws IOException {

        if (useJdk6API) {
            LOGGER.log(Level.FINEST, "Jdk6 detected, temp file (prefix:{0}, suffix:{1}) being created using old java.io API.", new Object[]{prefix, suffix});
            return File.createTempFile(prefix, suffix, dir);

        } else {

            try {
                if (dir != null) {
                    Object path = toPath(dir);
                    LOGGER.log(Level.FINEST, "Temp file (path: {0}, prefix:{1}, suffix:{2}) being created using NIO API.", new Object[]{dir.getAbsolutePath(), prefix, suffix});
                    return toFile(METHOD_FILES_CREATE_TEMP_FILE_WITHPATH.invoke(null, path, prefix, suffix, Array.newInstance(CLASS_FILE_ATTRIBUTE, 0)));
                } else {
                    LOGGER.log(Level.FINEST, "Temp file (prefix:{0}, suffix:{1}) being created using NIO API.", new Object[]{prefix, suffix});
                    return toFile(METHOD_FILES_CREATE_TEMP_FILE.invoke(null, prefix, suffix, Array.newInstance(CLASS_FILE_ATTRIBUTE, 0)));
                }

            } catch (IllegalAccessException e) {
                LOGGER.log(Level.SEVERE, "Exception caught", e);
                LOGGER.log(Level.WARNING, "Error invoking java.nio API, temp file (path: {0}, prefix:{1}, suffix:{2}) being created using old java.io API.",
                        new Object[]{dir != null ? dir.getAbsolutePath() : null, prefix, suffix});
                return File.createTempFile(prefix, suffix, dir);

            } catch (InvocationTargetException e) {
                LOGGER.log(Level.SEVERE, "Exception caught", e);
                LOGGER.log(Level.WARNING, "Error invoking java.nio API, temp file (path: {0}, prefix:{1}, suffix:{2}) being created using old java.io API.",
                        new Object[]{dir != null ? dir.getAbsolutePath() : null, prefix, suffix});
                return File.createTempFile(prefix, suffix, dir);
            }
        }

    }


}
