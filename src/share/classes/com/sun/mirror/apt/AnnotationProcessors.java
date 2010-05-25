/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.apt;

import com.sun.mirror.apt.*;
import java.util.*;

/**
 * Utilities to create specialized annotation processors.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  There is no direct analog
 * of the functionality of this class in the standardized API.
 *
 * @since 1.5
 * @author Joseph D. Darcy
 * @author Scott Seligman
 */
@Deprecated
@SuppressWarnings("deprecation")
public class AnnotationProcessors {
    static class NoOpAP implements AnnotationProcessor {
        NoOpAP() {}
        public void process(){}
    }

    /**
     * Combines multiple annotation processors into a simple composite
     * processor.
     * The composite processor functions by invoking each of its component
     * processors in sequence.
     */
    static class CompositeAnnotationProcessor implements AnnotationProcessor {

        private List<AnnotationProcessor> aps =
            new LinkedList<AnnotationProcessor>();

        /**
         * Constructs a new composite annotation processor.
         * @param aps  the component annotation processors
         */
        public CompositeAnnotationProcessor(Collection<AnnotationProcessor> aps) {
            this.aps.addAll(aps);
        }

        /**
         * Constructs a new composite annotation processor.
         * @param aps  the component annotation processors
         */
        public CompositeAnnotationProcessor(AnnotationProcessor... aps) {
            for(AnnotationProcessor ap: aps)
                this.aps.add(ap);
        }

        /**
         * Invokes the <tt>process</tt> method of each component processor,
         * in the order in which the processors were passed to the constructor.
         */
        public void process() {
            for(AnnotationProcessor ap: aps)
                ap.process();
        }
    }


    /**
     *  An annotation processor that does nothing and has no state.
     *  May be used multiple times.
     *
     * @since 1.5
     */
    public final static AnnotationProcessor NO_OP = new NoOpAP();

    /**
     * Constructs a new composite annotation processor.  A composite
     * annotation processor combines multiple annotation processors
     * into one and functions by invoking each of its component
     * processors' process methods in sequence.
     *
     * @param aps The processors to create a composite of
     * @since 1.5
     */
    public static AnnotationProcessor getCompositeAnnotationProcessor(AnnotationProcessor... aps) {
        return new CompositeAnnotationProcessor(aps);
    }

    /**
     * Constructs a new composite annotation processor.  A composite
     * annotation processor combines multiple annotation processors
     * into one and functions by invoking each of its component
     * processors' process methods in the sequence the processors are
     * returned by the collection's iterator.
     *
     * @param aps A collection of processors to create a composite of
     * @since 1.5
     */
    public static AnnotationProcessor getCompositeAnnotationProcessor(Collection<AnnotationProcessor> aps) {
        return new CompositeAnnotationProcessor(aps);
    }
}
