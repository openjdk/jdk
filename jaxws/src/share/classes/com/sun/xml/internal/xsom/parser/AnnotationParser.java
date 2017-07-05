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
package com.sun.xml.internal.xsom.parser;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;

/**
 * Used to parse &lt;xs:annotation>.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class AnnotationParser {
    /**
     * Called every time a new &lt;xs:annotation> element
     * is found.
     *
     * The sub-tree rooted at &lt;xs:annotation> will be
     * sent to this ContentHandler as if it is a whole document.
     *
     * @param context
     *      indicates the schema component that owns this annotation.
     *      Always non-null.
     * @param parentElementName
     *      local name of the element that contains &lt;xs:annotation>.
     *      (e.g., "element", "attribute", ... )
     * @param errorHandler
     *      The error handler that the client application specifies.
     *      The returned content handler can send its errors to this
     *      object.
     * @param entityResolver
     *      The entity resolver that is currently in use. Again,
     *      The returned content handler can use this object
     *      if it needs to resolve entities.
     */
    public abstract ContentHandler getContentHandler(
        AnnotationContext context,
        String parentElementName,
        ErrorHandler errorHandler,
        EntityResolver entityResolver );

    /**
     * Once the SAX events are fed to the ContentHandler,
     * this method will be called to retrieve the parsed result.
     *
     * @param existing
     *      An annotation object which was returned from another
     *      AnnotationParser before. Sometimes, one schema component
     *      can have multiple &lt:xs:annotation> elements and
     *      this parameter is used to merge all those annotations
     *      together. If there is no existing object, null will be
     *      passed.
     * @return
     *      Any object, including null.
     */
    public abstract Object getResult( Object existing );
}
