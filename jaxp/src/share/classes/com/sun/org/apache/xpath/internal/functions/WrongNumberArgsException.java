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
 * $Id: WrongNumberArgsException.java,v 1.2.4.1 2005/09/14 20:27:04 jeffsuttor Exp $
 */
package com.sun.org.apache.xpath.internal.functions;

/**
 * An exception that is thrown if the wrong number of arguments to an exception
 * are specified by the stylesheet.
 * @xsl.usage advanced
 */
public class WrongNumberArgsException extends Exception
{
    static final long serialVersionUID = -4551577097576242432L;

  /**
   * Constructor WrongNumberArgsException
   *
   * @param argsExpected Error message that tells the number of arguments that
   * were expected.
   */
  public WrongNumberArgsException(String argsExpected)
  {

    super(argsExpected);
  }
}
