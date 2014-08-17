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
 * The <CODE>BerDecoder</CODE> class is used for decoding
 * BER-encoded data.
 *
 * A <CODE>BerDecoder</CODE> needs to be set up with the byte string containing
 * the encoding. It maintains a current position in the byte string.
 *
 * Methods allows to fetch integer, string, OID, etc., from the current
 * position. After a fetch the current position is moved forward.
 *
 * A fetch throws a <CODE>BerException</CODE> if the encoding is not of the
 * expected type.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 *
 * @since 1.5
 */

public class BerDecoder {

  /**
  * Constructs a new decoder and attaches it to the specified byte string.
  *
  * @param b The byte string containing the encoded data.
  */

  public BerDecoder(byte b[]) {
    bytes = b ;
    reset() ;
  }

  public void reset() {
    next = 0 ;
    stackTop = 0 ;
  }

  /**
  * Fetch an integer.
  *
  * @return The decoded integer.
  *
  * @exception BerException Current position does not point to an integer.
  */

  public int fetchInteger() throws BerException {
    return fetchInteger(IntegerTag) ;
  }


  /**
  * Fetch an integer with the specified tag.
  *
  * @param tag The expected tag.
  *
  * @return The decoded integer.
  *
  * @exception BerException Current position does not point to an integer
  *                         or the tag is not the expected one.
  */

  public int fetchInteger(int tag) throws BerException {
    int result = 0 ;
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      result = fetchIntegerValue() ;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }

