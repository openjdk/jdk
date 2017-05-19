/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.core;

import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * listen to static errors found during building a JAXB model from a set of classes.
 * Implemented by the client of {@link com.sun.xml.internal.bind.v2.model.impl.ModelBuilderI}.
 *
 * <p>
 * All the static errors have to be reported while constructing a
 * model, not when a model is used (IOW, until the {@link com.sun.xml.internal.bind.v2.model.impl.ModelBuilderI} completes.
 * Internally, {@link com.sun.xml.internal.bind.v2.model.impl.ModelBuilderI} wraps an {@link ErrorHandler} and all the model
 * components should report errors through it.
 *
 * <p>
 * {@link IllegalAnnotationException} is a checked exception to remind
 * the model classes to report it rather than to throw it.
 *
 * @see com.sun.xml.internal.bind.v2.model.impl.ModelBuilderI
 * @author Kohsuke Kawaguchi
 */
public interface ErrorHandler {
    /**
     * Receives a notification for an error in the annotated code.
     * @param e
     */
    void error( IllegalAnnotationException e );
}
