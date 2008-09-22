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
package com.sun.org.apache.xml.internal.security.keys.content;



import java.math.BigInteger;
import java.security.cert.X509Certificate;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509CRL;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509Certificate;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509IssuerSerial;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SubjectName;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 *
 * @author $Author: mullan $
 */
public class X509Data extends SignatureElementProxy implements KeyInfoContent {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(X509Data.class.getName());

   /**
    * Constructor X509Data
    *
    * @param doc
    */
   public X509Data(Document doc) {

      super(doc);

      XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Constructor X509Data
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public X509Data(Element element, String BaseURI)
           throws XMLSecurityException {

      super(element, BaseURI);
      Node sibling=this._constructionElement.getFirstChild();
      while (sibling!=null) {
         if (sibling.getNodeType()!=Node.ELEMENT_NODE) {
                sibling=sibling.getNextSibling();
            continue;
         }
         return;
      }
      /* No Elements found */
      Object exArgs[] = { "Elements", Constants._TAG_X509DATA };
      throw new XMLSecurityException("xml.WrongContent", exArgs);
   }

   /**
    * Method addIssuerSerial
    *
    * @param X509IssuerName
    * @param X509SerialNumber
    */
   public void addIssuerSerial(String X509IssuerName,
                               BigInteger X509SerialNumber) {
      this.add(new XMLX509IssuerSerial(this._doc, X509IssuerName,
                                       X509SerialNumber));
   }

   /**
    * Method addIssuerSerial
    *
    * @param X509IssuerName
    * @param X509SerialNumber
    */
   public void addIssuerSerial(String X509IssuerName, String X509SerialNumber) {
      this.add(new XMLX509IssuerSerial(this._doc, X509IssuerName,
                                       X509SerialNumber));
   }

   /**
    * Method addIssuerSerial
    *
    * @param X509IssuerName
    * @param X509SerialNumber
    */
   public void addIssuerSerial(String X509IssuerName, int X509SerialNumber) {
      this.add(new XMLX509IssuerSerial(this._doc, X509IssuerName,
                                       X509SerialNumber));
   }

   /**
    * Method add
    *
    * @param xmlX509IssuerSerial
    */
   public void add(XMLX509IssuerSerial xmlX509IssuerSerial) {

         this._constructionElement
            .appendChild(xmlX509IssuerSerial.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addSKI
    *
    * @param skiBytes
    */
   public void addSKI(byte[] skiBytes) {
      this.add(new XMLX509SKI(this._doc, skiBytes));
   }

   /**
    * Method addSKI
    *
    * @param x509certificate
    * @throws XMLSecurityException
    */
   public void addSKI(X509Certificate x509certificate)
           throws XMLSecurityException {
      this.add(new XMLX509SKI(this._doc, x509certificate));
   }

   /**
    * Method add
    *
    * @param xmlX509SKI
    */
   public void add(XMLX509SKI xmlX509SKI) {
         this._constructionElement.appendChild(xmlX509SKI.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addSubjectName
    *
    * @param subjectName
    */
   public void addSubjectName(String subjectName) {
      this.add(new XMLX509SubjectName(this._doc, subjectName));
   }

   /**
    * Method addSubjectName
    *
    * @param x509certificate
    */
   public void addSubjectName(X509Certificate x509certificate) {
      this.add(new XMLX509SubjectName(this._doc, x509certificate));
   }

   /**
    * Method add
    *
    * @param xmlX509SubjectName
    */
   public void add(XMLX509SubjectName xmlX509SubjectName) {
         this._constructionElement.appendChild(xmlX509SubjectName.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addCertificate
    *
    * @param x509certificate
    * @throws XMLSecurityException
    */
   public void addCertificate(X509Certificate x509certificate)
           throws XMLSecurityException {
      this.add(new XMLX509Certificate(this._doc, x509certificate));
   }

   /**
    * Method addCertificate
    *
    * @param x509certificateBytes
    */
   public void addCertificate(byte[] x509certificateBytes) {
      this.add(new XMLX509Certificate(this._doc, x509certificateBytes));
   }

   /**
    * Method add
    *
    * @param xmlX509Certificate
    */
   public void add(XMLX509Certificate xmlX509Certificate) {
         this._constructionElement.appendChild(xmlX509Certificate.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addCRL
    *
    * @param crlBytes
    */
   public void addCRL(byte[] crlBytes) {
      this.add(new XMLX509CRL(this._doc, crlBytes));
   }

   /**
    * Method add
    *
    * @param xmlX509CRL
    */
   public void add(XMLX509CRL xmlX509CRL) {
         this._constructionElement.appendChild(xmlX509CRL.getElement());
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method addUnknownElement
    *
    * @param element
    */
   public void addUnknownElement(Element element) {
         this._constructionElement.appendChild(element);
         XMLUtils.addReturnToElement(this._constructionElement);
   }

   /**
    * Method lengthIssuerSerial
    *
    * @return the number of IssuerSerial elements in this X509Data
    */
   public int lengthIssuerSerial() {
      return this.length(Constants.SignatureSpecNS,
                         Constants._TAG_X509ISSUERSERIAL);
   }

   /**
    * Method lengthSKI
    *
    * @return the number of SKI elements in this X509Data
    */
   public int lengthSKI() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_X509SKI);
   }

   /**
    * Method lengthSubjectName
    *
    * @return the number of SubjectName elements in this X509Data
    */
   public int lengthSubjectName() {
      return this.length(Constants.SignatureSpecNS,
                         Constants._TAG_X509SUBJECTNAME);
   }

   /**
    * Method lengthCertificate
    *
    * @return the number of Certificate elements in this X509Data
    */
   public int lengthCertificate() {
      return this.length(Constants.SignatureSpecNS,
                         Constants._TAG_X509CERTIFICATE);
   }

   /**
    * Method lengthCRL
    *
    * @return the number of CRL elements in this X509Data
    */
   public int lengthCRL() {
      return this.length(Constants.SignatureSpecNS, Constants._TAG_X509CRL);
   }

   /**
    * Method lengthUnknownElement
    *
    * @return the number of UnknownElement elements in this X509Data
    */
   public int lengthUnknownElement() {

      int result = 0;
      Node n=this._constructionElement.getFirstChild();
      while (n!=null){

         if ((n.getNodeType() == Node.ELEMENT_NODE)
                 &&!n.getNamespaceURI().equals(Constants.SignatureSpecNS)) {
            result += 1;
         }
         n=n.getNextSibling();
      }

      return result;
   }

   /**
    * Method itemIssuerSerial
    *
    * @param i
    * @return the X509IssuerSerial, null if not present
    * @throws XMLSecurityException
    */
   public XMLX509IssuerSerial itemIssuerSerial(int i)
           throws XMLSecurityException {

      Element e =
        XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                       Constants._TAG_X509ISSUERSERIAL,i);

      if (e != null) {
         return new XMLX509IssuerSerial(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemSKI
    *
    * @param i
    * @return the X509SKI, null if not present
    * @throws XMLSecurityException
    */
   public XMLX509SKI itemSKI(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_X509SKI,i);

      if (e != null) {
         return new XMLX509SKI(e, this._baseURI);
      }
      return null;
   }

   /**
    * Method itemSubjectName
    *
    * @param i
    * @return the X509SubjectName, null if not present
    * @throws XMLSecurityException
    */
   public XMLX509SubjectName itemSubjectName(int i)
           throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_X509SUBJECTNAME,i);

      if (e != null) {
         return new XMLX509SubjectName(e, this._baseURI);
      }
       return null;
   }

   /**
    * Method itemCertificate
    *
    * @param i
    * @return the X509Certifacte, null if not present
    * @throws XMLSecurityException
    */
   public XMLX509Certificate itemCertificate(int i)
           throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_X509CERTIFICATE,i);

      if (e != null) {
         return new XMLX509Certificate(e, this._baseURI);
      }
       return null;
   }

   /**
    * Method itemCRL
    *
    * @param i
    * @return the X509CRL, null if not present
    * @throws XMLSecurityException
    */
   public XMLX509CRL itemCRL(int i) throws XMLSecurityException {

      Element e = XMLUtils.selectDsNode(this._constructionElement.getFirstChild(),
                                                Constants._TAG_X509CRL,i);

      if (e != null) {
         return new XMLX509CRL(e, this._baseURI);
      }
       return null;
   }

   /**
    * Method itemUnknownElement
    *
    * @param i
    * @return the Unknown Element at i
    * TODO implement
    **/
   public Element itemUnknownElement(int i) {
          log.log(java.util.logging.Level.FINE, "itemUnknownElement not implemented:"+i);
      return null;
   }

   /**
    * Method containsIssuerSerial
    *
    * @return true if this X509Data contains a IssuerSerial
    */
   public boolean containsIssuerSerial() {
      return this.lengthIssuerSerial() > 0;
   }

   /**
    * Method containsSKI
    *
    * @return true if this X509Data contains a SKI
    */
   public boolean containsSKI() {
      return this.lengthSKI() > 0;
   }

   /**
    * Method containsSubjectName
    *
    * @return true if this X509Data contains a SubjectName
    */
   public boolean containsSubjectName() {
      return this.lengthSubjectName() > 0;
   }

   /**
    * Method containsCertificate
    *
    * @return true if this X509Data contains a Certificate
    */
   public boolean containsCertificate() {
      return this.lengthCertificate() > 0;
   }

   /**
    * Method containsCRL
    *
    * @return true if this X509Data contains a CRL
    */
   public boolean containsCRL() {
      return this.lengthCRL() > 0;
   }

   /**
    * Method containsUnknownElement
    *
    * @return true if this X509Data contains an UnknownElement
    */
   public boolean containsUnknownElement() {
      return this.lengthUnknownElement() > 0;
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_X509DATA;
   }
}
