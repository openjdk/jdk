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

import java.security.MessageDigest;
import java.util.Hashtable;

/**
 *
 **/
public class ValueRepositoryId
{
  private MessageDigest sha;       // Message digest used to compute SHA-1
  private int           index;     // Current index in the 'logical' sequence
  private Hashtable     types;     // Already processed types
  private String        hashcode;  // The computed hashcode

  public ValueRepositoryId ()
  {
    try
    {
      sha = MessageDigest.getInstance ("SHA-1");
    }
    catch (Exception exception)
    {}
    index    = 0;
    types    = new Hashtable ();
    hashcode = null;
  } // ctor

  /**Add a value to the hashcode being computed.
     @param value the value to be added to the value RepositoryID. */
  public void addValue (int value)
  {
    sha.update ((byte)((value >> 24) & 0x0F));
    sha.update ((byte)((value >> 16) & 0x0F));
    sha.update ((byte)((value >>  8) & 0x0F));
    sha.update ((byte)(value & 0x0F));
    index++;
  } // addValue

  /** Add a type to the list of types which have already been included.
      Note that the type should be added prior to its value.
      @param entry the type to be added to the value RepositoryID. */
  public void addType (SymtabEntry entry)
  {
    types.put (entry, new Integer (index));
  }

  /** Check to see if a specified type has already been processed. If so,
      add the appropriate 'previously processed' code (0xFFFFFFFF) and
      sequence offset, and return false; otherwise add the symbol table entry
      and current offset to the hashtable and return false.
      @param entry the type to be checked
      @return true if the symbol table entry has not been previously added;
       and false otherwise. */
  public boolean isNewType (SymtabEntry entry)
  {
    Object index = types.get (entry);
    if (index == null)
    {
      addType (entry);
      return true;
    }
    addValue (0xFFFFFFFF);
    addValue (((Integer)index).intValue ());
    return false;
  } // isNewType

  /** Get the hashcode computed for the value type. This method MUST not be
      called until all fields have been added, since it computes the hash
      code from the values entered for each field.
      @return the 64 bit hashcode for the value type represented as a
       16 character hexadecimal string. */
  public String getHashcode ()
  {
    if (hashcode == null)
    {
      byte [] digest = sha.digest ();
      hashcode = hexOf (digest[0]) + hexOf (digest[1]) +
                 hexOf (digest[2]) + hexOf (digest[3]) +
                 hexOf (digest[4]) + hexOf (digest[5]) +
                 hexOf (digest[6]) + hexOf (digest[7]);
    }
    return hashcode;
  } // getHashCode

  // Convert a byte to a two character hex string:
  private static String hexOf (byte value)
  {
    int d1 = (value >> 4) & 0x0F;
    int d2 = value & 0x0F;
    return "0123456789ABCDEF".substring (d1, d1 + 1) +
           "0123456789ABCDEF".substring (d2, d2 + 1);
  } // hexOf
} // class ValueRepositoryId
