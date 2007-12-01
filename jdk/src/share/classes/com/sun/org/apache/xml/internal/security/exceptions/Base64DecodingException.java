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
package com.sun.org.apache.xml.internal.security.exceptions;



/**
 * This Exception is thrown if decoding of Base64 data fails.
 *
 * @author Christian Geuer-Pollmann
 */
public class Base64DecodingException extends XMLSecurityException {

   /**
         *
         */
        private static final long serialVersionUID = 1L;

   /**
    * Constructor Base64DecodingException
    *
    */
   public Base64DecodingException() {
      super();
   }

   /**
    * Constructor Base64DecodingException
    *
    * @param _msgID
    */
   public Base64DecodingException(String _msgID) {
      super(_msgID);
   }

   /**
    * Constructor Base64DecodingException
    *
    * @param _msgID
    * @param exArgs
    */
   public Base64DecodingException(String _msgID, Object exArgs[]) {
      super(_msgID, exArgs);
   }

   /**
    * Constructor Base64DecodingException
    *
    * @param _msgID
    * @param _originalException
    */
   public Base64DecodingException(String _msgID,
                                              Exception _originalException) {
      super(_msgID, _originalException);
   }

   /**
    * Constructor Base64DecodingException
    *
    * @param _msgID
    * @param exArgs
    * @param _originalException
    */
   public Base64DecodingException(String _msgID, Object exArgs[],
                                              Exception _originalException) {
      super(_msgID, exArgs, _originalException);
   }
}
