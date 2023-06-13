/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.xni;

/**
 * This class is used as a structure to pass text contained in the underlying
 * character buffer of the scanner. The offset and length fields allow the
 * buffer to be re-used without creating new character arrays.
 * <p>
 * <strong>Note:</strong> Methods that are passed an XMLString structure
 * should consider the contents read-only and not make any modifications
 * to the contents of the buffer. The method receiving this structure
 * should also not modify the offset and length if this structure (or
 * the values of this structure) are passed to another method.
 * <p>
 * <strong>Note:</strong> Methods that are passed an XMLString structure
 * are required to copy the information out of the buffer if it is to be
 * saved for use beyond the scope of the method. The contents of the
 * structure are volatile and the contents of the character buffer cannot
 * be assured once the method that is passed this structure returns.
 * Therefore, methods passed this structure should not save any reference
 * to the structure or the character array contained in the structure.
 *
 * @author Eric Ye, IBM
 * @author Andy Clark, IBM
 * @LastModified: Aug 2021
 */
public class XMLString {
    /** Default buffer size (32). */
    public static final int DEFAULT_SIZE = 32;

    //
    // Data
    //

    /** The character array. */
    public char[] ch;

    /** The offset into the character array. */
    public int offset;

    /** The length of characters from the offset. */
    public int length;

    //
    // Constructors
    //

    /** Default constructor. */
    public XMLString() {
    } // <init>()

    /**
     * Constructs an XMLString structure preset with the specified
     * values.
     *
     * @param ch     The character array.
     * @param offset The offset into the character array.
     * @param length The length of characters from the offset.
     */
    public XMLString(char[] ch, int offset, int length) {
        setValues(ch, offset, length);
    } // <init>(char[],int,int)

    /**
     * Constructs an XMLString structure with copies of the values in
     * the given structure.
     * <p>
     * <strong>Note:</strong> This does not copy the character array;
     * only the reference to the array is copied.
     *
     * @param string The XMLString to copy.
     */
    public XMLString(XMLString string) {
        setValues(string);
    } // <init>(XMLString)

    //
    // Public methods
    //

    /**
     * Initializes the contents of the XMLString structure with the
     * specified values.
     *
     * @param ch     The character array.
     * @param offset The offset into the character array.
     * @param length The length of characters from the offset.
     */
    public void setValues(char[] ch, int offset, int length) {
        this.ch = ch;
        this.offset = offset;
        this.length = length;
    } // setValues(char[],int,int)

    /**
     * Initializes the contents of the XMLString structure with copies
     * of the given string structure.
     * <p>
     * <strong>Note:</strong> This does not copy the character array;
     * only the reference to the array is copied.
     *
     * @param s
     */
    public void setValues(XMLString s) {
        setValues(s.ch, s.offset, s.length);
    } // setValues(XMLString)

    /** Resets all of the values to their defaults. */
    public void clear() {
        this.ch = null;
        this.offset = 0;
        this.length = -1;
    } // clear()

    /**
     * Returns true if the contents of this XMLString structure and
     * the specified array are equal.
     *
     * @param ch     The character array.
     * @param offset The offset into the character array.
     * @param length The length of characters from the offset.
     */
    public boolean equals(char[] ch, int offset, int length) {
        if (ch == null) {
            return false;
        }
        if (this.length != length) {
            return false;
        }

        for (int i=0; i<length; i++) {
            if (this.ch[this.offset+i] != ch[offset+i] ) {
                return false;
            }
        }
        return true;
    } // equals(char[],int,int):boolean

    /**
     * Returns true if the contents of this XMLString structure and
     * the specified string are equal.
     *
     * @param s The string to compare.
     */
    public boolean equals(String s) {
        if (s == null) {
            return false;
        }
        if ( length != s.length() ) {
            return false;
        }

        // is this faster than call s.toCharArray first and compare the
        // two arrays directly, which will possibly involve creating a
        // new char array object.
        for (int i=0; i<length; i++) {
            if (ch[offset+i] != s.charAt(i)) {
                return false;
            }
        }

        return true;
    } // equals(String):boolean

    //
    // Object methods
    //

    /** Returns a string representation of this object. */
    public String toString() {
        return length > 0 ? new String(ch, offset, length) : "";
    } // toString():String

    /**
     * Appends a char to the buffer.
     *
     * @param c the char
     */
    public void append(char c) {
        if(this.length + 1 > this.ch.length){
            int newLength = this.ch.length * 2 ;
            if(newLength < this.ch.length + DEFAULT_SIZE){
                newLength = this.ch.length + DEFAULT_SIZE;
            }
            char [] tmp = new char[newLength];
            System.arraycopy(this.ch, 0, tmp, 0, this.length);
            this.ch = tmp;
        }
        this.ch[this.length] = c ;
        this.length++;
    } // append(char)

    /**
     * Appends a string to the buffer.
     *
     * @param s the string
     */
    public void append(String s) {
        int length = s.length();
        if (this.length + length > this.ch.length) {
            int newLength = this.ch.length * 2 ;
            if(newLength < this.ch.length + length + DEFAULT_SIZE){
                newLength = this.ch.length + length+ DEFAULT_SIZE;
            }

            char[] newch = new char[newLength];
            System.arraycopy(this.ch, 0, newch, 0, this.length);
            this.ch = newch;
        }
        s.getChars(0, length, this.ch, this.length);
        this.length += length;
    } // append(String)

    /**
     * Appends a number of characters to the buffer.
     *
     * @param ch the char array
     * @param offset the offset
     * @param length the length
     */
    public void append(char[] ch, int offset, int length) {
        if (this.length + length > this.ch.length) {
            int newLength = this.ch.length * 2 ;
            if(newLength < this.ch.length + length + DEFAULT_SIZE){
                newLength = this.ch.length + length + DEFAULT_SIZE;
            }
            char[] newch = new char[newLength];
            System.arraycopy(this.ch, 0, newch, 0, this.length);
            this.ch = newch;
        }
        //making the code more robust as it would handle null or 0 length data,
        //add the data only when it contains some thing
        if(ch != null && length > 0){
            System.arraycopy(ch, offset, this.ch, this.length, length);
            this.length += length;
        }
    } // append(char[],int,int)

    /**
     * Appends another buffer to this buffer
     *
     * @param s another buffer
     */
    public void append(XMLString s) {
        append(s.ch, s.offset, s.length);
    } // append(XMLString)

} // class XMLString
