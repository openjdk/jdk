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
package javax.print.attribute.standard;

import java.net.URI;

import javax.print.attribute.Attribute;
import javax.print.attribute.URISyntax;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class Destination is a printing attribute class, a URI, that is used to
 * indicate an alternate destination for the spooled printer formatted
 * data. Many PrintServices will not support the notion of a destination
 * other than the printer device, and so will not support this attribute.
 * <p>
 * A common use for this attribute will be applications which want
 * to redirect output to a local disk file : eg."file:out.prn".
 * Note that proper construction of "file:" scheme URI instances should
 * be performed using the {@code toURI()} method of class
 * {@link java.io.File File}.
 * See the documentation on that class for more information.
 * <p>
 * If a destination URI is specified in a PrintRequest and it is not
 * accessible for output by the PrintService, a PrintException will be thrown.
 * The PrintException may implement URIException to provide a more specific
 * cause.
 * <P>
 * <B>IPP Compatibility:</B> Destination is not an IPP attribute.
 *
 * @author  Phil Race.
 */
public final class Destination extends URISyntax
        implements PrintJobAttribute, PrintRequestAttribute {

    private static final long serialVersionUID = 6776739171700415321L;

    /**
     * Constructs a new destination attribute with the specified URI.
     *
     * @param  uri  URI.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code uri} is null.
     */
    public Destination(URI uri) {
        super (uri);
    }

    /**
     * Returns whether this destination attribute is equivalent to the
     * passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class Destination.
     * <LI>
     * This destination attribute's URI and {@code object}'s URI
     * are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this destination
     *         attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) &&
                object instanceof Destination);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class Destination, the category is class Destination itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return Destination.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class Destination, the category name is {@code "spool-data-destination"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "spool-data-destination";
    }

}