    return result ;
  }



  /**
  * Fetch an integer and return a long value.
  *
  * @return The decoded integer.
  *
  * @exception BerException Current position does not point to an integer.
  */

  public long fetchIntegerAsLong() throws BerException {
    return fetchIntegerAsLong(IntegerTag) ;
  }


  /**
  * Fetch an integer with the specified tag and return a long value.
  *
  * @param tag The expected tag.
  *
  * @return The decoded integer.
  *
  * @exception BerException Current position does not point to an integer
  *                         or the tag is not the expected one.
  */

  public long fetchIntegerAsLong(int tag) throws BerException {
    long result = 0 ;
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      result = fetchIntegerValueAsLong() ;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }

    return result ;
  }



  /**
  * Fetch an octet string.
  *
  * @return The decoded string.
  *
  * @exception BerException Current position does not point to an octet string.
  */

  public byte[] fetchOctetString() throws BerException {
    return fetchOctetString(OctetStringTag) ;
  }


  /**
  * Fetch an octet string with a specified tag.
  *
  * @param tag The expected tag.
  *
  * @return The decoded string.
  *
  * @exception BerException Current position does not point to an octet string
  *                         or the tag is not the expected one.
  */

  public byte[] fetchOctetString(int tag) throws BerException {
    byte[] result = null ;
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      result = fetchStringValue() ;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }

    return result ;
  }


  /**
  * Fetch an object identifier.
  *
  * @return The decoded object identifier as an array of long.
  */

  public long[] fetchOid() throws BerException {
    return fetchOid(OidTag) ;
  }


  /**
  * Fetch an object identifier with a specified tag.
  *
  * @param tag The expected tag.
  *
  * @return The decoded object identifier as an array of long.
  *
  * @exception BerException Current position does not point to an oid
  *                         or the tag is not the expected one.
  */

  public long[] fetchOid(int tag) throws BerException {
    long[] result = null ;
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      result = fetchOidValue() ;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }

    return result ;
  }


  /**
  * Fetch a <CODE>NULL</CODE> value.
  *
  * @exception BerException Current position does not point to <CODE>NULL</CODE> value.
  */

  public void fetchNull() throws BerException {
    fetchNull(NullTag) ;
  }


  /**
  * Fetch a <CODE>NULL</CODE> value with a specified tag.
  *
  * @param tag The expected tag.
  *
  * @exception BerException Current position does not point to
  *            <CODE>NULL</CODE> value or the tag is not the expected one.
  */

  public void fetchNull(int tag) throws BerException {
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      final int length = fetchLength();
      if (length != 0) throw new BerException();
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }
  }



  /**
  * Fetch an <CODE>ANY</CODE> value. In fact, this method does not decode anything
  * it simply returns the next TLV as an array of bytes.
  *
  * @return The TLV as a byte array.
  *
  * @exception BerException The next TLV is really badly encoded...
  */

  public byte[] fetchAny() throws BerException {
    byte[] result = null ;
    final int backup = next ;
    try {
      final int tag = fetchTag() ;
      final int contentLength = fetchLength() ;
      if (contentLength < 0) throw new BerException() ;
      final int tlvLength = next + contentLength - backup ;
      if (contentLength > (bytes.length - next))
          throw new IndexOutOfBoundsException("Decoded length exceeds buffer");
      final byte[] data = new byte[tlvLength] ;
      java.lang.System.arraycopy(bytes,backup,data,0,tlvLength);
      // for (int i = 0 ; i < tlvLength ; i++) {
      //  data[i] = bytes[backup + i] ;
      // }
      next = next + contentLength ;
      result = data;
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }
    // catch(Error e) {
    //    debug("fetchAny: Error decoding BER: " + e);
    //    throw e;
    // }

    return result ;
  }


  /**
  * Fetch an <CODE>ANY</CODE> value with a specific tag.
  *
  * @param tag The expected tag.
  *
  * @return The TLV as a byte array.
  *
  * @exception BerException The next TLV is really badly encoded...
  */

  public byte[] fetchAny(int tag) throws BerException {
    if (getTag() != tag) {
      throw new BerException() ;
    }
    return fetchAny() ;
  }



  /**
  * Fetch a sequence header.
  * The decoder computes the end position of the sequence and push it
  * on its stack.
  *
  * @exception BerException Current position does not point to a sequence header.
  */

  public void openSequence() throws BerException {
    openSequence(SequenceTag) ;
  }


  /**
  * Fetch a sequence header with a specific tag.
  *
  * @param tag The expected tag.
  *
  * @exception BerException Current position does not point to a sequence header
  *                         or the tag is not the expected one.
  */

  public void openSequence(int tag) throws BerException {
    final int backup = next ;
    try {
      if (fetchTag() != tag) {
        throw new BerException() ;
      }
      final int l = fetchLength() ;
      if (l < 0) throw new BerException();
      if (l > (bytes.length - next)) throw new BerException();
      stackBuf[stackTop++] = next + l ;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }
  }


  /**
  * Close a sequence.
  * The decode pull the stack and verifies that the current position
  * matches with the calculated end of the sequence. If not it throws
  * an exception.
  *
  * @exception BerException The sequence is not expected to finish here.
  */

  public void closeSequence() throws BerException {
    if (stackBuf[stackTop - 1] == next) {
      stackTop-- ;
    }
    else {
      throw new BerException() ;
    }
  }


  /**
  * Return <CODE>true</CODE> if the end of the current sequence is not reached.
  * When this method returns <CODE>false</CODE>, <CODE>closeSequence</CODE> can (and must) be
  * invoked.
  *
  * @return <CODE>true</CODE> if there is still some data in the sequence.
  */

  public boolean cannotCloseSequence() {
    return (next < stackBuf[stackTop - 1]) ;
  }


  /**
  * Get the tag of the data at the current position.
  * Current position is unchanged.
  *
  * @return The next tag.
  */

  public int getTag() throws BerException {
    int result = 0 ;
    final int backup = next ;
    try {
      result = fetchTag() ;
    }
    finally {
      next = backup ;
    }

    return result ;
  }



  public String toString() {
    final StringBuffer result = new StringBuffer(bytes.length * 2) ;
    for (int i = 0 ; i < bytes.length ; i++) {
      final int b = (bytes[i] > 0) ? bytes[i] : bytes[i] + 256 ;
      if (i == next) {
        result.append("(") ;
      }
      result.append(Character.forDigit(b / 16, 16)) ;
      result.append(Character.forDigit(b % 16, 16)) ;
      if (i == next) {
        result.append(")") ;
      }
    }
    if (bytes.length == next) {
      result.append("()") ;
    }

    return new String(result) ;
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




  ////////////////////////// PRIVATE ///////////////////////////////



  /**
  * Fetch a tag and move the current position forward.
  *
  * @return The tag
  */

  private final int fetchTag() throws BerException {
    int result = 0 ;
    final int backup = next ;

    try {
      final byte b0 = bytes[next++] ;
      result = (b0 >= 0) ? b0 : b0 + 256 ;
      if ((result & 31) == 31) {
        while ((bytes[next] & 128) != 0) {
          result = result << 7 ;
          result = result | (bytes[next++] & 127);
        }
      }
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }

    return result ;
  }


  /**
  * Fetch a length and move the current position forward.
  *
  * @return The length
  */

  private final int fetchLength() throws BerException {
    int result = 0 ;
    final int backup = next ;

    try {
      final byte b0 = bytes[next++] ;
      if (b0 >= 0) {
        result = b0 ;
      }
      else {
        for (int c = 128 + b0 ; c > 0 ; c--) {
          final byte bX = bytes[next++] ;
          result = result << 8 ;
          result = result | ((bX >= 0) ? bX : bX+256) ;
        }
      }
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }

    return result ;
  }


  /**
  * Fetch an integer value and move the current position forward.
  *
  * @return The integer
  */

  private int fetchIntegerValue() throws BerException {
    int result = 0 ;
    final int backup = next ;

    try {
      final int length = fetchLength() ;
      if (length <= 0) throw new BerException() ;
      if (length > (bytes.length - next)) throw
          new IndexOutOfBoundsException("Decoded length exceeds buffer");
      final int end = next + length ;
      result = bytes[next++] ;
      while (next < end) {
        final byte b = bytes[next++] ;
        if (b < 0) {
          result = (result << 8) | (256 + b) ;
        }
        else {
          result = (result << 8) | b ;
        }
      }
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }
    catch(ArithmeticException e) {
      next = backup ;
      throw new BerException() ;
    }
    return result ;
  }


  /**
  * Fetch an integer value and return a long value.
  * FIX ME: someday we could have only on fetchIntegerValue() which always
  * returns a long value.
  *
  * @return The integer
  */

  private final long fetchIntegerValueAsLong() throws BerException {
    long result = 0 ;
    final int backup = next ;

    try {
      final int length = fetchLength() ;
      if (length <= 0) throw new BerException() ;
      if (length > (bytes.length - next)) throw
          new IndexOutOfBoundsException("Decoded length exceeds buffer");

      final int end = next + length ;
      result = bytes[next++] ;
      while (next < end) {
        final byte b = bytes[next++] ;
        if (b < 0) {
          result = (result << 8) | (256 + b) ;
        }
        else {
          result = (result << 8) | b ;
        }
      }
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }
    catch(ArithmeticException e) {
      next = backup ;
      throw new BerException() ;
    }
    return result ;
  }


  /**
  * Fetch a byte string and move the current position forward.
  *
  * @return The byte string
  */

  private byte[] fetchStringValue() throws BerException {
    byte[] result = null ;
    final int backup = next ;

    try {
      final int length = fetchLength() ;
      if (length < 0) throw new BerException() ;
      if (length > (bytes.length - next))
          throw new IndexOutOfBoundsException("Decoded length exceeds buffer");
      final byte data[] = new byte[length] ;
      java.lang.System.arraycopy(bytes,next,data,0,length);
      next += length;
      //      int i = 0 ;
      //      while (i < length) {
      //          result[i++] = bytes[next++] ;
      //      }
      result = data;
    }
    catch(BerException e) {
        next = backup ;
      throw e ;
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }
    catch(ArithmeticException e) {
      next = backup ;
      throw new BerException() ;
    }
    // catch(Error e) {
    //  debug("fetchStringValue: Error decoding BER: " + e);
    //  throw e;
    // }

    return result ;
  }



  /**
  * Fetch an oid and move the current position forward.
  *
  * @return The oid
  */

  private final long[] fetchOidValue() throws BerException {
    long[] result = null ;
    final int backup = next ;

    try {
      final int length = fetchLength() ;
      if (length <= 0) throw new BerException() ;
      if (length > (bytes.length - next))
          throw new IndexOutOfBoundsException("Decoded length exceeds buffer");
      // Count how many bytes have their 8th bit to 0
      // -> this gives the number of components in the oid
      int subidCount = 2 ;
      for (int i = 1 ; i < length ; i++) {
        if ((bytes[next + i] & 0x80) == 0) {
          subidCount++ ;
        }
      }
      final int datalen = subidCount;
      final long[] data = new long[datalen];
      final byte b0 = bytes[next++] ;

      // bugId 4641746
      // The 8th bit of the first byte should always be set to 0
      if (b0 < 0) throw new BerException();

      // bugId 4641746
      // The first sub Id cannot be greater than 2
      final long lb0 =  b0 / 40 ;
      if (lb0 > 2) throw new BerException();

      final long lb1 = b0 % 40;
      data[0] = lb0 ;
      data[1] = lb1 ;
      int i = 2 ;
      while (i < datalen) {
        long subid = 0 ;
        byte b = bytes[next++] ;
        while ((b & 0x80) != 0) {
          subid = (subid << 7) | (b & 0x7f) ;
          // bugId 4654674
          if (subid < 0) throw new BerException();
          b = bytes[next++] ;
        }
        subid = (subid << 7) | b ;
        // bugId 4654674
        if (subid < 0) throw new BerException();
        data[i++] = subid ;
      }
      result = data;
    }
    catch(BerException e) {
      next = backup ;
      throw e ;
    }
    catch(IndexOutOfBoundsException e) {
      next = backup ;
      throw new BerException() ;
    }
    // catch(Error e) {
    //  debug("fetchOidValue: Error decoding BER: " + e);
    //  throw e;
    // }

    return result ;
  }

    // private static final void debug(String str) {
    //   System.out.println(str);
    // }

  //
  // This is the byte array containing the encoding.
  //
  private final byte bytes[];

  //
  // This is the current location. It is the next byte
  // to be decoded. It's an index in bytes[].
  //
  private int next = 0 ;

  //
  // This is the stack where end of sequences are kept.
  // A value is computed and pushed in it each time openSequence()
  // is invoked.
  // A value is pulled and checked each time closeSequence() is called.
  //
  private final int stackBuf[] = new int[200] ;
  private int stackTop = 0 ;

}
