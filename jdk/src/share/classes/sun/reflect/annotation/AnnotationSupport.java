/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.annotation;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import sun.reflect.Reflection;
import sun.misc.JavaLangAccess;

public final class AnnotationSupport {
    private static JavaLangAccess javaLangAccess = sun.misc.SharedSecrets.getJavaLangAccess();

    /**
     * Finds and returns _one_ annotation of the type indicated by
     * {@code annotationClass} from the {@code Map} {@code
     * annotationMap}. Looks into containers of the {@code
     * annotationClass} (as specified by an the {@code
     * annotationClass} type being meta-annotated with an {@code
     * ContainedBy} annotation).
     *
     * @param annotationMap the {@code Map} used to store annotations and indexed by their type
     * @param annotationClass the type of annotation to search for
     *
     * @return in instance of {@code annotationClass} or {@code null} if none were found
     */
    public static <A extends Annotation> A getOneAnnotation(final Map<Class<? extends Annotation>, Annotation> annotationMap,
                                                            final Class<A> annotationClass) {
        @SuppressWarnings("unchecked")
        final A candidate = (A)annotationMap.get(annotationClass);
        if (candidate != null) {
            return candidate;
        }

        final Class<? extends Annotation> containerClass = getContainer(annotationClass);
        if (containerClass != null) {
            return unpackOne(annotationMap.get(containerClass), annotationClass);
        }

        return null; // found none
    }

    /**
     * Finds and returns all annotation of the type indicated by
     * {@code annotationClass} from the {@code Map} {@code
     * annotationMap}. Looks into containers of the {@code
     * annotationClass} (as specified by an the {@code
     * annotationClass} type being meta-annotated with an {@code
     * ContainedBy} annotation).
     *
     * @param annotationMap the {@code Map} used to store annotations indexed by their type
     * @param annotationClass the type of annotation to search for
     *
     * @return an array of instances of {@code annotationClass} or an empty array if none were found
     */
    public static  <A extends Annotation> A[] getMultipleAnnotations(final Map<Class<? extends Annotation>, Annotation> annotationMap,
                                                                     final Class<A> annotationClass) {
        final ArrayList<A> res = new ArrayList<A>();

        @SuppressWarnings("unchecked")
        final A candidate = (A)annotationMap.get(annotationClass);
        if (candidate != null) {
            res.add(candidate);
        }

        final Class<? extends Annotation> containerClass = getContainer(annotationClass);
        if (containerClass != null) {
            res.addAll(unpackAll(annotationMap.get(containerClass), annotationClass));
        }

        @SuppressWarnings("unchecked") // should be safe annotationClass is a token for A
        final A[] emptyTemplateArray = (A[])Array.newInstance(annotationClass, 0);
        return res.isEmpty() ? emptyTemplateArray : res.toArray(emptyTemplateArray);
    }

    /**
     * Unpacks the {@code annotationMap} parameter into an array of
     * {@code Annotation}s. This method will unpack all repeating
     * annotaions containers (once). An annotation type is marked as a
     * container by meta-annotating it the with the {@code
     * ContainerFor} annotation.
     *
     * @param annotationMap the {@code Map} from where the annotations are unpacked
     *
     * @return an array of Annotation
     */
    public static Annotation[] unpackToArray(Map<Class<? extends Annotation>, Annotation> annotationMap) {
        List<Annotation> res = new ArrayList<>();
        for (Map.Entry<Class<? extends Annotation>, Annotation> e : annotationMap.entrySet()) {
            Class<? extends Annotation> annotationClass = e.getKey();
            Annotation annotationInstance = e.getValue();
            Class<? extends Annotation> containee = getContainee(e.getKey());
            boolean isContainer = javaLangAccess.getDirectDeclaredAnnotation(annotationClass, ContainerFor.class) != null;

            if (isContainer) {
                res.addAll(unpackAll(annotationInstance, containee));
            } else {
                res.add(annotationInstance);
            }
        }

        return res.isEmpty()
               ? AnnotationParser.getEmptyAnnotationArray()
               : res.toArray(AnnotationParser.getEmptyAnnotationArray());
    }

