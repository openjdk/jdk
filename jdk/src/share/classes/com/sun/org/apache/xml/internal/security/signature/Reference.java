/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.signature;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.reference.ReferenceData;
import com.sun.org.apache.xml.internal.security.signature.reference.ReferenceNodeSetData;
import com.sun.org.apache.xml.internal.security.signature.reference.ReferenceOctetStreamData;
import com.sun.org.apache.xml.internal.security.signature.reference.ReferenceSubTreeData;
import com.sun.org.apache.xml.internal.security.transforms.InvalidTransformException;
import com.sun.org.apache.xml.internal.security.transforms.Transform;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.DigesterOutputStream;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.UnsyncBufferedOutputStream;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Handles <code>&lt;ds:Reference&gt;</code> elements.
 *
 * This includes:
 *
 * Constructs a <CODE>ds:Reference</CODE> from an {@link org.w3c.dom.Element}.
 *
 * <p>Create a new reference</p>
 * <pre>
 * Document doc;
 * MessageDigestAlgorithm sha1 = MessageDigestAlgorithm.getInstance("http://#sha1");
 * Reference ref = new Reference(new XMLSignatureInput(new FileInputStream("1.gif"),
 *                               "http://localhost/1.gif",
 *                               (Transforms) null, sha1);
 * Element refElem = ref.toElement(doc);
 * </pre>
 *
 * <p>Verify a reference</p>
 * <pre>
 * Element refElem = doc.getElement("Reference"); // PSEUDO
 * Reference ref = new Reference(refElem);
 * String url = ref.getURI();
 * ref.setData(new XMLSignatureInput(new FileInputStream(url)));
 * if (ref.verify()) {
 *    System.out.println("verified");
 * }
 * </pre>
 *
 * <pre>
 * &lt;element name="Reference" type="ds:ReferenceType"/&gt;
 *  &lt;complexType name="ReferenceType"&gt;
 *    &lt;sequence&gt;
 *      &lt;element ref="ds:Transforms" minOccurs="0"/&gt;
 *      &lt;element ref="ds:DigestMethod"/&gt;
 *      &lt;element ref="ds:DigestValue"/&gt;
 *    &lt;/sequence&gt;
 *    &lt;attribute name="Id" type="ID" use="optional"/&gt;
 *    &lt;attribute name="URI" type="anyURI" use="optional"/&gt;
 *    &lt;attribute name="Type" type="anyURI" use="optional"/&gt;
 *  &lt;/complexType&gt;
 * </pre>
 *
 * @author Christian Geuer-Pollmann
 * @see ObjectContainer
 * @see Manifest
 */
public class Reference extends SignatureElementProxy {

    /** Field OBJECT_URI */
    public static final String OBJECT_URI = Constants.SignatureSpecNS + Constants._TAG_OBJECT;

    /** Field MANIFEST_URI */
    public static final String MANIFEST_URI = Constants.SignatureSpecNS + Constants._TAG_MANIFEST;

    /**
     * The maximum number of transforms per reference, if secure validation is enabled.
     */
    public static final int MAXIMUM_TRANSFORM_COUNT = 5;

    private boolean secureValidation;

    /**
     * Look up useC14N11 system property. If true, an explicit C14N11 transform
     * will be added if necessary when generating the signature. See section
     * 3.1.1 of http://www.w3.org/2007/xmlsec/Drafts/xmldsig-core/ for more info.
     */
    private static boolean useC14N11 = (
        AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return Boolean.valueOf(Boolean.getBoolean("com.sun.org.apache.xml.internal.security.useC14N11"));
            }
        })).booleanValue();

    /** {@link org.apache.commons.logging} logging facility */
    private static final java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(Reference.class.getName());

    private Manifest manifest;
    private XMLSignatureInput transformsOutput;

    private Transforms transforms;

    private Element digestMethodElem;

    private Element digestValueElement;

    private ReferenceData referenceData;

    /**
     * Constructor Reference
     *
     * @param doc the {@link Document} in which <code>XMLsignature</code> is placed
     * @param baseURI the URI of the resource where the XML instance will be stored
     * @param referenceURI URI indicate where is data which will digested
     * @param manifest
     * @param transforms {@link Transforms} applied to data
     * @param messageDigestAlgorithm {@link MessageDigestAlgorithm Digest algorithm} which is
     * applied to the data
     * TODO should we throw XMLSignatureException if MessageDigestAlgoURI is wrong?
     * @throws XMLSignatureException
     */
    protected Reference(
        Document doc, String baseURI, String referenceURI, Manifest manifest,
        Transforms transforms, String messageDigestAlgorithm
    ) throws XMLSignatureException {
        super(doc);

        XMLUtils.addReturnToElement(this.constructionElement);

        this.baseURI = baseURI;
        this.manifest = manifest;

        this.setURI(referenceURI);

        // important: The ds:Reference must be added to the associated ds:Manifest
        //            or ds:SignedInfo _before_ the this.resolverResult() is called.
        // this.manifest.appendChild(this.constructionElement);
        // this.manifest.appendChild(this.doc.createTextNode("\n"));

        if (transforms != null) {
            this.transforms=transforms;
            this.constructionElement.appendChild(transforms.getElement());
            XMLUtils.addReturnToElement(this.constructionElement);
        }
        MessageDigestAlgorithm mda =
            MessageDigestAlgorithm.getInstance(this.doc, messageDigestAlgorithm);

        digestMethodElem = mda.getElement();
        this.constructionElement.appendChild(digestMethodElem);
        XMLUtils.addReturnToElement(this.constructionElement);

        digestValueElement =
            XMLUtils.createElementInSignatureSpace(this.doc, Constants._TAG_DIGESTVALUE);

        this.constructionElement.appendChild(digestValueElement);
        XMLUtils.addReturnToElement(this.constructionElement);
    }


    /**
     * Build a {@link Reference} from an {@link Element}
     *
     * @param element <code>Reference</code> element
     * @param baseURI the URI of the resource where the XML instance was stored
     * @param manifest is the {@link Manifest} of {@link SignedInfo} in which the Reference occurs.
     * We need this because the Manifest has the individual {@link ResourceResolver}s which have
     * been set by the user
     * @throws XMLSecurityException
     */
    protected Reference(Element element, String baseURI, Manifest manifest) throws XMLSecurityException {
        this(element, baseURI, manifest, false);
    }

    /**
     * Build a {@link Reference} from an {@link Element}
     *
     * @param element <code>Reference</code> element
     * @param baseURI the URI of the resource where the XML instance was stored
     * @param manifest is the {@link Manifest} of {@link SignedInfo} in which the Reference occurs.
     * @param secureValidation whether secure validation is enabled or not
     * We need this because the Manifest has the individual {@link ResourceResolver}s which have
     * been set by the user
     * @throws XMLSecurityException
     */
    protected Reference(Element element, String baseURI, Manifest manifest, boolean secureValidation)
        throws XMLSecurityException {
        super(element, baseURI);
        this.secureValidation = secureValidation;
        this.baseURI = baseURI;
        Element el = XMLUtils.getNextElement(element.getFirstChild());
        if (Constants._TAG_TRANSFORMS.equals(el.getLocalName())
            && Constants.SignatureSpecNS.equals(el.getNamespaceURI())) {
            transforms = new Transforms(el, this.baseURI);
            transforms.setSecureValidation(secureValidation);
            if (secureValidation && transforms.getLength() > MAXIMUM_TRANSFORM_COUNT) {
                Object exArgs[] = { transforms.getLength(), MAXIMUM_TRANSFORM_COUNT };

                throw new XMLSecurityException("signature.tooManyTransforms", exArgs);
            }
            el = XMLUtils.getNextElement(el.getNextSibling());
        }
        digestMethodElem = el;
        digestValueElement = XMLUtils.getNextElement(digestMethodElem.getNextSibling());
        this.manifest = manifest;
    }

    /**
     * Returns {@link MessageDigestAlgorithm}
     *
     *
     * @return {@link MessageDigestAlgorithm}
     *
     * @throws XMLSignatureException
     */
    public MessageDigestAlgorithm getMessageDigestAlgorithm() throws XMLSignatureException {
        if (digestMethodElem == null) {
            return null;
        }

        String uri = digestMethodElem.getAttributeNS(null, Constants._ATT_ALGORITHM);

        if (uri == null) {
            return null;
        }

        if (secureValidation && MessageDigestAlgorithm.ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5.equals(uri)) {
            Object exArgs[] = { uri };

            throw new XMLSignatureException("signature.signatureAlgorithm", exArgs);
        }

        return MessageDigestAlgorithm.getInstance(this.doc, uri);
    }

    /**
     * Sets the <code>URI</code> of this <code>Reference</code> element
     *
     * @param uri the <code>URI</code> of this <code>Reference</code> element
     */
    public void setURI(String uri) {
        if (uri != null) {
            this.constructionElement.setAttributeNS(null, Constants._ATT_URI, uri);
        }
    }

    /**
     * Returns the <code>URI</code> of this <code>Reference</code> element
     *
     * @return URI the <code>URI</code> of this <code>Reference</code> element
     */
    public String getURI() {
        return this.constructionElement.getAttributeNS(null, Constants._ATT_URI);
    }

    /**
     * Sets the <code>Id</code> attribute of this <code>Reference</code> element
     *
     * @param id the <code>Id</code> attribute of this <code>Reference</code> element
     */
    public void setId(String id) {
        if (id != null) {
            this.constructionElement.setAttributeNS(null, Constants._ATT_ID, id);
            this.constructionElement.setIdAttributeNS(null, Constants._ATT_ID, true);
        }
    }

    /**
     * Returns the <code>Id</code> attribute of this <code>Reference</code> element
     *
     * @return Id the <code>Id</code> attribute of this <code>Reference</code> element
     */
    public String getId() {
        return this.constructionElement.getAttributeNS(null, Constants._ATT_ID);
    }

    /**
     * Sets the <code>type</code> atttibute of the Reference indicate whether an
     * <code>ds:Object</code>, <code>ds:SignatureProperty</code>, or <code>ds:Manifest</code>
     * element.
     *
     * @param type the <code>type</code> attribute of the Reference
     */
    public void setType(String type) {
        if (type != null) {
            this.constructionElement.setAttributeNS(null, Constants._ATT_TYPE, type);
        }
    }

    /**
     * Return the <code>type</code> atttibute of the Reference indicate whether an
     * <code>ds:Object</code>, <code>ds:SignatureProperty</code>, or <code>ds:Manifest</code>
     * element
     *
     * @return the <code>type</code> attribute of the Reference
     */
    public String getType() {
        return this.constructionElement.getAttributeNS(null, Constants._ATT_TYPE);
    }

    /**
     * Method isReferenceToObject
     *
     * This returns true if the <CODE>Type</CODE> attribute of the
     * <CODE>Reference</CODE> element points to a <CODE>#Object</CODE> element
     *
     * @return true if the Reference type indicates that this Reference points to an
     * <code>Object</code>
     */
    public boolean typeIsReferenceToObject() {
        if (Reference.OBJECT_URI.equals(this.getType())) {
            return true;
        }

        return false;
    }

    /**
     * Method isReferenceToManifest
     *
     * This returns true if the <CODE>Type</CODE> attribute of the
     * <CODE>Reference</CODE> element points to a <CODE>#Manifest</CODE> element
     *
     * @return true if the Reference type indicates that this Reference points to a
     * {@link Manifest}
     */
    public boolean typeIsReferenceToManifest() {
        if (Reference.MANIFEST_URI.equals(this.getType())) {
            return true;
        }

        return false;
    }

    /**
     * Method setDigestValueElement
     *
     * @param digestValue
     */
    private void setDigestValueElement(byte[] digestValue) {
        Node n = digestValueElement.getFirstChild();
        while (n != null) {
            digestValueElement.removeChild(n);
            n = n.getNextSibling();
        }

        String base64codedValue = Base64.encode(digestValue);
        Text t = this.doc.createTextNode(base64codedValue);

        digestValueElement.appendChild(t);
    }

    /**
     * Method generateDigestValue
     *
     * @throws ReferenceNotInitializedException
     * @throws XMLSignatureException
     */
    public void generateDigestValue()
        throws XMLSignatureException, ReferenceNotInitializedException {
        this.setDigestValueElement(this.calculateDigest(false));
    }

    /**
     * Returns the XMLSignatureInput which is created by de-referencing the URI attribute.
     * @return the XMLSignatureInput of the source of this reference
     * @throws ReferenceNotInitializedException If the resolver found any
     * problem resolving the reference
     */
    public XMLSignatureInput getContentsBeforeTransformation()
        throws ReferenceNotInitializedException {
        try {
            Attr uriAttr =
                this.constructionElement.getAttributeNodeNS(null, Constants._ATT_URI);

            ResourceResolver resolver =
                ResourceResolver.getInstance(
                    uriAttr, this.baseURI, this.manifest.getPerManifestResolvers(), secureValidation
                );
            resolver.addProperties(this.manifest.getResolverProperties());

            return resolver.resolve(uriAttr, this.baseURI, secureValidation);
        }  catch (ResourceResolverException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        }
    }

    private XMLSignatureInput getContentsAfterTransformation(
        XMLSignatureInput input, OutputStream os
    ) throws XMLSignatureException {
        try {
            Transforms transforms = this.getTransforms();
            XMLSignatureInput output = null;

            if (transforms != null) {
                output = transforms.performTransforms(input, os);
                this.transformsOutput = output;//new XMLSignatureInput(output.getBytes());

                //this.transformsOutput.setSourceURI(output.getSourceURI());
            } else {
                output = input;
            }

            return output;
        } catch (ResourceResolverException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (CanonicalizationException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (InvalidCanonicalizerException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (TransformationException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (XMLSecurityException ex) {
            throw new XMLSignatureException("empty", ex);
        }
    }

    /**
     * Returns the XMLSignatureInput which is the result of the Transforms.
     * @return a XMLSignatureInput with all transformations applied.
     * @throws XMLSignatureException
     */
    public XMLSignatureInput getContentsAfterTransformation()
        throws XMLSignatureException {
        XMLSignatureInput input = this.getContentsBeforeTransformation();
        cacheDereferencedElement(input);

        return this.getContentsAfterTransformation(input, null);
    }

    /**
     * This method returns the XMLSignatureInput which represents the node set before
     * some kind of canonicalization is applied for the first time.
     * @return Gets a the node doing everything till the first c14n is needed
     *
     * @throws XMLSignatureException
     */
    public XMLSignatureInput getNodesetBeforeFirstCanonicalization()
        throws XMLSignatureException {
        try {
            XMLSignatureInput input = this.getContentsBeforeTransformation();
            cacheDereferencedElement(input);
            XMLSignatureInput output = input;
            Transforms transforms = this.getTransforms();

            if (transforms != null) {
                doTransforms: for (int i = 0; i < transforms.getLength(); i++) {
                    Transform t = transforms.item(i);
                    String uri = t.getURI();

                    if (uri.equals(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS)
                        || uri.equals(Transforms.TRANSFORM_C14N_EXCL_WITH_COMMENTS)
                        || uri.equals(Transforms.TRANSFORM_C14N_OMIT_COMMENTS)
                        || uri.equals(Transforms.TRANSFORM_C14N_WITH_COMMENTS)) {
                        break doTransforms;
                    }

                    output = t.performTransform(output, null);
                }

            output.setSourceURI(input.getSourceURI());
            }
            return output;
        } catch (IOException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (ResourceResolverException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (CanonicalizationException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (InvalidCanonicalizerException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (TransformationException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (XMLSecurityException ex) {
            throw new XMLSignatureException("empty", ex);
        }
    }

    /**
     * Method getHTMLRepresentation
     * @return The HTML of the transformation
     * @throws XMLSignatureException
     */
    public String getHTMLRepresentation() throws XMLSignatureException {
        try {
            XMLSignatureInput nodes = this.getNodesetBeforeFirstCanonicalization();

            Transforms transforms = this.getTransforms();
            Transform c14nTransform = null;

            if (transforms != null) {
                doTransforms: for (int i = 0; i < transforms.getLength(); i++) {
                    Transform t = transforms.item(i);
                    String uri = t.getURI();

                    if (uri.equals(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS)
                        || uri.equals(Transforms.TRANSFORM_C14N_EXCL_WITH_COMMENTS)) {
                        c14nTransform = t;
                        break doTransforms;
                    }
                }
            }

            Set<String> inclusiveNamespaces = new HashSet<String>();
            if (c14nTransform != null
                && (c14nTransform.length(
                    InclusiveNamespaces.ExclusiveCanonicalizationNamespace,
                    InclusiveNamespaces._TAG_EC_INCLUSIVENAMESPACES) == 1)) {

                // there is one InclusiveNamespaces element
                InclusiveNamespaces in =
                    new InclusiveNamespaces(
                        XMLUtils.selectNode(
                            c14nTransform.getElement().getFirstChild(),
                            InclusiveNamespaces.ExclusiveCanonicalizationNamespace,
                            InclusiveNamespaces._TAG_EC_INCLUSIVENAMESPACES,
                            0
                        ), this.getBaseURI());

                inclusiveNamespaces =
                    InclusiveNamespaces.prefixStr2Set(in.getInclusiveNamespaces());
            }

            return nodes.getHTMLRepresentation(inclusiveNamespaces);
        } catch (TransformationException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (InvalidTransformException ex) {
            throw new XMLSignatureException("empty", ex);
        } catch (XMLSecurityException ex) {
            throw new XMLSignatureException("empty", ex);
        }
    }

    /**
     * This method only works works after a call to verify.
     * @return the transformed output(i.e. what is going to be digested).
     */
    public XMLSignatureInput getTransformsOutput() {
        return this.transformsOutput;
    }

    /**
     * Get the ReferenceData that corresponds to the cached representation of the dereferenced
     * object before transformation.
     */
    public ReferenceData getReferenceData() {
        return referenceData;
    }

    /**
     * This method returns the {@link XMLSignatureInput} which is referenced by the
     * <CODE>URI</CODE> Attribute.
     * @param os where to write the transformation can be null.
     * @return the element to digest
     *
     * @throws XMLSignatureException
     * @see Manifest#verifyReferences()
     */
    protected XMLSignatureInput dereferenceURIandPerformTransforms(OutputStream os)
        throws XMLSignatureException {
        try {
            XMLSignatureInput input = this.getContentsBeforeTransformation();
            cacheDereferencedElement(input);

            XMLSignatureInput output = this.getContentsAfterTransformation(input, os);
            this.transformsOutput = output;
            return output;
        } catch (XMLSecurityException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        }
    }

    /**
     * Store the dereferenced Element(s) so that it/they can be retrieved later.
     */
    private void cacheDereferencedElement(XMLSignatureInput input) {
        if (input.isNodeSet()) {
            try {
                final Set<Node> s = input.getNodeSet();
                referenceData = new ReferenceNodeSetData() {
                    public Iterator<Node> iterator() {
                        return new Iterator<Node>() {

                            Iterator<Node> sIterator = s.iterator();

                            public boolean hasNext() {
                                return sIterator.hasNext();
                            }

                            public Node next() {
                                return sIterator.next();
                            }

                            public void remove() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
            } catch (Exception e) {
                // log a warning
                log.log(java.util.logging.Level.WARNING, "cannot cache dereferenced data: " + e);
            }
        } else if (input.isElement()) {
            referenceData = new ReferenceSubTreeData
                (input.getSubNode(), input.isExcludeComments());
        } else if (input.isOctetStream() || input.isByteArray()) {
            try {
                referenceData = new ReferenceOctetStreamData
                    (input.getOctetStream(), input.getSourceURI(),
                        input.getMIMEType());
            } catch (IOException ioe) {
                // log a warning
                log.log(java.util.logging.Level.WARNING, "cannot cache dereferenced data: " + ioe);
            }
        }
    }

    /**
     * Method getTransforms
     *
     * @return The transforms that applied this reference.
     * @throws InvalidTransformException
     * @throws TransformationException
     * @throws XMLSecurityException
     * @throws XMLSignatureException
     */
    public Transforms getTransforms()
        throws XMLSignatureException, InvalidTransformException,
        TransformationException, XMLSecurityException {
        return transforms;
    }

    /**
     * Method getReferencedBytes
     *
     * @return the bytes that will be used to generated digest.
     * @throws ReferenceNotInitializedException
     * @throws XMLSignatureException
     */
    public byte[] getReferencedBytes()
        throws ReferenceNotInitializedException, XMLSignatureException {
        try {
            XMLSignatureInput output = this.dereferenceURIandPerformTransforms(null);
            return output.getBytes();
        } catch (IOException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        } catch (CanonicalizationException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        }
    }


    /**
     * Method calculateDigest
     *
     * @param validating true if validating the reference
     * @return reference Calculate the digest of this reference.
     * @throws ReferenceNotInitializedException
     * @throws XMLSignatureException
     */
    private byte[] calculateDigest(boolean validating)
        throws ReferenceNotInitializedException, XMLSignatureException {
        OutputStream os = null;
        try {
            MessageDigestAlgorithm mda = this.getMessageDigestAlgorithm();

            mda.reset();
            DigesterOutputStream diOs = new DigesterOutputStream(mda);
            os = new UnsyncBufferedOutputStream(diOs);
            XMLSignatureInput output = this.dereferenceURIandPerformTransforms(os);
            // if signing and c14n11 property == true explicitly add
            // C14N11 transform if needed
            if (Reference.useC14N11 && !validating && !output.isOutputStreamSet()
                && !output.isOctetStream()) {
                if (transforms == null) {
                    transforms = new Transforms(this.doc);
                    transforms.setSecureValidation(secureValidation);
                    this.constructionElement.insertBefore(transforms.getElement(), digestMethodElem);
                }
                transforms.addTransform(Transforms.TRANSFORM_C14N11_OMIT_COMMENTS);
                output.updateOutputStream(os, true);
            } else {
                output.updateOutputStream(os);
            }
            os.flush();

            if (output.getOctetStreamReal() != null) {
                output.getOctetStreamReal().close();
            }

            //this.getReferencedBytes(diOs);
            //mda.update(data);

            return diOs.getDigestValue();
        } catch (XMLSecurityException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        } catch (IOException ex) {
            throw new ReferenceNotInitializedException("empty", ex);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ex) {
                    throw new ReferenceNotInitializedException("empty", ex);
                }
            }
        }
    }

    /**
     * Returns the digest value.
     *
     * @return the digest value.
     * @throws Base64DecodingException if Reference contains no proper base64 encoded data.
     * @throws XMLSecurityException if the Reference does not contain a DigestValue element
     */
    public byte[] getDigestValue() throws Base64DecodingException, XMLSecurityException {
        if (digestValueElement == null) {
            // The required element is not in the XML!
            Object[] exArgs ={ Constants._TAG_DIGESTVALUE, Constants.SignatureSpecNS };
            throw new XMLSecurityException(
                "signature.Verification.NoSignatureElement", exArgs
            );
        }
        return Base64.decode(digestValueElement);
    }


    /**
     * Tests reference validation is success or false
     *
     * @return true if reference validation is success, otherwise false
     * @throws ReferenceNotInitializedException
     * @throws XMLSecurityException
     */
    public boolean verify()
        throws ReferenceNotInitializedException, XMLSecurityException {
        byte[] elemDig = this.getDigestValue();
        byte[] calcDig = this.calculateDigest(true);
        boolean equal = MessageDigestAlgorithm.isEqual(elemDig, calcDig);

        if (!equal) {
            log.log(java.util.logging.Level.WARNING, "Verification failed for URI \"" + this.getURI() + "\"");
            log.log(java.util.logging.Level.WARNING, "Expected Digest: " + Base64.encode(elemDig));
            log.log(java.util.logging.Level.WARNING, "Actual Digest: " + Base64.encode(calcDig));
        } else {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "Verification successful for URI \"" + this.getURI() + "\"");
            }
        }

        return equal;
    }

    /**
     * Method getBaseLocalName
     * @inheritDoc
     */
    public String getBaseLocalName() {
        return Constants._TAG_REFERENCE;
    }
}
