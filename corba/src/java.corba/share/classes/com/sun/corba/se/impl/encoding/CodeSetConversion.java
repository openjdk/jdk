/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.encoding;

import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.charset.UnmappableCharacterException;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;
import com.sun.corba.se.spi.logging.CORBALogDomains;

/**
 * Collection of classes, interfaces, and factory methods for
 * CORBA code set conversion.
 *
 * This is mainly used to shield other code from the sun.io
 * converters which might change, as well as provide some basic
 * translation from conversion to CORBA error exceptions.  Some
 * extra work is required here to facilitate the way CORBA
 * says it uses UTF-16 as of the 00-11-03 spec.
 *
 * REVISIT - Since the nio.Charset and nio.Charset.Encoder/Decoder
 *           use NIO ByteBuffer and NIO CharBuffer, the interaction
 *           and interface between this class and the CDR streams
 *           should be looked at more closely for optimizations to
 *           avoid unnecessary copying of data between
 *           {@code char[] & CharBuffer} and
 *           {@code byte[] & ByteBuffer}, especially
 *           DirectByteBuffers.
 *
 */
public class CodeSetConversion
{
    /**
     * Abstraction for char to byte conversion.
     *
     * Must be used in the proper sequence:
     *
     * 1)  convert
     * 2)  Optional getNumBytes and/or getAlignment (if necessary)
     * 3)  getBytes (see warning)
     */
    public abstract static class CTBConverter
    {
        // Perform the conversion of the provided char or String,
        // allowing the caller to query for more information
        // before writing.
        public abstract void convert(char chToConvert);
        public abstract void convert(String strToConvert);

        // How many bytes resulted from the conversion?
        public abstract int getNumBytes();

        // What's the maximum number of bytes per character?
        public abstract float getMaxBytesPerChar();

        public abstract boolean isFixedWidthEncoding();

        // What byte boundary should the stream align to before
        // calling writeBytes?  For instance, a fixed width
        // encoding with 2 bytes per char in a stream which
        // doesn't encapsulate the char's bytes should align
        // on a 2 byte boundary.  (Ex:  UTF16 in GIOP1.1)
        //
        // Note: This has no effect on the converted bytes.  It
        // is just information available to the caller.
        public abstract int getAlignment();

        // Get the resulting bytes.  Warning:  You must use getNumBytes()
        // to determine the end of the data in the byte array instead
        // of array.length!  The array may be used internally, so don't
        // save references.
        public abstract byte[] getBytes();
    }

    /**
     * Abstraction for byte to char conversion.
     */
    public abstract static class BTCConverter
    {
        // In GIOP 1.1, interoperability can only be achieved with
        // fixed width encodings like UTF-16.  This is because wstrings
        // specified how many code points follow rather than specifying
        // the length in octets.
        public abstract boolean isFixedWidthEncoding();
        public abstract int getFixedCharWidth();

        // Called after getChars to determine the true size of the
        // converted array.
        public abstract int getNumChars();

        // Perform the conversion using length bytes from the given
        // input stream.  Warning:  You must use getNumChars() to
        // determine the correct length of the resulting array.
        // The same array may be used internally over multiple
        // calls.
        public abstract char[] getChars(byte[] bytes, int offset, int length);
    }

    /**
     * Implementation of CTBConverter which uses a nio.Charset.CharsetEncoder
     * to do the real work.  Handles translation of exceptions to the
     * appropriate CORBA versions.
     */
    private class JavaCTBConverter extends CTBConverter
    {
        private ORBUtilSystemException wrapper = ORBUtilSystemException.get(
            CORBALogDomains.RPC_ENCODING ) ;

        private OMGSystemException omgWrapper = OMGSystemException.get(
            CORBALogDomains.RPC_ENCODING ) ;

        // nio.Charset.CharsetEncoder actually does the work here
        // have to use it directly rather than through String's interface
        // because we want to know when errors occur during the conversion.
        private CharsetEncoder ctb;

        // Proper alignment for this type of converter.  For instance,
        // ASCII has alignment of 1 (1 byte per char) but UTF16 has
        // alignment of 2 (2 bytes per char)
        private int alignment;

        // Char buffer to hold the input.
        private char[] chars = null;

        // How many bytes are generated from the conversion?
        private int numBytes = 0;

        // How many characters were converted (temporary variable
        // for cross method communication)
        private int numChars = 0;

        // ByteBuffer holding the converted input.  This is necessary
        // since we have to do calculations that require the conversion
        // before writing the array to the stream.
        private ByteBuffer buffer;

