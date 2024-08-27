/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.util;

import jdk.internal.javac.PreviewFeature;

import javax.annotation.processing.SupportedSourceVersion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

/**
 * A simple visitor for annotation values with default behavior
 * appropriate for a {@linkplain
 * ProcessingEnvironment#isPreviewEnabled preview} source version.
 *
 * Visit methods call {@link #defaultAction
 * defaultAction} passing their arguments to {@code defaultAction}'s
 * corresponding parameters.
 *
 * @apiNote
 * Methods in this class may be overridden subject to their general
 * contract.
 *
 * @param <R> the return type of this visitor's methods
 * @param <P> the type of the additional parameter to this visitor's methods.
 *
 * @see javax.lang.model.util##expectedEvolution
 * <strong>Expected visitor evolution</strong>
 * @see AbstractAnnotationValueVisitor6##note_for_subclasses
 * <strong>Compatibility note for subclasses</strong>
 * @see SimpleAnnotationValueVisitor6
 * @see SimpleAnnotationValueVisitor7
 * @see SimpleAnnotationValueVisitor8
 * @see SimpleAnnotationValueVisitor9
 * @see SimpleAnnotationValueVisitor14
 * @since 23
 */
@SupportedSourceVersion(RELEASE_24)
@PreviewFeature(feature=PreviewFeature.Feature.LANGUAGE_MODEL, reflective=true)
public class SimpleAnnotationValueVisitorPreview<R, P> extends SimpleAnnotationValueVisitor14<R, P> {
    /**
     * Constructor for concrete subclasses; uses {@code null} for the
     * default value.
     */
    protected SimpleAnnotationValueVisitorPreview() {
        super(null);
    }

    /**
     * Constructor for concrete subclasses; uses the argument for the
     * default value.
     *
     * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
     */
    protected SimpleAnnotationValueVisitorPreview(R defaultValue) {
        super(defaultValue);
    }
}
