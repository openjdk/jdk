/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.org.apache.bcel.internal;


import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.util.SyntheticRepository;

/**
 * The repository maintains informations about class interdependencies, for example, whether a class is a sub-class of another.
 * Delegates actual class loading to SyntheticRepository with current class path by default.
 *
 * @see com.sun.org.apache.bcel.internal.util.Repository
 * @see SyntheticRepository
 *
 * @LastModified: Oct 2026
 */
public abstract class Repository {

    private static com.sun.org.apache.bcel.internal.util.Repository repository = SyntheticRepository.getInstance();

    /**
     * Adds clazz to repository if there isn't an equally named class already in there.
     *
     * @param clazz The class to add.
     * @return old entry in repository.
     */
    public static JavaClass addClass(final JavaClass clazz) {
        final JavaClass old = repository.findClass(clazz.getClassName());
        repository.storeClass(clazz);
        return old;
    }

    /**
     * Clears the repository.
     */
    public static void clearCache() {
        repository.clear();
    }

    /**
     * Gets all interfaces implemented by the class.
     *
     * @param clazz The class.
     * @return all interfaces implemented by class and its super classes and the interfaces that those interfaces extend,
     *         and so on. (Some people call this a transitive hull).
     * @throws ClassNotFoundException Thrown if any of the class's superclasses or superinterfaces can't be found.
     */
    public static JavaClass[] getInterfaces(final JavaClass clazz) throws ClassNotFoundException {
        return clazz.getAllInterfaces();
    }

    /**
     * Gets all interfaces implemented by the class.
     *
     * @param className The class name.
     * @return all interfaces implemented by class and its super classes and the interfaces that extend those interfaces,
     *         and so on.
     * @throws ClassNotFoundException Thrown if the named class can't be found, or if any of its superclasses or superinterfaces
     *         can't be found.
     */
    public static JavaClass[] getInterfaces(final String className) throws ClassNotFoundException {
        return getInterfaces(lookupClass(className));
    }

    /**
     * Gets the currently used repository instance.
     *
     * @return currently used repository instance.
     */
    public static com.sun.org.apache.bcel.internal.util.Repository getRepository() {
        return repository;
    }

    /**
     * Gets the list of super classes.
     *
     * @param clazz The class.
     * @return list of super classes of clazz in ascending order, that is, Object is always the last element.
     * @throws ClassNotFoundException Thrown if any of the superclasses can't be found.
     */
    public static JavaClass[] getSuperClasses(final JavaClass clazz) throws ClassNotFoundException {
        return clazz.getSuperClasses();
    }

    /**
     * Gets the list of super classes.
     *
     * @param className The class name.
     * @return list of super classes of clazz in ascending order, that is, Object is always the last element.
     * @throws ClassNotFoundException Thrown if the named class or any of its superclasses can't be found.
     */
    public static JavaClass[] getSuperClasses(final String className) throws ClassNotFoundException {
        return getSuperClasses(lookupClass(className));
    }

    /**
     * Tests if clazz is an implementation of interface inter.
     *
     * @param clazz The class to test.
     * @param inter The interface.
     * @return true, if clazz is an implementation of interface inter.
     * @throws ClassNotFoundException Thrown if any superclasses or superinterfaces of clazz can't be found.
     */
    public static boolean implementationOf(final JavaClass clazz, final JavaClass inter) throws ClassNotFoundException {
        return clazz.implementationOf(inter);
    }

    /**
     * Tests if clazz is an implementation of interface inter.
     *
     * @param clazz The class to test.
     * @param inter The interface name.
     * @return true, if clazz is an implementation of interface inter.
     * @throws ClassNotFoundException Thrown if inter or any superclasses or superinterfaces of clazz can't be found.
     */
    public static boolean implementationOf(final JavaClass clazz, final String inter) throws ClassNotFoundException {
        return implementationOf(clazz, lookupClass(inter));
    }