    /** Helper to get the container, or null if none, of an annotation. */
    private static <A extends Annotation> Class<? extends Annotation> getContainer(Class<A> annotationClass) {
        ContainedBy containerAnnotation =
            javaLangAccess.getDirectDeclaredAnnotation(annotationClass, ContainedBy.class);
        return (containerAnnotation == null) ? null : containerAnnotation.value();
    }

    /** Helper to get the containee, or null if this isn't a container, of a possible container annotation. */
    private static <A extends Annotation> Class<? extends Annotation> getContainee(Class<A> annotationClass) {
        ContainerFor containerAnnotation =
            javaLangAccess.getDirectDeclaredAnnotation(annotationClass, ContainerFor.class);
        return (containerAnnotation == null) ? null : containerAnnotation.value();
    }

    /** Reflectively look up and get the returned array from the the
     * invocation of the value() element on an instance of an
     * Annotation.
     */
    private static  <A extends Annotation> A[] getValueArray(Annotation containerInstance) {
        try {
            // the spec tells us the container must have an array-valued
            // value element. Get the AnnotationType, get the "value" element
            // and invoke it to get the contents.

            Class<?> containerClass = containerInstance.annotationType();
            AnnotationType annoType = javaLangAccess.getAnnotationType(containerClass);
            if (annoType == null)
                throw new InvalidContainerAnnotationError(containerInstance + " is an invalid container for repeating annotations");

            Method m = annoType.members().get("value");
            if (m == null)
                throw new InvalidContainerAnnotationError(containerInstance + " is an invalid container for repeating annotations");
            m.setAccessible(true);

            @SuppressWarnings("unchecked") // not provably safe, but we catch the ClassCastException
            A[] a = (A[])m.invoke(containerInstance); // this will erase to (Annotation[]) but we
                                                      // do a runtime cast on the return-value
                                                      // in the methods that call this method
            return a;
        } catch (IllegalAccessException | // couldnt loosen security
                 IllegalArgumentException | // parameters doesn't match
                 InvocationTargetException | // the value method threw an exception
                 ClassCastException e) { // well, a cast failed ...
            e.getCause().printStackTrace();
            throw new InvalidContainerAnnotationError(containerInstance + " is an invalid container for repeating annotations",
                                                      e,
                                                      containerInstance,
                                                      null);
        }
    }

    /* Sanity check type of and return the first annotation instance
     * of type {@code annotationClass} from {@code
     * containerInstance}.
     */
    private static <A extends Annotation> A unpackOne(Annotation containerInstance, Class<A> annotationClass) {
        if (containerInstance == null) {
            return null;
        }

        try {
            return annotationClass.cast(getValueArray(containerInstance)[0]);
        } catch (ArrayIndexOutOfBoundsException | // empty array
                 ClassCastException | // well, a cast failed ...
                 NullPointerException e) { // can this NP? for good meassure
            throw new InvalidContainerAnnotationError(String.format("%s is an invalid container for repeating annotations of type: %s",
                                                                    containerInstance, annotationClass),
                                                      e,
                                                      containerInstance,
                                                      annotationClass);
        }
    }

    /* Sanity check type of and return a list of all the annotation
     * instances of type {@code annotationClass} from {@code
     * containerInstance}.
     */
    private static <A extends Annotation> List<A> unpackAll(Annotation containerInstance, Class<A> annotationClass) {
        if (containerInstance == null) {
            return Collections.emptyList(); // container not present
        }

        try {
            A[] a = getValueArray(containerInstance);
            ArrayList<A> l = new ArrayList<>(a.length);
            for (int i  = 0; i < a.length; i++)
                l.add(annotationClass.cast(a[i]));
            return l;
        } catch (ClassCastException |
                 NullPointerException e) {
            throw new InvalidContainerAnnotationError(String.format("%s is an invalid container for repeating annotations of type: %s",
                                                                    containerInstance, annotationClass),
                                                      e,
                                                      containerInstance,
                                                      annotationClass);
        }
    }
}
