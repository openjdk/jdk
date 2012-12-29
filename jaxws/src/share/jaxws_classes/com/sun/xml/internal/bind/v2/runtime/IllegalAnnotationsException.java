/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.sun.xml.internal.bind.v2.model.core.ErrorHandler;

/**
 * A list of {@link IllegalAnnotationException} wrapped in one exception.
 *
 * <p>
 * This exception is used to report all the errors to the client application
 * through {@link JAXBContext#newInstance}.
 *
 * @since JAXB 2.0 EA1
 * @author Kohsuke Kawaguchi
 */
public class IllegalAnnotationsException extends JAXBException {
    private final List<IllegalAnnotationException> errors;

    private static final long serialVersionUID = 1L;

    public IllegalAnnotationsException(List<IllegalAnnotationException> errors) {
        super(errors.size()+" counts of IllegalAnnotationExceptions");
        assert !errors.isEmpty() : "there must be at least one error";
        this.errors = Collections.unmodifiableList(new ArrayList<IllegalAnnotationException>(errors));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append('\n');

        for( IllegalAnnotationException error : errors )
            sb.append(error.toString()).append('\n');

        return sb.toString();
    }

    /**
     * Returns a read-only list of {@link IllegalAnnotationException}s
     * wrapped in this exception.
     *
     * @return
     *      a non-null list.
     */
    public List<IllegalAnnotationException> getErrors() {
        return errors;
    }

    public static class Builder implements ErrorHandler {
        private final List<IllegalAnnotationException> list = new ArrayList<IllegalAnnotationException>();
        public void error(IllegalAnnotationException e) {
            list.add(e);
        }
        /**
         * If an error was reported, throw the exception.
         * Otherwise exit normally.
         */
        public void check() throws IllegalAnnotationsException {
            if(list.isEmpty())
                return;
            throw new IllegalAnnotationsException(list);
        }
    }
}
