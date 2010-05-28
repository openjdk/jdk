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


import java.util.Collection;
import java.util.Set;

import com.sun.mirror.declaration.AnnotationTypeDeclaration;


/**
 * A factory for creating annotation processors.
 * Each factory is responsible for creating processors for one or more
 * annotation types.
 * The factory is said to <i>support</i> these types.
 *
 * <p> Each implementation of an <tt>AnnotationProcessorFactory</tt>
 * must provide a public no-argument constructor to be used by tools to
 * instantiate the factory.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.annotation.processing.Processor}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface AnnotationProcessorFactory {

    /**
     * Returns the options recognized by this factory or by any of the
     * processors it may create.
     * Only {@linkplain AnnotationProcessorEnvironment#getOptions()
     * processor-specific} options are included, each of which begins
     * with <tt>"-A"</tt>.  For example, if this factory recognizes
     * options such as <tt>-Adebug -Aloglevel=3</tt>, it will
     * return the strings <tt>"-Adebug"</tt> and <tt>"-Aloglevel"</tt>.
     *
     * <p> A tool might use this information to determine if any
     * options provided by a user are unrecognized by any processor,
     * in which case it may wish to report an error.
     *
     * @return the options recognized by this factory or by any of the
     * processors it may create, or an empty collection if none
     */
    Collection<String> supportedOptions();

    /**
     * Returns the names of the annotation types supported by this factory.
     * An element of the result may be the canonical (fully qualified) name
     * of a supported annotation type.  Alternately it may be of the form
     * <tt>"<i>name</i>.*"</tt>
     * representing the set of all annotation types
     * with canonical names beginning with <tt>"<i>name</i>."</tt>
     * Finally, <tt>"*"</tt> by itself represents the set of all
     * annotation types.
     *
     * @return the names of the annotation types supported by this factory
     */
    Collection<String> supportedAnnotationTypes();

    /**
     * Returns an annotation processor for a set of annotation
     * types. The set will be empty if the factory supports
     * &quot;<tt>*</tt>&quot; and the specified type declarations have
     * no annotations.  Note that the set of annotation types may be
     * empty for other reasons, such as giving the factory an
     * opportunity to register a listener.  An
     * <tt>AnnotationProcessorFactory</tt> must gracefully handle an
     * empty set of annotations; an appropriate response to an empty
     * set will often be returning {@link AnnotationProcessors#NO_OP}.
     *
     * @param atds type declarations of the annotation types to be processed
     * @param env  environment to use during processing
     * @return an annotation processor for the given annotation types,
     *          or <tt>null</tt> if the types are not supported or the
     *          processor cannot be created
     */
    AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                        AnnotationProcessorEnvironment env);
}
