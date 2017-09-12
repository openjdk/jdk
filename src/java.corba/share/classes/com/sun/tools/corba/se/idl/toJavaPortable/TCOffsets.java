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
 * COMPONENT_NAME: idl.toJava
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.toJavaPortable;

// NOTES:

import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.*;

// This class is passed through the JavaGenerator.HelperType methods.
// It is ONLY used when a recursive sequence is detected. ie.
//
//   struct S1
//   {
//     sequence <S1> others;
//   };

/**
 *
 **/
public class TCOffsets
{
  /**
   * Return -1 if the given name is not in the list of types.
   **/
  public int offset (String name)
  {
    Integer value = (Integer)tcs.get (name);
    return value == null ? -1 : value.intValue ();
  } // offset

  /**
   *
   **/
  public void set (SymtabEntry entry)
  {
    if (entry == null)
      offset += 8;
    else
    {
      tcs.put (entry.fullName (), new Integer (offset));
      offset += 4;
      String repID = Util.stripLeadingUnderscoresFromID (entry.repositoryID ().ID ());
      if (entry instanceof InterfaceEntry)
        offset += alignStrLen (repID) + alignStrLen (entry.name ());
      else if (entry instanceof StructEntry)
        offset += alignStrLen (repID) + alignStrLen (entry.name ()) + 4;
      else if (entry instanceof UnionEntry)
        offset += alignStrLen (repID) + alignStrLen (entry.name ()) + 12;
      else if (entry instanceof EnumEntry)
      {
        offset += alignStrLen (repID) + alignStrLen (entry.name ()) + 4;
        Enumeration e = ((EnumEntry)entry).elements ().elements ();
        while (e.hasMoreElements ())
          offset += alignStrLen ((String)e.nextElement ());
      }
      else if (entry instanceof StringEntry)
        offset += 4;
      else if (entry instanceof TypedefEntry)
      {
        offset += alignStrLen (repID) + alignStrLen (entry.name ());
        if (((TypedefEntry)entry).arrayInfo ().size () != 0)
          offset += 8;
      }
    }
  } // set

  /**
   * Return the full length of the string type:  4 byte length, x bytes for
   * string + 1 for the null terminator, align it so it ends on a 4-byte
   * boundary.  This method assumes the string starts at a 4-byte boundary
   * since it doesn't do any leading alignment.
   **/
  public int alignStrLen (String string)
  {
    int len = string.length () + 1;
    int align = 4 - (len % 4);
    if (align == 4) align = 0;
    return len + align + 4;
  } // alignStrLen

  /**
   *
   **/
  public void setMember (SymtabEntry entry)
  {
    offset += alignStrLen (entry.name ());
    if (((TypedefEntry)entry).arrayInfo ().size () != 0)
      offset += 4;
  } // setMember

  /**
   *
   **/
  public int currentOffset ()
  {
    return offset;
  } // currentOffset

  /**
   *
   **/
  public void bumpCurrentOffset (int value)
  {
    offset += value;
  } // bumpCurrentOffset

  private Hashtable tcs    = new Hashtable ();
  private int       offset = 0;
} // class TCOffsets
