/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.util;

import java.io.IOException;
import java.io.InputStream;

import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Utility;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * This repository is used in situations where a Class is created outside the realm of a ClassLoader. Classes are loaded
 * from the file systems using the paths specified in the given class path. By default, this is the value returned by
 * ClassPath.getClassPath().
 * <p>
 * This repository uses a factory design, allowing it to maintain a collection of different classpaths, and as such It
 * is designed to be used as a singleton per classpath.
 * </p>
 *
 * @see com.sun.org.apache.bcel.internal.Repository
 *
 * @LastModified: Feb 2023
 */
public class SyntheticRepository implements Repository {

    // CLASSNAME X JAVACLASS
    private final Map<String, SoftReference<JavaClass>> loadedClasses = new HashMap<>();

    private SyntheticRepository() {
    }

    public static SyntheticRepository getInstance() {
        return new SyntheticRepository();
    }

    /**
     * Clear all entries from cache.
     */
    @Override
    public void clear() {
        loadedClasses.clear();
    }

    /**
     * Find an already defined (cached) JavaClass object by name.
     */
    @Override
    public JavaClass findClass(final String className) {
        final SoftReference<JavaClass> ref = loadedClasses.get(className);
        return ref == null ? null : ref.get();
    }

    /**
     * Remove class from repository
     */
    @Override
    public void removeClass(final JavaClass clazz) {
        loadedClasses.remove(clazz.getClassName());
    }

    /**
     * Store a new JavaClass instance into this Repository.
     */
    @Override
    public void storeClass(final JavaClass clazz) {
        // Not calling super.storeClass because this subclass maintains the mapping.
        loadedClasses.put(clazz.getClassName(), new SoftReference<>(clazz));
        clazz.setRepository(this);
    }

    /**
     * Finds the JavaClass object for a runtime Class object. If a class with the same name is already in this Repository,
     * the Repository version is returned. Otherwise, getResourceAsStream() is called on the Class object to find the
     * class's representation. If the representation is found, it is added to the Repository.
     *
     * @see Class
     * @param clazz the runtime Class object
     * @return JavaClass object for given runtime class
     * @throws ClassNotFoundException if the class is not in the Repository, and its representation could not be found
     */
    @Override
    public JavaClass loadClass(final Class<?> clazz) throws ClassNotFoundException {
        final String className = clazz.getName();
        final JavaClass repositoryClass = findClass(className);
        if (repositoryClass != null) {
            return repositoryClass;
        }
        String name = className;
        final int i = name.lastIndexOf('.');
        if (i > 0) {
            name = name.substring(i + 1);
        }

        try (InputStream clsStream = clazz.getResourceAsStream(name + JavaClass.EXTENSION)) {
            return loadClass(clsStream, className);
        } catch (final IOException e) {
            return null;
        }
    }

    private JavaClass loadClass(final InputStream inputStream, final String className) throws ClassNotFoundException {
        try {
            if (inputStream != null) {
                final ClassParser parser = new ClassParser(inputStream, className);
                final JavaClass clazz = parser.parse();
                storeClass(clazz);
                return clazz;
            }
        } catch (final IOException e) {
            throw new ClassNotFoundException("Exception while looking for class " + className + ": " + e, e);
        }
        throw new ClassNotFoundException("ClassRepository could not load " + className);
    }

    /**
     * Finds a JavaClass object by name. If it is already in this Repository, the Repository version is returned. Otherwise,
     * the Repository's classpath is searched for the class (and it is added to the Repository if found).
     *
     * @param className the name of the class
     * @return the JavaClass object
     * @throws ClassNotFoundException if the class is not in the Repository, and could not be found on the classpath
     */
    @Override
    public JavaClass loadClass(String className) throws ClassNotFoundException {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Invalid class name " + className);
        }
        className = Utility.pathToPackage(className); // Just in case, canonical form
        final JavaClass clazz = findClass(className);
        if (clazz != null) {
            return clazz;
        }
        IOException e = new IOException("Couldn't find: " + className + ".class");
        throw new ClassNotFoundException("Exception while looking for class " +
                className + ": " + e, e);
    }
}
