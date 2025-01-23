/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.namespace;

import java.io.Serializable;
import javax.xml.XMLConstants;
import jdk.xml.internal.SecuritySupport;

/**
 * <p><code>QName</code> represents a <strong>qualified name</strong>
 * as defined in the XML specifications: <a
 * href="http://www.w3.org/TR/xmlschema-2/#QName">XML Schema Part2:
 * Datatypes specification</a>, <a
 * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">Namespaces
 * in XML</a>.
 *
 * <p>The value of a <code>QName</code> contains a <strong>Namespace
 * URI</strong>, <strong>local part</strong> and
 * <strong>prefix</strong>.</p>
 *
 * <p>The prefix is included in <code>QName</code> to retain lexical
 * information <strong><em>when present</em></strong> in an {@link
 * javax.xml.transform.Source XML input source}. The prefix is
 * <strong><em>NOT</em></strong> used in {@link #equals(Object)
 * QName.equals(Object)} or to compute the {@link #hashCode()
 * QName.hashCode()}.  Equality and the hash code are defined using
 * <strong><em>only</em></strong> the Namespace URI and local part.</p>
 *
 * <p>If not specified, the Namespace URI is set to {@link
 * XMLConstants#NULL_NS_URI XMLConstants.NULL_NS_URI}.
 * If not specified, the prefix is set to {@link
 * XMLConstants#DEFAULT_NS_PREFIX
 * XMLConstants.DEFAULT_NS_PREFIX}.</p>
 *
 * <p><code>QName</code> is immutable.</p>
 *
 * @author Jeff Suttor
 * @see <a href="http://www.w3.org/TR/xmlschema-2/#QName">
 *   XML Schema Part2: Datatypes specification</a>
 * @see <a href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
 *   Namespaces in XML</a>
 * @since 1.5
 */

public class QName implements Serializable {
    // tests show that the ID is the same from JDK 1.5 through JDK 9
    private static final long serialVersionUID = -9120448754896609940L;

    /**
     * <p>Namespace URI of this <code>QName</code>.</p>
     */
    private final String namespaceURI;

    /**
     * <p>local part of this <code>QName</code>.</p>
     */
    private final String localPart;

    /**
     * <p>prefix of this <code>QName</code>.</p>
     */
    private final String prefix;

    /**
     * <p><code>QName</code> constructor specifying the Namespace URI
     * and local part.</p>
     *
     * <p>If the Namespace URI is <code>null</code>, it is set to
     * {@link XMLConstants#NULL_NS_URI
     * XMLConstants.NULL_NS_URI}.  This value represents no
     * explicitly defined Namespace as defined by the <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">Namespaces
     * in XML</a> specification.  This action preserves compatible
     * behavior with QName 1.0.  Explicitly providing the {@link
     * XMLConstants#NULL_NS_URI
     * XMLConstants.NULL_NS_URI} value is the preferred coding
     * style.</p>
     *
     * <p>If the local part is <code>null</code> an
     * <code>IllegalArgumentException</code> is thrown.
     * A local part of "" is allowed to preserve
     * compatible behavior with QName 1.0. </p>
     *
     * <p>When using this constructor, the prefix is set to {@link
     * XMLConstants#DEFAULT_NS_PREFIX
     * XMLConstants.DEFAULT_NS_PREFIX}.</p>
     *
     * <p>The Namespace URI is not validated as a
     * <a href="https://www.ietf.org/rfc/rfc2396.txt">URI reference</a>.
     * The local part is not validated as a
     * <a href="http://www.w3.org/TR/REC-xml-names/#NT-NCName">NCName</a>
     * as specified in <a href="http://www.w3.org/TR/REC-xml-names/">Namespaces
     * in XML</a>.</p>
     *
     * @param namespaceURI Namespace URI of the <code>QName</code>
     * @param localPart    local part of the <code>QName</code>
     *
     * @throws IllegalArgumentException When <code>localPart</code> is
     *   <code>null</code>
     *
     * @see #QName(String namespaceURI, String localPart, String
     * prefix) QName(String namespaceURI, String localPart, String
     * prefix)
     */
    public QName(final String namespaceURI, final String localPart) {
        this(namespaceURI, localPart, XMLConstants.DEFAULT_NS_PREFIX);
    }

