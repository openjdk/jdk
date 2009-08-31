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

package com.sun.xml.internal.bind.api;

/**
 * A JAXB Bean that works like a DOM.
 *
 * <p>
 * This bean is bound to XML as a sequence of elements, where each
 * element[i] is from bridges[i] (which defines the tag name and the expected type)
 * and values[i] (which defines the actual value.)
 *
 * <p>
 * This object allows you to treat multiple unrelated JAXB beans as a single tree.
 * This in turn allows you to marshal this tree in one marshal method invocation,
 * which is faster than multiple invocations of the marshal method.
 *
 * <p>
 * The binding of this class is always known to {@link JAXBRIContext}, so it can be
 * used without passing anything to {@link JAXBRIContext#newInstance}.
 * This object can be only used for marshalling, not for unmarshalling.
 *
 * @author Kohsuke Kawaguchi
 */
public class CompositeStructure {
    public Bridge[] bridges;
    public Object[] values;
}
