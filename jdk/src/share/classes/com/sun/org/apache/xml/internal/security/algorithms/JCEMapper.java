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
package com.sun.org.apache.xml.internal.security.algorithms;



import java.util.HashMap;
import java.util.Map;


import com.sun.org.apache.xml.internal.security.Init;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;



/**
 * This class maps algorithm identifier URIs to JAVA JCE class names.
 *
 * @author $Author: raul $
 */
public class JCEMapper {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(JCEMapper.class.getName());



   private static Map uriToJCEName = new HashMap();

   private static Map algorithmsMap = new HashMap();

   private static String providerName = null;
   /**
    * Method init
    *
    * @param mappingElement
    * @throws Exception
    */
   public static void init(Element mappingElement) throws Exception {

      loadAlgorithms((Element)mappingElement.getElementsByTagName("Algorithms").item(0));
   }

   static void loadAlgorithms( Element algorithmsEl) {
       Element[] algorithms = XMLUtils.selectNodes(algorithmsEl.getFirstChild(),Init.CONF_NS,"Algorithm");
       for (int i = 0 ;i < algorithms.length ;i ++) {
           Element el = algorithms[i];
           String id = el.getAttribute("URI");
           String jceName = el.getAttribute("JCEName");
           uriToJCEName.put(id, jceName);
           algorithmsMap.put(id, new Algorithm(el));
       }
   }

   static Algorithm getAlgorithmMapping(String algoURI) {
           return ((Algorithm)algorithmsMap.get(algoURI));
   }

   /**
    * Method translateURItoJCEID
    *
    * @param AlgorithmURI
    * @return the JCE standard name corresponding to the given URI
    *
    */
   public static String translateURItoJCEID(String AlgorithmURI) {
      if (true)
          if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Request for URI " + AlgorithmURI);

      String jceName = (String) uriToJCEName.get(AlgorithmURI);
      return jceName;
   }

   /**
    * Method getAlgorithmClassFromURI
    * NOTE(Raul Benito) It seems a buggy function the loop doesn't do
    * anything??
    * @param AlgorithmURI
    * @return the class name that implements this algorithm
    *
    */
   public static String getAlgorithmClassFromURI(String AlgorithmURI) {
       if (true)
           if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Request for URI " + AlgorithmURI);

       return ((Algorithm) algorithmsMap.get(AlgorithmURI)).algorithmClass;
   }

   /**
    * Returns the keylength in bit for a particular algorithm.
    *
    * @param AlgorithmURI
    * @return The length of the key used in the alogrithm
    */
   public static int getKeyLengthFromURI(String AlgorithmURI) {
       return Integer.parseInt(((Algorithm) algorithmsMap.get(AlgorithmURI)).keyLength);
   }

   /**
    * Method getJCEKeyAlgorithmFromURI
    *
    * @param AlgorithmURI
    * @return The KeyAlgorithm for the given URI.
    *
    */
   public static String getJCEKeyAlgorithmFromURI(String AlgorithmURI) {

        return  ((Algorithm) algorithmsMap.get(AlgorithmURI)).requiredKey;

   }

   /**
    * Gets the default Provider for obtaining the security algorithms
    * @return the default providerId.
    */
   public static String getProviderId() {
                return providerName;
   }

   /**
    * Sets the default Provider for obtaining the security algorithms
    * @param provider the default providerId.
    */
   public static void setProviderId(String provider) {
                providerName=provider;
   }

   /**
    * Represents the Algorithm xml element
    */
   public static class Algorithm {
            String algorithmClass;
            String keyLength;
            String requiredKey;
        /**
         * Gets data from element
         * @param el
         */
        public Algorithm(Element el) {
                algorithmClass=el.getAttribute("AlgorithmClass");
            keyLength=el.getAttribute("KeyLength");
            requiredKey=el.getAttribute("RequiredKey");
        }
   }
}
