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



import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;


/**
 *
 * @author Christian Geuer-Pollmann
 */
public class InvalidTransformException extends XMLSecurityException {

   /**
         *
         */
        private static final long serialVersionUID = 1L;

   /**
    * Constructor InvalidTransformException
    *
    */
   public InvalidTransformException() {
      super();
   }

   /**
    * Constructor InvalidTransformException
    *
    * @param _msgId
    */
   public InvalidTransformException(String _msgId) {
      super(_msgId);
   }

   /**
    * Constructor InvalidTransformException
    *
    * @param _msgId
    * @param exArgs
    */
   public InvalidTransformException(String _msgId, Object exArgs[]) {
      super(_msgId, exArgs);
   }

   /**
    * Constructor InvalidTransformException
    *
    * @param _msgId
    * @param _originalException
    */
   public InvalidTransformException(String _msgId, Exception _originalException) {
      super(_msgId, _originalException);
   }

   /**
    * Constructor InvalidTransformException
    *
    * @param _msgId
    * @param exArgs
    * @param _originalException
    */
   public InvalidTransformException(String _msgId, Object exArgs[],
                                    Exception _originalException) {
      super(_msgId, exArgs, _originalException);
   }
}
