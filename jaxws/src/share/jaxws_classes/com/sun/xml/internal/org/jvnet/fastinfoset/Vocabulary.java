/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.org.jvnet.fastinfoset;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A canonical representation of a vocabulary.
 * <p>
 * Each vocabulary table is represented as a Set. A vocabulary table entry is
 * represented as an item in the Set.
 * <p>
 * The 1st item contained in a Set is assigned the smallest index value,
 * n say (where n >= 0). The 2nd item is assigned an index value of n + 1. The kth
 * item is assigned an index value of n + (k - 1).
 * <p>
 * A Fast Infoset parser/serializer implementation will tranform the canonical
 * representation of a Vocabulary instance into a more optimal form suitable
 * for the efficient usage according to the API implemented by the parsers and
 * serialziers.
 */
public class Vocabulary {
    /**
     * The restricted alphabet table, containing String objects.
     */
    public final Set restrictedAlphabets = new LinkedHashSet();

    /**
     * The encoding algorithm table, containing String objects.
     */
    public final Set encodingAlgorithms = new LinkedHashSet();

    /**
     * The prefix table, containing String objects.
     */
    public final Set prefixes = new LinkedHashSet();

    /**
     * The namespace name table, containing String objects.
     */
    public final Set namespaceNames = new LinkedHashSet();

    /**
     * The local name table, containing String objects.
     */
    public final Set localNames = new LinkedHashSet();

    /**
     * The "other NCName" table, containing String objects.
     */
    public final Set otherNCNames = new LinkedHashSet();

    /**
     * The "other URI" table, containing String objects.
     */
    public final Set otherURIs = new LinkedHashSet();

    /**
     * The "attribute value" table, containing String objects.
     */
    public final Set attributeValues = new LinkedHashSet();

    /**
     * The "other string" table, containing String objects.
     */
    public final Set otherStrings = new LinkedHashSet();

    /**
     * The "character content chunk" table, containing String objects.
     */
    public final Set characterContentChunks = new LinkedHashSet();

    /**
     * The element table, containing QName objects.
     */
    public final Set elements = new LinkedHashSet();

    /**
     * The attribute table, containing QName objects.
     */
    public final Set attributes = new LinkedHashSet();
}
