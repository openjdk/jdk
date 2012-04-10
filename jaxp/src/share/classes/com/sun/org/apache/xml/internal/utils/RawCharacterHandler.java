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
 * $Id: RawCharacterHandler.java,v 1.2.4.1 2005/09/15 08:15:52 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.utils;

/**
 * An interface that a Serializer/ContentHandler/ContentHandler must
 * implement in order for disable-output-escaping to work.
 * @xsl.usage advanced
 */
public interface RawCharacterHandler
{

  /**
   * Serialize the characters without escaping.
   *
   * @param ch Array of characters
   * @param start Start index of characters in the array
   * @param length Number of characters in the array
   *
   * @throws javax.xml.transform.TransformerException
   */
  public void charactersRaw(char ch[], int start, int length)
    throws javax.xml.transform.TransformerException;
}
