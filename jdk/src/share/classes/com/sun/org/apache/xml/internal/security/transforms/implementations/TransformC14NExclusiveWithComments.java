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



import java.io.OutputStream;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.implementations.Canonicalizer20010315ExclWithComments;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.TransformSpi;
import com.sun.org.apache.xml.internal.security.transforms.Transforms;
import com.sun.org.apache.xml.internal.security.transforms.params.InclusiveNamespaces;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;


/**
 * Implements the <CODE>http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments</CODE>
 * transform.
 *
 * @author Christian Geuer-Pollmann
 */
public class TransformC14NExclusiveWithComments extends TransformSpi {

   /** Field implementedTransformURI */
   public static final String implementedTransformURI =
      Transforms.TRANSFORM_C14N_EXCL_WITH_COMMENTS;


   /**
    * Method engineGetURI
    *@inheritDoc
    *
    */
   protected String engineGetURI() {
      return implementedTransformURI;
   }

   /**
    * @inheritDoc
    */
   protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input)
           throws CanonicalizationException {
            return enginePerformTransform(input,null);
   }
    protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input,OutputStream os)
    throws CanonicalizationException {
     try {
        String inclusiveNamespaces = null;

        if (this._transformObject
                .length(InclusiveNamespaces
                   .ExclusiveCanonicalizationNamespace, InclusiveNamespaces
                   ._TAG_EC_INCLUSIVENAMESPACES) == 1) {
           Element inclusiveElement =
               XMLUtils.selectNode(
              this._transformObject.getElement().getFirstChild(),
                 InclusiveNamespaces.ExclusiveCanonicalizationNamespace,
                 InclusiveNamespaces._TAG_EC_INCLUSIVENAMESPACES,0);

           inclusiveNamespaces = new InclusiveNamespaces(inclusiveElement,
                   this._transformObject.getBaseURI()).getInclusiveNamespaces();
        }

        Canonicalizer20010315ExclWithComments c14n =
            new Canonicalizer20010315ExclWithComments();
        if (os!=null) {
           c14n.setWriter( os);
        }
        input.setNeedsToBeExpanded(true);
        byte []result;
        result =c14n.engineCanonicalize(input, inclusiveNamespaces);
        XMLSignatureInput output=new XMLSignatureInput(result);

        return output;
     } catch (XMLSecurityException ex) {
        throw new CanonicalizationException("empty", ex);
     }
   }
}
