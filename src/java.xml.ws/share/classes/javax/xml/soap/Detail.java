/*
 * Copyright (c) 2004, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;

import java.util.Iterator;

import javax.xml.namespace.QName;

/**
 * A container for {@code DetailEntry} objects. {@code DetailEntry}
 * objects give detailed error information that is application-specific and
 * related to the {@code SOAPBody} object that contains it.
 *<P>
 * A {@code Detail} object, which is part of a {@code SOAPFault}
 * object, can be retrieved using the method {@code SOAPFault.getDetail}.
 * The {@code Detail} interface provides two methods. One creates a new
 * {@code DetailEntry} object and also automatically adds it to
 * the {@code Detail} object. The second method gets a list of the
 * {@code DetailEntry} objects contained in a {@code Detail}
 * object.
 * <P>
 * The following code fragment, in which <i>sf</i> is a {@code SOAPFault}
 * object, gets its {@code Detail} object (<i>d</i>), adds a new
 * {@code DetailEntry} object to <i>d</i>, and then gets a list of all the
 * {@code DetailEntry} objects in <i>d</i>. The code also creates a
 * {@code Name} object to pass to the method {@code addDetailEntry}.
 * The variable <i>se</i>, used to create the {@code Name} object,
 * is a {@code SOAPEnvelope} object.
 * <pre>{@code
 *    Detail d = sf.getDetail();
 *    Name name = se.createName("GetLastTradePrice", "WOMBAT",
 *                                "http://www.wombat.org/trader");
 *    d.addDetailEntry(name);
 *    Iterator<DetailEntry> it = d.getDetailEntries();
 * }</pre>
 *
 * @since 1.6
 */
public interface Detail extends SOAPFaultElement {

    /**
     * Creates a new {@code DetailEntry} object with the given
     * name and adds it to this {@code Detail} object.
     *
     * @param name a {@code Name} object identifying the
     *         new {@code DetailEntry} object
     *
     * @return the new {@code DetailEntry} object that was
     *         created
     *
     * @exception SOAPException thrown when there is a problem in adding a
     * DetailEntry object to this Detail object.
     *
     * @see Detail#addDetailEntry(QName qname)
     */
    public DetailEntry addDetailEntry(Name name) throws SOAPException;

    /**
     * Creates a new {@code DetailEntry} object with the given
     * QName and adds it to this {@code Detail} object. This method
     * is the preferred over the one using Name.
     *
     * @param qname a {@code QName} object identifying the
     *         new {@code DetailEntry} object
     *
     * @return the new {@code DetailEntry} object that was
     *         created
     *
     * @exception SOAPException thrown when there is a problem in adding a
     * DetailEntry object to this Detail object.
     *
     * @see Detail#addDetailEntry(Name name)
     * @since 1.6, SAAJ 1.3
     */
    public DetailEntry addDetailEntry(QName qname) throws SOAPException;

    /**
     * Gets an Iterator over all of the {@code DetailEntry}s in this {@code Detail} object.
     *
     * @return an {@code Iterator} object over the {@code DetailEntry}
     *             objects in this {@code Detail} object
     */
    public Iterator<DetailEntry> getDetailEntries();
}
