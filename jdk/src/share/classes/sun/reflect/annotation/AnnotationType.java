/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.annotation;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Represents an annotation type at run time.  Used to type-check annotations
 * and apply member defaults.
 *
 * @author  Josh Bloch
 * @since   1.5
 */
public class AnnotationType {
    /**
     * Member name -> type mapping. Note that primitive types
     * are represented by the class objects for the corresponding wrapper
     * types.  This matches the return value that must be used for a
     * dynamic proxy, allowing for a simple isInstance test.
     */
    private final Map<String, Class<?>> memberTypes = new HashMap<String,Class<?>>();

    /**
     * Member name -> default value mapping.
     */
    private final Map<String, Object> memberDefaults =
        new HashMap<String, Object>();

    /**
     * Member name -> Method object mapping. This (and its assoicated
     * accessor) are used only to generate AnnotationTypeMismatchExceptions.
     */
    private final Map<String, Method> members = new HashMap<String, Method>();

    /**
     * The retention policy for this annotation type.
     */
    private RetentionPolicy retention = RetentionPolicy.RUNTIME;;

    /**
     * Whether this annotation type is inherited.
     */
    private boolean inherited = false;

    /**
     * Returns an AnnotationType instance for the specified annotation type.
     *
     * @throw IllegalArgumentException if the specified class object for
     *     does not represent a valid annotation type
     */
    public static synchronized AnnotationType getInstance(
        Class<? extends Annotation> annotationClass)
    {
        AnnotationType result = sun.misc.SharedSecrets.getJavaLangAccess().
            getAnnotationType(annotationClass);
        if (result == null)
            result = new AnnotationType((Class<? extends Annotation>) annotationClass);

        return result;
    }

    /**
     * Sole constructor.
     *
     * @param annotationClass the class object for the annotation type
     * @throw IllegalArgumentException if the specified class object for
     *     does not represent a valid annotation type
     */
    private AnnotationType(final Class<? extends Annotation> annotationClass) {
        if (!annotationClass.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type");

        Method[] methods =
            AccessController.doPrivileged(new PrivilegedAction<Method[]>() {
                public Method[] run() {
                    // Initialize memberTypes and defaultValues
                    return annotationClass.getDeclaredMethods();
                }
            });


        for (Method method :  methods) {
            if (method.getParameterTypes().length != 0)
                throw new IllegalArgumentException(method + " has params");
            String name = method.getName();
            Class<?> type = method.getReturnType();
            memberTypes.put(name, invocationHandlerReturnType(type));
            members.put(name, method);

            Object defaultValue = method.getDefaultValue();
            if (defaultValue != null)
                memberDefaults.put(name, defaultValue);

            members.put(name, method);
        }

        sun.misc.SharedSecrets.getJavaLangAccess().
            setAnnotationType(annotationClass, this);

        // Initialize retention, & inherited fields.  Special treatment
        // of the corresponding annotation types breaks infinite recursion.
        if (annotationClass != Retention.class &&
            annotationClass != Inherited.class) {
            Retention ret = annotationClass.getAnnotation(Retention.class);
            retention = (ret == null ? RetentionPolicy.CLASS : ret.value());
            inherited = annotationClass.isAnnotationPresent(Inherited.class);
        }
    }

    /**
     * Returns the type that must be returned by the invocation handler
     * of a dynamic proxy in order to have the dynamic proxy return
     * the specified type (which is assumed to be a legal member type
     * for an annotation).
     */
    public static Class<?> invocationHandlerReturnType(Class<?> type) {
        // Translate primitives to wrappers
        if (type == byte.class)
            return Byte.class;
        if (type == char.class)
            return Character.class;
        if (type == double.class)
            return Double.class;
        if (type == float.class)
            return Float.class;
        if (type == int.class)
            return Integer.class;
        if (type == long.class)
            return Long.class;
        if (type == short.class)
            return Short.class;
        if (type == boolean.class)
            return Boolean.class;

        // Otherwise, just return declared type
        return type;
    }

    /**
     * Returns member types for this annotation type
     * (member name -> type mapping).
     */
    public Map<String, Class<?>> memberTypes() {
        return memberTypes;
    }

    /**
     * Returns members of this annotation type
     * (member name -> associated Method object mapping).
     */
    public Map<String, Method> members() {
        return members;
    }

    /**
     * Returns the default values for this annotation type
     * (Member name -> default value mapping).
     */
    public Map<String, Object> memberDefaults() {
        return memberDefaults;
    }

    /**
     * Returns the retention policy for this annotation type.
     */
    public RetentionPolicy retention() {
        return retention;
    }

    /**
     * Returns true if this this annotation type is inherited.
     */
    public boolean isInherited() {
        return inherited;
    }

    /**
     * For debugging.
     */
    public String toString() {
        StringBuffer s = new StringBuffer("Annotation Type:" + "\n");
        s.append("   Member types: " + memberTypes + "\n");
        s.append("   Member defaults: " + memberDefaults + "\n");
        s.append("   Retention policy: " + retention + "\n");
        s.append("   Inherited: " + inherited);
        return s.toString();
    }
}
