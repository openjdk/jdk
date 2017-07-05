/*
 * Portions Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * ===========================================================================
 *
 * (C) Copyright IBM Corp. 2003 All Rights Reserved.
 *
 * ===========================================================================
 */
/*
 * $Id: DOMReference.java,v 1.40 2005/09/19 18:27:04 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dom.DOMURIReference;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.jcp.xml.dsig.internal.DigesterOutputStream;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.UnsyncBufferedOutputStream;

/**
 * DOM-based implementation of Reference.
 *
 * @author Sean Mullan
 * @author Joyce Leung
 */
public final class DOMReference extends DOMStructure
    implements Reference, DOMURIReference {

    private static Logger log = Logger.getLogger("org.jcp.xml.dsig.internal.dom");

    private final DigestMethod digestMethod;
    private final String id;
    private final List appliedTransforms;
    private final List transforms;
    private final List allTransforms;
    private final Data appliedTransformData;
    private Attr here;
    private final String uri;
    private final String type;
    private byte[] digestValue;
    private byte[] calcDigestValue;
    private Element refElem;
    private boolean digested = false;
    private boolean validated = false;
    private boolean validationStatus;
    private Data derefData;
    private InputStream dis;
    private MessageDigest md;

    /**
     * Creates a <code>Reference</code> from the specified parameters.
     *
     * @param uri the URI (may be null)
     * @param type the type (may be null)
     * @param dm the digest method
     * @param transforms a list of {@link Transform}s. The list
     *    is defensively copied to protect against subsequent modification.
     *    May be <code>null</code> or empty.
     * @param id the reference ID (may be <code>null</code>)
     * @return a <code>Reference</code>
     * @throws NullPointerException if <code>dm</code> is <code>null</code>
     * @throws ClassCastException if any of the <code>transforms</code> are
     *    not of type <code>Transform</code>
     */
    public DOMReference(String uri, String type, DigestMethod dm,
        List transforms, String id) {
        this(uri, type, dm, null, null, transforms, id, null);
    }

    public DOMReference(String uri, String type, DigestMethod dm,
        List appliedTransforms, Data result, List transforms, String id) {
        this(uri, type, dm, appliedTransforms, result, transforms, id, null);
    }

    public DOMReference(String uri, String type, DigestMethod dm,
        List appliedTransforms, Data result, List transforms, String id,
        byte[] digestValue){
        if (dm == null) {
            throw new NullPointerException("DigestMethod must be non-null");
        }
        if (appliedTransforms == null || appliedTransforms.isEmpty()) {
            this.appliedTransforms = Collections.EMPTY_LIST;
        } else {
            List transformsCopy = new ArrayList(appliedTransforms);
            for (int i = 0, size = transformsCopy.size(); i < size; i++) {
                if (!(transformsCopy.get(i) instanceof Transform)) {
                    throw new ClassCastException
                        ("appliedTransforms["+i+"] is not a valid type");
                }
            }
            this.appliedTransforms =
                Collections.unmodifiableList(transformsCopy);
        }
        if (transforms == null || transforms.isEmpty()) {
            this.transforms = Collections.EMPTY_LIST;
        } else {
            List transformsCopy = new ArrayList(transforms);
            for (int i = 0, size = transformsCopy.size(); i < size; i++) {
                if (!(transformsCopy.get(i) instanceof Transform)) {
                    throw new ClassCastException
                        ("transforms["+i+"] is not a valid type");
                }
            }
            this.transforms = Collections.unmodifiableList(transformsCopy);
        }
        List all = new ArrayList(this.appliedTransforms);
        all.addAll(this.transforms);
        this.allTransforms = Collections.unmodifiableList(all);
        this.digestMethod = dm;
        this.uri = uri;
        if ((uri != null) && (!uri.equals(""))) {
            try {
                new URI(uri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        this.type = type;
        this.id = id;
        if (digestValue != null) {
            this.digestValue = (byte[]) digestValue.clone();
            this.digested = true;
        }
        this.appliedTransformData = result;
    }

    /**
     * Creates a <code>DOMReference</code> from an element.
     *
     * @param refElem a Reference element
     */
    public DOMReference(Element refElem, XMLCryptoContext context)
        throws MarshalException {
        // unmarshal Transforms, if specified
        Element nextSibling = DOMUtils.getFirstChildElement(refElem);
        List transforms = new ArrayList(5);
        if (nextSibling.getLocalName().equals("Transforms")) {
            Element transformElem = DOMUtils.getFirstChildElement(nextSibling);
            while (transformElem != null) {
                transforms.add(new DOMTransform(transformElem, context));
                transformElem = DOMUtils.getNextSiblingElement(transformElem);
            }
            nextSibling = DOMUtils.getNextSiblingElement(nextSibling);
        }

        // unmarshal DigestMethod
        Element dmElem = nextSibling;
        this.digestMethod = DOMDigestMethod.unmarshal(dmElem);

        // unmarshal DigestValue
        try {
            Element dvElem = DOMUtils.getNextSiblingElement(dmElem);
            this.digestValue = Base64.decode(dvElem);
        } catch (Base64DecodingException bde) {
            throw new MarshalException(bde);
        }

        // unmarshal attributes
        this.uri = DOMUtils.getAttributeValue(refElem, "URI");
        this.id = DOMUtils.getAttributeValue(refElem, "Id");

        this.type = DOMUtils.getAttributeValue(refElem, "Type");
        this.here = refElem.getAttributeNodeNS(null, "URI");
        this.refElem = refElem;

        if (transforms.isEmpty()) {
            this.transforms = Collections.EMPTY_LIST;
        } else {
            this.transforms = Collections.unmodifiableList(transforms);
        }
        this.appliedTransforms = Collections.EMPTY_LIST;
        this.allTransforms = transforms;
        this.appliedTransformData = null;
    }

    public DigestMethod getDigestMethod() {
        return digestMethod;
    }

    public String getId() {
        return id;
    }

    public String getURI() {
        return uri;
    }

    public String getType() {
        return type;
    }

    public List getTransforms() {
        return allTransforms;
    }

    public byte[] getDigestValue() {
        return (digestValue == null ? null : (byte[]) digestValue.clone());
    }

    public byte[] getCalculatedDigestValue() {
        return (calcDigestValue == null ? null
                : (byte[]) calcDigestValue.clone());
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Marshalling Reference");
        }
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        refElem = DOMUtils.createElement
            (ownerDoc, "Reference", XMLSignature.XMLNS, dsPrefix);

        // set attributes
        DOMUtils.setAttributeID(refElem, "Id", id);
        DOMUtils.setAttribute(refElem, "URI", uri);
        DOMUtils.setAttribute(refElem, "Type", type);

        // create and append Transforms element
        if (!transforms.isEmpty() || !appliedTransforms.isEmpty()) {
            Element transformsElem = DOMUtils.createElement
                (ownerDoc, "Transforms", XMLSignature.XMLNS, dsPrefix);
            refElem.appendChild(transformsElem);
            for (int i = 0, size = appliedTransforms.size(); i < size; i++) {
                DOMStructure transform =
                    (DOMStructure) appliedTransforms.get(i);
                transform.marshal(transformsElem, dsPrefix, context);
            }
            for (int i = 0, size = transforms.size(); i < size; i++) {
                DOMStructure transform = (DOMStructure) transforms.get(i);
                transform.marshal(transformsElem, dsPrefix, context);
            }
        }

        // create and append DigestMethod element
        ((DOMDigestMethod) digestMethod).marshal(refElem, dsPrefix, context);

        // create and append DigestValue element
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Adding digestValueElem");
        }
        Element digestValueElem = DOMUtils.createElement
            (ownerDoc, "DigestValue", XMLSignature.XMLNS, dsPrefix);
        if (digestValue != null) {
            digestValueElem.appendChild
                (ownerDoc.createTextNode(Base64.encode(digestValue)));
        }
        refElem.appendChild(digestValueElem);

        parent.appendChild(refElem);
        here = refElem.getAttributeNodeNS(null, "URI");
    }

    public void digest(XMLSignContext signContext)
        throws XMLSignatureException {
        Data data = null;
        if (appliedTransformData == null) {
            data = dereference(signContext);
        } else {
            data = appliedTransformData;
        }
        digestValue = transform(data, signContext);

        // insert digestValue into DigestValue element
        String encodedDV = Base64.encode(digestValue);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Reference object uri = " + uri);
        }
        Element digestElem = DOMUtils.getLastChildElement(refElem);
        if (digestElem == null) {
            throw new XMLSignatureException("DigestValue element expected");
        }
        DOMUtils.removeAllChildren(digestElem);
        digestElem.appendChild
            (refElem.getOwnerDocument().createTextNode(encodedDV));

        digested = true;
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Reference digesting completed");
        }
    }

    public boolean validate(XMLValidateContext validateContext)
        throws XMLSignatureException {
        if (validateContext == null) {
            throw new NullPointerException("validateContext cannot be null");
        }
        if (validated) {
            return validationStatus;
        }
        Data data = dereference(validateContext);
        calcDigestValue = transform(data, validateContext);

        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Expected digest: "
                + Base64.encode(digestValue));
            log.log(Level.FINE, "Actual digest: "
                + Base64.encode(calcDigestValue));
        }

        validationStatus = Arrays.equals(digestValue, calcDigestValue);
        validated = true;
        return validationStatus;
    }

    public Data getDereferencedData() {
        return derefData;
    }

    public InputStream getDigestInputStream() {
        return dis;
    }

    private Data dereference(XMLCryptoContext context)
        throws XMLSignatureException {
        Data data = null;

        // use user-specified URIDereferencer if specified; otherwise use deflt
        URIDereferencer deref = context.getURIDereferencer();
        if (deref == null) {
            deref = DOMURIDereferencer.INSTANCE;
        }
        try {
            data = deref.dereference(this, context);
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "URIDereferencer class name: "
                    + deref.getClass().getName());
                log.log(Level.FINE, "Data class name: "
                    + data.getClass().getName());
            }
        } catch (URIReferenceException ure) {
            throw new XMLSignatureException(ure);
        }

        return data;
    }

    private byte[] transform(Data dereferencedData,
        XMLCryptoContext context) throws XMLSignatureException {

        if (md == null) {
            try {
                md = MessageDigest.getInstance
                    (((DOMDigestMethod) digestMethod).getMessageDigestAlgorithm());
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        md.reset();
        DigesterOutputStream dos;
        Boolean cache = (Boolean)
            context.getProperty("javax.xml.crypto.dsig.cacheReference");
        if (cache != null && cache.booleanValue() == true) {
            this.derefData = copyDerefData(dereferencedData);
            dos = new DigesterOutputStream(md, true);
        } else {
            dos = new DigesterOutputStream(md);
        }
        OutputStream os = new UnsyncBufferedOutputStream(dos);
        Data data = dereferencedData;
        for (int i = 0, size = transforms.size(); i < size; i++) {
            DOMTransform transform = (DOMTransform) transforms.get(i);
            try {
                if (i < size - 1) {
                    data = transform.transform(data, context);
                } else {
                    data = transform.transform(data, context, os);
                }
            } catch (TransformException te) {
                throw new XMLSignatureException(te);
            }
        }

        try {
            if (data != null) {
                XMLSignatureInput xi;
                if (data instanceof ApacheData) {
                    xi = ((ApacheData) data).getXMLSignatureInput();
                } else if (data instanceof OctetStreamData) {
                    xi = new XMLSignatureInput
                        (((OctetStreamData)data).getOctetStream());
                } else if (data instanceof NodeSetData) {
                    TransformService spi = TransformService.getInstance
                        (CanonicalizationMethod.INCLUSIVE, "DOM");
                    data = spi.transform(data, context);
                    xi = new XMLSignatureInput
                        (((OctetStreamData)data).getOctetStream());
                } else {
                    throw new XMLSignatureException("unrecognized Data type");
                }
                xi.updateOutputStream(os);
            }
            os.flush();
            if (cache != null && cache.booleanValue() == true) {
                this.dis = dos.getInputStream();
            }
            return dos.getDigestValue();
        } catch (Exception e) {
            throw new XMLSignatureException(e);
        }
    }

    public Node getHere() {
        return here;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Reference)) {
            return false;
        }
        Reference oref = (Reference) o;

        boolean idsEqual = (id == null ? oref.getId() == null :
            id.equals(oref.getId()));
        boolean urisEqual = (uri == null ? oref.getURI() == null :
            uri.equals(oref.getURI()));
        boolean typesEqual = (type == null ? oref.getType() == null :
            type.equals(oref.getType()));
        boolean digestValuesEqual =
            Arrays.equals(digestValue, oref.getDigestValue());

        return (digestMethod.equals(oref.getDigestMethod()) && idsEqual &&
            urisEqual && typesEqual && transforms.equals(oref.getTransforms()));
    }

    boolean isDigested() {
        return digested;
    }

    private static Data copyDerefData(Data dereferencedData) {
        if (dereferencedData instanceof ApacheData) {
            // need to make a copy of the Data
            ApacheData ad = (ApacheData) dereferencedData;
            XMLSignatureInput xsi = ad.getXMLSignatureInput();
            if (xsi.isNodeSet()) {
                try {
                    final Set s = xsi.getNodeSet();
                    return new NodeSetData() {
                        public Iterator iterator() { return s.iterator(); }
                    };
                } catch (Exception e) {
                    // log a warning
                    log.log(Level.WARNING,
                        "cannot cache dereferenced data: " + e);
                    return null;
                }
            } else if (xsi.isElement()) {
                return new DOMSubTreeData
                    (xsi.getSubNode(), xsi.isExcludeComments());
            } else if (xsi.isOctetStream() || xsi.isByteArray()) {
                try {
                return new OctetStreamData
                  (xsi.getOctetStream(), xsi.getSourceURI(), xsi.getMIMEType());
                } catch (IOException ioe) {
                    // log a warning
                    log.log(Level.WARNING,
                        "cannot cache dereferenced data: " + ioe);
                    return null;
                }
            }
        }
        return dereferencedData;
    }
}
