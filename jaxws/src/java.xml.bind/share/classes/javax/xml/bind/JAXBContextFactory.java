/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import java.util.Map;

/**
 * <p>Factory that creates new <code>JAXBContext</code> instances.
 *
 * JAXBContextFactory can be located using {@link java.util.ServiceLoader#load(Class)}
 *
 * @since 9, JAXB 2.3
 */
public interface JAXBContextFactory {

    /**
     * <p>
     * Create a new instance of a {@code JAXBContext} class.
     *
     * <p>
     * For semantics see {@link javax.xml.bind.JAXBContext#newInstance(Class[], java.util.Map)}
     *
     * @param classesToBeBound
     *      list of java classes to be recognized by the new {@link JAXBContext}.
     *      Can be empty, in which case a {@link JAXBContext} that only knows about
     *      spec-defined classes will be returned.
     * @param properties
     *      provider-specific properties. Can be null, which means the same thing as passing
     *      in an empty map.
     *
     * @return
     *      A new instance of a {@code JAXBContext}.
     *
     * @throws JAXBException
     *      if an error was encountered while creating the
     *      {@code JAXBContext}. See {@link JAXBContext#newInstance(Class[], Map)} for details.
     *
     * @throws IllegalArgumentException
     *      if the parameter contains {@code null} (i.e., {@code newInstance(null,someMap);})
     *
     * @since 9, JAXB 2.3
     */
    JAXBContext createContext(Class<?>[] classesToBeBound,
                              Map<String, ?> properties ) throws JAXBException;

    /**
     * <p>
     * Create a new instance of a {@code JAXBContext} class.
     *
     * <p>
     * For semantics see {@link javax.xml.bind.JAXBContext#newInstance(String, ClassLoader, java.util.Map)}
     *
     * <p>
     * The interpretation of properties is up to implementations. Implementations must
     * throw {@code JAXBException} if it finds properties that it doesn't understand.
     *
     * @param contextPath list of java package names that contain schema derived classes
     * @param classLoader
     *      This class loader will be used to locate the implementation classes.
     * @param properties
     *      provider-specific properties. Can be null, which means the same thing as passing
     *      in an empty map.
     *
     * @return a new instance of a {@code JAXBContext}
     * @throws JAXBException if an error was encountered while creating the
     *      {@code JAXBContext}. See {@link JAXBContext#newInstance(String, ClassLoader, Map)} for details.
     *
     * @since 9, JAXB 2.3
     */
    JAXBContext createContext(String contextPath,
                              ClassLoader classLoader,
                              Map<String, ?> properties ) throws JAXBException;

}
