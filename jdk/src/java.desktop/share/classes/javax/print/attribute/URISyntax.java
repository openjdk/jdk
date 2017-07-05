/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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


package javax.print.attribute;

import java.io.Serializable;
import java.net.URI;

/**
 * Class URISyntax is an abstract base class providing the common
 * implementation of all attributes whose value is a Uniform Resource
 * Identifier (URI). Once constructed, a URI attribute's value is immutable.
 *
 * @author  Alan Kaminsky
 */
public abstract class URISyntax implements Serializable, Cloneable {

    private static final long serialVersionUID = -7842661210486401678L;

    /**
     * URI value of this URI attribute.
     * @serial
     */
    private URI uri;

    /**
     * Constructs a URI attribute with the specified URI.
     *
     * @param  uri  URI.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>uri</CODE> is null.
     */
    protected URISyntax(URI uri) {
        this.uri = verify (uri);
    }

    private static URI verify(URI uri) {
        if (uri == null) {
            throw new NullPointerException(" uri is null");
        }
        return uri;
    }

    /**
     * Returns this URI attribute's URI value.
     * @return the URI.
     */
    public URI getURI()  {
        return uri;
    }

    /**
     * Returns a hashcode for this URI attribute.
     *
     * @return  A hashcode value for this object.
     */
    public int hashCode() {
        return uri.hashCode();
    }

    /**
     * Returns whether this URI attribute is equivalent to the passed in
     * object.
     * To be equivalent, all of the following conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class URISyntax.
     * <LI>
     * This URI attribute's underlying URI and <CODE>object</CODE>'s
     * underlying URI are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this URI
     *          attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return(object != null &&
               object instanceof URISyntax &&
               this.uri.equals (((URISyntax) object).uri));
    }

    /**
     * Returns a String identifying this URI attribute. The String is the
     * string representation of the attribute's underlying URI.
     *
     * @return  A String identifying this object.
     */
    public String toString() {
        return uri.toString();
    }

}
