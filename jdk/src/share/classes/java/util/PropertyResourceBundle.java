/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 */

package java.util;

import java.io.InputStream;
import java.io.Reader;
import java.io.IOException;
import sun.util.ResourceBundleEnumeration;

/**
 * <code>PropertyResourceBundle</code> is a concrete subclass of
 * <code>ResourceBundle</code> that manages resources for a locale
 * using a set of static strings from a property file. See
 * {@link ResourceBundle ResourceBundle} for more information about resource
 * bundles.
 *
 * <p>
 * Unlike other types of resource bundle, you don't subclass
 * <code>PropertyResourceBundle</code>.  Instead, you supply properties
 * files containing the resource data.  <code>ResourceBundle.getBundle</code>
 * will automatically look for the appropriate properties file and create a
 * <code>PropertyResourceBundle</code> that refers to it. See
 * {@link ResourceBundle#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader) ResourceBundle.getBundle}
 * for a complete description of the search and instantiation strategy.
 *
 * <p>
 * The following <a name="sample">example</a> shows a member of a resource
 * bundle family with the base name "MyResources".
 * The text defines the bundle "MyResources_de",
 * the German member of the bundle family.
 * This member is based on <code>PropertyResourceBundle</code>, and the text
 * therefore is the content of the file "MyResources_de.properties"
 * (a related <a href="ListResourceBundle.html#sample">example</a> shows
 * how you can add bundles to this family that are implemented as subclasses
 * of <code>ListResourceBundle</code>).
 * The keys in this example are of the form "s1" etc. The actual
 * keys are entirely up to your choice, so long as they are the same as
 * the keys you use in your program to retrieve the objects from the bundle.
 * Keys are case-sensitive.
 * <blockquote>
 * <pre>
 * # MessageFormat pattern
 * s1=Die Platte \"{1}\" enth&auml;lt {0}.
 *
 * # location of {0} in pattern
 * s2=1
 *
 * # sample disk name
 * s3=Meine Platte
 *
 * # first ChoiceFormat choice
 * s4=keine Dateien
 *
 * # second ChoiceFormat choice
 * s5=eine Datei
 *
 * # third ChoiceFormat choice
 * s6={0,number} Dateien
 *
 * # sample date
 * s7=3. M&auml;rz 1996
 * </pre>
 * </blockquote>
 *
 * <p>
 * <strong>Note:</strong> PropertyResourceBundle can be constructed either
 * from an InputStream or a Reader, which represents a property file.
 * Constructing a PropertyResourceBundle instance from an InputStream requires
 * that the input stream be encoded in ISO-8859-1.  In that case, characters
 * that cannot be represented in ISO-8859-1 encoding must be represented by Unicode Escapes
 * as defined in section 3.3 of
 * <cite>The Java&trade; Language Specification</cite>
 * whereas the other constructor which takes a Reader does not have that limitation.
 *
 * @see ResourceBundle
 * @see ListResourceBundle
 * @see Properties
 * @since JDK1.1
 */
public class PropertyResourceBundle extends ResourceBundle {
    /**
     * Creates a property resource bundle from an {@link java.io.InputStream
     * InputStream}.  The property file read with this constructor
     * must be encoded in ISO-8859-1.
     *
     * @param stream an InputStream that represents a property file
     *        to read from.
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if <code>stream</code> is null
     */
    public PropertyResourceBundle (InputStream stream) throws IOException {
        Properties properties = new Properties();
        properties.load(stream);
        lookup = new HashMap(properties);
    }

    /**
     * Creates a property resource bundle from a {@link java.io.Reader
     * Reader}.  Unlike the constructor
     * {@link #PropertyResourceBundle(java.io.InputStream) PropertyResourceBundle(InputStream)},
     * there is no limitation as to the encoding of the input property file.
     *
     * @param reader a Reader that represents a property file to
     *        read from.
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if <code>reader</code> is null
     * @since 1.6
     */
    public PropertyResourceBundle (Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        lookup = new HashMap(properties);
    }

    // Implements java.util.ResourceBundle.handleGetObject; inherits javadoc specification.
    public Object handleGetObject(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return lookup.get(key);
    }

    /**
     * Returns an <code>Enumeration</code> of the keys contained in
     * this <code>ResourceBundle</code> and its parent bundles.
     *
     * @return an <code>Enumeration</code> of the keys contained in
     *         this <code>ResourceBundle</code> and its parent bundles.
     * @see #keySet()
     */
    public Enumeration<String> getKeys() {
        ResourceBundle parent = this.parent;
        return new ResourceBundleEnumeration(lookup.keySet(),
                (parent != null) ? parent.getKeys() : null);
    }

    /**
     * Returns a <code>Set</code> of the keys contained
     * <em>only</em> in this <code>ResourceBundle</code>.
     *
     * @return a <code>Set</code> of the keys contained only in this
     *         <code>ResourceBundle</code>
     * @since 1.6
     * @see #keySet()
     */
    protected Set<String> handleKeySet() {
        return lookup.keySet();
    }

    // ==================privates====================

    private Map<String,Object> lookup;
}
