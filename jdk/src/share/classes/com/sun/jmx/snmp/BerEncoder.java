/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp;


/**
 * The <CODE>BerEncoder</CODE> class is used for encoding data using BER.
 *
 * A <CODE>BerEncoder</CODE> needs to be set up with a byte buffer. The encoded
 * data are stored in this byte buffer.
 * <P>
 * NOTE : the buffer is filled from end to start. This means the caller
 * needs to encode its data in the reverse order.
 *
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 *
 * @since 1.5
 */

public class BerEncoder {

  /**
  * Constructs a new encoder and attaches it to the specified byte string.
  *
  * @param b The byte string containing the encoded data.
  */

  public BerEncoder(byte b[]) {
    bytes = b ;
    start = b.length ;
    stackTop = 0 ;
  }


  /**
  * Trim the encoding data and returns the length of the encoding.
  *
  * The encoder does backward encoding : so the bytes buffer is
  * filled from end to start. The encoded data must be shift before
  * the buffer can be used. This is the purpose of the <CODE>trim</CODE> method.
  *
  * After a call to the <CODE>trim</CODE> method, the encoder is reinitialized and <CODE>putXXX</CODE>
  * overwrite any existing encoded data.
  *
  * @return The length of the encoded data.
  */

  public int trim() {
    final int result = bytes.length - start ;

    // for (int i = start ; i < bytes.length ; i++) {
    //  bytes[i-start] = bytes[i] ;
    // }
    if (result > 0)
        java.lang.System.arraycopy(bytes,start,bytes,0,result);

    start = bytes.length ;
    stackTop = 0 ;

    return result ;
  }

  /**
  * Put an integer.
  *
  * @param v The integer to encode.
  */

  public void putInteger(int v) {
    putInteger(v, IntegerTag) ;
  }


  /**
  * Put an integer with the specified tag.
  *
  * @param v The integer to encode.
  * @param tag The tag to encode.
  */

  public void putInteger(int v, int tag) {
    putIntegerValue(v) ;
    putTag(tag) ;
  }



  /**
  * Put an integer expressed as a long.
  *
  * @param v The long to encode.
  */

  public void putInteger(long v) {
    putInteger(v, IntegerTag) ;
  }


  /**
  * Put an integer expressed as a long with the specified tag.
  *
  * @param v The long to encode
  * @param tag The tag to encode.
  */

  public void putInteger(long v, int tag) {
    putIntegerValue(v) ;
    putTag(tag) ;
  }



  /**
  * Put an octet string.
  *
  * @param s The bytes to encode
  */

  public void putOctetString(byte[] s) {
    putOctetString(s, OctetStringTag) ;
  }


  /**
  * Put an octet string with a specified tag.
  *
  * @param s The bytes to encode
  * @param tag The tag to encode.
  */

  public void putOctetString(byte[] s, int tag) {
    putStringValue(s) ;
    putTag(tag) ;
  }


  /**
  * Put an object identifier.
  *
  * @param s The oid to encode.
  */

  public void putOid(long[] s) {
    putOid(s, OidTag) ;
  }


  /**
  * Put an object identifier with a specified tag.
  *
  * @param s The integer to encode.
  * @param tag The tag to encode.
  */

  public void putOid(long[] s, int tag) {
    putOidValue(s) ;
    putTag(tag) ;
  }


  /**
  * Put a <CODE>NULL</CODE> value.
  */

  public void putNull() {
    putNull(NullTag) ;
  }


  /**
  * Put a <CODE>NULL</CODE> value with a specified tag.
  *
  * @param tag The tag to encode.
  */

  public void putNull(int tag) {
    putLength(0) ;
    putTag(tag) ;
  }



  /**
  * Put an <CODE>ANY</CODE> value. In fact, this method does not encode anything.
  * It simply copies the specified bytes into the encoding.
  *
  * @param s The encoding of the <CODE>ANY</CODE> value.
  */

  public void putAny(byte[] s) {
        putAny(s, s.length) ;
  }


  /**
  * Put an <CODE>ANY</CODE> value. Only the first <CODE>byteCount</CODE> are considered.
  *
  * @param s The encoding of the <CODE>ANY</CODE> value.
  * @param byteCount The number of bytes of the encoding.
  */

  public void putAny(byte[] s, int byteCount) {
      java.lang.System.arraycopy(s,0,bytes,start-byteCount,byteCount);
      start -= byteCount;
      //    for (int i = byteCount - 1 ; i >= 0 ; i--) {
      //      bytes[--start] = s[i] ;
      //    }
  }


  /**
  * Open a sequence.
  * The encoder push the current position on its stack.
  */

  public void openSequence() {
    stackBuf[stackTop++] = start ;
  }


  /**
  * Close a sequence.
  * The decode pull the stack to know the end of the current sequence.
  */

  public void closeSequence() {
    closeSequence(SequenceTag) ;
  }


  /**
  * Close a sequence with the specified tag.
  */

  public void closeSequence(int tag) {
    final int end = stackBuf[--stackTop] ;
    putLength(end - start) ;
    putTag(tag) ;
  }


  //
  // Some standard tags
  //
  public final static int BooleanTag      = 1 ;
  public final static int IntegerTag      = 2 ;
  public final static int OctetStringTag  = 4 ;
  public final static int NullTag          = 5 ;
  public final static int OidTag          = 6 ;
  public final static int SequenceTag      = 0x30 ;