        // What code set are we using?
        private OSFCodeSetRegistry.Entry codeset;

        public JavaCTBConverter(OSFCodeSetRegistry.Entry codeset,
                                int alignmentForEncoding) {

            try {
                ctb = cache.getCharToByteConverter(codeset.getName());
                if (ctb == null) {
                    Charset tmpCharset = Charset.forName(codeset.getName());
                    ctb = tmpCharset.newEncoder();
                    cache.setConverter(codeset.getName(), ctb);
                }
            } catch(IllegalCharsetNameException icne) {

                // This can only happen if one of our Entries has
                // an invalid name.
                throw wrapper.invalidCtbConverterName(icne,codeset.getName());
            } catch(UnsupportedCharsetException ucne) {

                // This can only happen if one of our Entries has
                // an unsupported name.
                throw wrapper.invalidCtbConverterName(ucne,codeset.getName());
            }

            this.codeset = codeset;
            alignment = alignmentForEncoding;
        }

        public final float getMaxBytesPerChar() {
            return ctb.maxBytesPerChar();
        }

        public void convert(char chToConvert) {
            if (chars == null)
                chars = new char[1];

            // The CharToByteConverter only takes a char[]
            chars[0] = chToConvert;
            numChars = 1;

            convertCharArray();
        }

        public void convert(String strToConvert) {
            // Try to save a memory allocation if possible.  Usual
            // space/time trade off.  If we could get the char[] out of
            // the String without copying, that would be great, but
            // it's forbidden since String is immutable.
            if (chars == null || chars.length < strToConvert.length())
                chars = new char[strToConvert.length()];

            numChars = strToConvert.length();

            strToConvert.getChars(0, numChars, chars, 0);

            convertCharArray();
        }

        public final int getNumBytes() {
            return numBytes;
        }

        public final int getAlignment() {
            return alignment;
        }

        public final boolean isFixedWidthEncoding() {
            return codeset.isFixedWidth();
        }

        public byte[] getBytes() {
            // Note that you can't use buffer.length since the buffer might
            // be larger than the actual number of converted bytes depending
            // on the encoding.
            return buffer.array();
        }

        private void convertCharArray() {
            try {

                // Possible optimization of directly converting into the CDR buffer.
                // However, that means the CDR code would have to reserve
                // a 4 byte string length ahead of time, and we'd need a
                // confusing partial conversion scheme for when we couldn't
                // fit everything in the buffer but needed to know the
                // converted length before proceeding due to fragmentation.
                // Then there's the issue of the chunking code.
                //
                // For right now, this is less messy and basic tests don't
                // show more than a 1 ms penalty worst case.  Less than a
                // factor of 2 increase.

                // Convert the characters
                buffer = ctb.encode(CharBuffer.wrap(chars,0,numChars));

                // ByteBuffer returned by the encoder will set its limit
                // to byte immediately after the last written byte.
                numBytes = buffer.limit();

            } catch (IllegalStateException ise) {
                // an encoding operation is already in progress
                throw wrapper.ctbConverterFailure( ise ) ;
            } catch (MalformedInputException mie) {
                // There were illegal Unicode char pairs
                throw wrapper.badUnicodePair( mie ) ;
            } catch (UnmappableCharacterException uce) {
                // A character doesn't map to the desired code set
                // CORBA formal 00-11-03.
                throw omgWrapper.charNotInCodeset( uce ) ;
            } catch (CharacterCodingException cce) {
                // If this happens, then some other encoding error occured
                throw wrapper.ctbConverterFailure( cce ) ;
            }
        }
    }

    /**
     * Special UTF16 converter which can either always write a BOM
     * or use a specified byte order without one.
     */
    private class UTF16CTBConverter extends JavaCTBConverter
    {
        // Using this constructor, we will always write a BOM
        public UTF16CTBConverter() {
            super(OSFCodeSetRegistry.UTF_16, 2);
        }

        // Using this constructor, we don't use a BOM and use the
        // byte order specified
        public UTF16CTBConverter(boolean littleEndian) {
            super(littleEndian ?
                  OSFCodeSetRegistry.UTF_16LE :
                  OSFCodeSetRegistry.UTF_16BE,
                  2);
        }
    }

