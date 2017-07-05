/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The in-memory model of the JAXB-bound beans.
 *
 * <h2>Parameterizations</h2>
 * <p>
 * Interfaces in this package are parameterized to work with arbitrary Java reflection library.
 * This is necessary because the RI needs to work with both the runtime reflection library
 * ({@link java.lang.reflect}) and the Annotation Processing.
 *
 * <p>
 * The meaning of parameterizations are as follows:
 *
 * <dl>
 *  <dt><b>T</b></dt>
 *  <dd>Represents an use of type, such as {@code int}, {@code Foo[]}, or {@code List<Foo>}.
 *      Corresponds to {@link java.lang.reflect.Type}.</dd>
*
 *  <dt><b>C</b></dt>
 *  <dd>Represents a declaration of a type (that is, class, interface, enum, or annotation.)
 *      This doesn't include {@code int}, {@code Foo[]}, or {@code List<Foo>}, because
 *      they don't have corresponding declarations.
 *      Corresponds to {@link java.lang.Class} (roughly).</dd>
 *
 *  <dt><b>F</b></dt>
 *  <dd>Represents a field.
 *      Corresponds to {@link java.lang.reflect.Field}.</dd>
 *
 *  <dt><b>M</b></dt>
 *  <dd>Represents a method.
 *      Corresponds to {@link java.lang.reflect.Method}.</dd>
 *
 * </dl>
 */
@XmlSchema(namespace="http://jaxb.dev.java.net/xjc/model",elementFormDefault=QUALIFIED)
package com.sun.xml.internal.bind.v2.model.core;

import javax.xml.bind.annotation.XmlSchema;

import static javax.xml.bind.annotation.XmlNsForm.QUALIFIED;
