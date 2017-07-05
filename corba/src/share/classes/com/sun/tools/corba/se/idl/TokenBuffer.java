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

class TokenBuffer
{
  private final int DEFAULT_SIZE = 10;

  private int   _size      = 0;
  private Token _buffer [] = null;
  private int   _currPos   = -1;

  TokenBuffer ()
  {
    _size    = DEFAULT_SIZE;
    _buffer  = new Token [_size];
    _currPos = -1;
  } // ctor

  TokenBuffer (int size) throws Exception
  {
    _size    = size;   // _size == 0 is legal, but useless and problematic
    _buffer  = new Token [_size];
    _currPos = -1;
  } // ctor

  /** Inserts a token at the head of the buffer. */
  void insert (Token token)
  {
    // _size == 0 ==> ArithmeticException: divide by zero
    _currPos = ++_currPos % _size;
    _buffer [_currPos] = token;
  }

  /** Returns the token residing "i" elements from the head of the buffer. */
  Token lookBack (int i)
  {
    // Beware: i > _size ==> idx < 0 ==> ArrayOutOfBoundsException
    return _buffer [(_currPos - i) >= 0 ? _currPos - i : _currPos - i + _size];
  }

  /** Return the token most recently inserted into the buffer (i.e., the head of the buffer.) */
  Token current ()
  {
    // Beware: _buffer empty || _size == 0 ==> ArrayOutOfBoundsException
    return _buffer [_currPos];
  }
}   // class TokenBuffer


/*==================================================================================
  DATE<AUTHOR>   ACTION
  ----------------------------------------------------------------------------------
  11aug1997<daz> Initial version completed.  Buffer used to maintain history of
                 comments extracted from source file during parse.
  ==================================================================================*/
