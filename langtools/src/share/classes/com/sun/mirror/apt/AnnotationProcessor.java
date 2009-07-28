/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.mirror.apt;


import java.io.IOException;
import java.util.Collection;


/**
 * An annotation processor, used to examine and process the
 * annotations of program elements.  An annotation processor may,
 * for example, create new source files and XML documents to be used
 * in conjunction with the original code.
 *
 * <p> An annotation processor is constructed by a
 * {@linkplain AnnotationProcessorFactory factory}, which provides it with an
 * {@linkplain AnnotationProcessorEnvironment environment} that
 * encapsulates the state it needs.
 * Messages regarding warnings and errors encountered during processing
 * should be directed to the environment's {@link Messager},
 * and new files may be created using the environment's {@link Filer}.
 *
 * <p> Each annotation processor is created to process annotations
 * of a particular annotation type or set of annotation types.
 * It may use its environment to find the program elements with
 * annotations of those types.  It may freely examine any other program
 * elements in the course of its processing.
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
public interface AnnotationProcessor {

    /**
     * Process all program elements supported by this annotation processor.
     */
    void process();
}
