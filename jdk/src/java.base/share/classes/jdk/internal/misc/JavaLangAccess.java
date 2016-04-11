/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.net.URL;
import java.security.AccessControlContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import jdk.internal.module.ServicesCatalog;
import sun.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationType;
import sun.nio.ch.Interruptible;

public interface JavaLangAccess {
    /** Return the constant pool for a class. */
    ConstantPool getConstantPool(Class<?> klass);

    /**
     * Compare-And-Swap the AnnotationType instance corresponding to this class.
     * (This method only applies to annotation types.)
     */
    boolean casAnnotationType(Class<?> klass, AnnotationType oldType, AnnotationType newType);

    /**
     * Get the AnnotationType instance corresponding to this class.
     * (This method only applies to annotation types.)
     */
    AnnotationType getAnnotationType(Class<?> klass);

    /**
     * Get the declared annotations for a given class, indexed by their types.
     */
    Map<Class<? extends Annotation>, Annotation> getDeclaredAnnotationMap(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Class' annotations.
     */
    byte[] getRawClassAnnotations(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Class' type annotations.
     */
    byte[] getRawClassTypeAnnotations(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Executable's type annotations.
     */
    byte[] getRawExecutableTypeAnnotations(Executable executable);

    /**
     * Returns the elements of an enum class or null if the
     * Class object does not represent an enum type;
     * the result is uncloned, cached, and shared by all callers.
     */
    <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> klass);

    /** Set thread's blocker field. */
    void blockedOn(Thread t, Interruptible b);

    /**
     * Registers a shutdown hook.
     *
     * It is expected that this method with registerShutdownInProgress=true
     * is only used to register DeleteOnExitHook since the first file
     * may be added to the delete on exit list by the application shutdown
     * hooks.
     *
     * @param slot  the slot in the shutdown hook array, whose element
     *              will be invoked in order during shutdown
     * @param registerShutdownInProgress true to allow the hook
     *        to be registered even if the shutdown is in progress.
     * @param hook  the hook to be registered
     *
     * @throws IllegalStateException if shutdown is in progress and
     *         the slot is not valid to register.
     */
    void registerShutdownHook(int slot, boolean registerShutdownInProgress, Runnable hook);

    /**
     * Returns a new string backed by the provided character array. The
     * character array is not copied and must never be modified after the
     * String is created, in order to fulfill String's contract.
     *
     * @param chars the character array to back the string
     * @return a newly created string whose content is the character array
     */
    String newStringUnsafe(char[] chars);

    /**
     * Returns a new Thread with the given Runnable and an
     * inherited AccessControlContext.
     */
    Thread newThreadWithAcc(Runnable target, AccessControlContext acc);

    /**
     * Invokes the finalize method of the given object.
     */
    void invokeFinalize(Object o) throws Throwable;

    /**
     * Returns the boot Layer
     */
    Layer getBootLayer();

    /**
     * Returns the ServicesCatalog for the given class loader.
     */
    ServicesCatalog getServicesCatalog(ClassLoader cl);

    /**
     * Returns the ServicesCatalog for the given class loader, creating it
     * if doesn't already exist.
     */
    ServicesCatalog createOrGetServicesCatalog(ClassLoader cl);

    /**
     * Returns the ConcurrentHashMap used as a storage for ClassLoaderValue(s)
     * associated with the given class loader, creating it if it doesn't already exist.
     */
    ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap(ClassLoader cl);

    /**
     * Returns a class loaded by the bootstrap class loader.
     */
    Class<?> findBootstrapClassOrNull(ClassLoader cl, String name);

    /**
     * Returns a URL to a resource with the given name in a module that is
     * defined to the given class loader.
     */
    URL findResource(ClassLoader cl, String moduleName, String name) throws IOException;

    /**
     * Returns the Packages for the given class loader.
     */
    Stream<Package> packages(ClassLoader cl);

    /**
     * Define a Package of the given name and module by the given class loader.
     */
    Package definePackage(ClassLoader cl, String name, Module module);

    /**
     * Invokes Long.fastUUID
     */
    String fastUUID(long lsb, long msb);
}
