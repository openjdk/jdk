/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.resolver;

/**
 * Signal Catalog exception.
 *
 * <p>This exception is thrown if an error occurs loading a
 * catalog file.</p>
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class CatalogException extends Exception {

  private static final long serialVersionUID = 4007157171817798450L;

  /** A wrapper around another exception */
  public static final int WRAPPER = 1;
  /** An invalid entry */
  public static final int INVALID_ENTRY = 2;
  /** An invalid entry type */
  public static final int INVALID_ENTRY_TYPE = 3;
  /** Could not instantiate an XML parser */
  public static final int NO_XML_PARSER = 4;
  /** Unknown XML format */
  public static final int UNKNOWN_FORMAT = 5;
  /** Unparseable XML catalog (not XML)*/
  public static final int UNPARSEABLE = 6;
  /** XML but parse failed */
  public static final int PARSE_FAILED = 7;
  /** Text catalog ended in mid-comment */
  public static final int UNENDED_COMMENT = 8;

  /**
   * The embedded exception if tunnelling, or null.
   */
  private final Exception exception;
  private final int exceptionType;

  /**
   * Create a new CatalogException.
   *
   * @param type The exception type
   * @param message The error or warning message.
   */
  public CatalogException (int type, String message) {
    super(message);
    this.exceptionType = type;
    this.exception = null;
  }

  /**
   * Create a new CatalogException.
   *
   * @param type The exception type
   */
  public CatalogException (int type) {
    super("Catalog Exception " + type);
    this.exceptionType = type;
    this.exception = null;
  }

  /**
   * Create a new CatalogException wrapping an existing exception.
   *
   * <p>The existing exception will be embedded in the new
   * one, and its message will become the default message for
   * the CatalogException.</p>
   *
   * @param e The exception to be wrapped in a CatalogException.
   */
  public CatalogException (Exception e) {
    super();
    this.exceptionType = WRAPPER;
    this.exception = e;
  }

  /**
   * Create a new CatalogException from an existing exception.
   *
   * <p>The existing exception will be embedded in the new
   * one, but the new exception will have its own message.</p>
   *
   * @param message The detail message.
   * @param e The exception to be wrapped in a CatalogException.
   */
  public CatalogException (String message, Exception e) {
    super(message);
    this.exceptionType = WRAPPER;
    this.exception = e;
  }

  /**
   * Return a detail message for this exception.
   *
   * <p>If there is an embedded exception, and if the CatalogException
   * has no detail message of its own, this method will return
   * the detail message from the embedded exception.</p>
   *
   * @return The error or warning message.
   */
  public String getMessage ()
  {
    String message = super.getMessage();

    if (message == null && exception != null) {
      return exception.getMessage();
    } else {
      return message;
    }
  }

  /**
   * Return the embedded exception, if any.
   *
   * @return The embedded exception, or null if there is none.
   */
  public Exception getException ()
  {
    return exception;
  }

  /**
   * Return the exception type
   *
   * @return The exception type
   */
  public int getExceptionType ()
  {
    return exceptionType;
  }

  /**
   * Override toString to pick up any embedded exception.
   *
   * @return A string representation of this exception.
   */
  public String toString ()
  {
    if (exception != null) {
      return exception.toString();
    } else {
      return super.toString();
    }
  }
}
