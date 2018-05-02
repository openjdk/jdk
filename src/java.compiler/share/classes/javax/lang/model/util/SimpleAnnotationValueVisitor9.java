/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

/**
 * A simple visitor for annotation values with default behavior
 * appropriate for source versions {@link SourceVersion#RELEASE_9
 * RELEASE_9} through {@link SourceVersion#RELEASE_11 RELEASE_11}.
 *
 * Visit methods call {@link #defaultAction
 * defaultAction} passing their arguments to {@code defaultAction}'s
 * corresponding parameters.
 *
 * <p> Methods in this class may be overridden subject to their
 * general contract.  Note that annotating methods in concrete
 * subclasses with {@link java.lang.Override @Override} will help
 * ensure that methods are overridden as intended.
 *
 * <p> <b>WARNING:</b> The {@code AnnotationValueVisitor} interface
 * implemented by this class may have methods added to it in the
 * future to accommodate new, currently unknown, language structures
 * added to future versions of the Java&trade; programming language.
 * Therefore, methods whose names begin with {@code "visit"} may be
 * added to this class in the future; to avoid incompatibilities,
 * classes which extend this class should not declare any instance
 * methods with names beginning with {@code "visit"}.
 *
 * <p>When such a new visit method is added, the default
 * implementation in this class will be to call the {@link
 * #visitUnknown visitUnknown} method.  A new simple annotation
 * value visitor class will also be introduced to correspond to the
 * new language level; this visitor will have different default
 * behavior for the visit method in question.  When the new visitor is
 * introduced, all or portions of this visitor may be deprecated.
 *
 * @param <R> the return type of this visitor's methods
 * @param <P> the type of the additional parameter to this visitor's methods.
 *
 * @see SimpleAnnotationValueVisitor6
 * @see SimpleAnnotationValueVisitor7
 * @see SimpleAnnotationValueVisitor8
 * @since 9
 */
@SupportedSourceVersion(RELEASE_11)
public class SimpleAnnotationValueVisitor9<R, P> extends SimpleAnnotationValueVisitor8<R, P> {
    /**
     * Constructor for concrete subclasses; uses {@code null} for the
     * default value.
     */
    protected SimpleAnnotationValueVisitor9() {
        super(null);
    }

    /**
     * Constructor for concrete subclasses; uses the argument for the
     * default value.
     *
     * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
     */
    protected SimpleAnnotationValueVisitor9(R defaultValue) {
        super(defaultValue);
    }
}
