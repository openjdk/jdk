/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.xml.sax.Locator;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Marks a property that receives a location from which the object is unmarshalled.
 *
 * <h2>Usage</h2>
 * <p>
 * The @XmlLocation can be specified on:
 * <ul>
 *  <li>a field whose type is {@link Locator}, or
 *  <li>a method that takes a {@link Locator} as the sole parameter
 * </ul>
 *
 * <p>
 * When a class that contains such a field/method is unmarshalled by the JAXB RI,
 * such a field/method will receive an immutable {@link Locator} object that describes
 * the location in the XML document where the object is unmarshalled from.
 *
 * <p>
 * If the unmarshaller does not know the source location information, the locator
 * will not be set. For example, this happens when it is unmarshalling from a DOM tree.
 * This also happens if you use JAXB implementations other than the JAXB RI.
 *
 * <p>
 * This information can be used by applications, for example to provide user-friendly
 * error information.
 *
 *
 * @author Kohsuke Kawaguchi
 * @since JAXB RI 2.0 EA
 */
@Retention(RUNTIME) @Target({FIELD,METHOD})
public @interface XmlLocation {
}
