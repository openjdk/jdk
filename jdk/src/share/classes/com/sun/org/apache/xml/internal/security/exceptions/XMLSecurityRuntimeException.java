package com.sun.org.apache.xml.internal.security.exceptions;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;

import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.I18n;

/**
 * The mother of all runtime Exceptions in this bundle. It allows exceptions to have
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
public class XMLSecurityRuntimeException
        extends RuntimeException {
   /**
     *
     */
    private static final long serialVersionUID = 1L;

   /** Field originalException */
   protected Exception originalException = null;

   /** Field msgID */
   protected String msgID;

   /**
    * Constructor XMLSecurityRuntimeException
    *
    */
   public XMLSecurityRuntimeException() {

      super("Missing message string");

      this.msgID = null;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityRuntimeException
    *
    * @param _msgID
    */
   public XMLSecurityRuntimeException(String _msgID) {

      super(I18n.getExceptionMessage(_msgID));

      this.msgID = _msgID;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityRuntimeException
    *
    * @param _msgID
    * @param exArgs
    */
   public XMLSecurityRuntimeException(String _msgID, Object exArgs[]) {

      super(MessageFormat.format(I18n.getExceptionMessage(_msgID), exArgs));

      this.msgID = _msgID;
      this.originalException = null;
   }

   /**
    * Constructor XMLSecurityRuntimeException
    *
    * @param _originalException
    */
   public XMLSecurityRuntimeException(Exception _originalException) {

      super("Missing message ID to locate message string in resource bundle \""
            + Constants.exceptionMessagesResourceBundleBase
            + "\". Original Exception was a "
            + _originalException.getClass().getName() + " and message "
            + _originalException.getMessage());

      this.originalException = _originalException;
   }

   /**
    * Constructor XMLSecurityRuntimeException
    *
    * @param _msgID
    * @param _originalException
    */
   public XMLSecurityRuntimeException(String _msgID, Exception _originalException) {

      super(I18n.getExceptionMessage(_msgID, _originalException));

      this.msgID = _msgID;
      this.originalException = _originalException;
   }

   /**
    * Constructor XMLSecurityRuntimeException
    *
    * @param _msgID
    * @param exArgs
    * @param _originalException
    */
   public XMLSecurityRuntimeException(String _msgID, Object exArgs[],
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
