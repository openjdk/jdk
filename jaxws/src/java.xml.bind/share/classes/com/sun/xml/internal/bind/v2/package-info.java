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
 * <h1>The JAXB 2.0 runtime</h1>.
 *
 * <h1>Overview</h1>
 * <p>
 * This module provides code that implements {@link javax.xml.bind.JAXBContext}.
 * Roughly speaking the runtime works like this:
 *
 * <ol>
 *  <li>There's a set of classes and interfaces that model JAXB-bound types.
 *      You can think of this as a reflection library for JAXB.
 *  <li>There's a set of classes that constitute the unmarshaller and marshaller.
 *      Each class represents a small portion, and they are composed to perform
 *      the operations.
 *  <li>{@link com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl} builds itself by reading the model and
 *      composing unmarshallers and marshallers.
 * </ol>
 *
 * <h1>Interesting Pieces inside Runtime</h1>
 * <p>
 * The followings are the interesting pieces inside the runtime.
 *
 * <dl>
 *  <dt>{@link com.sun.xml.internal.bind.v2.model model}
 *  <dd>
 *    This set of classes and interfaces models JAXB-bound types.
 *
 *  <dt>{@link com.sun.xml.internal.bind.v2.runtime XML I/O}
 *  <dd>
 *    This set of classes implements the JAXB API and provides the XML I/O functionality.
 * </dl>
 *
 * <p>
 * The classes <b>NOT</b> in the {@link com.sun.xml.internal.bind.v2} package (and its subpackages)
 * are also used by old JAXB 1.0 clients.
 *
 * <h1>Models</h1>
 * <p>
 * "Model" is the portion of the code that represents JAXB-bound types.
 *
 * <p>
 * The following picture illustrates the relationship among major
 * packages of the binding model.
 *
 * <div>
 *   <img src="doc-files/packages.png" alt="">
 * </div>
 *
 * <p>
 * The core model contracts are all interfaces, and they are parameterized
 * so that they can be used
 * with different reflection libraries. This is necessary, as the model
 * is used:
 * <ol>
 *  <li> at runtime to process loaded classes,
 *  <li> at tool-time to process source files / class files, and
 *  <li> at schema compile time to generate source code.
 * </ol>
 * They all use different reflection libraries.
 *
 * <p>
 * This portion is used by all
 * three running mode of JAXB.
 * <a href="model/impl/package-summary.html">The corresponding base-level implementaion</a>
 * is also parameterized.
 *
 * <p>
 * The runtime model contract and implementation are used only at the run-time.
 * These packages fix the parameterization to the Java reflection,
 * and also exposes additional functionalities to actually do the
 * unmarshalling/marshalling. These classes have "Runtime" prefix.
 *
 * <p>
 * Finally XJC has its own implementation of the contract in
 * its own package. This package also fixes the parameterization
 * to its own reflection library.
 *
 * <p>
 * When you work on the code, it is often helpful to know the layer you are in.
 *
 *
 * <p>
 * The binding model design roughly looks like the following.
 * For more details, see the javadoc of each component.
 *
 * <div>
 *  <img src="doc-files/j2s_architecture.gif" alt="">
 * </div>
 *
 * <b><i>TODO: link to classes from above pictures</i></b>
 *
 *
 * <h2>Evolution Rules</h2>
 * None of the class in this package or below should be directly
 * referenced by the generated code. Hence they can be changed freely
 * from versions to versions.
 *
 *
 *
 *
 * <h1>Performance Characteristics</h1>
 * <p>
 * Model construction happens inside {@link javax.xml.bind.JAXBContext#newInstance(Class[])}.
 * It's desirable for this step to be fast and consume less memory,
 * but it's not too performance sensitive.
 *
 * <p>
 * Code that implements the unmarshaller and the marshaller OTOH
 * needs to be very carefully written to achieve maximum sustaining
 * performance.
 *
 *
 *
 *
 * <h1>Bootstrap Sequence</h1>
 * <p>
 * The following picture illustrates how the {@link javax.xml.bind.JAXBContext#newInstance(Class[])} method
 * triggers activities.
 *
 */
package com.sun.xml.internal.bind.v2;