  ////////////////////////// PROTECTED ///////////////////////////////



  /**
  * Put a tag and move the current position backward.
  *
  * @param tag The tag to encode.
  */

  protected final void putTag(int tag) {
    if (tag < 256) {
      bytes[--start] = (byte)tag ;
    }
    else {
      while (tag != 0) {
        bytes[--start] = (byte)(tag & 127) ;
        tag = tag << 7 ;
      }
    }
  }


  /**
  * Put a length and move the current position backward.
  *
  * @param length The length to encode.
  */

  protected final void putLength(final int length) {
    if (length < 0) {
      throw new IllegalArgumentException() ;
    }
    else if (length < 128) {
      bytes[--start] = (byte)length ;
    }
    else if (length < 256) {
      bytes[--start] = (byte)length ;
      bytes[--start] = (byte)0x81 ;
    }
    else if (length < 65536) {
      bytes[--start] = (byte)(length) ;
      bytes[--start] = (byte)(length >> 8) ;
      bytes[--start] = (byte)0x82 ;
    }
    else if (length < 16777126) {
      bytes[--start] = (byte)(length) ;
      bytes[--start] = (byte)(length >> 8) ;
      bytes[--start] = (byte)(length >> 16) ;
      bytes[--start] = (byte)0x83 ;
    }
    else {
      bytes[--start] = (byte)(length) ;
      bytes[--start] = (byte)(length >> 8) ;
      bytes[--start] = (byte)(length >> 16) ;
      bytes[--start] = (byte)(length >> 24) ;
      bytes[--start] = (byte)0x84 ;
    }
  }


  /**
  * Put an integer value and move the current position backward.
  *
  * @param v The integer to encode.
  */

  protected final void putIntegerValue(int v) {
    final int end = start ;
    int mask = 0x7f800000 ;
    int byteNeeded = 4 ;
    if (v < 0) {
      while (((mask & v) == mask) && (byteNeeded > 1)) {
        mask = mask >> 8 ;
        byteNeeded-- ;
      }
    }
    else {
      while (((mask & v) == 0) && (byteNeeded > 1)) {
        mask = mask >> 8 ;
        byteNeeded-- ;
      }
    }
    for (int i = 0 ; i < byteNeeded ; i++) {
      bytes[--start] = (byte)v ;
      v =  v >> 8 ;
    }
    putLength(end - start) ;
  }


  /**
  * Put an integer value expressed as a long.
  *
  * @param v The integer to encode.
  */

  protected final void putIntegerValue(long v) {
    final int end = start ;
    long mask = 0x7f80000000000000L ;
    int byteNeeded = 8 ;
    if (v < 0) {
      while (((mask & v) == mask) && (byteNeeded > 1)) {
        mask = mask >> 8 ;
        byteNeeded-- ;
      }
    }
    else {
      while (((mask & v) == 0) && (byteNeeded > 1)) {
        mask = mask >> 8 ;
        byteNeeded-- ;
      }
    }
    for (int i = 0 ; i < byteNeeded ; i++) {
      bytes[--start] = (byte)v ;
      v =  v >> 8 ;
    }
    putLength(end - start) ;
  }


  /**
  * Put a byte string and move the current position backward.
  *
  * @param s The byte string to encode.
  */

  protected final void putStringValue(byte[] s) {
      final int datalen = s.length;
      java.lang.System.arraycopy(s,0,bytes,start-datalen,datalen);
      start -= datalen;
      // for (int i = s.length - 1 ; i >= 0 ; i--) {
      //   bytes[--start] = s[i] ;
      // }
      putLength(datalen) ;
  }



  /**
  * Put an oid and move the current position backward.
  *
  * @param s The oid to encode.
  */

  protected final void putOidValue(final long[] s) {
      final int end = start ;
      final int slength = s.length;

      // bugId 4641746: 0, 1, and 2 are legal values.
      if ((slength < 2) || (s[0] > 2) || (s[1] >= 40)) {
          throw new IllegalArgumentException() ;
      }
      for (int i = slength - 1 ; i >= 2 ; i--) {
          long c = s[i] ;
          if (c < 0) {
              throw new IllegalArgumentException() ;
          }
          else if (c < 128) {
              bytes[--start] = (byte)c ;
          }
          else {
              bytes[--start] = (byte)(c & 127) ;
              c = c >> 7 ;
              while (c != 0) {
                  bytes[--start] = (byte)(c | 128) ;
                  c = c >> 7 ;
              }
          }
      }
      bytes[--start] = (byte)(s[0] * 40 + s[1]) ;
      putLength(end - start) ;
  }


  //
  // This is the byte array containing the encoding.
  //
  protected final byte bytes[];

  //
  // This is the index of the first byte of the encoding.
  // It is initialized to <CODE>bytes.length</CODE> and decrease each time
  // an value is put in the encoder.
  //
  protected int start = -1 ;

  //
  // This is the stack where end of sequences are kept.
  // A value is computed and pushed in it each time the <CODE>openSequence</CODE> method
  // is invoked.
  // A value is pulled and checked each time the <CODE>closeSequence</CODE> method is called.
  //
  protected final int stackBuf[] = new int[200] ;
  protected int stackTop = 0 ;

}
