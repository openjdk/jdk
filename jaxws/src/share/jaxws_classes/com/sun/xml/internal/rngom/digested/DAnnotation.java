/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2011
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.digested;

import org.xml.sax.Locator;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Annotation.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DAnnotation {

    /**
     * Instance reserved to be empty.
     */
    static final DAnnotation EMPTY = new DAnnotation();

    /**
     * Keyed by QName.
     */
    final Map<QName,Attribute> attributes = new HashMap<QName,Attribute>();

    /**
     * List of nested elements.
     */
    final List<Element> contents = new ArrayList<Element>();

    /**
     * Attribute.
     */
    public static class Attribute {
        private final String ns;
        private final String localName;
        private final String prefix;

        private String value;
        private Locator loc;

        public Attribute(String ns, String localName, String prefix) {
            this.ns = ns;
            this.localName = localName;
            this.prefix = prefix;
        }

        public Attribute(String ns, String localName, String prefix, String value, Locator loc) {
            this.ns = ns;
            this.localName = localName;
            this.prefix = prefix;
            this.value = value;
            this.loc = loc;
        }

        /**
         * Gets the namespace URI of this attribute.
         *
         * @return
         *      can be empty (to represent the default namespace), but never null.
         */
        public String getNs() {
            return ns;
        }

        /**
         * Gets the local name of this attribute.
         *
         * @return
         *      always non-null.
         */
        public String getLocalName() {
            return localName;
        }

        /**
         * Gets the prefix of thie attribute.
         *
         * @return
         *      null if this attribute didn't have a prefix.
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Gets the attribute value.
         *
         * @return
         *      never null.
         */
        public String getValue() {
            return value;
        }

        /**
         * Gets the location in the source schema file where this annotation was present.
         *
         * @return
         *      never null.
         */
        public Locator getLoc() {
            return loc;
        }
    }

    /**
     * Gets the attribute of a given name.
     *
     * @param nsUri
     *      can be empty but must not be null.
     * @return
     *      null if no such attribute is found.
     */
    public Attribute getAttribute( String nsUri, String localName ) {
        return getAttribute(new QName(nsUri,localName));
    }

    public Attribute getAttribute( QName n ) {
        return attributes.get(n);
    }

    /**
     * Gets the read-only view of all the attributes.
     *
     * @return
     *      can be empty but never null.
     *      the returned map is read-only.
     */
    public Map<QName,Attribute> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Gets the read-only view of all the child elements of this annotation.
     *
     * @return
     *      can be empty but never null.
     *      the returned list is read-only.
     */
    public List<Element> getChildren() {
        return Collections.unmodifiableList(contents);
    }
}
