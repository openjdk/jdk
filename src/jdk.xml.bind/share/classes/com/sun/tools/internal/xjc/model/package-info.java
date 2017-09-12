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
 * Implementation of the {@link com.sun.xml.internal.bind.v2.model.core} package for XJC.
 *
 * <p>
 * This model is the recipes for the code generation.
 * It captures the essence of the JAXB-bound beans,
 * so that the actual Java code can be generated from this object model
 * mechanically without knowing anything about how the model was built.
 *
 * <p>
 * Most of the classes/interfaces in this package has one-to-one relationship
 * with the parameterized core model in the {@link com.sun.xml.internal.bind.v2.model.core} package.
 * Refer to the core model for better documentation.
 *
 * <p>
 * The model for XJC also exposes a few additional information on top of the core model.
 * Those are defined in this package. This includes such information as:
 *
 * <dl>
 *  <dt>Source location information
 *  <dd>{@link org.xml.sax.Locator} object that can be used to tell where the model components
 *      are created from in terms of the source file. Useful for error reporting.
 *
 *  <dt>Source schema component
 *  <dd>{@link com.sun.xml.internal.xsom.XSComponent} object from which the model components are created from.
 *      See {@link com.sun.tools.internal.xjc.model.CCustomizable#getSchemaComponent()} for example.
 *
 *  <dt>Plugin customizations
 *  <dd>See {@link com.sun.tools.internal.xjc.model.CCustomizable}.
 * </dl>
 */
package com.sun.tools.internal.xjc.model;