    /**
     * Tests if clazz is an implementation of interface inter.
     *
     * @param clazz The class name to test.
     * @param inter The interface.
     * @return true, if clazz is an implementation of interface inter.
     * @throws ClassNotFoundException Thrown if clazz or any superclasses or superinterfaces of clazz can't be found.
     */
    public static boolean implementationOf(final String clazz, final JavaClass inter) throws ClassNotFoundException {
        return implementationOf(lookupClass(clazz), inter);
    }

    /**
     * Tests if clazz is an implementation of interface inter.
     *
     * @param clazz The class name to test.
     * @param inter The interface name.
     * @return true, if clazz is an implementation of interface inter.
     * @throws ClassNotFoundException Thrown if clazz, inter, or any superclasses or superinterfaces of clazz can't be found.
     */
    public static boolean implementationOf(final String clazz, final String inter) throws ClassNotFoundException {
        return implementationOf(lookupClass(clazz), lookupClass(inter));
    }

    /**
     * Equivalent to runtime "instanceof" operator.
     *
     * @param clazz The class to test.
     * @param superclass The superclass.
     * @return true, if clazz is an instance of superclass.
     * @throws ClassNotFoundException Thrown if any superclasses or superinterfaces of clazz can't be found.
     */
    public static boolean instanceOf(final JavaClass clazz, final JavaClass superclass) throws ClassNotFoundException {
        return clazz.instanceOf(superclass);
    }

    /**
     * Tests if clazz is an instance of superclass.
     *
     * @param clazz The class to test.
     * @param superclass The superclass name.
     * @return true, if clazz is an instance of superclass.
     * @throws ClassNotFoundException Thrown if superclass can't be found.
     */
    public static boolean instanceOf(final JavaClass clazz, final String superclass) throws ClassNotFoundException {
        return instanceOf(clazz, lookupClass(superclass));
    }

    /**
     * Tests if clazz is an instance of superclass.
     *
     * @param clazz The class name to test.
     * @param superclass The superclass.
     * @return true, if clazz is an instance of superclass.
     * @throws ClassNotFoundException Thrown if clazz can't be found.
     */
    public static boolean instanceOf(final String clazz, final JavaClass superclass) throws ClassNotFoundException {
        return instanceOf(lookupClass(clazz), superclass);
    }

    /**
     * Tests if clazz is an instance of superclass.
     *
     * @param clazz The class name to test.
     * @param superclass The superclass name.
     * @return true, if clazz is an instance of superclass.
     * @throws ClassNotFoundException Thrown if either clazz or superclass can't be found.
     */
    public static boolean instanceOf(final String clazz, final String superclass) throws ClassNotFoundException {
        return instanceOf(lookupClass(clazz), lookupClass(superclass));
    }

    /**
     * Tries to find class source using the internal repository instance.
     *
     * @param clazz The class.
     * @see Class
     * @return JavaClass object for given runtime class.
     * @throws ClassNotFoundException Thrown if the class could not be found or parsed correctly.
     */
    public static JavaClass lookupClass(final Class<?> clazz) throws ClassNotFoundException {
        return repository.loadClass(clazz);
    }

    /**
     * Lookups class somewhere found on your CLASSPATH, or wherever the repository instance looks for it.
     *
     * @param className The class name.
     * @return class object for given fully qualified class name.
     * @throws ClassNotFoundException Thrown if the class could not be found or parsed correctly.
     */
    public static JavaClass lookupClass(final String className) throws ClassNotFoundException {
        return repository.loadClass(className);
    }

    /**
     * Removes given class from repository.
     *
     * @param clazz The class to remove.
     */
    public static void removeClass(final JavaClass clazz) {
        repository.removeClass(clazz);
    }

    /**
     * Removes class with given (fully qualified) name from repository.
     *
     * @param clazz The class name to remove.
     */
    public static void removeClass(final String clazz) {
        repository.removeClass(repository.findClass(clazz));
    }

    /**
     * Sets repository instance to be used for class loading.
     *
     * @param rep The repository instance.
     */
    public static void setRepository(final com.sun.org.apache.bcel.internal.util.Repository rep) {
        repository = rep;
    }

    /**
     * Constructs a new Repository.
     */
    public Repository() {
    }
}