    /**
     * Implementation of BTCConverter which uses a sun.io.ByteToCharConverter
     * for the real work.  Handles translation of exceptions to the
     * appropriate CORBA versions.
     */
    private class JavaBTCConverter extends BTCConverter
    {
        private ORBUtilSystemException wrapper = ORBUtilSystemException.get(
            CORBALogDomains.RPC_ENCODING ) ;

        private OMGSystemException omgWrapper = OMGSystemException.get(
            CORBALogDomains.RPC_ENCODING ) ;

        protected CharsetDecoder btc;
        private char[] buffer;
        private int resultingNumChars;
        private OSFCodeSetRegistry.Entry codeset;

        public JavaBTCConverter(OSFCodeSetRegistry.Entry codeset) {

            // Obtain a Decoder
            btc = this.getConverter(codeset.getName());

            this.codeset = codeset;
        }

        public final boolean isFixedWidthEncoding() {
            return codeset.isFixedWidth();
        }

        // Should only be called if isFixedWidthEncoding is true
        // IMPORTANT: This calls OSFCodeSetRegistry.Entry, not
        //            CharsetDecoder.maxCharsPerByte().
        public final int getFixedCharWidth() {
            return codeset.getMaxBytesPerChar();
        }

        public final int getNumChars() {
            return resultingNumChars;
        }

        public char[] getChars(byte[] bytes, int offset, int numBytes) {

            // Possible optimization of reading directly from the CDR
            // byte buffer.  The sun.io converter supposedly can handle
            // incremental conversions in which a char is broken across
            // two convert calls.
            //
            // Basic tests didn't show more than a 1 ms increase
            // worst case.  It's less than a factor of 2 increase.
            // Also makes the interface more difficult.


            try {

                ByteBuffer byteBuf = ByteBuffer.wrap(bytes, offset, numBytes);
                CharBuffer charBuf = btc.decode(byteBuf);

                // CharBuffer returned by the decoder will set its limit
                // to byte immediately after the last written byte.
                resultingNumChars = charBuf.limit();

                // IMPORTANT - It's possible the underlying char[] in the
                //             CharBuffer returned by btc.decode(byteBuf)
                //             is longer in length than the number of characters
                //             decoded. Hence, the check below to ensure the
                //             char[] returned contains all the chars that have
                //             been decoded and no more.
                if (charBuf.limit() == charBuf.capacity()) {
                    buffer = charBuf.array();
                } else {
                    buffer = new char[charBuf.limit()];
                    charBuf.get(buffer, 0, charBuf.limit()).position(0);
                }

                return buffer;

            } catch (IllegalStateException ile) {
                // There were a decoding operation already in progress
                throw wrapper.btcConverterFailure( ile ) ;
            } catch (MalformedInputException mie) {
                // There were illegal Unicode char pairs
                throw wrapper.badUnicodePair( mie ) ;
            } catch (UnmappableCharacterException uce) {
                // A character doesn't map to the desired code set.
                // CORBA formal 00-11-03.
                throw omgWrapper.charNotInCodeset( uce ) ;
            } catch (CharacterCodingException cce) {
                // If this happens, then a character decoding error occured.
                throw wrapper.btcConverterFailure( cce ) ;
            }
        }

        /**
         * Utility method to find a CharsetDecoder in the
         * cache or create a new one if necessary.  Throws an
         * INTERNAL if the code set is unknown.
         */
        protected CharsetDecoder getConverter(String javaCodeSetName) {

            CharsetDecoder result = null;
            try {
                result = cache.getByteToCharConverter(javaCodeSetName);

                if (result == null) {
                    Charset tmpCharset = Charset.forName(javaCodeSetName);
                    result = tmpCharset.newDecoder();
                    cache.setConverter(javaCodeSetName, result);
                }

            } catch(IllegalCharsetNameException icne) {
                // This can only happen if one of our charset entries has
                // an illegal name.
                throw wrapper.invalidBtcConverterName( icne, javaCodeSetName ) ;
            }

            return result;
        }
    }

    /**
     * Special converter for UTF16 since it's required to optionally
     * support a byte order marker while the internal Java converters
     * either require it or require that it isn't there.
     *
     * The solution is to check for the byte order marker, and if we
     * need to do something differently, switch internal converters.
     */
    private class UTF16BTCConverter extends JavaBTCConverter
    {
        private boolean defaultToLittleEndian;
        private boolean converterUsesBOM = true;

        private static final char UTF16_BE_MARKER = (char) 0xfeff;
        private static final char UTF16_LE_MARKER = (char) 0xfffe;

