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
package com.sun.org.apache.xml.internal.security.transforms;



import java.io.IOException;
import java.io.OutputStream;

import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import org.xml.sax.SAXException;


/**
 * Base class which all Transform algorithms extend. The common methods that
 * have to be overridden are the {@link #enginePerformTransform(XMLSignatureInput)} method.
 *
 * @author Christian Geuer-Pollmann
 */
public abstract class TransformSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(TransformSpi.class.getName());

   protected Transform _transformObject = null;
   protected void setTransform(Transform transform) {
      this._transformObject = transform;
   }

   /**
    * The mega method which MUST be implemented by the Transformation Algorithm.
    *
    * @param input {@link XMLSignatureInput} as the input of transformation
    * @param os where to output this transformation.
    * @return {@link XMLSignatureInput} as the result of transformation
    * @throws CanonicalizationException
    * @throws IOException
    * @throws InvalidCanonicalizerException
    * @throws ParserConfigurationException
    * @throws SAXException
    * @throws TransformationException
    */
   protected XMLSignatureInput enginePerformTransform(
      XMLSignatureInput input, OutputStream os)
         throws IOException,
                CanonicalizationException, InvalidCanonicalizerException,
                TransformationException, ParserConfigurationException,
                SAXException {
            return enginePerformTransform(input);
   }
   /**
    * The mega method which MUST be implemented by the Transformation Algorithm.
    *
    * @param input {@link XMLSignatureInput} as the input of transformation
    * @return {@link XMLSignatureInput} as the result of transformation
    * @throws CanonicalizationException
    * @throws IOException
    * @throws InvalidCanonicalizerException
    * @throws ParserConfigurationException
    * @throws SAXException
    * @throws TransformationException
    */
   protected abstract XMLSignatureInput enginePerformTransform(
      XMLSignatureInput input)
         throws IOException,
                CanonicalizationException, InvalidCanonicalizerException,
                TransformationException, ParserConfigurationException,
                SAXException;

   /**
    * Returns the URI representation of <code>Transformation algorithm</code>
    *
    * @return the URI representation of <code>Transformation algorithm</code>
    */
   protected abstract String engineGetURI();
}
