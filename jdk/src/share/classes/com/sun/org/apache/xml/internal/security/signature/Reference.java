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



import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.transforms.InvalidTransformException;
import com.sun.org.apache.xml.internal.security.transforms.Transform;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.DigesterOutputStream;
import com.sun.org.apache.xml.internal.security.utils.IdResolver;
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
 * Constuct a <CODE>ds:Reference</CODE> from an {@link org.w3c.dom.Element}.
 *
 * <p>Create a new reference</p>
 * <pre>
 * Document _doc;
 * MessageDigestAlgorithm sha1 = MessageDigestAlgorithm.getInstance("http://#sha1");
 * Reference ref = new Reference(new XMLSignatureInput(new FileInputStream("1.gif"),
 *                               "http://localhost/1.gif",
 *                               (Transforms) null, sha1);
 * Element refElem = ref.toElement(_doc);
 * </pre>
 *
 * <p>Verify a reference</p>
 * <pre>
 * Element refElem = _doc.getElement("Reference"); // PSEUDO
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

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(Reference.class.getName());

   /** Field OBJECT_URI */
   public static final String OBJECT_URI = Constants.SignatureSpecNS
                                           + Constants._TAG_OBJECT;

   /** Field MANIFEST_URI */
   public static final String MANIFEST_URI = Constants.SignatureSpecNS
                                             + Constants._TAG_MANIFEST;
   //J-
   Manifest _manifest = null;
   XMLSignatureInput _transformsOutput;
   //J+

   /**
    * Constructor Reference
    *
    * @param doc the {@link Document} in which <code>XMLsignature</code> is placed
    * @param BaseURI the URI of the resource where the XML instance will be stored
    * @param ReferenceURI URI indicate where is data which will digested
    * @param manifest
    * @param transforms {@link Transforms} applied to data
    * @param messageDigestAlgorithm {@link MessageDigestAlgorithm Digest algorithm} which is applied to the data
    * TODO should we throw XMLSignatureException if MessageDigestAlgoURI is wrong?
    * @throws XMLSignatureException
    */
   protected Reference(Document doc, String BaseURI, String ReferenceURI, Manifest manifest, Transforms transforms, String messageDigestAlgorithm)
           throws XMLSignatureException {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);

      this._baseURI = BaseURI;
      this._manifest = manifest;

      this.setURI(ReferenceURI);

      // important: The ds:Reference must be added to the associated ds:Manifest
      //            or ds:SignedInfo _before_ the this.resolverResult() is called.
      // this._manifest.appendChild(this._constructionElement);
      // this._manifest.appendChild(this._doc.createTextNode("\n"));

      if (transforms != null) {
         this._constructionElement.appendChild(transforms.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
      }
      {
         MessageDigestAlgorithm mda =
            MessageDigestAlgorithm.getInstance(this._doc,
                                               messageDigestAlgorithm);

         this._constructionElement.appendChild(mda.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
      }
      {
         Element digestValueElement =
            XMLUtils.createElementInSignatureSpace(this._doc,
                                                   Constants._TAG_DIGESTVALUE);

         this._constructionElement.appendChild(digestValueElement);
         XMLUtils.addReturnToElement(this._constructionElement);
      }
   }


   /**
    * Build a {@link Reference} from an {@link Element}
    *
    * @param element <code>Reference</code> element
    * @param BaseURI the URI of the resource where the XML instance was stored
    * @param manifest is the {@link Manifest} of {@link SignedInfo} in which the Reference occurs. We need this because the Manifest has the individual {@link ResourceResolver}s whcih have been set by the user
    * @throws XMLSecurityException
    */
   protected Reference(Element element, String BaseURI, Manifest manifest)
           throws XMLSecurityException {

      super(element, BaseURI);

      this._manifest = manifest;
   }

   /**
    * Returns {@link MessageDigestAlgorithm}
    *
    *
    * @return {@link MessageDigestAlgorithm}
    *
    * @throws XMLSignatureException
    */
   public MessageDigestAlgorithm getMessageDigestAlgorithm()
           throws XMLSignatureException {

      Element digestMethodElem = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
            Constants._TAG_DIGESTMETHOD,0);

      if (digestMethodElem == null) {
         return null;
      }

      String uri = digestMethodElem.getAttributeNS(null,
         Constants._ATT_ALGORITHM);

          if (uri == null) {
                  return null;
          }

      return MessageDigestAlgorithm.getInstance(this._doc, uri);
   }

   /**
    * Sets the <code>URI</code> of this <code>Reference</code> element
    *
    * @param URI the <code>URI</code> of this <code>Reference</code> element
    */
   public void setURI(String URI) {

      if ((this._state == MODE_SIGN) && (URI != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_URI,
                                                  URI);
      }
   }

   /**
    * Returns the <code>URI</code> of this <code>Reference</code> element
    *
    * @return URI the <code>URI</code> of this <code>Reference</code> element
    */
   public String getURI() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_URI);
   }

   /**
    * Sets the <code>Id</code> attribute of this <code>Reference</code> element
    *
    * @param Id the <code>Id</code> attribute of this <code>Reference</code> element
    */
   public void setId(String Id) {

      if ((this._state == MODE_SIGN) && (Id != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_ID, Id);
         IdResolver.registerElementById(this._constructionElement, Id);
      }
   }

   /**
    * Returns the <code>Id</code> attribute of this <code>Reference</code> element
    *
    * @return Id the <code>Id</code> attribute of this <code>Reference</code> element
    */
   public String getId() {
      return this._constructionElement.getAttributeNS(null, Constants._ATT_ID);
   }

   /**
    * Sets the <code>type</code> atttibute of the Reference indicate whether an <code>ds:Object</code>, <code>ds:SignatureProperty</code>, or <code>ds:Manifest</code> element
    *
    * @param Type the <code>type</code> attribute of the Reference
    */
   public void setType(String Type) {

      if ((this._state == MODE_SIGN) && (Type != null)) {
         this._constructionElement.setAttributeNS(null, Constants._ATT_TYPE,
                                                  Type);
      }
   }

   /**
    * Return the <code>type</code> atttibute of the Reference indicate whether an <code>ds:Object</code>, <code>ds:SignatureProperty</code>, or <code>ds:Manifest</code> element
    *
    * @return the <code>type</code> attribute of the Reference
    */
   public String getType() {
      return this._constructionElement.getAttributeNS(null,
              Constants._ATT_TYPE);
   }

   /**
    * Method isReferenceToObject
    *
    * This returns true if the <CODE>Type</CODE> attribute of the
    * <CODE>Refernce</CODE> element points to a <CODE>#Object</CODE> element
    *
    * @return true if the Reference type indicates that this Reference points to an <code>Object</code>
    */
   public boolean typeIsReferenceToObject() {

      if ((this.getType() != null)
              && this.getType().equals(Reference.OBJECT_URI)) {
         return true;
      }

      return false;
   }

   /**
    * Method isReferenceToManifest
    *
    * This returns true if the <CODE>Type</CODE> attribute of the
    * <CODE>Refernce</CODE> element points to a <CODE>#Manifest</CODE> element
    *
    * @return true if the Reference type indicates that this Reference points to a {@link Manifest}
    */
   public boolean typeIsReferenceToManifest() {

      if ((this.getType() != null)
              && this.getType().equals(Reference.MANIFEST_URI)) {
         return true;
      }

      return false;
   }

   /**
    * Method setDigestValueElement
    *
    * @param digestValue
    */
   private void setDigestValueElement(byte[] digestValue)
   {

      if (this._state == MODE_SIGN) {
         Element digestValueElement =XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                 Constants._TAG_DIGESTVALUE,0);
         Node n=digestValueElement.getFirstChild();
         while (n!=null) {
               digestValueElement.removeChild(n);
               n = n.getNextSibling();
         }

         String base64codedValue = Base64.encode(digestValue);
         Text t = this._doc.createTextNode(base64codedValue);

         digestValueElement.appendChild(t);
      }
   }

   /**
    * Method generateDigestValue
    *
    * @throws ReferenceNotInitializedException
    * @throws XMLSignatureException
    */
   public void generateDigestValue()
           throws XMLSignatureException, ReferenceNotInitializedException {

      if (this._state == MODE_SIGN) {

         this.setDigestValueElement(this.calculateDigest());
      }
   }

   /**
    * Returns the XMLSignatureInput which is created by de-referencing the URI attribute.
    * @return the XMLSignatureInput of the source of this reference
    * @throws ReferenceNotInitializedException If the resolver found any
    *  problem resolving the reference
    */
   public XMLSignatureInput getContentsBeforeTransformation()
           throws ReferenceNotInitializedException {

      try {
         Attr URIAttr = this._constructionElement.getAttributeNodeNS(null,
            Constants._ATT_URI);
         String URI;

         if (URIAttr == null) {
            URI = null;
         } else {
            URI = URIAttr.getNodeValue();
         }

         ResourceResolver resolver = ResourceResolver.getInstance(URIAttr,
            this._baseURI, this._manifest._perManifestResolvers);

         if (resolver == null) {
            Object exArgs[] = { URI };

            throw new ReferenceNotInitializedException(
               "signature.Verification.Reference.NoInput", exArgs);
         }

         resolver.addProperties(this._manifest._resolverProperties);

         XMLSignatureInput input = resolver.resolve(URIAttr, this._baseURI);


         return input;
      }  catch (ResourceResolverException ex) {
         throw new ReferenceNotInitializedException("empty", ex);
      } catch (XMLSecurityException ex) {
         throw new ReferenceNotInitializedException("empty", ex);
      }
   }

   /**
    * Returns the data which is referenced by the URI attribute. This method
    * only works works after a call to verify.
    * @return a XMLSignature with a byte array.
    * @throws ReferenceNotInitializedException
    *
    * @deprecated use getContentsBeforeTransformation
    */
   public XMLSignatureInput getTransformsInput() throws ReferenceNotInitializedException
        {
                XMLSignatureInput input=getContentsBeforeTransformation();
                XMLSignatureInput result;
                try {
                        result = new XMLSignatureInput(input.getBytes());
                } catch (CanonicalizationException ex) {
                         throw new ReferenceNotInitializedException("empty", ex);
                } catch (IOException ex) {
                         throw new ReferenceNotInitializedException("empty", ex);
                }
                result.setSourceURI(input.getSourceURI());
                return result;

   }

   private XMLSignatureInput getContentsAfterTransformation(XMLSignatureInput input, OutputStream os)
           throws XMLSignatureException {

      try {
         Transforms transforms = this.getTransforms();
         XMLSignatureInput output = null;

         if (transforms != null) {
            output = transforms.performTransforms(input,os);
            this._transformsOutput = output;//new XMLSignatureInput(output.getBytes());

            //this._transformsOutput.setSourceURI(output.getSourceURI());
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
         XMLSignatureInput output = input;
         Transforms transforms = this.getTransforms();

         if (transforms != null) {
            doTransforms: for (int i = 0; i < transforms.getLength(); i++) {
               Transform t = transforms.item(i);
               String URI = t.getURI();

               if (URI.equals(Transforms
                       .TRANSFORM_C14N_EXCL_OMIT_COMMENTS) || URI
                          .equals(Transforms
                             .TRANSFORM_C14N_EXCL_WITH_COMMENTS) || URI
                                .equals(Transforms
                                   .TRANSFORM_C14N_OMIT_COMMENTS) || URI
                                      .equals(Transforms
                                         .TRANSFORM_C14N_WITH_COMMENTS)) {

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
         Set inclusiveNamespaces = new HashSet();

         {
            Transforms transforms = this.getTransforms();
            Transform c14nTransform = null;

            if (transforms != null) {
               doTransforms: for (int i = 0; i < transforms.getLength(); i++) {
                  Transform t = transforms.item(i);
                  String URI = t.getURI();

                  if (URI.equals(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS)
                          || URI.equals(
                             Transforms.TRANSFORM_C14N_EXCL_WITH_COMMENTS)) {
                     c14nTransform = t;

                     break doTransforms;
                  }
               }
            }

            if (c14nTransform != null) {

               if (c14nTransform
                       .length(InclusiveNamespaces
                          .ExclusiveCanonicalizationNamespace, InclusiveNamespaces
                          ._TAG_EC_INCLUSIVENAMESPACES) == 1) {

                  // there is one InclusiveNamespaces element
                  InclusiveNamespaces in = new InclusiveNamespaces(
                        XMLUtils.selectNode(
                        c14nTransform.getElement().getFirstChild(),
                                                InclusiveNamespaces.ExclusiveCanonicalizationNamespace,
                        InclusiveNamespaces._TAG_EC_INCLUSIVENAMESPACES,0), this.getBaseURI());

                  inclusiveNamespaces = InclusiveNamespaces.prefixStr2Set(
                     in.getInclusiveNamespaces());
               }
            }
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
      return this._transformsOutput;
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
         XMLSignatureInput output = this.getContentsAfterTransformation(input, os);

         /* at this stage, this._transformsInput and this._transformsOutput
          * contain a huge amount of nodes. When we do not cache these nodes
          * but only preserve the octets, the memory footprint is dramatically
          * reduced.
          */

         this._transformsOutput = output;

         return output;
      } catch (XMLSecurityException ex) {
         throw new ReferenceNotInitializedException("empty", ex);
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

      Element transformsElement = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
            Constants._TAG_TRANSFORMS,0);

      if (transformsElement != null) {
         Transforms transforms = new Transforms(transformsElement,
                                                this._baseURI);

         return transforms;
      }
       return null;
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
        XMLSignatureInput output=this.dereferenceURIandPerformTransforms(null);

        byte[] signedBytes = output.getBytes();

        return signedBytes;
     } catch (IOException ex) {
        throw new ReferenceNotInitializedException("empty", ex);
     } catch (CanonicalizationException ex) {
        throw new ReferenceNotInitializedException("empty", ex);
     }

   }


   /**
    * Method resolverResult
    *
    * @return reference Calculate the digest of this reference.
    * @throws ReferenceNotInitializedException
    * @throws XMLSignatureException
    */
   private byte[] calculateDigest()
           throws ReferenceNotInitializedException, XMLSignatureException {

      try {

         MessageDigestAlgorithm mda = this.getMessageDigestAlgorithm();

         mda.reset();
         DigesterOutputStream diOs=new DigesterOutputStream(mda);
         OutputStream os=new UnsyncBufferedOutputStream(diOs);
         XMLSignatureInput output=this.dereferenceURIandPerformTransforms(os);
         output.updateOutputStream(os);
         os.flush();
         //this.getReferencedBytes(diOs);
         //mda.update(data);

         return diOs.getDigestValue();
      } catch (XMLSecurityException ex) {
         throw new ReferenceNotInitializedException("empty", ex);
      } catch (IOException ex) {
         throw new ReferenceNotInitializedException("empty", ex);
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
      Element digestValueElem = XMLUtils.selectDsNode(this._constructionElement.getFirstChild()
            ,Constants._TAG_DIGESTVALUE,0);
          if (digestValueElem == null) {
                  // The required element is not in the XML!
                  Object[] exArgs ={ Constants._TAG_DIGESTVALUE,
                                                         Constants.SignatureSpecNS };
                  throw new XMLSecurityException(
                                        "signature.Verification.NoSignatureElement",
                                        exArgs);
          }
      byte[] elemDig = Base64.decode(digestValueElem);
      return elemDig;
   }


   /**
    * Tests reference valdiation is success or false
    *
    * @return true if reference valdiation is success, otherwise false
    * @throws ReferenceNotInitializedException
    * @throws XMLSecurityException
    */
   public boolean verify()
           throws ReferenceNotInitializedException, XMLSecurityException {

      byte[] elemDig = this.getDigestValue();
      byte[] calcDig = this.calculateDigest();
      boolean equal = MessageDigestAlgorithm.isEqual(elemDig, calcDig);

      if (!equal) {
         log.log(java.util.logging.Level.WARNING, "Verification failed for URI \"" + this.getURI() + "\"");
      } else {
         if (log.isLoggable(java.util.logging.Level.INFO))                                  log.log(java.util.logging.Level.INFO, "Verification successful for URI \"" + this.getURI() + "\"");
      }

      return equal;
   }

   /**
    * Method getBaseLocalName
    * @inheritDoc
    *
    */
   public String getBaseLocalName() {
      return Constants._TAG_REFERENCE;
   }
}
