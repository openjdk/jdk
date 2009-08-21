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


package com.sun.tools.internal.ws.wsdl.parser;

import org.w3c.dom.Element;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Encapsulates schema-language dependent internalization logic.
 *
 * {@link com.sun.tools.internal.xjc.reader.internalizer.Internalizer} and {@link DOMForest} are responsible for
 * doing schema language independent part, and this object is responsible
 * for schema language dependent part.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *     Vivek Pandey
 */
public interface InternalizationLogic {
    /**
     * Creates a new instance of XMLFilter that can be used to
     * find references to external schemas.
     *
     * <p>
     * Schemas that are included/imported need to be a part of
     * {@link DOMForest}, and this filter will be expected to
     * find such references.
     *
     * <p>
     * Once such a reference is found, the filter is expected to
     * call the parse method of DOMForest.
     *
     * <p>
     * {@link DOMForest} will register ErrorHandler to the returned
     * object, so any error should be sent to that error handler.
     *
     * @return
     *      This method returns {@link org.xml.sax.helpers.XMLFilterImpl} because
     *      the filter has to be usable for two directions
     *      (wrapping a reader and wrapping a ContentHandler)
     */
    XMLFilterImpl createExternalReferenceFinder( DOMForest parent );

    /**
     * Checks if the specified element is a valid target node
     * to attach a customization.
     *
     * @param parent
     *      The owner DOMForest object. Probably useful only
     *      to obtain context information, such as error handler.
     * @param bindings
     *      &lt;jaxb:bindings> element or a customization element.
     * @return
     *      true if it's OK, false if not.
     */
    boolean checkIfValidTargetNode( DOMForest parent, Element bindings, Element target );

    /**
     * Prepares an element that actually receives customizations.
     *
     * <p>
     * For example, in XML Schema, target nodes can be any schema
     * element but it is always the &lt;xsd:appinfo> element that
     * receives customization.
     *
     * @param target
     *      The target node designated by the customization.
     * @return
     *      Always return non-null valid object
     */
    Element refineSchemaTarget( Element target );

    /**
     * Prepares a WSDL element that actually receives customizations.
     *
     *
     * @param target
     *      The target node designated by the customization.
     * @return
     *      Always return non-null valid object
     */
    Element refineWSDLTarget( Element target );

}
