/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.lang.model;

/**
 * Superclass of exceptions which indicate that an unknown kind of
 * entity was encountered.  This situation can occur if the language
 * evolves and new kinds of constructs are introduced.  Subclasses of
 * this exception may be thrown by visitors to indicate that the
 * visitor was created for a prior version of the language.
 *
 * <p>A common superclass for those exceptions allows a single catch
 * block to have code handling them uniformly.
 *
 * @author Joseph D. Darcy
 * @see javax.lang.model.element.UnknownElementException
 * @see javax.lang.model.element.UnknownAnnotationValueException
 * @see javax.lang.model.type.UnknownTypeException
 * @since 1.7
 */
public class UnknownEntityException extends RuntimeException {

    private static final long serialVersionUID = 269L;

    /**
     * Creates a new {@code UnknownEntityException} with the specified
     * detail message.
     *
     * @param message the detail message
     */
    protected UnknownEntityException(String message) {
        super(message);
    }
}
