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

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GenFileStream extends PrintWriter
{
  public GenFileStream (String filename)
  {
    // What I really want to do here is:
    // super (byteStream = new ByteArrayOutputStream ());
    // but that isn't legal.  The super constructor MUST
    // be called before any instance variables are used.
    // This implementation gets around that problem.
    // <f49747.1>
    //super (tmpByteStream = new ByteArrayOutputStream ());
    //byteStream = tmpByteStream;
    super (tmpCharArrayWriter = new CharArrayWriter());
    charArrayWriter = tmpCharArrayWriter;
    name = filename;
  } // ctor

  public void close ()
  {
    File file = new File (name);
    try
    {
      if (checkError ())
        throw new IOException ();
      // <f49747.1>
      //FileOutputStream fileStream = new FileOutputStream (file);
      //fileStream.write (byteStream.toByteArray ());
      //fileStream.close ();
      FileWriter fileWriter = new FileWriter (file);
      fileWriter.write (charArrayWriter.toCharArray ());
      fileWriter.close ();
    }
    catch (IOException e)
    {
      String[] parameters = {name, e.toString ()};
      System.err.println (Util.getMessage ("GenFileStream.1", parameters));
    }
    super.close ();
  } // close

  public String name ()
  {
    return name;
  } // name

  // <f49747.1>
  //private ByteArrayOutputStream        byteStream;
  //private static ByteArrayOutputStream tmpByteStream;
  private        CharArrayWriter    charArrayWriter;
  private static CharArrayWriter tmpCharArrayWriter;
  private String                       name;
} // GenFileStream
