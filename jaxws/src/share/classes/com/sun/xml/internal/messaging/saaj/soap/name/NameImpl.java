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


package com.sun.xml.internal.messaging.saaj.soap.name;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPConstants;

//import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import org.w3c.dom.Element;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class NameImpl implements Name {
    public static final String XML_NAMESPACE_PREFIX = "xml";
    public static final String XML_SCHEMA_NAMESPACE_PREFIX = "xs";
    public static final String SOAP_ENVELOPE_PREFIX = "SOAP-ENV";

    public static final String XML_NAMESPACE =
        "http://www.w3.org/XML/1998/namespace";
    public static final String SOAP11_NAMESPACE =
        SOAPConstants.URI_NS_SOAP_ENVELOPE;
    public static final String SOAP12_NAMESPACE =
        SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE;
    public static final String XML_SCHEMA_NAMESPACE =
        "http://www.w3.org/2001/XMLSchema";

    protected String uri = "";
    protected String localName = "";
    protected String prefix = "";
    private String qualifiedName = null;

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.NAMING_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.name.LocalStrings");

    /**
     * XML Information Set REC
     * all namespace attributes (including those named xmlns,
     * whose [prefix] property has no value) have a namespace URI of http://www.w3.org/2000/xmlns/
     */
    public final static String XMLNS_URI = "http://www.w3.org/2000/xmlns/".intern();

    protected NameImpl(String name) {
        this.localName = name == null ? "" : name;
    }

    protected NameImpl(String name, String prefix, String uri) {
        this.uri = uri == null ? "" : uri;
        this.localName = name == null ? "" : name;
        this.prefix = prefix == null ? "" : prefix;

        if (this.prefix.equals("xmlns") && this.uri.equals("")) {
            this.uri = XMLNS_URI;
        }
        if (this.uri.equals(XMLNS_URI) && this.prefix.equals("")) {
            this.prefix = "xmlns";
        }
    }

    public static Name convertToName(QName qname) {
        return new NameImpl(qname.getLocalPart(),
                            qname.getPrefix(),
                            qname.getNamespaceURI());
    }

    public static QName convertToQName(Name name) {
        return new QName(name.getURI(),
                         name.getLocalName(),
                         name.getPrefix());
    }

    public static NameImpl createFromUnqualifiedName(String name) {
        return new NameImpl(name);
    }

    public static Name createFromTagName(String tagName) {
        return createFromTagAndUri(tagName, "");
    }

    public static Name createFromQualifiedName(
        String qualifiedName,
        String uri) {
        return createFromTagAndUri(qualifiedName, uri);
    }

    protected static Name createFromTagAndUri(String tagName, String uri) {
        if (tagName == null) {
            log.severe("SAAJ0201.name.not.created.from.null.tag");
            throw new IllegalArgumentException("Cannot create a name from a null tag.");
        }
        int index = tagName.indexOf(':');
        if (index < 0) {
            return new NameImpl(tagName, "", uri);
        } else {
            return new NameImpl(
                tagName.substring(index + 1),
                tagName.substring(0, index),
                uri);
        }
    }

    protected static int getPrefixSeparatorIndex(String qualifiedName) {
        int index = qualifiedName.indexOf(':');
        if (index < 0) {
            log.log(
                Level.SEVERE,
                "SAAJ0202.name.invalid.arg.format",
                new String[] { qualifiedName });
            throw new IllegalArgumentException(
                "Argument \""
                    + qualifiedName
                    + "\" must be of the form: \"prefix:localName\"");
        }
        return index;
    }

    public static String getPrefixFromQualifiedName(String qualifiedName) {
        return qualifiedName.substring(
            0,
            getPrefixSeparatorIndex(qualifiedName));
    }

    public static String getLocalNameFromQualifiedName(String qualifiedName) {
        return qualifiedName.substring(
            getPrefixSeparatorIndex(qualifiedName) + 1);
    }

    public static String getPrefixFromTagName(String tagName) {
        if (isQualified(tagName)) {
            return getPrefixFromQualifiedName(tagName);
        }
        return "";
    }

    public static String getLocalNameFromTagName(String tagName) {
        if (isQualified(tagName)) {
            return getLocalNameFromQualifiedName(tagName);
        }
        return tagName;
    }

    public static boolean isQualified(String tagName) {
        return tagName.indexOf(':') >= 0;
    }

    public static NameImpl create(String name, String prefix, String uri) {
        if (prefix == null)
            prefix = "";
        if (uri == null)
            uri = "";
        if (name == null)
            name = "";

        if (!uri.equals("") && !name.equals("")) {
            if (uri.equals(NameImpl.SOAP11_NAMESPACE)) {
                if (name.equalsIgnoreCase("Envelope"))
                    return createEnvelope1_1Name(prefix);
                else if (name.equalsIgnoreCase("Header"))
                    return createHeader1_1Name(prefix);
                else if (name.equalsIgnoreCase("Body"))
                    return createBody1_1Name(prefix);
                else if (name.equalsIgnoreCase("Fault"))
                    return createFault1_1Name(prefix);
                else
                    return new SOAP1_1Name(name, prefix);
            } else if (uri.equals(SOAP12_NAMESPACE)) {
                if (name.equalsIgnoreCase("Envelope"))
                    return createEnvelope1_2Name(prefix);
                else if (name.equalsIgnoreCase("Header"))
                    return createHeader1_2Name(prefix);
                else if (name.equalsIgnoreCase("Body"))
                    return createBody1_2Name(prefix);
                else if (
                    name.equals("Fault")
                        || name.equals("Reason")
                        || name.equals("Detail"))
                    return createFault1_2Name(name, prefix);
                else if (name.equals("Code") || name.equals("Subcode"))
                    return createCodeSubcode1_2Name(prefix, name);
                else
                    return new SOAP1_2Name(name, prefix);
            }

        }
        return new NameImpl(name, prefix, uri);
    }

    public static String createQName(String prefix, String localName) {
        if (prefix == null || prefix.equals("")) {
            return localName;
        }
        return prefix + ":" + localName;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Name)) {
            return false;
        }

        Name otherName = (Name) obj;

        if (!uri.equals(otherName.getURI())) {
            return false;
        }

        if (!localName.equals(otherName.getLocalName())) {
            return false;
        }

        return true;
    }

    /**
     * Get the local name part of this XML Name.
     *
     * @return a string for the local name.
     */
    public String getLocalName() {
        return localName;
    }

    /* getQualifiedName is inherited from QName */

    /**
     * Returns the prefix associated with the namespace of the name.
     *
     * @return the prefix as a string.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the URI associated of the namespace.
     *
     * @return the uri as a string.
     */
    public String getURI() {
        return uri;
    }

    /**
     * Returns a String version of the name suitable for use in an XML document.
     */
    public String getQualifiedName() {
        if (qualifiedName == null) {
            if (prefix != null && prefix.length() > 0) {
                qualifiedName = prefix + ":" + localName;
            } else {
                qualifiedName = localName;
            }
        }
        return qualifiedName;
    }

    /**
     * Create a name object for a SOAP1.1 Envelope.
     */
    public static NameImpl createEnvelope1_1Name(String prefix) {
        return new Envelope1_1Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2 Envelope.
     */
    public static NameImpl createEnvelope1_2Name(String prefix) {
        return new Envelope1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.1 Header.
     */
    public static NameImpl createHeader1_1Name(String prefix) {
        return new Header1_1Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2 Header.
     */
    public static NameImpl createHeader1_2Name(String prefix) {
        return new Header1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.1 Body.
     */
    public static NameImpl createBody1_1Name(String prefix) {
        return new Body1_1Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2 Body.
     */
    public static NameImpl createBody1_2Name(String prefix) {
        return new Body1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.1 Fault.
     */
    public static NameImpl createFault1_1Name(String prefix) {
        return new Fault1_1Name(prefix);
    }

    /**
      * Create a name object for a SOAP1.2 NotUnderstood element.
      */
    public static NameImpl createNotUnderstood1_2Name(String prefix) {
        return new NotUnderstood1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2 Upgrade element.
     */
    public static NameImpl createUpgrade1_2Name(String prefix) {
        return new Upgrade1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2 SupportedEnvelope Upgrade element.
     */
    public static NameImpl createSupportedEnvelope1_2Name(String prefix) {
        return new SupportedEnvelope1_2Name(prefix);
    }

    /**
     * Create a name object for a SOAP1.2
     * Fault, Reason or Detail.
     *
     * @param localName Local Name of element
     */
    public static NameImpl createFault1_2Name(
        String localName,
        String prefix) {
        return new Fault1_2Name(localName, prefix);
    }

    /**
     * Create a name object for a SOAP1.2 Fault/Code or Subcode.
     *
     * @param localName Either "Code" or "Subcode"
     */
    public static NameImpl createCodeSubcode1_2Name(
        String prefix,
        String localName) {
        return new CodeSubcode1_2Name(localName, prefix);
    }

    /**
     * Create a name object for a SOAP1.1 Fault Detail.
     */
    public static NameImpl createDetail1_1Name() {
        return new Detail1_1Name();
    }

    public static NameImpl createDetail1_1Name(String prefix) {
        return new Detail1_1Name(prefix);
    }

    public static NameImpl createFaultElement1_1Name(String localName) {
        return new FaultElement1_1Name(localName);
    }

    public static NameImpl createFaultElement1_1Name(String localName,
                                                     String prefix) {
        return new FaultElement1_1Name(localName, prefix);
    }

    public static NameImpl createSOAP11Name(String string) {
        return new SOAP1_1Name(string, null);
    }
    public static NameImpl createSOAP12Name(String string) {
        return new SOAP1_2Name(string, null);
    }

    public static NameImpl createSOAP12Name(String localName, String prefix) {
        return new SOAP1_2Name(localName, prefix);
    }

    public static NameImpl createXmlName(String localName) {
        return new NameImpl(localName, XML_NAMESPACE_PREFIX, XML_NAMESPACE);
    }

    public static Name copyElementName(Element element) {
        String localName = element.getLocalName();
        String prefix = element.getPrefix();
        String uri = element.getNamespaceURI();
        return create(localName, prefix, uri);
    }


static class SOAP1_1Name extends NameImpl {
    SOAP1_1Name(String name, String prefix) {
        super(
            name,
            (prefix == null || prefix.equals(""))
                ? NameImpl.SOAP_ENVELOPE_PREFIX
                : prefix,
            NameImpl.SOAP11_NAMESPACE);
    }
}

static class Envelope1_1Name extends SOAP1_1Name {
    Envelope1_1Name(String prefix) {
        super("Envelope", prefix);
    }
}

static class Header1_1Name extends SOAP1_1Name {
    Header1_1Name(String prefix) {
        super("Header", prefix);
    }
}

static class Body1_1Name extends SOAP1_1Name {
    Body1_1Name(String prefix) {
        super("Body", prefix);
    }
}

static class Fault1_1Name extends NameImpl {
    Fault1_1Name(String prefix) {
        super(
            "Fault",
            (prefix == null || prefix.equals(""))
                ? SOAP_ENVELOPE_PREFIX
                : prefix,
            SOAP11_NAMESPACE);
    }
}

static class Detail1_1Name extends NameImpl {
    Detail1_1Name() {
        super("detail");
    }

    Detail1_1Name(String prefix) {
        super("detail", prefix, "");
    }
}

static class FaultElement1_1Name extends NameImpl {
    FaultElement1_1Name(String localName) {
        super(localName);
    }

    FaultElement1_1Name(String localName, String prefix) {
        super(localName, prefix, "");
    }
}

static class SOAP1_2Name extends NameImpl {
    SOAP1_2Name(String name, String prefix) {
        super(
            name,
            (prefix == null || prefix.equals(""))
                ? SOAPConstants.SOAP_ENV_PREFIX
                : prefix,
            SOAP12_NAMESPACE);
    }
}

static class Envelope1_2Name extends SOAP1_2Name {
    Envelope1_2Name(String prefix) {
        super("Envelope", prefix);
    }
}

static class Header1_2Name extends SOAP1_2Name {
    Header1_2Name(String prefix) {
        super("Header", prefix);
    }
}

static class Body1_2Name extends SOAP1_2Name {
    Body1_2Name(String prefix) {
        super("Body", prefix);
    }
}

static class Fault1_2Name extends NameImpl {
    Fault1_2Name(String name, String prefix) {
        super(
            (name == null || name.equals("")) ? "Fault" : name,
            (prefix == null || prefix.equals(""))
                ? SOAPConstants.SOAP_ENV_PREFIX
                : prefix,
            SOAP12_NAMESPACE);
    }
}

static class NotUnderstood1_2Name extends NameImpl {
    NotUnderstood1_2Name(String prefix) {
        super(
            "NotUnderstood",
            (prefix == null || prefix.equals(""))
                ? SOAPConstants.SOAP_ENV_PREFIX
                : prefix,
            SOAP12_NAMESPACE);
    }
}

static class Upgrade1_2Name extends NameImpl {
    Upgrade1_2Name(String prefix) {
        super(
            "Upgrade",
            (prefix == null || prefix.equals(""))
                ? SOAPConstants.SOAP_ENV_PREFIX
                : prefix,
            SOAP12_NAMESPACE);
    }
}

static class SupportedEnvelope1_2Name extends NameImpl {
    SupportedEnvelope1_2Name(String prefix) {
        super(
            "SupportedEnvelope",
            (prefix == null || prefix.equals(""))
                ? SOAPConstants.SOAP_ENV_PREFIX
                : prefix,
            SOAP12_NAMESPACE);
    }
}

static class CodeSubcode1_2Name extends SOAP1_2Name {
    CodeSubcode1_2Name(String prefix, String localName) {
        super(prefix, localName);
    }
}

}
