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

package com.sun.xml.internal.xsom;

import org.relaxng.datatype.ValidationContext;

/**
 * String with in-scope namespace binding information.
 *
 * <p>
 * In a general case, text (PCDATA/attributes) that appear in XML schema
 * cannot be correctly interpreted unless you also have in-scope namespace
 * binding (a case in point is QName.) Therefore, it's convenient to
 * handle the lexical representation and the in-scope namespace binding
 * in a pair.
 *
 * @author Kohsuke Kawaguchi
 */
public final class XmlString {
    /**
     * Textual value. AKA lexical representation.
     */
    public final String value;

    /**
     * Used to resole in-scope namespace bindings.
     */
    public final ValidationContext context;

    /**
     * Creates a new {@link XmlString} from a lexical representation and in-scope namespaces.
     */
    public XmlString(String value, ValidationContext context) {
        this.value = value;
        this.context = context;
        if(context==null)
            throw new IllegalArgumentException();
    }

    /**
     * Creates a new {@link XmlString} with empty in-scope namespace bindings.
     */
    public XmlString(String value) {
        this(value,NULL_CONTEXT);
    }

    /**
     * Resolves a namespace prefix to the corresponding namespace URI.
     *
     * This method is used for resolving prefixes in the {@link #value}
     * (such as when {@link #value} represents a QName type.)
     *
     * <p>
     * If the prefix is "" (empty string), the method
     * returns the default namespace URI.
     *
     * <p>
     * If the prefix is "xml", then the method returns
     * "http://www.w3.org/XML/1998/namespace",
     * as defined in the XML Namespaces Recommendation.
     *
     * @return
     *          namespace URI of this prefix.
     *          If the specified prefix is not declared,
     *          the implementation returns null.
     */
    public final String resolvePrefix(String prefix) {
        return context.resolveNamespacePrefix(prefix);
    }

    public String toString() {
        return value;
    }

    private static final ValidationContext NULL_CONTEXT = new ValidationContext() {
        public String resolveNamespacePrefix(String s) {
            if(s.length()==0)   return "";
            if(s.equals("xml")) return "http://www.w3.org/XML/1998/namespace";
            return null;
        }

        public String getBaseUri() {
            return null;
        }

        public boolean isUnparsedEntity(String s) {
            return false;
        }

        public boolean isNotation(String s) {
            return false;
        }
    };
}
