/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.signature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Handles <code>&lt;ds:SignedInfo&gt;</code> elements
 * This <code>SignedInfo<code> element includes the canonicalization algorithm,
 * a signature algorithm, and one or more references.
 *
 * @author Christian Geuer-Pollmann
 */
public class SignedInfo extends Manifest {

    /** Field _signatureAlgorithm */
    private SignatureAlgorithm _signatureAlgorithm = null;

    /** Field _c14nizedBytes           */
    private byte[] _c14nizedBytes = null;

    private Element c14nMethod;
    private Element signatureMethod;

    /**
     * Overwrites {@link Manifest#addDocument} because it creates another
     * Element.
     *
     * @param doc the {@link Document} in which <code>XMLsignature</code> will
     *    be placed
     * @throws XMLSecurityException
     */
    public SignedInfo(Document doc) throws XMLSecurityException {
        this(doc, XMLSignature.ALGO_ID_SIGNATURE_DSA,
             Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
    }

    /**
     * Constructs {@link SignedInfo} using given Canonicalization algorithm and
     * Signature algorithm.
     *
     * @param doc <code>SignedInfo</code> is placed in this document
     * @param signatureMethodURI URI representation of the Digest and
     *    Signature algorithm
     * @param canonicalizationMethodURI URI representation of the
     *    Canonicalization method
     * @throws XMLSecurityException
     */
    public SignedInfo(Document doc, String signatureMethodURI,
        String canonicalizationMethodURI)
              throws XMLSecurityException {
        this(doc, signatureMethodURI, 0, canonicalizationMethodURI);
    }

    /**
     * Constructor SignedInfo
     *
     * @param doc <code>SignedInfo</code> is placed in this document
     * @param signatureMethodURI URI representation of the Digest and
     *    Signature algorithm
     * @param hMACOutputLength
     * @param canonicalizationMethodURI URI representation of the
     *    Canonicalization method
     * @throws XMLSecurityException
     */
    public SignedInfo(Document doc, String signatureMethodURI,
        int hMACOutputLength, String canonicalizationMethodURI)
              throws XMLSecurityException {

        super(doc);

        c14nMethod = XMLUtils.createElementInSignatureSpace(this._doc,
                                Constants._TAG_CANONICALIZATIONMETHOD);

        c14nMethod.setAttributeNS(null, Constants._ATT_ALGORITHM,
                                  canonicalizationMethodURI);
        this._constructionElement.appendChild(c14nMethod);
        XMLUtils.addReturnToElement(this._constructionElement);

        if (hMACOutputLength > 0) {
            this._signatureAlgorithm = new SignatureAlgorithm(this._doc,
                    signatureMethodURI, hMACOutputLength);
        } else {
            this._signatureAlgorithm = new SignatureAlgorithm(this._doc,
                    signatureMethodURI);
        }

        signatureMethod = this._signatureAlgorithm.getElement();
        this._constructionElement.appendChild(signatureMethod);
        XMLUtils.addReturnToElement(this._constructionElement);
    }

    /**
     * @param doc
     * @param signatureMethodElem
     * @param canonicalizationMethodElem
     * @throws XMLSecurityException
     */
    public SignedInfo(Document doc, Element signatureMethodElem,
        Element canonicalizationMethodElem) throws XMLSecurityException {

        super(doc);
        // Check this?
        this.c14nMethod = canonicalizationMethodElem;
        this._constructionElement.appendChild(c14nMethod);
        XMLUtils.addReturnToElement(this._constructionElement);

        this._signatureAlgorithm =
            new SignatureAlgorithm(signatureMethodElem, null);

        signatureMethod = this._signatureAlgorithm.getElement();
        this._constructionElement.appendChild(signatureMethod);

        XMLUtils.addReturnToElement(this._constructionElement);
    }

    /**
     * Build a {@link SignedInfo} from an {@link Element}
     *
     * @param element <code>SignedInfo</code>
     * @param baseURI the URI of the resource where the XML instance was stored
     * @throws XMLSecurityException
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0033.html">Question</A>
     * @see <A HREF="http://lists.w3.org/Archives/Public/w3c-ietf-xmldsig/2001OctDec/0054.html">Answer</A>
     */
    public SignedInfo(Element element, String baseURI)
           throws XMLSecurityException {

        // Parse the Reference children and Id attribute in the Manifest
        super(element, baseURI);

        /* canonicalize ds:SignedInfo, reparse it into a new document
         * and replace the original not-canonicalized ds:SignedInfo by
         * the re-parsed canonicalized one.
         */
        c14nMethod = XMLUtils.getNextElement(element.getFirstChild());
        String c14nMethodURI = this.getCanonicalizationMethodURI();
        if (!(c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS) ||
              c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS) ||
              c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS) ||
              c14nMethodURI.equals(Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS))) {
            // the c14n is not a secure one and can rewrite the URIs or like
            // that reparse the SignedInfo to be sure
            try {
                Canonicalizer c14nizer =
                Canonicalizer.getInstance(this.getCanonicalizationMethodURI());

                this._c14nizedBytes =
                    c14nizer.canonicalizeSubtree(this._constructionElement);
                javax.xml.parsers.DocumentBuilderFactory dbf =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                               Boolean.TRUE);
                javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
                Document newdoc =
                    db.parse(new ByteArrayInputStream(this._c14nizedBytes));
                Node imported =
                    this._doc.importNode(newdoc.getDocumentElement(), true);

                this._constructionElement.getParentNode().replaceChild(imported,
                    this._constructionElement);

                this._constructionElement = (Element) imported;
            } catch (ParserConfigurationException ex) {
                throw new XMLSecurityException("empty", ex);
            } catch (IOException ex) {
                throw new XMLSecurityException("empty", ex);
            } catch (SAXException ex) {
                throw new XMLSecurityException("empty", ex);
            }
        }
        signatureMethod = XMLUtils.getNextElement(c14nMethod.getNextSibling());
        this._signatureAlgorithm =
            new SignatureAlgorithm(signatureMethod, this.getBaseURI());
    }

   /**
    * Tests core validation process
    *
    * @return true if verification was successful
    * @throws MissingResourceFailureException
    * @throws XMLSecurityException
    */
   public boolean verify()
           throws MissingResourceFailureException, XMLSecurityException {
      return super.verifyReferences(false);
   }

   /**
    * Tests core validation process
    *
    * @param followManifests defines whether the verification process has to verify referenced <CODE>ds:Manifest</CODE>s, too
    * @return true if verification was successful
    * @throws MissingResourceFailureException
    * @throws XMLSecurityException
    */
   public boolean verify(boolean followManifests)
           throws MissingResourceFailureException, XMLSecurityException {
      return super.verifyReferences(followManifests);
   }

   /**
    * Returns getCanonicalizedOctetStream
    *
    * @return the canonicalization result octedt stream of <code>SignedInfo</code> element
    * @throws CanonicalizationException
    * @throws InvalidCanonicalizerException
    * @throws XMLSecurityException
    */
   public byte[] getCanonicalizedOctetStream()
           throws CanonicalizationException, InvalidCanonicalizerException,
                 XMLSecurityException {

      if ((this._c14nizedBytes == null)
              /*&& (this._state == ElementProxy.MODE_SIGN)*/) {
         Canonicalizer c14nizer =
            Canonicalizer.getInstance(this.getCanonicalizationMethodURI());

         this._c14nizedBytes =
            c14nizer.canonicalizeSubtree(this._constructionElement);
      }

      // make defensive copy
      byte[] output = new byte[this._c14nizedBytes.length];

      System.arraycopy(this._c14nizedBytes, 0, output, 0, output.length);

      return output;
   }

   /**
    *  Output the C14n stream to the give outputstream.
    * @param os
    * @throws CanonicalizationException
    * @throws InvalidCanonicalizerException
    * @throws XMLSecurityException
    */
   public void signInOctectStream(OutputStream os)
       throws CanonicalizationException, InvalidCanonicalizerException,
           XMLSecurityException {

        if ((this._c14nizedBytes == null)) {
       Canonicalizer c14nizer =
          Canonicalizer.getInstance(this.getCanonicalizationMethodURI());
       c14nizer.setWriter(os);
       String inclusiveNamespaces = this.getInclusiveNamespaces();

       if(inclusiveNamespaces == null)
        c14nizer.canonicalizeSubtree(this._constructionElement);
       else
        c14nizer.canonicalizeSubtree(this._constructionElement, inclusiveNamespaces);
    } else {
        try {
                        os.write(this._c14nizedBytes);
                } catch (IOException e) {
                        throw new RuntimeException(""+e);
                }
    }
   }

   /**
    * Returns the Canonicalization method URI
    *
    * @return the Canonicalization method URI
    */
   public String getCanonicalizationMethodURI() {


     return c14nMethod.getAttributeNS(null, Constants._ATT_ALGORITHM);
   }

   /**
    * Returns the Signature method URI
    *
    * @return the Signature method URI
    */
   public String getSignatureMethodURI() {

      Element signatureElement = this.getSignatureMethodElement();

      if (signatureElement != null) {
         return signatureElement.getAttributeNS(null, Constants._ATT_ALGORITHM);
      }

      return null;
   }

   /**
    * Method getSignatureMethodElement
    * @return gets The SignatureMethod Node.
    *
    */
   public Element getSignatureMethodElement() {
           return signatureMethod;
   }

   /**
    * Creates a SecretKey for the appropriate Mac algorithm based on a
    * byte[] array password.
    *
    * @param secretKeyBytes
    * @return the secret key for the SignedInfo element.
    */
   public SecretKey createSecretKey(byte[] secretKeyBytes)
   {

      return new SecretKeySpec(secretKeyBytes,
                               this._signatureAlgorithm
                                  .getJCEAlgorithmString());
   }

   protected SignatureAlgorithm getSignatureAlgorithm() {
           return _signatureAlgorithm;
   }
   /**
    * Method getBaseLocalName
    * @inheritDoc
    *
    */
   public String getBaseLocalName() {
      return Constants._TAG_SIGNEDINFO;
   }

   public String getInclusiveNamespaces() {



     String c14nMethodURI = c14nMethod.getAttributeNS(null, Constants._ATT_ALGORITHM);
     if(!(c14nMethodURI.equals("http://www.w3.org/2001/10/xml-exc-c14n#") ||
                        c14nMethodURI.equals("http://www.w3.org/2001/10/xml-exc-c14n#WithComments"))) {
                return null;
            }

     Element inclusiveElement = XMLUtils.getNextElement(
                 c14nMethod.getFirstChild());

     if(inclusiveElement != null)
     {
         try
         {
             String inclusiveNamespaces = new InclusiveNamespaces(inclusiveElement,
                         InclusiveNamespaces.ExclusiveCanonicalizationNamespace).getInclusiveNamespaces();
             return inclusiveNamespaces;
         }
         catch (XMLSecurityException e)
         {
             return null;
         }
     }
     return null;
    }
}
