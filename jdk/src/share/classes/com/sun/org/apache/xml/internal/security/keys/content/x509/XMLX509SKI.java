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
package com.sun.org.apache.xml.internal.security.keys.content.x509;



import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.SignatureElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import sun.security.util.DerValue;


/**
 * Handles SubjectKeyIdentifier (SKI) for X.509v3.
 *
 * @author $Author: raul $
 * @see <A HREF="http://java.sun.com/products/jdk/1.2/docs/api/java/security/cert/X509Extension.html">Interface X509Extension</A>
 */
public class XMLX509SKI extends SignatureElementProxy
        implements XMLX509DataContent {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(XMLX509SKI.class.getName());

   /**
    * <CODE>SubjectKeyIdentifier (id-ce-subjectKeyIdentifier) (2.5.29.14)</CODE>:
    * This extension identifies the public key being certified. It enables
    * distinct keys used by the same subject to be differentiated
    * (e.g., as key updating occurs).
    * <BR />
    * A key identifer shall be unique with respect to all key identifiers
    * for the subject with which it is used. This extension is always non-critical.
    */
   public static final String SKI_OID = "2.5.29.14";

   /**
    * Constructor X509SKI
    *
    * @param doc
    * @param skiBytes
    */
   public XMLX509SKI(Document doc, byte[] skiBytes) {

      super(doc);

      this.addBase64Text(skiBytes);
   }

   /**
    * Constructor XMLX509SKI
    *
    * @param doc
    * @param x509certificate
    * @throws XMLSecurityException
    */
   public XMLX509SKI(Document doc, X509Certificate x509certificate)
           throws XMLSecurityException {

      super(doc);

      this.addBase64Text(XMLX509SKI.getSKIBytesFromCert(x509certificate));
   }

   /**
    * Constructor XMLX509SKI
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public XMLX509SKI(Element element, String BaseURI)
           throws XMLSecurityException {
      super(element, BaseURI);
   }

   /**
    * Method getSKIBytes
    *
    * @return the skibytes
    * @throws XMLSecurityException
    */
   public byte[] getSKIBytes() throws XMLSecurityException {
      return this.getBytesFromTextChild();
   }

   /**
    * Method getSKIBytesFromCert
    *
    * @param cert
    * @return sky bytes from the given certificate
    *
    * @throws XMLSecurityException
    * @see java.security.cert.X509Extension#getExtensionValue(java.lang.String)
    */
   public static byte[] getSKIBytesFromCert(X509Certificate cert)
           throws XMLSecurityException {

      try {

         /*
          * Gets the DER-encoded OCTET string for the extension value (extnValue)
          * identified by the passed-in oid String. The oid string is
          * represented by a set of positive whole numbers separated by periods.
          */
         byte[] derEncodedValue = cert.getExtensionValue(XMLX509SKI.SKI_OID);

         if (cert.getVersion() < 3) {
            Object exArgs[] = { new Integer(cert.getVersion()) };

            throw new XMLSecurityException("certificate.noSki.lowVersion",
                                           exArgs);
         }

          byte[] extensionValue = null;

          /**
           * Use sun.security.util.DerValue if it is present.
           */
          try {
                  DerValue dervalue = new DerValue(derEncodedValue);
                  if (dervalue == null) {
                      throw new XMLSecurityException("certificate.noSki.null");
                  }
                  if (dervalue.tag != DerValue.tag_OctetString) {
                      throw new XMLSecurityException("certificate.noSki.notOctetString");
                  }
                  extensionValue = dervalue.getOctetString();
          } catch (NoClassDefFoundError e) {
          }

          /**
           * Fall back to org.bouncycastle.asn1.DERInputStream
           */
          if (extensionValue == null) {
              try {
                  Class clazz = Class.forName("org.bouncycastle.asn1.DERInputStream");
                  if (clazz != null) {
                      Constructor constructor = clazz.getConstructor(new Class[]{InputStream.class});
                      InputStream is = (InputStream) constructor.newInstance(new Object[]{new ByteArrayInputStream(derEncodedValue)});
                      Method method = clazz.getMethod("readObject", new Class[]{});
                      Object obj = method.invoke(is, new Object[]{});
                      if (obj == null) {
                          throw new XMLSecurityException("certificate.noSki.null");
                      }
                      Class clazz2 = Class.forName("org.bouncycastle.asn1.ASN1OctetString");
                      if (!clazz2.isInstance(obj)) {
                          throw new XMLSecurityException("certificate.noSki.notOctetString");
                      }
                      Method method2 = clazz2.getMethod("getOctets", new Class[]{});
                      extensionValue = (byte[]) method2.invoke(obj, new Object[]{});
                  }
              } catch (Throwable t) {
              }
          }

         /**
          * Strip away first two bytes from the DerValue (tag and length)
          */
         byte abyte0[] = new byte[extensionValue.length - 2];

         System.arraycopy(extensionValue, 2, abyte0, 0, abyte0.length);

         /*
         byte abyte0[] = new byte[derEncodedValue.length - 4];
         System.arraycopy(derEncodedValue, 4, abyte0, 0, abyte0.length);
         */
         if (true)
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Base64 of SKI is " + Base64.encode(abyte0));

         return abyte0;
      } catch (IOException ex) {
         throw new XMLSecurityException("generic.EmptyMessage", ex);
      }
   }

   /** @inheritDoc */
   public boolean equals(Object obj) {

      if (!obj.getClass().getName().equals(this.getClass().getName())) {
         return false;
      }

      XMLX509SKI other = (XMLX509SKI) obj;

      try {
         return java.security.MessageDigest.isEqual(other.getSKIBytes(),
                                        this.getSKIBytes());
      } catch (XMLSecurityException ex) {
         return false;
      }
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_X509SKI;
   }
}
