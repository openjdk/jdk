// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
*******************************************************************************
* Copyright (C) 1996-2004, International Business Machines Corporation and    *
* others. All Rights Reserved.                                                *
*******************************************************************************
*/
package jdk.internal.icu.impl;

/**
* Internal character utility class for simple data type conversion and String 
* parsing functions. Does not have an analog in the JDK.
* @author Syn Wee Quek
* @since sep2900
*/

public final class UCharacterUtility
{
    // public methods -----------------------------------------------------
    
    /**
    * Determines if codepoint is a non character
    * @param ch codepoint
    * @return true if codepoint is a non character false otherwise
    */
    public static boolean isNonCharacter(int ch) 
    {
        if ((ch & NON_CHARACTER_SUFFIX_MIN_3_0_) == 
                                            NON_CHARACTER_SUFFIX_MIN_3_0_) {
            return true;
        }
        
        return ch >= NON_CHARACTER_MIN_3_1_ && ch <=  NON_CHARACTER_MAX_3_1_;
    }
    
    // package private methods ---------------------------------------------
      
    /**
    * joining 2 chars to form an int
    * @param msc most significant char
    * @param lsc least significant char
    * @return int form
    */
    static int toInt(char msc, char lsc)
    {
        return ((msc << 16) | lsc);
    }
       
    /**
    * Retrieves a null terminated substring from an array of bytes.
    * Substring is a set of non-zero bytes starting from argument start to the 
    * next zero byte. If the first byte is a zero, the next byte will be taken as
    * the first byte.
    * @param str stringbuffer to store data in, data will be store with each
    *            byte as a char
    * @param array byte array
    * @param index to start substring in byte count
    * @return the end position of the substring within the character array
    */
    static int getNullTermByteSubString(StringBuffer str, byte[] array, 
                                                  int index)
    {
        byte b = 1;
        
        while (b != 0)
        {
            b = array[index];
            if (b != 0) {
                str.append((char)(b & 0x00FF));
            }
            index ++;
        }
        return index;
    }
       
    /**
    * Compares a null terminated substring from an array of bytes.
    * Substring is a set of non-zero bytes starting from argument start to the 
    * next zero byte. if the first byte is a zero, the next byte will be taken as
    * the first byte.
    * @param str string to compare
    * @param array byte array
    * @param strindex index within str to start comparing
    * @param aindex array index to start in byte count
    * @return the end position of the substring within str if matches otherwise 
    *         a -1
    */
    static int compareNullTermByteSubString(String str, byte[] array, 
                                                      int strindex, int aindex)
    {
        byte b = 1;
        int length = str.length();
        
        while (b != 0)
        {
            b = array[aindex];  
            aindex ++;
            if (b == 0) {
                break;
            }
            // if we have reached the end of the string and yet the array has not 
            // reached the end of their substring yet, abort
            if (strindex == length 
                || (str.charAt(strindex) != (char)(b & 0xFF))) {
              return -1;
            }
            strindex ++;
        }
        return strindex;
    }
       
    /**
    * Skip null terminated substrings from an array of bytes.
    * Substring is a set of non-zero bytes starting from argument start to the 
    * next zero byte. If the first byte is a zero, the next byte will be taken as
    * the first byte.
    * @param array byte array
    * @param index to start substrings in byte count
    * @param skipcount number of null terminated substrings to skip
    * @return the end position of the substrings within the character array
    */
    static int skipNullTermByteSubString(byte[] array, int index, 
                                                   int skipcount)
    {
        byte b;
        for (int i = 0; i < skipcount; i ++)
        {
            b = 1;
            while (b != 0)
            {
                b = array[index];
                index ++;
            }
        }
        return index;
    }
       
    /**
     * skip substrings from an array of characters, where each character is a set 
     * of 2 bytes. substring is a set of non-zero bytes starting from argument 
     * start to the byte of the argument value. skips up to a max number of 
     * characters
     * @param array byte array to parse
     * @param index to start substrings in byte count
     * @param length the max number of bytes to skip
     * @param skipend value of byte to skip to
     * @return the number of bytes skipped
     */
    static int skipByteSubString(byte[] array, int index, int length, 
                                           byte skipend)
    {
        int result;
        byte b;
        
        for (result = 0; result < length; result ++)
        {
            b = array[index + result];
            if (b == skipend)
            {
                result ++;
                break;
            }
        }
        
        return result;
    }
    
    // private data member --------------------------------------------------
    
    /**
    * Minimum suffix value that indicates if a character is non character.
    * Unicode 3.0 non characters
    */
    private static final int NON_CHARACTER_SUFFIX_MIN_3_0_ = 0xFFFE;
    /**
    * New minimum non character in Unicode 3.1
    */
    private static final int NON_CHARACTER_MIN_3_1_ = 0xFDD0;
    /**
    * New non character range in Unicode 3.1
    */
    private static final int NON_CHARACTER_MAX_3_1_ = 0xFDEF;
    
    // private constructor --------------------------------------------------
      
    ///CLOVER:OFF
    /**
    * private constructor to avoid initialization
    */
    private UCharacterUtility()
    {
    }
    ///CLOVER:ON
}

