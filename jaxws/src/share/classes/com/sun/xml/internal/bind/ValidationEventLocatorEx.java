/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.bind;

import javax.xml.bind.ValidationEventLocator;

/**
 * Defines additional accessor methods for the event source location.
 * <p>
 * This interface exposes the location information only available
 * in the JAXB RI specific extension.
 * <p>
 * <em>DO NOT IMPLEMENT THIS INTERFACE BY YOUR CODE</em> because
 * we might add more methods on this interface in the future release
 * of the RI.
 *
 * <h2>Usage</h2>
 * <p>
 * If you obtain a reference to {@link javax.xml.bind.ValidationEventLocator},
 * check if you can cast it to {@link ValidationEventLocatorEx} first, like this:
 * <pre>
 * void foo( ValidationEvent e ) {
 *     ValidationEventLocator loc = e.getLocator();
 *     if( loc instanceof ValidationEventLocatorEx ) {
 *         String fieldName = ((ValidationEventLocatorEx)loc).getFieldName();
 *         if( fieldName!=null ) {
 *             // do something with location.
 *         }
 *     }
 * }
 * </pre>
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface ValidationEventLocatorEx extends ValidationEventLocator {
    /**
     * Returns the field name of the object where the error occured.
     * <p>
     * This method always returns null when you are doing
     * a validation during unmarshalling.
     *
     * When not null, the field name indicates the field of the object
     * designated by the {@link #getObject()} method where the error
     * occured.
     */
    String getFieldName();
}
