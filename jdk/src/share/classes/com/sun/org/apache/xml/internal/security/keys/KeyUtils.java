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
package com.sun.org.apache.xml.internal.security.keys;



import java.io.PrintStream;
import java.security.PublicKey;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.KeyName;
import com.sun.org.apache.xml.internal.security.keys.content.KeyValue;
import com.sun.org.apache.xml.internal.security.keys.content.MgmtData;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;


/**
 * Utility class for for <CODE>com.sun.org.apache.xml.internal.security.keys</CODE> package.
 *
 * @author $Author: mullan $
 */
public class KeyUtils {

   private KeyUtils() {
      // no instantiation
   }

   /**
    * Method prinoutKeyInfo
    *
    * @param ki
    * @param os
    * @throws XMLSecurityException
    */
   public static void prinoutKeyInfo(KeyInfo ki, PrintStream os)
           throws XMLSecurityException {

      for (int i = 0; i < ki.lengthKeyName(); i++) {
         KeyName x = ki.itemKeyName(i);

         os.println("KeyName(" + i + ")=\"" + x.getKeyName() + "\"");
      }

      for (int i = 0; i < ki.lengthKeyValue(); i++) {
         KeyValue x = ki.itemKeyValue(i);
         PublicKey pk = x.getPublicKey();

         os.println("KeyValue Nr. " + i);
         os.println(pk);
      }

      for (int i = 0; i < ki.lengthMgmtData(); i++) {
         MgmtData x = ki.itemMgmtData(i);

         os.println("MgmtData(" + i + ")=\"" + x.getMgmtData() + "\"");
      }

      for (int i = 0; i < ki.lengthX509Data(); i++) {
         X509Data x = ki.itemX509Data(i);

         os.println("X509Data(" + i + ")=\"" + (x.containsCertificate()
                                                ? "Certificate "
                                                : "") + (x
                                                   .containsIssuerSerial()
                                                         ? "IssuerSerial "
                                                         : "") + "\"");
      }
   }
}
