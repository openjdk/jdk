/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2000-2002,2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.serialize;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import com.sun.org.apache.xerces.internal.util.EncodingMap;

/**
 * This class represents an encoding.
 *
 */
public class EncodingInfo {

    // An array to hold the argument for a method of Charset, CharsetEncoder or CharToByteConverter.
    private Object [] fArgsForMethod = null;

    // name of encoding as registered with IANA;
    // preferably a MIME name, but aliases are fine too.
    String ianaName;
    String javaName;
    int lastPrintable;

    // The CharsetEncoder with which we test unusual characters.
    Object fCharsetEncoder = null;

    // The CharToByteConverter with which we test unusual characters.
    Object fCharToByteConverter = null;

    // Is the converter null because it can't be instantiated
    // for some reason (perhaps we're running with insufficient authority as
    // an applet?
    boolean fHaveTriedCToB = false;

    // Is the charset encoder usable or available.
    boolean fHaveTriedCharsetEncoder = false;

    /**
     * Creates new <code>EncodingInfo</code> instance.
     */
    public EncodingInfo(String ianaName, String javaName, int lastPrintable) {
        this.ianaName = ianaName;
        this.javaName = EncodingMap.getIANA2JavaMapping(ianaName);
        this.lastPrintable = lastPrintable;
    }

    /**
     * Returns a MIME charset name of this encoding.
     */
    public String getIANAName() {
        return this.ianaName;
    }

    /**
     * Returns a writer for this encoding based on
     * an output stream.
     *
     * @return A suitable writer
     * @exception UnsupportedEncodingException There is no convertor
     *  to support this encoding
     */
    public Writer getWriter(OutputStream output)
        throws UnsupportedEncodingException {
        // this should always be true!
        if (javaName != null)
            return new OutputStreamWriter(output, javaName);
        javaName = EncodingMap.getIANA2JavaMapping(ianaName);
        if(javaName == null)
            // use UTF-8 as preferred encoding
            return new OutputStreamWriter(output, "UTF8");
        return new OutputStreamWriter(output, javaName);
    }

    /**
     * Checks whether the specified character is printable or not in this encoding.
     *
     * @param ch a code point (0-0x10ffff)
     */
    public boolean isPrintable(char ch) {
        if (ch <= this.lastPrintable) {
            return true;
        }
        return isPrintable0(ch);
    }

    /**
     * Checks whether the specified character is printable or not in this encoding.
     * This method accomplishes this using a java.nio.CharsetEncoder. If NIO isn't
     * available it will attempt use a sun.io.CharToByteConverter.
     *
     * @param ch a code point (0-0x10ffff)
     */
    private boolean isPrintable0(char ch) {

        // Attempt to get a CharsetEncoder for this encoding.
        if (fCharsetEncoder == null && CharsetMethods.fgNIOCharsetAvailable && !fHaveTriedCharsetEncoder) {
            if (fArgsForMethod == null) {
                fArgsForMethod = new Object [1];
            }
            // try and create the CharsetEncoder
            try {
                fArgsForMethod[0] = javaName;
                Object charset = CharsetMethods.fgCharsetForNameMethod.invoke(null, fArgsForMethod);
                if (((Boolean) CharsetMethods.fgCharsetCanEncodeMethod.invoke(charset, (Object[]) null)).booleanValue()) {
                    fCharsetEncoder = CharsetMethods.fgCharsetNewEncoderMethod.invoke(charset, (Object[]) null);
                }
                // This charset cannot be used for encoding, don't try it again...
                else {
                    fHaveTriedCharsetEncoder = true;
                }
            }
            catch (Exception e) {
                // don't try it again...
                fHaveTriedCharsetEncoder = true;
            }
        }
        // Attempt to use the CharsetEncoder to determine whether the character is printable.
        if (fCharsetEncoder != null) {
            try {
                fArgsForMethod[0] = new Character(ch);
                return ((Boolean) CharsetMethods.fgCharsetEncoderCanEncodeMethod.invoke(fCharsetEncoder, fArgsForMethod)).booleanValue();
            }
            catch (Exception e) {
                // obviously can't use this charset encoder; possibly a JDK bug
                fCharsetEncoder = null;
                fHaveTriedCharsetEncoder = false;
            }
        }

        // As a last resort try to use a sun.io.CharToByteConverter to
        // determine whether this character is printable. We will always
        // reach here on JDK 1.3 or below.
        if (fCharToByteConverter == null) {
            if (fHaveTriedCToB || !CharToByteConverterMethods.fgConvertersAvailable) {
                // forget it; nothing we can do...
                return false;
            }
            if (fArgsForMethod == null) {
                fArgsForMethod = new Object [1];
            }
            // try and create the CharToByteConverter
            try {
                fArgsForMethod[0] = javaName;
                fCharToByteConverter = CharToByteConverterMethods.fgGetConverterMethod.invoke(null, fArgsForMethod);
            }
            catch (Exception e) {
                // don't try it again...
                fHaveTriedCToB = true;
                return false;
            }
        }
        try {
            fArgsForMethod[0] = new Character(ch);
            return ((Boolean) CharToByteConverterMethods.fgCanConvertMethod.invoke(fCharToByteConverter, fArgsForMethod)).booleanValue();
        }
        catch (Exception e) {
            // obviously can't use this converter; probably some kind of
            // security restriction
            fCharToByteConverter = null;
            fHaveTriedCToB = false;
            return false;
        }
    }