    /**
     * <p><code>QName</code> constructor specifying the Namespace URI,
     * local part and prefix.</p>
     *
     * <p>If the Namespace URI is <code>null</code>, it is set to
     * {@link XMLConstants#NULL_NS_URI
     * XMLConstants.NULL_NS_URI}.  This value represents no
     * explicitly defined Namespace as defined by the <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">Namespaces
     * in XML</a> specification.  This action preserves compatible
     * behavior with QName 1.0.  Explicitly providing the {@link
     * XMLConstants#NULL_NS_URI
     * XMLConstants.NULL_NS_URI} value is the preferred coding
     * style.</p>
     *
     * <p>If the local part is <code>null</code> an
     * <code>IllegalArgumentException</code> is thrown.
     * A local part of "" is allowed to preserve
     * compatible behavior with QName 1.0. </p>
     *
     * <p>If the prefix is <code>null</code>, an
     * <code>IllegalArgumentException</code> is thrown.  Use {@link
     * XMLConstants#DEFAULT_NS_PREFIX
     * XMLConstants.DEFAULT_NS_PREFIX} to explicitly indicate that no
     * prefix is present or the prefix is not relevant.</p>
     *
     * <p>The Namespace URI is not validated as a
     * <a href="https://www.ietf.org/rfc/rfc2396.txt">URI reference</a>.
     * The local part and prefix are not validated as a
     * <a href="http://www.w3.org/TR/REC-xml-names/#NT-NCName">NCName</a>
     * as specified in <a href="http://www.w3.org/TR/REC-xml-names/">Namespaces
     * in XML</a>.</p>
     *
     * @param namespaceURI Namespace URI of the <code>QName</code>
     * @param localPart    local part of the <code>QName</code>
     * @param prefix       prefix of the <code>QName</code>
     *
     * @throws IllegalArgumentException When <code>localPart</code>
     *   or <code>prefix</code> is <code>null</code>
     */
    public QName(String namespaceURI, String localPart, String prefix) {

        // map null Namespace URI to default
        // to preserve compatibility with QName 1.0
        if (namespaceURI == null) {
            this.namespaceURI = XMLConstants.NULL_NS_URI;
        } else {
            this.namespaceURI = namespaceURI;
        }

        // local part is required.
        // "" is allowed to preserve compatibility with QName 1.0
        if (localPart == null) {
            throw new IllegalArgumentException(
                    "local part cannot be \"null\" when creating a QName");
        }
        this.localPart = localPart;

        // prefix is required
        if (prefix == null) {
            throw new IllegalArgumentException(
                    "prefix cannot be \"null\" when creating a QName");
        }
        this.prefix = prefix;
    }

    /**
     * <p><code>QName</code> constructor specifying the local part.</p>
     *
     * <p>If the local part is <code>null</code> an
     * <code>IllegalArgumentException</code> is thrown.
     * A local part of "" is allowed to preserve
     * compatible behavior with QName 1.0. </p>
     *
     * <p>When using this constructor, the Namespace URI is set to
     * {@link XMLConstants#NULL_NS_URI
     * XMLConstants.NULL_NS_URI} and the prefix is set to {@link
     * XMLConstants#DEFAULT_NS_PREFIX
     * XMLConstants.DEFAULT_NS_PREFIX}.</p>
     *
     * <p><em>In an XML context, all Element and Attribute names exist
     * in the context of a Namespace.  Making this explicit during the
     * construction of a <code>QName</code> helps prevent hard to
     * diagnosis XML validity errors.  The constructors {@link
     * #QName(String namespaceURI, String localPart) QName(String
     * namespaceURI, String localPart)} and
     * {@link #QName(String namespaceURI, String localPart, String prefix)}
     * are preferred.</em></p>
     *
     * <p>The local part is not validated as a
     * <a href="http://www.w3.org/TR/REC-xml-names/#NT-NCName">NCName</a>
     * as specified in <a href="http://www.w3.org/TR/REC-xml-names/">Namespaces
     * in XML</a>.</p>
     *
     * @param localPart local part of the <code>QName</code>
     *
     * @throws IllegalArgumentException When <code>localPart</code> is
     *   <code>null</code>
     *
     * @see #QName(String namespaceURI, String localPart) QName(String
     * namespaceURI, String localPart)
     * @see #QName(String namespaceURI, String localPart, String
     * prefix) QName(String namespaceURI, String localPart, String
     * prefix)
     */
    public QName(String localPart) {
        this(
            XMLConstants.NULL_NS_URI,
            localPart,
            XMLConstants.DEFAULT_NS_PREFIX);
    }

    /**
     * <p>Get the Namespace URI of this <code>QName</code>.</p>
     *
     * @return Namespace URI of this <code>QName</code>
     */
    public String getNamespaceURI() {
        return namespaceURI;
    }

    /**
     * <p>Get the local part of this <code>QName</code>.</p>
     *
     *  @return local part of this <code>QName</code>
     */
    public String getLocalPart() {
        return localPart;
    }

