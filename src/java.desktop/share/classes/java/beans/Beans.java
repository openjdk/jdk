/*
 * Copyright (c) 1996, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

import com.sun.beans.finder.ClassFinder;

import java.awt.Image;

import java.beans.beancontext.BeanContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

import java.lang.reflect.Modifier;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

/**
 * This class provides some general purpose beans control methods.
 *
 * @since 1.1
 */

public class Beans {

    /**
     * Constructs a {@code Beans}.
     */
    public Beans() {}

    /**
     * <p>
     * Instantiate a JavaBean.
     * </p>
     * The bean is created based on a name relative to a class-loader.
     * This name should be a {@linkplain ClassLoader##binary-name binary name} of a class such as "a.b.C".
     * <p>
     * The given name can indicate either a serialized object or a class.
     * We first try to treat the {@code beanName} as a serialized object
     * name then as a class name.
     * <p>
     * When using the {@code beanName} as a serialized object name we convert the
     * given {@code beanName} to a resource pathname and add a trailing ".ser" suffix.
     * We then try to load a serialized object from that resource.
     * <p>
     * For example, given a {@code beanName} of "x.y", {@code Beans.instantiate} would first
     * try to read a serialized object from the resource "x/y.ser" and if
     * that failed it would try to load the class "x.y" and create an
     * instance of that class.
     *
     * @return a JavaBean
     * @param     cls         the class-loader from which we should create
     *                        the bean.  If this is null, then the system
     *                        class-loader is used.
     * @param     beanName    the name of the bean within the class-loader.
     *                        For example "sun.beanbox.foobah"
     *
     * @throws ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @throws IOException if an I/O error occurs.
     */

    public static Object instantiate(ClassLoader cls, String beanName) throws IOException, ClassNotFoundException {
        return Beans.instantiate(cls, beanName, null);
    }

