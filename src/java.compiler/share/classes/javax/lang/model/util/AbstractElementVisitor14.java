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

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.RecordComponentElement;
import static javax.lang.model.SourceVersion.*;

/**
 * A skeletal visitor of program elements with default behavior
 * appropriate for the {@link SourceVersion#RELEASE_14 RELEASE_14}
 * source version.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @see AbstractElementVisitor6##note_for_subclasses
 * <strong>Compatibility note for subclasses</strong>
 * @see AbstractElementVisitor6
 * @see AbstractElementVisitor7
 * @see AbstractElementVisitor8
 * @see AbstractElementVisitor9
 * @since 16
 */
@SupportedSourceVersion(RELEASE_25)
public abstract class AbstractElementVisitor14<R, P> extends AbstractElementVisitor9<R, P> {
    /**
     * Constructor for concrete subclasses to call.
     */
    protected AbstractElementVisitor14(){
        super();
    }

    /**
     * {@inheritDoc ElementVisitor}
     *
     * @implSpec Visits a {@code RecordComponentElement} in a manner defined by a
     * subclass.
     *
     * @param e  {@inheritDoc ElementVisitor}
     * @param p  {@inheritDoc ElementVisitor}
     * @return   {@inheritDoc ElementVisitor}
     */
    @Override
    public abstract R visitRecordComponent(RecordComponentElement e, P p);
}