    // is this an encoding name recognized by this JDK?
    // if not, will throw UnsupportedEncodingException
    public static void testJavaEncodingName(String name)  throws UnsupportedEncodingException {
        final byte [] bTest = {(byte)'v', (byte)'a', (byte)'l', (byte)'i', (byte)'d'};
        String s = new String(bTest, name);
    }

    /**
     * Holder of methods from java.nio.charset.Charset and java.nio.charset.CharsetEncoder.
     */
    static class CharsetMethods {

        // Method: java.nio.charset.Charset.forName(java.lang.String)
        private static java.lang.reflect.Method fgCharsetForNameMethod = null;

        // Method: java.nio.charset.Charset.canEncode()
        private static java.lang.reflect.Method fgCharsetCanEncodeMethod = null;

        // Method: java.nio.charset.Charset.newEncoder()
        private static java.lang.reflect.Method fgCharsetNewEncoderMethod = null;

        // Method: java.nio.charset.CharsetEncoder.canEncode(char)
        private static java.lang.reflect.Method fgCharsetEncoderCanEncodeMethod = null;

        // Flag indicating whether or not java.nio.charset.* is available.
        private static boolean fgNIOCharsetAvailable = false;

        private CharsetMethods() {}

        // Attempt to get methods for Charset and CharsetEncoder on class initialization.
        static {
            try {
                Class charsetClass = Class.forName("java.nio.charset.Charset");
                Class charsetEncoderClass = Class.forName("java.nio.charset.CharsetEncoder");
                fgCharsetForNameMethod = charsetClass.getMethod("forName", new Class [] {String.class});
                fgCharsetCanEncodeMethod = charsetClass.getMethod("canEncode", new Class [] {});
                fgCharsetNewEncoderMethod = charsetClass.getMethod("newEncoder", new Class [] {});
                fgCharsetEncoderCanEncodeMethod = charsetEncoderClass.getMethod("canEncode", new Class [] {Character.TYPE});
                fgNIOCharsetAvailable = true;
            }
            // ClassNotFoundException, NoSuchMethodException or SecurityException
            // Whatever the case, we cannot use java.nio.charset.*.
            catch (Exception exc) {
                fgCharsetForNameMethod = null;
                fgCharsetCanEncodeMethod = null;
                fgCharsetEncoderCanEncodeMethod = null;
                fgCharsetNewEncoderMethod = null;
                fgNIOCharsetAvailable = false;
            }
        }
    }

    /**
     * Holder of methods from sun.io.CharToByteConverter.
     */
    static class CharToByteConverterMethods {

        // Method: sun.io.CharToByteConverter.getConverter(java.lang.String)
        private static java.lang.reflect.Method fgGetConverterMethod = null;

        // Method: sun.io.CharToByteConverter.canConvert(char)
        private static java.lang.reflect.Method fgCanConvertMethod = null;

        // Flag indicating whether or not sun.io.CharToByteConverter is available.
        private static boolean fgConvertersAvailable = false;

        private CharToByteConverterMethods() {}

        // Attempt to get methods for char to byte converter on class initialization.
        static {
            try {
                Class clazz = Class.forName("sun.io.CharToByteConverter");
                fgGetConverterMethod = clazz.getMethod("getConverter", new Class [] {String.class});
                fgCanConvertMethod = clazz.getMethod("canConvert", new Class [] {Character.TYPE});
                fgConvertersAvailable = true;
            }
            // ClassNotFoundException, NoSuchMethodException or SecurityException
            // Whatever the case, we cannot use sun.io.CharToByteConverter.
            catch (Exception exc) {
                fgGetConverterMethod = null;
                fgCanConvertMethod = null;
                fgConvertersAvailable = false;
            }
        }
    }
}