    /**
     * <p>
     * Instantiate a JavaBean.
     * </p>
     * The bean is created based on a name relative to a class-loader.
     * This name should be a {@linkplain ClassLoader##binary-name binary name} of a class such as "a.b.C".
     * <p>
     * The given name can indicate either a serialized object or a class.
     * We first try to treat the {@code beanName} as a serialized object
     * name then as a class name.
     * <p>
     * When using the {@code beanName} as a serialized object name we convert the
     * given {@code beanName} to a resource pathname and add a trailing ".ser" suffix.
     * We then try to load a serialized object from that resource.
     * <p>
     * For example, given a {@code beanName} of "x.y", {@code Beans.instantiate} would first
     * try to read a serialized object from the resource "x/y.ser" and if
     * that failed it would try to load the class "x.y" and create an
     * instance of that class.
     *
     * @return a JavaBean
     *
     * @param     cls         the class-loader from which we should create
     *                        the bean.  If this is null, then the system
     *                        class-loader is used.
     * @param     beanName    the name of the bean within the class-loader.
     *                        For example "sun.beanbox.foobah"
     * @param     beanContext The BeanContext in which to nest the new bean
     *
     * @throws ClassNotFoundException if the class of a serialized
     *              object could not be found.
     * @throws IOException if an I/O error occurs.
     * @since 1.2
     * @deprecated this method will be removed when java.beans.beancontext is removed
     */
    @Deprecated(since = "23", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object instantiate(ClassLoader cls, String beanName,
                                     BeanContext beanContext)
            throws IOException, ClassNotFoundException {

        InputStream ins;
        ObjectInputStream oins = null;
        Object result = null;
        boolean serialized = false;
        IOException serex = null;

        // If the given classloader is null, we check if an
        // system classloader is available and (if so)
        // use that instead.
        // Note that calls on the system class loader will
        // look in the bootstrap class loader first.
        if (cls == null) {
            cls = ClassLoader.getSystemClassLoader();
        }

        // Try to find a serialized object with this name
        final String serName = beanName.replace('.','/').concat(".ser");
        if (cls == null)
            ins =  ClassLoader.getSystemResourceAsStream(serName);
        else
            ins =  cls.getResourceAsStream(serName);
        if (ins != null) {
            try (ins) {
                if (cls == null) {
                    oins = new ObjectInputStream(ins);
                } else {
                    oins = new ObjectInputStreamWithLoader(ins, cls);
                }
                result = oins.readObject();
                serialized = true;
                oins.close();
            } catch (IOException ex) {
                // Drop through and try opening the class.  But remember
                // the exception in case we can't find the class either.
                serex = ex;
            }
        }

        if (result == null) {
            // No serialized object, try just instantiating the class
            Class<?> cl;

            try {
                cl = ClassFinder.findClass(beanName, cls);
            } catch (ClassNotFoundException ex) {
                // There is no appropriate class.  If we earlier tried to
                // deserialize an object and got an IO exception, throw that,
                // otherwise rethrow the ClassNotFoundException.
                if (serex != null) {
                    throw serex;
                }
                throw ex;
            }

            if (!Modifier.isPublic(cl.getModifiers())) {
                throw new ClassNotFoundException("" + cl + " : no public access");
            }

            /*
             * Try to instantiate the class.
             */

            try {
                result = cl.newInstance();
            } catch (Exception ex) {
                // We have to remap the exception to one in our signature.
                // But we pass extra information in the detail message.
                throw new ClassNotFoundException("" + cl + " : " + ex, ex);
            }
        }

        if (result != null) {
           if (beanContext != null) unsafeBeanContextAdd(beanContext, result);
        }

        return result;
    }

    @Deprecated(since = "23", forRemoval = true)
    @SuppressWarnings({ "unchecked", "removal" })
    private static void unsafeBeanContextAdd(BeanContext beanContext, Object res) {
        beanContext.add(res);
    }

    @SuppressWarnings("deprecation")
    private static URL newURL(String spec) throws MalformedURLException {
        return new URL(spec);
    }

    /**
     * From a given bean, obtain an object representing a specified
     * type view of that source object.
     * <p>
     * The result may be the same object or a different object.  If
     * the requested target view isn't available then the given
     * bean is returned.
     * <p>
     * This method is provided in Beans 1.0 as a hook to allow the
     * addition of more flexible bean behaviour in the future.
     *
     * @return an object representing a specified type view of the
     * source object
     * @param bean        Object from which we want to obtain a view.
     * @param targetType  The type of view we'd like to get.
     *
     */
    public static Object getInstanceOf(Object bean, Class<?> targetType) {
        return bean;
    }

    /**
     * Check if a bean can be viewed as a given target type.
     * The result will be true if the Beans.getInstanceof method
     * can be used on the given bean to obtain an object that
     * represents the specified targetType type view.
     *
     * @param bean  Bean from which we want to obtain a view.
     * @param targetType  The type of view we'd like to get.
     * @return "true" if the given bean supports the given targetType.
     *
     */
    public static boolean isInstanceOf(Object bean, Class<?> targetType) {
        return Introspector.isSubclass(bean.getClass(), targetType);
    }

    /**
     * Test if we are in design-mode.
     *
     * @return  True if we are running in an application construction
     *          environment.
     *
     * @see DesignMode
     */
    public static boolean isDesignTime() {
        return ThreadGroupContext.getContext().isDesignTime();
    }

    /**
     * Determines whether beans can assume a GUI is available.
     *
     * @return  True if we are running in an environment where beans
     *     can assume that an interactive GUI is available, so they
     *     can pop up dialog boxes, etc.  This will normally return
     *     true in a windowing environment, and will normally return
     *     false in a server environment or if an application is
     *     running as part of a batch job.
     *
     * @see Visibility
     *
     */
    public static boolean isGuiAvailable() {
        return ThreadGroupContext.getContext().isGuiAvailable();
    }

    /**
     * Used to indicate whether of not we are running in an application
     * builder environment.
     *
     * @param isDesignTime  True if we're in an application builder tool.
     */

    public static void setDesignTime(boolean isDesignTime) {
        ThreadGroupContext.getContext().setDesignTime(isDesignTime);
    }

    /**
     * Used to indicate whether of not we are running in an environment
     * where GUI interaction is available.
     *
     * @param isGuiAvailable  True if GUI interaction is available.
     */

    public static void setGuiAvailable(boolean isGuiAvailable) {
        ThreadGroupContext.getContext().setGuiAvailable(isGuiAvailable);
    }
}

/**
 * This subclass of ObjectInputStream delegates loading of classes to
 * an existing ClassLoader.
 */

class ObjectInputStreamWithLoader extends ObjectInputStream
{
    private ClassLoader loader;

    /**
     * Loader must be non-null;
     */

    public ObjectInputStreamWithLoader(InputStream in, ClassLoader loader)
            throws IOException, StreamCorruptedException {

        super(in);
        if (loader == null) {
            throw new IllegalArgumentException("Illegal null argument to ObjectInputStreamWithLoader");
        }
        this.loader = loader;
    }

    /**
     * Use the given ClassLoader rather than using the system class
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected Class resolveClass(ObjectStreamClass classDesc)
        throws IOException, ClassNotFoundException {

        String cname = classDesc.getName();
        return ClassFinder.resolveClass(cname, this.loader);
    }
}
