/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * Information from the OSF code set registry version 1.2g.
 *
 * Use the Entry corresponding to the desired code set.
 *
 * Consider rename to CodeSetRegistry since OSF is dead.
 */
public final class OSFCodeSetRegistry
{
    // Numbers from the OSF code set registry version 1.2g.
    //
    // Please see the individual Entry definitions for
    // more details.
    public static final int ISO_8859_1_VALUE = 0x00010001;
    public static final int UTF_16_VALUE = 0x00010109;
    public static final int UTF_8_VALUE = 0x05010001;
    public static final int UCS_2_VALUE = 0x00010100;
    public static final int ISO_646_VALUE = 0x00010020;

    private OSFCodeSetRegistry() {}

    /**
     * An entry in the OSF registry which allows users
     * to find out the equivalent Java character encoding
     * name as well as some other facts from the registry.
     */
    public final static class Entry
    {
        private String javaName;
        private int encodingNum;
        private boolean isFixedWidth;
        private int maxBytesPerChar;

        private Entry(String javaName,
                      int encodingNum,
                      boolean isFixedWidth,
                      int maxBytesPerChar) {
            this.javaName = javaName;
            this.encodingNum = encodingNum;
            this.isFixedWidth = isFixedWidth;
            this.maxBytesPerChar = maxBytesPerChar;
        }

        /**
         * Returns the Java equivalent name.  If the encoding has
         * an optional byte order marker, this name will map to the
         * Java encoding that includes the marker.
         */
        public String getName() {
            return javaName;
        }

        /**
         * Get the OSF registry number for this code set.
         */
        public int getNumber() {
            return encodingNum;
        }

        /**
         * Is this a fixed or variable width code set?  (In CORBA
         * terms, "non-byte-oriented" or a "byte-oriented"
         * code set, respectively)
         */
        public boolean isFixedWidth() {
            return isFixedWidth;
        }

        public int getMaxBytesPerChar() {
            return maxBytesPerChar;
        }

        /**
         * First checks reference equality since it's expected
         * people will use the pre-defined constant Entries.
         */
        public boolean equals(Object obj) {
            if (this == obj)
                return true;

            if (!(obj instanceof OSFCodeSetRegistry.Entry))
                return false;

            OSFCodeSetRegistry.Entry other
                = (OSFCodeSetRegistry.Entry)obj;

            return (javaName.equals(other.javaName) &&
                    encodingNum == other.encodingNum &&
                    isFixedWidth == other.isFixedWidth &&
                    maxBytesPerChar == other.maxBytesPerChar);
        }

        /**
         * Uses the registry number as the hash code.
         */
        public int hashCode() {
            return encodingNum;
        }
    }

    /**
     * 8-bit encoding required for GIOP 1.0, and used as the char set
     * when nothing else is specified.
     */
    public static final Entry ISO_8859_1
        = new Entry("ISO-8859-1",
                    ISO_8859_1_VALUE,
                    true,
                    1);

    /**
     * UTF-16 as specified in the OSF registry has an optional
     * byte order marker.  UTF-16BE and UTF-16LE are not in the OSF
     * registry since it is no longer being developed.  When the OMG
     * switches to the IANA registry, these can be public.  Right
     * now, they're used internally by CodeSetConversion.
     */
    static final Entry UTF_16BE
        = new Entry("UTF-16BE",
                    -1,
                    true,
                    2);

    static final Entry UTF_16LE
        = new Entry("UTF-16LE",
                    -2,
                    true,
                    2);

    /**
     * Fallback wchar code set.
     *
     * In the resolution of issue 3405b, UTF-16 defaults to big endian, so
     * doesn't have to have a byte order marker.  Unfortunately, this has to be
     * a special case for compatibility.
     */
    public static final Entry UTF_16
        = new Entry("UTF-16",
                    UTF_16_VALUE,
                    true,
                    4);

    /**
     * Fallback char code set.  Also the code set for char data
     * in encapsulations.  However, since CORBA says chars are
     * only one octet, it is really the same as Latin-1.
     */
    public static final Entry UTF_8
        = new Entry("UTF-8",
                    UTF_8_VALUE,
                    false,
                    6);

    /*
     * At least in JDK 1.3, UCS-2 isn't one of the mandatory Java character
     * encodings.  However, our old ORBs require what they call UCS2, even
     * though they didn't necessarily do the correct encoding of it.
     *
     * This is a special case for our legacy ORBs, and put as the last thing
     * in our conversion list for wchar data.
     *
     * If a foreign ORB actually tries to speak UCS2 with us, it probably
     * won't work!  Beware!
     */
    public static final Entry UCS_2
        = new Entry("UCS-2",
                    UCS_2_VALUE,
                    true,
                    2);

    /**
     * This is the encoding older JavaSoft ORBs advertised as their
     * CORBA char code set.  Actually, they took the lower byte of
     * the Java char.  This is a 7-bit encoding, so they
     * were really sending ISO8859-1.
     */
    public static final Entry ISO_646
        = new Entry("US-ASCII",
                    ISO_646_VALUE,
                    true,
                    1);

    /**
     * Given an OSF registry value, return the corresponding Entry.
     * Returns null if an Entry for that value is unavailable.
     */
    public static Entry lookupEntry(int encodingValue) {
        switch(encodingValue) {
            case ISO_8859_1_VALUE:
                return OSFCodeSetRegistry.ISO_8859_1;
            case UTF_16_VALUE:
                return OSFCodeSetRegistry.UTF_16;
            case UTF_8_VALUE:
                return OSFCodeSetRegistry.UTF_8;
            case ISO_646_VALUE:
                return OSFCodeSetRegistry.ISO_646;
            case UCS_2_VALUE:
                return OSFCodeSetRegistry.UCS_2;
            default:
                return null;
        }
    }
}
