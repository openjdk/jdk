/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Classes to support module descriptors and creating configurations of modules
 * by means of resolution and service binding.
 *
 * <h2><a id="resolution">Resolution</a></h2>
 *
 * <p> Resolution is the process of computing the transitive closure of a set
 * of root modules over a set of observable modules by resolving the
 * dependences expressed by {@link
 * java.lang.module.ModuleDescriptor.Requires requires} clauses.
 * The <em>dependence graph</em> is augmented with edges that take account of
 * implicitly declared dependences ({@code requires transitive}) to create a
 * <em>readability graph</em>. The result of resolution is a {@link
 * java.lang.module.Configuration Configuration} that encapsulates the
 * readability graph. </p>
 *
 * <p> As an example, suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { requires m2; }
 *     module m2 { requires transitive m3; }
 *     module m3 { }
 *     module m4 { }
 * } </pre>
 *
 * <p> If the module {@code m1} is resolved then the resulting configuration
 * contains three modules ({@code m1}, {@code m2}, {@code m3}). The edges in
 * its readability graph are: </p>
 * <pre> {@code
 *     m1 --> m2  (meaning m1 reads m2)
 *     m1 --> m3
 *     m2 --> m3
 * } </pre>
 *
 * <p> Resolution is an additive process. When computing the transitive closure
 * then the dependence relation may include dependences on modules in {@link
 * java.lang.module.Configuration#parents() parent} configurations. The result
 * is a <em>relative configuration</em> that is relative to one or more parent
 * configurations and where the readability graph may have edges from modules
 * in the configuration to modules in parent configurations. </p>
 *
 * <p> As an example, suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { requires m2; requires java.xml; }
 *     module m2 { }
 * } </pre>
 *
 * <p> If module {@code m1} is resolved with the configuration for the {@link
 * java.lang.ModuleLayer#boot() boot} layer as the parent then the resulting
 * configuration contains two modules ({@code m1}, {@code m2}). The edges in
 * its readability graph are:
 * <pre> {@code
 *     m1 --> m2
 *     m1 --> java.xml
 * } </pre>
 * where module {@code java.xml} is in the parent configuration. For
 * simplicity, this example omits the implicitly declared dependence on the
 * {@code java.base} module.
 *
 * <p> Requires clauses that are "{@code requires static}" express an optional
 * dependence (except at compile-time). If a module declares that it
 * "{@code requires static M}" then resolution does not search the observable
 * modules for "{@code M}". However, if "{@code M}" is resolved (because resolution
 * resolves a module that requires "{@code M}" without the {@link
 * java.lang.module.ModuleDescriptor.Requires.Modifier#STATIC static} modifier)
 * then the readability graph will contain read edges for each module that
 * "{@code requires static M}". </p>
 *
 * <p> {@link java.lang.module.ModuleDescriptor#isAutomatic() Automatic} modules
 * receive special treatment during resolution. Each automatic module is resolved
 * as if it "{@code requires transitive}" all observable automatic modules and
 * all automatic modules in the parent configurations. Each automatic module is
 * resolved so that it reads all other modules in the resulting configuration and
 * all modules in parent configurations. </p>
 *
 * <h2><a id="servicebinding">Service binding</a></h2>
 *
 * <p> Service binding is the process of augmenting a graph of resolved modules
 * from the set of observable modules induced by the service-use dependence
 * ({@code uses} and {@code provides} clauses). Any module that was not
 * previously in the graph requires resolution to compute its transitive
 * closure. Service binding is an iterative process in that adding a module
 * that satisfies some service-use dependence may introduce new service-use
 * dependences. </p>
 *
 * <p> Suppose we have the following observable modules: </p>
 * <pre> {@code
 *     module m1 { exports p; uses p.S; }
 *     module m2 { requires m1; provides p.S with p2.S2; }
 *     module m3 { requires m1; requires m4; provides p.S with p3.S3; }
 *     module m4 { }
 * } </pre>
 *
 * <p> If the module {@code m1} is resolved then the resulting graph of modules
 * has one module ({@code m1}). If the graph is augmented with modules induced
 * by the service-use dependence relation then the configuration will contain
 * four modules ({@code m1}, {@code m2}, {@code m3}, {@code m4}). The edges in
 * its readability graph are: </p>
 * <pre> {@code
 *     m2 --> m1
 *     m3 --> m1
 *     m3 --> m4
 * } </pre>
 * <p> The edges in the conceptual service-use graph are: </p>
 * <pre> {@code
 *     m1 --> m2  (meaning m1 uses a service that is provided by m2)
 *     m1 --> m3
 * } </pre>
 *
 * <h2>General Exceptions</h2>
 *
 * <p> Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method of any class or interface in this package will cause a {@link
 * java.lang.NullPointerException NullPointerException} to be thrown. Additionally,
 * invoking a method with an array or collection containing a {@code null} element
 * will cause a {@code NullPointerException}, unless otherwise specified. </p>
 *
 * @since 9
 * @spec JPMS
 */

package java.lang.module;
