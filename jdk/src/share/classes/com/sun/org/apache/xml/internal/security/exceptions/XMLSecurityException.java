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



import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;

import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.I18n;


/**
 * The mother of all Exceptions in this bundle. It allows exceptions to have
 * their messages translated to the different locales.
 *
 * The <code>xmlsecurity_en.properties</code> file contains this line:
 * <pre>
 * xml.WrongElement = Can't create a {0} from a {1} element
 * </pre>
 *
 * Usage in the Java source is:
 * <pre>
 * {
 *    Object exArgs[] = { Constants._TAG_TRANSFORMS, "BadElement" };
 *
 *    throw new XMLSecurityException("xml.WrongElement", exArgs);
 * }
 * </pre>
 *
 * Additionally, if another Exception has been caught, we can supply it, too>
 * <pre>
 * try {
 *    ...
 * } catch (Exception oldEx) {
 *    Object exArgs[] = { Constants._TAG_TRANSFORMS, "BadElement" };
 *
 *    throw new XMLSecurityException("xml.WrongElement", exArgs, oldEx);
 * }
 * </pre>
 *
 *
 * @author Christian Geuer-Pollmann
 */
public class XMLSecurityException extends Exception {



   /**
         *
         */
        private static final long serialVersionUID = 1L;

   /** Field originalException */
   protected Exception originalException = null;

   /** Field msgID */
   protected String msgID;

   /**
    * Constructor XMLSecurityException
    *
    */
   public XMLSecurityException() {

      super("Missing message string");

      this.msgID = null;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityException
    *
    * @param _msgID
    */
   public XMLSecurityException(String _msgID) {

      super(I18n.getExceptionMessage(_msgID));

      this.msgID = _msgID;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityException
    *
    * @param _msgID
    * @param exArgs
    */
   public XMLSecurityException(String _msgID, Object exArgs[]) {

      super(MessageFormat.format(I18n.getExceptionMessage(_msgID), exArgs));

      this.msgID = _msgID;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityException
    *
    * @param _originalException
    */
   public XMLSecurityException(Exception _originalException) {

      super("Missing message ID to locate message string in resource bundle \""
            + Constants.exceptionMessagesResourceBundleBase
            + "\". Original Exception was a "
            + _originalException.getClass().getName() + " and message "
            + _originalException.getMessage());

      this.originalException = _originalException;
   }

   /**
    * Constructor XMLSecurityException
    *
    * @param _msgID
    * @param _originalException
    */
   public XMLSecurityException(String _msgID, Exception _originalException) {

      super(I18n.getExceptionMessage(_msgID, _originalException));

      this.msgID = _msgID;
      this.originalException = _originalException;
   }

   /**
    * Constructor XMLSecurityException
    *
    * @param _msgID
    * @param exArgs
    * @param _originalException
    */
   public XMLSecurityException(String _msgID, Object exArgs[],
                               Exception _originalException) {

      super(MessageFormat.format(I18n.getExceptionMessage(_msgID), exArgs));

      this.msgID = _msgID;
      this.originalException = _originalException;
   }

   /**
    * Method getMsgID
    *
    * @return the messageId
    */
   public String getMsgID() {

      if (msgID == null) {
         return "Missing message ID";
      }
      return msgID;
   }

   /** @inheritDoc */
   public String toString() {

      String s = this.getClass().getName();
      String message = super.getLocalizedMessage();

      if (message != null) {
         message = s + ": " + message;
      } else {
         message = s;
      }

      if (originalException != null) {
         message = message + "\nOriginal Exception was "
                   + originalException.toString();
      }

      return message;
   }

   /**
    * Method printStackTrace
    *
    */
   public void printStackTrace() {

      synchronized (System.err) {
         super.printStackTrace(System.err);

         if (this.originalException != null) {
            this.originalException.printStackTrace(System.err);
         }
      }
   }

   /**
    * Method printStackTrace
    *
    * @param printwriter
    */
   public void printStackTrace(PrintWriter printwriter) {

      super.printStackTrace(printwriter);

      if (this.originalException != null) {
         this.originalException.printStackTrace(printwriter);
      }
   }

   /**
    * Method printStackTrace
    *
    * @param printstream
    */
   public void printStackTrace(PrintStream printstream) {

      super.printStackTrace(printstream);

      if (this.originalException != null) {
         this.originalException.printStackTrace(printstream);
      }
   }

   /**
    * Method getOriginalException
    *
    * @return the original exception
    */
   public Exception getOriginalException() {
      return originalException;
   }
}
