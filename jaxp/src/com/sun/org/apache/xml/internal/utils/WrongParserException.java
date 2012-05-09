/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: WrongParserException.java,v 1.2.4.1 2005/09/15 08:16:00 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.utils;

/**
 * Certain functions may throw this error if they are paired with
 * the incorrect parser.
 * @xsl.usage general
 */
public class WrongParserException extends RuntimeException
{
    static final long serialVersionUID = 6481643018533043846L;

  /**
   * Create a WrongParserException object.
   * @param message The error message that should be reported to the user.
   */
  public WrongParserException(String message)
  {
    super(message);
  }
}