        // When there isn't a byte order marker, used the byte
        // order specified.
        public UTF16BTCConverter(boolean defaultToLittleEndian) {
            super(OSFCodeSetRegistry.UTF_16);

            this.defaultToLittleEndian = defaultToLittleEndian;
        }

        public char[] getChars(byte[] bytes, int offset, int numBytes) {

            if (hasUTF16ByteOrderMarker(bytes, offset, numBytes)) {
                if (!converterUsesBOM)
                    switchToConverter(OSFCodeSetRegistry.UTF_16);

                converterUsesBOM = true;

                return super.getChars(bytes, offset, numBytes);
            } else {
                if (converterUsesBOM) {
                    if (defaultToLittleEndian)
                        switchToConverter(OSFCodeSetRegistry.UTF_16LE);
                    else
                        switchToConverter(OSFCodeSetRegistry.UTF_16BE);

                    converterUsesBOM = false;
                }

                return super.getChars(bytes, offset, numBytes);
            }
        }

        /**
         * Utility method for determining if a UTF-16 byte order marker is present.
         */
        private boolean hasUTF16ByteOrderMarker(byte[] array, int offset, int length) {
            // If there aren't enough bytes to represent the marker and data,
            // return false.
            if (length >= 4) {

                int b1 = array[offset] & 0x00FF;
                int b2 = array[offset + 1] & 0x00FF;

                char marker = (char)((b1 << 8) | (b2 << 0));

                return (marker == UTF16_BE_MARKER || marker == UTF16_LE_MARKER);
            } else
                return false;
        }

        /**
         * The current solution for dealing with UTF-16 in CORBA
         * is that if our sun.io converter requires byte order markers,
         * and then we see a CORBA wstring/wchar without them, we
         * switch to the sun.io converter that doesn't require them.
         */
        private void switchToConverter(OSFCodeSetRegistry.Entry newCodeSet) {

            // Use the getConverter method from our superclass.
            btc = super.getConverter(newCodeSet.getName());
        }
    }

    /**
     * CTB converter factory for single byte or variable length encodings.
     */
    public CTBConverter getCTBConverter(OSFCodeSetRegistry.Entry codeset) {
        int alignment = (!codeset.isFixedWidth() ?
                         1 :
                         codeset.getMaxBytesPerChar());

        return new JavaCTBConverter(codeset, alignment);
    }

    /**
     * CTB converter factory for multibyte (mainly fixed) encodings.
     *
     * Because of the awkwardness with byte order markers and the possibility of
     * using UCS-2, you must specify both the endianness of the stream as well as
     * whether or not to use byte order markers if applicable.  UCS-2 has no byte
     * order markers.  UTF-16 has optional markers.
     *
     * If you select useByteOrderMarkers, there is no guarantee that the encoding
     * will use the endianness specified.
     *
     */
    public CTBConverter getCTBConverter(OSFCodeSetRegistry.Entry codeset,
                                        boolean littleEndian,
                                        boolean useByteOrderMarkers) {

        // UCS2 doesn't have byte order markers, and we're encoding it
        // as UTF-16 since UCS2 isn't available in all Java platforms.
        // They should be identical with only minor differences in
        // negative cases.
        if (codeset == OSFCodeSetRegistry.UCS_2)
            return new UTF16CTBConverter(littleEndian);

        // We can write UTF-16 with or without a byte order marker.
        if (codeset == OSFCodeSetRegistry.UTF_16) {
            if (useByteOrderMarkers)
                return new UTF16CTBConverter();
            else
                return new UTF16CTBConverter(littleEndian);
        }

        // Everything else uses the generic JavaCTBConverter.
        //
        // Variable width encodings are aligned on 1 byte boundaries.
        // A fixed width encoding with a max. of 4 bytes/char should
        // align on a 4 byte boundary.  Note that UTF-16 is a special
        // case because of the optional byte order marker, so it's
        // handled above.
        //
        // This doesn't matter for GIOP 1.2 wchars and wstrings
        // since the encoded bytes are treated as an encapsulation.
        int alignment = (!codeset.isFixedWidth() ?
                         1 :
                         codeset.getMaxBytesPerChar());

        return new JavaCTBConverter(codeset, alignment);
    }

    /**
     * BTCConverter factory for single byte or variable width encodings.
     */
    public BTCConverter getBTCConverter(OSFCodeSetRegistry.Entry codeset) {
        return new JavaBTCConverter(codeset);
    }

