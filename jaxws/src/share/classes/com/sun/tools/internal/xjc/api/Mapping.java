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
package com.sun.tools.internal.xjc.api;

import java.util.List;

import javax.xml.namespace.QName;

/**
 * JAXB-induced mapping between a Java class
 * and an XML element declaration. A part of the compiler artifacts.
 *
 * <p>
 * To be precise, this is a mapping between two Java classes and an
 * XML element declaration. There's one Java class/interface that
 * represents the element, and there's another Java class/interface that
 * represents the type of the element.
 *
 * The former is called "element representation" and the latter is called
 * "type representation".
 *
 * <p>
 * The {@link Mapping} interface provides operation that lets the caller
 * convert an instance of the element representation to that of the
 * type representation or vice versa.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface Mapping {
    /**
     * Name of the XML element.
     *
     * @return
     *      never be null.
     */
    QName getElement();

    /**
     * Returns the fully-qualified name of the java class for the type of this element.
     *
     * TODO: does this method returns the name of the wrapper bean when it's qualified
     * for the wrapper style? Seems no (consider &lt;xs:element name='foo' type='xs:long' />),
     * but then how does JAX-RPC captures that bean?
     *
     * @return
     *      never be null.
     */
    TypeAndAnnotation getType();

    /**
     * If this element is a so-called "wrapper-style" element,
     * obtains its member information.
     *
     * <p>
     * The notion of the wrapper style should be defined by the JAXB spec,
     * and ideally it should differ from that of the JAX-RPC only at
     * the point where the JAX-RPC imposes additional restriction
     * on the element name.
     *
     * <p>
     * As of this writing the JAXB spec doesn't define "the wrapper style"
     * and as such the exact definition of what XJC thinks
     * "the wrapper style" isn't spec-ed.
     *
     * <p>
     * Ths returned list includes {@link Property} defined not just
     * in this class but in all its base classes.
     *
     * @return
     *      null if this isn't a wrapper-style element.
     *      Otherwise list of {@link Property}s. The order signifies
     *      the order they appeared inside a schema.
     */
    List<? extends Property> getWrapperStyleDrilldown();
}
