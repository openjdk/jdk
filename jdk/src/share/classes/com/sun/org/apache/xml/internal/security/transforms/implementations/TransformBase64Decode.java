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
package com.sun.org.apache.xml.internal.security.transforms.implementations;



import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.TransformSpi;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;


/**
 * Implements the <CODE>http://www.w3.org/2000/09/xmldsig#base64</CODE> decoding
 * transform.
 *
 * <p>The normative specification for base64 decoding transforms is
 * <A HREF="http://www.w3.org/TR/2001/CR-xmldsig-core-20010419/#ref-MIME">[MIME]</A>.
 * The base64 Transform element has no content. The input
 * is decoded by the algorithms. This transform is useful if an
 * application needs to sign the raw data associated with the encoded
 * content of an element. </p>
 *
 * <p>This transform requires an octet stream for input.
 * If an XPath node-set (or sufficiently functional alternative) is
 * given as input, then it is converted to an octet stream by
 * performing operations logically equivalent to 1) applying an XPath
 * transform with expression self::text(), then 2) taking the string-value
 * of the node-set. Thus, if an XML element is identified by a barename
 * XPointer in the Reference URI, and its content consists solely of base64
 * encoded character data, then this transform automatically strips away the
 * start and end tags of the identified element and any of its descendant
 * elements as well as any descendant comments and processing instructions.
 * The output of this transform is an octet stream.</p>
 *
 * @author Christian Geuer-Pollmann
 * @see com.sun.org.apache.xml.internal.security.utils.Base64
 */
public class TransformBase64Decode extends TransformSpi {

   /** Field implementedTransformURI */
   public static final String implementedTransformURI =
      Transforms.TRANSFORM_BASE64_DECODE;

   /**
    * Method engineGetURI
    *
    * @inheritDoc
    */
   protected String engineGetURI() {
      return TransformBase64Decode.implementedTransformURI;
   }

   /**
    * Method enginePerformTransform
    *
    * @param input
    * @return {@link XMLSignatureInput} as the result of transformation
    * @inheritDoc
    * @throws CanonicalizationException
    * @throws IOException
    * @throws TransformationException
    */
   protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input)
           throws IOException, CanonicalizationException,
                  TransformationException {
        return enginePerformTransform(input,null);
   }
    protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input,
            OutputStream os)
    throws IOException, CanonicalizationException,
           TransformationException {
         try {
      if (input.isElement()) {
         Node el=input.getSubNode();
         if (input.getSubNode().getNodeType()==Node.TEXT_NODE) {
            el=el.getParentNode();
         }
         StringBuffer sb=new StringBuffer();
         traverseElement((Element)el,sb);
         if (os==null) {
                byte[] decodedBytes = Base64.decode(sb.toString());
                return new XMLSignatureInput(decodedBytes);
         }
                Base64.decode(sb.toString().getBytes(),os);
            XMLSignatureInput output=new XMLSignatureInput((byte[])null);
            output.setOutputStream(os);
            return output;

      }
      if (input.isOctetStream() || input.isNodeSet()) {


        if (os==null) {
            byte[] base64Bytes = input.getBytes();
            byte[] decodedBytes = Base64.decode(base64Bytes);
            return new XMLSignatureInput(decodedBytes);
         }
        if (input.isByteArray() || input.isNodeSet()) {
               Base64.decode(input.getBytes(),os);
        } else {
            Base64.decode(new BufferedInputStream(input.getOctetStreamReal())
                    ,os);
        }
            XMLSignatureInput output=new XMLSignatureInput((byte[])null);
            output.setOutputStream(os);
            return output;


      }

         try {
            //Exceptional case there is current not text case testing this(Before it was a
                    //a common case).
            Document doc =
               DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                  input.getOctetStream());

            Element rootNode = doc.getDocumentElement();
            StringBuffer sb = new StringBuffer();
            traverseElement(rootNode,sb);
            byte[] decodedBytes = Base64.decode(sb.toString());

            return new XMLSignatureInput(decodedBytes);
                  } catch (ParserConfigurationException e) {
                          throw new TransformationException("c14n.Canonicalizer.Exception",e);
                  } catch (SAXException e) {
                          throw new TransformationException("SAX exception", e);
                  }
        } catch (Base64DecodingException e) {
        throw new TransformationException("Base64Decoding", e);
        }
   }

   void traverseElement(org.w3c.dom.Element node,StringBuffer sb) {
            Node sibling=node.getFirstChild();
        while (sibling!=null) {
                switch (sibling.getNodeType()) {
                        case Node.ELEMENT_NODE:
                    traverseElement((Element)sibling,sb);
                    break;
               case Node.TEXT_NODE:
                    sb.append(((Text)sibling).getData());
            }
            sibling=sibling.getNextSibling();
        }
   }
}
