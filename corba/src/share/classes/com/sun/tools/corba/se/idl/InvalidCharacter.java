/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl;

// NOTES:
// -D62023<daz> Cast char to byte for JDK 1.2 compatability.

import java.io.IOException;

public class InvalidCharacter extends IOException
{
  public InvalidCharacter (String filename, String line, int lineNumber, int pos, char ch)
  {
    String pointer = "^";
    if (pos > 1)
    {
      byte[] bytes = new byte [pos - 1];
      for (int i = 0; i < pos - 1; ++i)
        bytes[i] = (byte)' ';  // <d62023>
      pointer = new String (bytes) + pointer;
    }
    String[] parameters = {filename, Integer.toString (lineNumber), "" + ch, Integer.toString ((int)ch), line, pointer};
    message = Util.getMessage ("InvalidCharacter.1", parameters);
  }

  public String getMessage ()
  {
    return message;
  } // getMessage

  private String message = null;
} // class InvalidCharacter