    /**
     * BTCConverter factory for fixed width multibyte encodings.
     */
    public BTCConverter getBTCConverter(OSFCodeSetRegistry.Entry codeset,
                                        boolean defaultToLittleEndian) {

        if (codeset == OSFCodeSetRegistry.UTF_16 ||
            codeset == OSFCodeSetRegistry.UCS_2) {

            return new UTF16BTCConverter(defaultToLittleEndian);
        } else {
            return new JavaBTCConverter(codeset);
        }
    }

    /**
     * Follows the code set negotiation algorithm in CORBA formal 99-10-07 13.7.2.
     *
     * Returns the proper negotiated OSF character encoding number or
     * CodeSetConversion.FALLBACK_CODESET.
     */
    private int selectEncoding(CodeSetComponentInfo.CodeSetComponent client,
                               CodeSetComponentInfo.CodeSetComponent server) {

        // A "null" value for the server's nativeCodeSet means that
        // the server desired not to indicate one.  We'll take that
        // to mean that it wants the first thing in its conversion list.
        // If it's conversion list is empty, too, then use the fallback
        // codeset.
        int serverNative = server.nativeCodeSet;

        if (serverNative == 0) {
            if (server.conversionCodeSets.length > 0)
                serverNative = server.conversionCodeSets[0];
            else
                return CodeSetConversion.FALLBACK_CODESET;
        }

        if (client.nativeCodeSet == serverNative) {
            // Best case -- client and server don't have to convert
            return serverNative;
        }

        // Is this client capable of converting to the server's
        // native code set?
        for (int i = 0; i < client.conversionCodeSets.length; i++) {
            if (serverNative == client.conversionCodeSets[i]) {
                // The client will convert to the server's
                // native code set.
                return serverNative;
            }
        }

        // Is the server capable of converting to the client's
        // native code set?
        for (int i = 0; i < server.conversionCodeSets.length; i++) {
            if (client.nativeCodeSet == server.conversionCodeSets[i]) {
                // The server will convert to the client's
                // native code set.
                return client.nativeCodeSet;
            }
        }

        // See if there are any code sets that both the server and client
        // support (giving preference to the server).  The order
        // of conversion sets is from most to least desired.
        for (int i = 0; i < server.conversionCodeSets.length; i++) {
            for (int y = 0; y < client.conversionCodeSets.length; y++) {
                if (server.conversionCodeSets[i] == client.conversionCodeSets[y]) {
                    return server.conversionCodeSets[i];
                }
            }
        }

        // Before using the fallback codesets, the spec calls for a
        // compatibility check on the native code sets.  It doesn't make
        // sense because loss free communication is always possible with
        // UTF8 and UTF16, the fall back code sets.  It's also a lot
        // of work to implement.  In the case of incompatibility, the
        // spec says to throw a CODESET_INCOMPATIBLE exception.

        // Use the fallback
        return CodeSetConversion.FALLBACK_CODESET;
    }

    /**
     * Perform the code set negotiation algorithm and come up with
     * the two encodings to use.
     */
    public CodeSetComponentInfo.CodeSetContext negotiate(CodeSetComponentInfo client,
                                                         CodeSetComponentInfo server) {
        int charData
            = selectEncoding(client.getCharComponent(),
                             server.getCharComponent());

        if (charData == CodeSetConversion.FALLBACK_CODESET) {
            charData = OSFCodeSetRegistry.UTF_8.getNumber();
        }

        int wcharData
            = selectEncoding(client.getWCharComponent(),
                             server.getWCharComponent());

        if (wcharData == CodeSetConversion.FALLBACK_CODESET) {
            wcharData = OSFCodeSetRegistry.UTF_16.getNumber();
        }

        return new CodeSetComponentInfo.CodeSetContext(charData,
                                                       wcharData);
    }

    // No one should instantiate a CodeSetConversion but the singleton
    // instance method
    private CodeSetConversion() {}

    // initialize-on-demand holder
    private static class CodeSetConversionHolder {
        static final CodeSetConversion csc = new CodeSetConversion() ;
    }

    /**
     * CodeSetConversion is a singleton, and this is the access point.
     */
    public final static CodeSetConversion impl() {
        return CodeSetConversionHolder.csc ;
    }

    // Singleton instance
    private static CodeSetConversion implementation;

    // Number used internally to indicate the fallback code
    // set.
    private static final int FALLBACK_CODESET = 0;

    // Provides a thread local cache for the sun.io
    // converters.
    private CodeSetCache cache = new CodeSetCache();
}