    /**
     * <p>Get the prefix of this <code>QName</code>.</p>
     *
     * <p>The prefix assigned to a <code>QName</code> might
     * <strong><em>NOT</em></strong> be valid in a different
     * context. For example, a <code>QName</code> may be assigned a
     * prefix in the context of parsing a document but that prefix may
     * be invalid in the context of a different document.</p>
     *
     *  @return prefix of this <code>QName</code>
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * <p>Test this <code>QName</code> for equality with another
     * <code>Object</code>.</p>
     *
     * <p>If the <code>Object</code> to be tested is not a
     * <code>QName</code> or is <code>null</code>, then this method
     * returns <code>false</code>.</p>
     *
     * <p>Two <code>QName</code>s are considered equal if and only if
     * both the Namespace URI and local part are equal. This method
     * uses <code>String.equals()</code> to check equality of the
     * Namespace URI and local part. The prefix is
     * <strong><em>NOT</em></strong> used to determine equality.</p>
     *
     * <p>This method satisfies the general contract of {@link
     * java.lang.Object#equals(Object) Object.equals(Object)}</p>
     *
     * @param objectToTest the <code>Object</code> to test for
     * equality with this <code>QName</code>
     * @return <code>true</code> if the given <code>Object</code> is
     * equal to this <code>QName</code> else <code>false</code>
     */
    public final boolean equals(Object objectToTest) {
        if (objectToTest == this) {
            return true;
        }

        if (objectToTest == null || !(objectToTest instanceof QName)) {
            return false;
        }

        QName qName = (QName) objectToTest;

        return localPart.equals(qName.localPart)
            && namespaceURI.equals(qName.namespaceURI);
    }

    /**
     * <p>Generate the hash code for this <code>QName</code>.</p>
     *
     * <p>The hash code is calculated using both the Namespace URI and
     * the local part of the <code>QName</code>.  The prefix is
     * <strong><em>NOT</em></strong> used to calculate the hash
     * code.</p>
     *
     * <p>This method satisfies the general contract of {@link
     * java.lang.Object#hashCode() Object.hashCode()}.</p>
     *
     * @return hash code for this <code>QName</code> <code>Object</code>
     */
    public final int hashCode() {
        return namespaceURI.hashCode() ^ localPart.hashCode();
    }

    /**
     * {@return the string representation of this {@code QName}}
     * The format is:
     * <pre> {@code
     *     {NamespaceURI}LocalPart
     * }</pre>
     * If {@code NamespaceURI} is {@code null}, only {@code LocalPart} is returned.
     *
     * @apiNote The {@code Prefix} is not returned in the string representation.
     */
    public String toString() {
        if (namespaceURI.equals(XMLConstants.NULL_NS_URI)) {
            return localPart;
        } else {
            return "{" + namespaceURI + "}" + localPart;
        }
    }

    /**
     * {@return a {@code QName} from its string representation}
     * The string representation must be in the format returned by {@link #toString()}:
     * <pre> {@code
     *     {NamespaceURI}LocalPart
     * }</pre>
     * Since the {@code Prefix} is not represented in the string form, it will be
     * set to {@link XMLConstants#DEFAULT_NS_PREFIX XMLConstants.DEFAULT_NS_PREFIX}.
     *
     * @apiNote This method does not perform full validation of the resulting
     * {@code QName}. The {@code NamespaceURI} is not validated as a
     * <a href="https://www.ietf.org/rfc/rfc2396.txt">URI reference</a>.
     * The {@code LocalPart} is not validated as a
     * <a href="http://www.w3.org/TR/REC-xml-names/#NT-NCName">NCName</a>
     * as specified in
     * <a href="http://www.w3.org/TR/REC-xml-names/">Namespaces in XML</a>.
     *
     * @param qNameAsString the string representation of the {@code QName}
     * @throws IllegalArgumentException if {@code qNameAsString} is {@code null}
     * or malformed
     * @see #toString() QName.toString()
     */
    public static QName valueOf(String qNameAsString) {

        // null is not valid
        if (qNameAsString == null) {
            throw new IllegalArgumentException(
                    "cannot create QName from \"null\" or \"\" String");
        }

        // "" local part is valid to preserve compatible behavior with QName 1.0
        if (qNameAsString.length() == 0) {
            return new QName(
                XMLConstants.NULL_NS_URI,
                qNameAsString,
                XMLConstants.DEFAULT_NS_PREFIX);
        }

        // local part only?
        if (qNameAsString.charAt(0) != '{') {
            return new QName(
                XMLConstants.NULL_NS_URI,
                qNameAsString,
                XMLConstants.DEFAULT_NS_PREFIX);
        }

        // Namespace URI improperly specified?
        if (qNameAsString.startsWith("{" + XMLConstants.NULL_NS_URI + "}")) {
            throw new IllegalArgumentException(
                "Namespace URI .equals(XMLConstants.NULL_NS_URI), "
                + ".equals(\"" + XMLConstants.NULL_NS_URI + "\"), "
                + "only the local part, "
                + "\""
                + qNameAsString.substring(2 + XMLConstants.NULL_NS_URI.length())
                + "\", "
                + "should be provided.");
        }

        // Namespace URI and local part specified
        int endOfNamespaceURI = qNameAsString.indexOf('}');
        if (endOfNamespaceURI == -1) {
            throw new IllegalArgumentException(
                "cannot create QName from \""
                    + qNameAsString
                    + "\", missing closing \"}\"");
        }
        return new QName(
            qNameAsString.substring(1, endOfNamespaceURI),
            qNameAsString.substring(endOfNamespaceURI + 1),
            XMLConstants.DEFAULT_NS_PREFIX);
    }
}
