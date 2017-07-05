/*
 * Portions Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
 */

/* Copyright  (c) 2002 Graz University of Technology. All rights reserved.
 *
 * Redistribution and use in  source and binary forms, with or without
 * modification, are permitted  provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in  binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment:
 *
 *    "This product includes software developed by IAIK of Graz University of
 *     Technology."
 *
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Graz University of Technology" and "IAIK of Graz University of
 *    Technology" must not be used to endorse or promote products derived from
 *    this software without prior written permission.
 *
 * 5. Products derived from this software may not be called
 *    "IAIK PKCS Wrapper", nor may "IAIK" appear in their name, without prior
 *    written permission of Graz University of Technology.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY  OF SUCH DAMAGE.
 */

package sun.security.pkcs11.wrapper;

import java.math.BigInteger;

import java.util.*;

import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * This class contains onyl static methods. It is the place for all functions
 * that are used by several classes in this package.
 *
 * @author Karl Scheibelhofer <Karl.Scheibelhofer@iaik.at>
 * @author Martin Schlaeffer <schlaeff@sbox.tugraz.at>
 */
public class Functions {

    // maps between ids and their names, forward and reverse
    // ids are stored as Integers to save space
    // since only the lower 32 bits are ever used anyway

    // mechanisms (CKM_*)
    private static final Map<Integer,String> mechNames =
        new HashMap<Integer,String>();

    private static final Map<String,Integer> mechIds =
        new HashMap<String,Integer>();

    // key types (CKK_*)
    private static final Map<Integer,String> keyNames =
        new HashMap<Integer,String>();

    private static final Map<String,Integer> keyIds =
        new HashMap<String,Integer>();

    // attributes (CKA_*)
    private static final Map<Integer,String> attributeNames =
        new HashMap<Integer,String>();

    private static final Map<String,Integer> attributeIds =
        new HashMap<String,Integer>();

    // object classes (CKO_*)
    private static final Map<Integer,String> objectClassNames =
        new HashMap<Integer,String>();

    private static final Map<String,Integer> objectClassIds =
        new HashMap<String,Integer>();


    /**
     * For converting numbers to their hex presentation.
     */
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * Converts a long value to a hexadecimal String of length 16. Includes
     * leading zeros if necessary.
     *
     * @param value The long value to be converted.
     * @return The hexadecimal string representation of the long value.
     */
    public static String toFullHexString(long value) {
        long currentValue = value;
        StringBuffer stringBuffer = new StringBuffer(16);
        for(int j = 0; j < 16; j++) {
            int currentDigit = (int) currentValue & 0xf;
            stringBuffer.append(HEX_DIGITS[currentDigit]);
            currentValue >>>= 4;
        }

        return stringBuffer.reverse().toString();
    }

    /**
     * Converts a int value to a hexadecimal String of length 8. Includes
     * leading zeros if necessary.
     *
     * @param value The int value to be converted.
     * @return The hexadecimal string representation of the int value.
     */
    public static String toFullHexString(int value) {
        int currentValue = value;
        StringBuffer stringBuffer = new StringBuffer(8);
        for(int i = 0; i < 8; i++) {
            int currentDigit = currentValue & 0xf;
            stringBuffer.append(HEX_DIGITS[currentDigit]);
            currentValue >>>= 4;
        }

        return stringBuffer.reverse().toString();
    }

    /**
     * converts a long value to a hexadecimal String
     *
     * @param value the long value to be converted
     * @return the hexadecimal string representation of the long value
     */
    public static String toHexString(long value) {
        return Long.toHexString(value);
    }

    /**
     * Converts a byte array to a hexadecimal String. Each byte is presented by
     * its two digit hex-code; 0x0A -> "0a", 0x00 -> "00". No leading "0x" is
     * included in the result.
     *
     * @param value the byte array to be converted
     * @return the hexadecimal string representation of the byte array
     */
    public static String toHexString(byte[] value) {
        if (value == null) {
            return null;
        }

        StringBuffer buffer = new StringBuffer(2 * value.length);
        int          single;

        for (int i = 0; i < value.length; i++) {
            single = value[i] & 0xFF;

            if (single < 0x10) {
                buffer.append('0');
            }

            buffer.append(Integer.toString(single, 16));
        }

        return buffer.toString();
    }

    /**
     * converts a long value to a binary String
     *
     * @param value the long value to be converted
     * @return the binary string representation of the long value
     */
    public static String toBinaryString(long value) {
        return Long.toString(value, 2);
    }

    /**
     * converts a byte array to a binary String
     *
     * @param value the byte array to be converted
     * @return the binary string representation of the byte array
     */
    public static String toBinaryString(byte[] value) {
        BigInteger helpBigInteger = new BigInteger(1, value);

        return helpBigInteger.toString(2);
    }

    private static class Flags {
        private final long[] flagIds;
        private final String[] flagNames;
        Flags(long[] flagIds, String[] flagNames) {
            if (flagIds.length != flagNames.length) {
                throw new AssertionError("Array lengths do not match");
            }
            this.flagIds = flagIds;
            this.flagNames = flagNames;
        }
        String toString(long val) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < flagIds.length; i++) {
                if ((val & flagIds[i]) != 0) {
                    if (first == false) {
                        sb.append(" | ");
                    }
                    sb.append(flagNames[i]);
                    first = false;
                }
            }
            return sb.toString();
        }
    }

    private static final Flags slotInfoFlags = new Flags(new long[] {
        CKF_TOKEN_PRESENT,
        CKF_REMOVABLE_DEVICE,
        CKF_HW_SLOT,
    }, new String[] {
        "CKF_TOKEN_PRESENT",
        "CKF_REMOVABLE_DEVICE",
        "CKF_HW_SLOT",
    });

    /**
     * converts the long value flags to a SlotInfoFlag string
     *
     * @param flags the flags to be converted
     * @return the SlotInfoFlag string representation of the flags
     */
    public static String slotInfoFlagsToString(long flags) {
        return slotInfoFlags.toString(flags);
    }

    private static final Flags tokenInfoFlags = new Flags(new long[] {
        CKF_RNG,
        CKF_WRITE_PROTECTED,
        CKF_LOGIN_REQUIRED,
        CKF_USER_PIN_INITIALIZED,
        CKF_RESTORE_KEY_NOT_NEEDED,
        CKF_CLOCK_ON_TOKEN,
        CKF_PROTECTED_AUTHENTICATION_PATH,
        CKF_DUAL_CRYPTO_OPERATIONS,
        CKF_TOKEN_INITIALIZED,
        CKF_SECONDARY_AUTHENTICATION,
        CKF_USER_PIN_COUNT_LOW,
        CKF_USER_PIN_FINAL_TRY,
        CKF_USER_PIN_LOCKED,
        CKF_USER_PIN_TO_BE_CHANGED,
        CKF_SO_PIN_COUNT_LOW,
        CKF_SO_PIN_FINAL_TRY,
        CKF_SO_PIN_LOCKED,
        CKF_SO_PIN_TO_BE_CHANGED,
    }, new String[] {
        "CKF_RNG",
        "CKF_WRITE_PROTECTED",
        "CKF_LOGIN_REQUIRED",
        "CKF_USER_PIN_INITIALIZED",
        "CKF_RESTORE_KEY_NOT_NEEDED",
        "CKF_CLOCK_ON_TOKEN",
        "CKF_PROTECTED_AUTHENTICATION_PATH",
        "CKF_DUAL_CRYPTO_OPERATIONS",
        "CKF_TOKEN_INITIALIZED",
        "CKF_SECONDARY_AUTHENTICATION",
        "CKF_USER_PIN_COUNT_LOW",
        "CKF_USER_PIN_FINAL_TRY",
        "CKF_USER_PIN_LOCKED",
        "CKF_USER_PIN_TO_BE_CHANGED",
        "CKF_SO_PIN_COUNT_LOW",
        "CKF_SO_PIN_FINAL_TRY",
        "CKF_SO_PIN_LOCKED",
        "CKF_SO_PIN_TO_BE_CHANGED",
    });

    /**
     * converts long value flags to a TokenInfoFlag string
     *
     * @param flags the flags to be converted
     * @return the TokenInfoFlag string representation of the flags
     */
    public static String tokenInfoFlagsToString(long flags) {
        return tokenInfoFlags.toString(flags);
    }

    private static final Flags sessionInfoFlags = new Flags(new long[] {
        CKF_RW_SESSION,
        CKF_SERIAL_SESSION,
    }, new String[] {
        "CKF_RW_SESSION",
        "CKF_SERIAL_SESSION",
    });

    /**
     * converts the long value flags to a SessionInfoFlag string
     *
     * @param flags the flags to be converted
     * @return the SessionInfoFlag string representation of the flags
     */
    public static String sessionInfoFlagsToString(long flags) {
        return sessionInfoFlags.toString(flags);
    }

    /**
     * converts the long value state to a SessionState string
     *
     * @param state the state to be converted
     * @return the SessionState string representation of the state
     */
    public static String sessionStateToString(long state) {
        String name;

        if (state == CKS_RO_PUBLIC_SESSION) {
            name = "CKS_RO_PUBLIC_SESSION";
        } else if (state == CKS_RO_USER_FUNCTIONS) {
            name = "CKS_RO_USER_FUNCTIONS";
        } else if (state == CKS_RW_PUBLIC_SESSION) {
            name = "CKS_RW_PUBLIC_SESSION";
        } else if (state == CKS_RW_USER_FUNCTIONS) {
            name = "CKS_RW_USER_FUNCTIONS";
        } else if (state == CKS_RW_SO_FUNCTIONS) {
            name = "CKS_RW_SO_FUNCTIONS";
        } else {
            name = "ERROR: unknown session state 0x" + toFullHexString(state);
        }

        return name;
    }

    private static final Flags mechanismInfoFlags = new Flags(new long[] {
        CKF_HW,
        CKF_ENCRYPT,
        CKF_DECRYPT,
        CKF_DIGEST,
        CKF_SIGN,
        CKF_SIGN_RECOVER,
        CKF_VERIFY,
        CKF_VERIFY_RECOVER,
        CKF_GENERATE,
        CKF_GENERATE_KEY_PAIR,
        CKF_WRAP,
        CKF_UNWRAP,
        CKF_DERIVE,
        CKF_EC_F_P,
        CKF_EC_F_2M,
        CKF_EC_ECPARAMETERS,
        CKF_EC_NAMEDCURVE,
        CKF_EC_UNCOMPRESS,
        CKF_EC_COMPRESS,
        CKF_EXTENSION,
    }, new String[] {
        "CKF_HW",
        "CKF_ENCRYPT",
        "CKF_DECRYPT",
        "CKF_DIGEST",
        "CKF_SIGN",
        "CKF_SIGN_RECOVER",
        "CKF_VERIFY",
        "CKF_VERIFY_RECOVER",
        "CKF_GENERATE",
        "CKF_GENERATE_KEY_PAIR",
        "CKF_WRAP",
        "CKF_UNWRAP",
        "CKF_DERIVE",
        "CKF_EC_F_P",
        "CKF_EC_F_2M",
        "CKF_EC_ECPARAMETERS",
        "CKF_EC_NAMEDCURVE",
        "CKF_EC_UNCOMPRESS",
        "CKF_EC_COMPRESS",
        "CKF_EXTENSION",
    });

    /**
     * converts the long value flags to a MechanismInfoFlag string
     *
     * @param flags the flags to be converted
     * @return the MechanismInfoFlag string representation of the flags
     */
    public static String mechanismInfoFlagsToString(long flags) {
        return mechanismInfoFlags.toString(flags);
    }

    private static String getName(Map<Integer,String> nameMap, long id) {
        String name = null;
        if ((id >>> 32) == 0) {
            name = nameMap.get(Integer.valueOf((int)id));
        }
        if (name == null) {
            name = "Unknown 0x" + toFullHexString(id);
        }
        return name;
    }

    public static long getId(Map<String,Integer> idMap, String name) {
        Integer mech = idMap.get(name);
        if (mech == null) {
            throw new IllegalArgumentException("Unknown name " + name);
        }
        return mech.intValue() & 0xffffffffL;
    }

    public static String getMechanismName(long id) {
        return getName(mechNames, id);
    }

    public static long getMechanismId(String name) {
        return getId(mechIds, name);
    }

    public static String getKeyName(long id) {
        return getName(keyNames, id);
    }

    public static long getKeyId(String name) {
        return getId(keyIds, name);
    }

    public static String getAttributeName(long id) {
        return getName(attributeNames, id);
    }

    public static long getAttributeId(String name) {
        return getId(attributeIds, name);
    }

    public static String getObjectClassName(long id) {
        return getName(objectClassNames, id);
    }

    public static long getObjectClassId(String name) {
        return getId(objectClassIds, name);
    }

    /**
     * Check the given arrays for equalitiy. This method considers both arrays as
     * equal, if both are <code>null</code> or both have the same length and
     * contain exactly the same byte values.
     *
     * @param array1 The first array.
     * @param array2 The second array.
     * @return True, if both arrays are <code>null</code> or both have the same
     *         length and contain exactly the same byte values. False, otherwise.
     * @preconditions
     * @postconditions
     */
    public static boolean equals(byte[] array1, byte[] array2) {
        return Arrays.equals(array1, array2);
    }

    /**
     * Check the given arrays for equalitiy. This method considers both arrays as
     * equal, if both are <code>null</code> or both have the same length and
     * contain exactly the same char values.
     *
     * @param array1 The first array.
     * @param array2 The second array.
     * @return True, if both arrays are <code>null</code> or both have the same
     *         length and contain exactly the same char values. False, otherwise.
     * @preconditions
     * @postconditions
     */
    public static boolean equals(char[] array1, char[] array2) {
        return Arrays.equals(array1, array2);
    }

    /**
     * Check the given dates for equalitiy. This method considers both dates as
     * equal, if both are <code>null</code> or both contain exactly the same char
     * values.
     *
     * @param date1 The first date.
     * @param date2 The second date.
     * @return True, if both dates are <code>null</code> or both contain the same
     *         char values. False, otherwise.
     * @preconditions
     * @postconditions
     */
    public static boolean equals(CK_DATE date1, CK_DATE date2) {
        boolean equal = false;

        if (date1 == date2) {
            equal = true;
        } else if ((date1 != null) && (date2 != null)) {
            equal = equals(date1.year, date2.year)
              && equals(date1.month, date2.month)
              && equals(date1.day, date2.day);
        } else {
            equal = false;
        }

        return equal ;
    }

    /**
     * Calculate a hash code for the given byte array.
     *
     * @param array The byte array.
     * @return A hash code for the given array.
     * @preconditions
     * @postconditions
     */
    public static int hashCode(byte[] array) {
        int hash = 0;

        if (array != null) {
            for (int i = 0; (i < 4) && (i < array.length); i++) {
                hash ^= ((int) (0xFF & array[i])) << ((i%4) << 3);
            }
        }

        return hash ;
    }

    /**
     * Calculate a hash code for the given char array.
     *
     * @param array The char array.
     * @return A hash code for the given array.
     * @preconditions
     * @postconditions
     */
    public static int hashCode(char[] array) {
        int hash = 0;

        if (array != null) {
            for (int i = 0; (i < 4) && (i < array.length); i++) {
                hash ^= ((int) (0xFFFF & array[i])) << ((i%2) << 4);
            }
        }

        return hash ;
    }

    /**
     * Calculate a hash code for the given date object.
     *
     * @param date The date object.
     * @return A hash code for the given date.
     * @preconditions
     * @postconditions
     */
    public static int hashCode(CK_DATE date) {
        int hash = 0;

        if (date != null) {
            if (date.year.length == 4) {
                hash ^= ((int) (0xFFFF & date.year[0])) << 16;
                hash ^= (int) (0xFFFF & date.year[1]);
                hash ^= ((int) (0xFFFF & date.year[2])) << 16;
                hash ^= (int) (0xFFFF & date.year[3]);
            }
            if (date.month.length == 2) {
                hash ^= ((int) (0xFFFF & date.month[0])) << 16;
                hash ^= (int) (0xFFFF & date.month[1]);
            }
            if (date.day.length == 2) {
                hash ^= ((int) (0xFFFF & date.day[0])) << 16;
                hash ^= (int) (0xFFFF & date.day[1]);
            }
        }

        return hash ;
    }

    private static void addMapping(Map<Integer,String> nameMap,
            Map<String,Integer> idMap, long id, String name) {
        if ((id >>> 32) != 0) {
            throw new AssertionError("Id has high bits set: " + id + ", " + name);
        }
        Integer intId = Integer.valueOf((int)id);
        if (nameMap.put(intId, name) != null) {
            throw new AssertionError("Duplicate id: " + id + ", " + name);
        }
        if (idMap.put(name, intId) != null) {
            throw new AssertionError("Duplicate name: " + id + ", " + name);
        }
    }

    private static void addMech(long id, String name) {
        addMapping(mechNames, mechIds, id, name);
    }

    private static void addKeyType(long id, String name) {
        addMapping(keyNames, keyIds, id, name);
    }

    private static void addAttribute(long id, String name) {
        addMapping(attributeNames, attributeIds, id, name);
    }

    private static void addObjectClass(long id, String name) {
        addMapping(objectClassNames, objectClassIds, id, name);
    }

    static {
        addMech(CKM_RSA_PKCS_KEY_PAIR_GEN,      "CKM_RSA_PKCS_KEY_PAIR_GEN");
        addMech(CKM_RSA_PKCS,                   "CKM_RSA_PKCS");
        addMech(CKM_RSA_9796,                   "CKM_RSA_9796");
        addMech(CKM_RSA_X_509,                  "CKM_RSA_X_509");
        addMech(CKM_MD2_RSA_PKCS,               "CKM_MD2_RSA_PKCS");
        addMech(CKM_MD5_RSA_PKCS,               "CKM_MD5_RSA_PKCS");
        addMech(CKM_SHA1_RSA_PKCS,              "CKM_SHA1_RSA_PKCS");
        addMech(CKM_RIPEMD128_RSA_PKCS,         "CKM_RIPEMD128_RSA_PKCS");
        addMech(CKM_RIPEMD160_RSA_PKCS,         "CKM_RIPEMD160_RSA_PKCS");
        addMech(CKM_RSA_PKCS_OAEP,              "CKM_RSA_PKCS_OAEP");
        addMech(CKM_RSA_X9_31_KEY_PAIR_GEN,     "CKM_RSA_X9_31_KEY_PAIR_GEN");
        addMech(CKM_RSA_X9_31,                  "CKM_RSA_X9_31");
        addMech(CKM_SHA1_RSA_X9_31,             "CKM_SHA1_RSA_X9_31");
        addMech(CKM_RSA_PKCS_PSS,               "CKM_RSA_PKCS_PSS");
        addMech(CKM_SHA1_RSA_PKCS_PSS,          "CKM_SHA1_RSA_PKCS_PSS");
        addMech(CKM_DSA_KEY_PAIR_GEN,           "CKM_DSA_KEY_PAIR_GEN");
        addMech(CKM_DSA,                        "CKM_DSA");
        addMech(CKM_DSA_SHA1,                   "CKM_DSA_SHA1");
        addMech(CKM_DH_PKCS_KEY_PAIR_GEN,       "CKM_DH_PKCS_KEY_PAIR_GEN");
        addMech(CKM_DH_PKCS_DERIVE,             "CKM_DH_PKCS_DERIVE");
        addMech(CKM_X9_42_DH_KEY_PAIR_GEN,      "CKM_X9_42_DH_KEY_PAIR_GEN");
        addMech(CKM_X9_42_DH_DERIVE,            "CKM_X9_42_DH_DERIVE");
        addMech(CKM_X9_42_DH_HYBRID_DERIVE,     "CKM_X9_42_DH_HYBRID_DERIVE");
        addMech(CKM_X9_42_MQV_DERIVE,           "CKM_X9_42_MQV_DERIVE");
        addMech(CKM_SHA256_RSA_PKCS,            "CKM_SHA256_RSA_PKCS");
        addMech(CKM_SHA384_RSA_PKCS,            "CKM_SHA384_RSA_PKCS");
        addMech(CKM_SHA512_RSA_PKCS,            "CKM_SHA512_RSA_PKCS");
        addMech(CKM_RC2_KEY_GEN,                "CKM_RC2_KEY_GEN");
        addMech(CKM_RC2_ECB,                    "CKM_RC2_ECB");
        addMech(CKM_RC2_CBC,                    "CKM_RC2_CBC");
        addMech(CKM_RC2_MAC,                    "CKM_RC2_MAC");
        addMech(CKM_RC2_MAC_GENERAL,            "CKM_RC2_MAC_GENERAL");
        addMech(CKM_RC2_CBC_PAD,                "CKM_RC2_CBC_PAD");
        addMech(CKM_RC4_KEY_GEN,                "CKM_RC4_KEY_GEN");
        addMech(CKM_RC4,                        "CKM_RC4");
        addMech(CKM_DES_KEY_GEN,                "CKM_DES_KEY_GEN");
        addMech(CKM_DES_ECB,                    "CKM_DES_ECB");
        addMech(CKM_DES_CBC,                    "CKM_DES_CBC");
        addMech(CKM_DES_MAC,                    "CKM_DES_MAC");
        addMech(CKM_DES_MAC_GENERAL,            "CKM_DES_MAC_GENERAL");
        addMech(CKM_DES_CBC_PAD,                "CKM_DES_CBC_PAD");
        addMech(CKM_DES2_KEY_GEN,               "CKM_DES2_KEY_GEN");
        addMech(CKM_DES3_KEY_GEN,               "CKM_DES3_KEY_GEN");
        addMech(CKM_DES3_ECB,                   "CKM_DES3_ECB");
        addMech(CKM_DES3_CBC,                   "CKM_DES3_CBC");
        addMech(CKM_DES3_MAC,                   "CKM_DES3_MAC");
        addMech(CKM_DES3_MAC_GENERAL,           "CKM_DES3_MAC_GENERAL");
        addMech(CKM_DES3_CBC_PAD,               "CKM_DES3_CBC_PAD");
        addMech(CKM_CDMF_KEY_GEN,               "CKM_CDMF_KEY_GEN");
        addMech(CKM_CDMF_ECB,                   "CKM_CDMF_ECB");
        addMech(CKM_CDMF_CBC,                   "CKM_CDMF_CBC");
        addMech(CKM_CDMF_MAC,                   "CKM_CDMF_MAC");
        addMech(CKM_CDMF_MAC_GENERAL,           "CKM_CDMF_MAC_GENERAL");
        addMech(CKM_CDMF_CBC_PAD,               "CKM_CDMF_CBC_PAD");
        addMech(CKM_MD2,                        "CKM_MD2");
        addMech(CKM_MD2_HMAC,                   "CKM_MD2_HMAC");
        addMech(CKM_MD2_HMAC_GENERAL,           "CKM_MD2_HMAC_GENERAL");
        addMech(CKM_MD5,                        "CKM_MD5");
        addMech(CKM_MD5_HMAC,                   "CKM_MD5_HMAC");
        addMech(CKM_MD5_HMAC_GENERAL,           "CKM_MD5_HMAC_GENERAL");
        addMech(CKM_SHA_1,                      "CKM_SHA_1");
        addMech(CKM_SHA_1_HMAC,                 "CKM_SHA_1_HMAC");
        addMech(CKM_SHA_1_HMAC_GENERAL,         "CKM_SHA_1_HMAC_GENERAL");
        addMech(CKM_RIPEMD128,                  "CKM_RIPEMD128");
        addMech(CKM_RIPEMD128_HMAC,             "CKM_RIPEMD128_HMAC");
        addMech(CKM_RIPEMD128_HMAC_GENERAL,     "CKM_RIPEMD128_HMAC_GENERAL");
        addMech(CKM_RIPEMD160,                  "CKM_RIPEMD160");
        addMech(CKM_RIPEMD160_HMAC,             "CKM_RIPEMD160_HMAC");
        addMech(CKM_RIPEMD160_HMAC_GENERAL,     "CKM_RIPEMD160_HMAC_GENERAL");
        addMech(CKM_SHA256,                     "CKM_SHA256");
        addMech(CKM_SHA256_HMAC,                "CKM_SHA256_HMAC");
        addMech(CKM_SHA256_HMAC_GENERAL,        "CKM_SHA256_HMAC_GENERAL");
        addMech(CKM_SHA384,                     "CKM_SHA384");
        addMech(CKM_SHA384_HMAC,                "CKM_SHA384_HMAC");
        addMech(CKM_SHA384_HMAC_GENERAL,        "CKM_SHA384_HMAC_GENERAL");
        addMech(CKM_SHA512,                     "CKM_SHA512");
        addMech(CKM_SHA512_HMAC,                "CKM_SHA512_HMAC");
        addMech(CKM_SHA512_HMAC_GENERAL,        "CKM_SHA512_HMAC_GENERAL");
        addMech(CKM_CAST_KEY_GEN,               "CKM_CAST_KEY_GEN");
        addMech(CKM_CAST_ECB,                   "CKM_CAST_ECB");
        addMech(CKM_CAST_CBC,                   "CKM_CAST_CBC");
        addMech(CKM_CAST_MAC,                   "CKM_CAST_MAC");
        addMech(CKM_CAST_MAC_GENERAL,           "CKM_CAST_MAC_GENERAL");
        addMech(CKM_CAST_CBC_PAD,               "CKM_CAST_CBC_PAD");
        addMech(CKM_CAST3_KEY_GEN,              "CKM_CAST3_KEY_GEN");
        addMech(CKM_CAST3_ECB,                  "CKM_CAST3_ECB");
        addMech(CKM_CAST3_CBC,                  "CKM_CAST3_CBC");
        addMech(CKM_CAST3_MAC,                  "CKM_CAST3_MAC");
        addMech(CKM_CAST3_MAC_GENERAL,          "CKM_CAST3_MAC_GENERAL");
        addMech(CKM_CAST3_CBC_PAD,              "CKM_CAST3_CBC_PAD");
        addMech(CKM_CAST128_KEY_GEN,            "CKM_CAST128_KEY_GEN");
        addMech(CKM_CAST128_ECB,                "CKM_CAST128_ECB");
        addMech(CKM_CAST128_CBC,                "CKM_CAST128_CBC");
        addMech(CKM_CAST128_MAC,                "CKM_CAST128_MAC");
        addMech(CKM_CAST128_MAC_GENERAL,        "CKM_CAST128_MAC_GENERAL");
        addMech(CKM_CAST128_CBC_PAD,            "CKM_CAST128_CBC_PAD");
        addMech(CKM_RC5_KEY_GEN,                "CKM_RC5_KEY_GEN");
        addMech(CKM_RC5_ECB,                    "CKM_RC5_ECB");
        addMech(CKM_RC5_CBC,                    "CKM_RC5_CBC");
        addMech(CKM_RC5_MAC,                    "CKM_RC5_MAC");
        addMech(CKM_RC5_MAC_GENERAL,            "CKM_RC5_MAC_GENERAL");
        addMech(CKM_RC5_CBC_PAD,                "CKM_RC5_CBC_PAD");
        addMech(CKM_IDEA_KEY_GEN,               "CKM_IDEA_KEY_GEN");
        addMech(CKM_IDEA_ECB,                   "CKM_IDEA_ECB");
        addMech(CKM_IDEA_CBC,                   "CKM_IDEA_CBC");
        addMech(CKM_IDEA_MAC,                   "CKM_IDEA_MAC");
        addMech(CKM_IDEA_MAC_GENERAL,           "CKM_IDEA_MAC_GENERAL");
        addMech(CKM_IDEA_CBC_PAD,               "CKM_IDEA_CBC_PAD");
        addMech(CKM_GENERIC_SECRET_KEY_GEN,     "CKM_GENERIC_SECRET_KEY_GEN");
        addMech(CKM_CONCATENATE_BASE_AND_KEY,   "CKM_CONCATENATE_BASE_AND_KEY");
        addMech(CKM_CONCATENATE_BASE_AND_DATA,  "CKM_CONCATENATE_BASE_AND_DATA");
        addMech(CKM_CONCATENATE_DATA_AND_BASE,  "CKM_CONCATENATE_DATA_AND_BASE");
        addMech(CKM_XOR_BASE_AND_DATA,          "CKM_XOR_BASE_AND_DATA");
        addMech(CKM_EXTRACT_KEY_FROM_KEY,       "CKM_EXTRACT_KEY_FROM_KEY");
        addMech(CKM_SSL3_PRE_MASTER_KEY_GEN,    "CKM_SSL3_PRE_MASTER_KEY_GEN");
        addMech(CKM_SSL3_MASTER_KEY_DERIVE,     "CKM_SSL3_MASTER_KEY_DERIVE");
        addMech(CKM_SSL3_KEY_AND_MAC_DERIVE,    "CKM_SSL3_KEY_AND_MAC_DERIVE");
        addMech(CKM_SSL3_MASTER_KEY_DERIVE_DH,  "CKM_SSL3_MASTER_KEY_DERIVE_DH");
        addMech(CKM_TLS_PRE_MASTER_KEY_GEN,     "CKM_TLS_PRE_MASTER_KEY_GEN");
        addMech(CKM_TLS_MASTER_KEY_DERIVE,      "CKM_TLS_MASTER_KEY_DERIVE");
        addMech(CKM_TLS_KEY_AND_MAC_DERIVE,     "CKM_TLS_KEY_AND_MAC_DERIVE");
        addMech(CKM_TLS_MASTER_KEY_DERIVE_DH,   "CKM_TLS_MASTER_KEY_DERIVE_DH");
        addMech(CKM_TLS_PRF,                    "CKM_TLS_PRF");
        addMech(CKM_SSL3_MD5_MAC,               "CKM_SSL3_MD5_MAC");
        addMech(CKM_SSL3_SHA1_MAC,              "CKM_SSL3_SHA1_MAC");
        addMech(CKM_MD5_KEY_DERIVATION,         "CKM_MD5_KEY_DERIVATION");
        addMech(CKM_MD2_KEY_DERIVATION,         "CKM_MD2_KEY_DERIVATION");
        addMech(CKM_SHA1_KEY_DERIVATION,        "CKM_SHA1_KEY_DERIVATION");
        addMech(CKM_SHA256_KEY_DERIVATION,      "CKM_SHA256_KEY_DERIVATION");
        addMech(CKM_SHA384_KEY_DERIVATION,      "CKM_SHA384_KEY_DERIVATION");
        addMech(CKM_SHA512_KEY_DERIVATION,      "CKM_SHA512_KEY_DERIVATION");
        addMech(CKM_PBE_MD2_DES_CBC,            "CKM_PBE_MD2_DES_CBC");
        addMech(CKM_PBE_MD5_DES_CBC,            "CKM_PBE_MD5_DES_CBC");
        addMech(CKM_PBE_MD5_CAST_CBC,           "CKM_PBE_MD5_CAST_CBC");
        addMech(CKM_PBE_MD5_CAST3_CBC,          "CKM_PBE_MD5_CAST3_CBC");
        addMech(CKM_PBE_MD5_CAST128_CBC,        "CKM_PBE_MD5_CAST128_CBC");
        addMech(CKM_PBE_SHA1_CAST128_CBC,       "CKM_PBE_SHA1_CAST128_CBC");
        addMech(CKM_PBE_SHA1_RC4_128,           "CKM_PBE_SHA1_RC4_128");
        addMech(CKM_PBE_SHA1_RC4_40,            "CKM_PBE_SHA1_RC4_40");
        addMech(CKM_PBE_SHA1_DES3_EDE_CBC,      "CKM_PBE_SHA1_DES3_EDE_CBC");
        addMech(CKM_PBE_SHA1_DES2_EDE_CBC,      "CKM_PBE_SHA1_DES2_EDE_CBC");
        addMech(CKM_PBE_SHA1_RC2_128_CBC,       "CKM_PBE_SHA1_RC2_128_CBC");
        addMech(CKM_PBE_SHA1_RC2_40_CBC,        "CKM_PBE_SHA1_RC2_40_CBC");
        addMech(CKM_PKCS5_PBKD2,                "CKM_PKCS5_PBKD2");
        addMech(CKM_PBA_SHA1_WITH_SHA1_HMAC,    "CKM_PBA_SHA1_WITH_SHA1_HMAC");
        addMech(CKM_KEY_WRAP_LYNKS,             "CKM_KEY_WRAP_LYNKS");
        addMech(CKM_KEY_WRAP_SET_OAEP,          "CKM_KEY_WRAP_SET_OAEP");
        addMech(CKM_SKIPJACK_KEY_GEN,           "CKM_SKIPJACK_KEY_GEN");
        addMech(CKM_SKIPJACK_ECB64,             "CKM_SKIPJACK_ECB64");
        addMech(CKM_SKIPJACK_CBC64,             "CKM_SKIPJACK_CBC64");
        addMech(CKM_SKIPJACK_OFB64,             "CKM_SKIPJACK_OFB64");
        addMech(CKM_SKIPJACK_CFB64,             "CKM_SKIPJACK_CFB64");
        addMech(CKM_SKIPJACK_CFB32,             "CKM_SKIPJACK_CFB32");
        addMech(CKM_SKIPJACK_CFB16,             "CKM_SKIPJACK_CFB16");
        addMech(CKM_SKIPJACK_CFB8,              "CKM_SKIPJACK_CFB8");
        addMech(CKM_SKIPJACK_WRAP,              "CKM_SKIPJACK_WRAP");
        addMech(CKM_SKIPJACK_PRIVATE_WRAP,      "CKM_SKIPJACK_PRIVATE_WRAP");
        addMech(CKM_SKIPJACK_RELAYX,            "CKM_SKIPJACK_RELAYX");
        addMech(CKM_KEA_KEY_PAIR_GEN,           "CKM_KEA_KEY_PAIR_GEN");
        addMech(CKM_KEA_KEY_DERIVE,             "CKM_KEA_KEY_DERIVE");
        addMech(CKM_FORTEZZA_TIMESTAMP,         "CKM_FORTEZZA_TIMESTAMP");
        addMech(CKM_BATON_KEY_GEN,              "CKM_BATON_KEY_GEN");
        addMech(CKM_BATON_ECB128,               "CKM_BATON_ECB128");
        addMech(CKM_BATON_ECB96,                "CKM_BATON_ECB96");
        addMech(CKM_BATON_CBC128,               "CKM_BATON_CBC128");
        addMech(CKM_BATON_COUNTER,              "CKM_BATON_COUNTER");
        addMech(CKM_BATON_SHUFFLE,              "CKM_BATON_SHUFFLE");
        addMech(CKM_BATON_WRAP,                 "CKM_BATON_WRAP");
        addMech(CKM_EC_KEY_PAIR_GEN,            "CKM_EC_KEY_PAIR_GEN");
        addMech(CKM_ECDSA,                      "CKM_ECDSA");
        addMech(CKM_ECDSA_SHA1,                 "CKM_ECDSA_SHA1");
        addMech(CKM_ECDH1_DERIVE,               "CKM_ECDH1_DERIVE");
        addMech(CKM_ECDH1_COFACTOR_DERIVE,      "CKM_ECDH1_COFACTOR_DERIVE");
        addMech(CKM_ECMQV_DERIVE,               "CKM_ECMQV_DERIVE");
        addMech(CKM_JUNIPER_KEY_GEN,            "CKM_JUNIPER_KEY_GEN");
        addMech(CKM_JUNIPER_ECB128,             "CKM_JUNIPER_ECB128");
        addMech(CKM_JUNIPER_CBC128,             "CKM_JUNIPER_CBC128");
        addMech(CKM_JUNIPER_COUNTER,            "CKM_JUNIPER_COUNTER");
        addMech(CKM_JUNIPER_SHUFFLE,            "CKM_JUNIPER_SHUFFLE");
        addMech(CKM_JUNIPER_WRAP,               "CKM_JUNIPER_WRAP");
        addMech(CKM_FASTHASH,                   "CKM_FASTHASH");
        addMech(CKM_AES_KEY_GEN,                "CKM_AES_KEY_GEN");
        addMech(CKM_AES_ECB,                    "CKM_AES_ECB");
        addMech(CKM_AES_CBC,                    "CKM_AES_CBC");
        addMech(CKM_AES_MAC,                    "CKM_AES_MAC");
        addMech(CKM_AES_MAC_GENERAL,            "CKM_AES_MAC_GENERAL");
        addMech(CKM_AES_CBC_PAD,                "CKM_AES_CBC_PAD");
        addMech(CKM_BLOWFISH_KEY_GEN,           "CKM_BLOWFISH_KEY_GEN");
        addMech(CKM_BLOWFISH_CBC,               "CKM_BLOWFISH_CBC");
        addMech(CKM_DSA_PARAMETER_GEN,          "CKM_DSA_PARAMETER_GEN");
        addMech(CKM_DH_PKCS_PARAMETER_GEN,      "CKM_DH_PKCS_PARAMETER_GEN");
        addMech(CKM_X9_42_DH_PARAMETER_GEN,     "CKM_X9_42_DH_PARAMETER_GEN");
        addMech(CKM_VENDOR_DEFINED,             "CKM_VENDOR_DEFINED");

        addMech(CKM_NSS_TLS_PRF_GENERAL,        "CKM_NSS_TLS_PRF_GENERAL");

        addMech(PCKM_SECURERANDOM,              "SecureRandom");
        addMech(PCKM_KEYSTORE,                  "KeyStore");

        addKeyType(CKK_RSA,                     "CKK_RSA");
        addKeyType(CKK_DSA,                     "CKK_DSA");
        addKeyType(CKK_DH,                      "CKK_DH");
        addKeyType(CKK_EC,                      "CKK_EC");
        addKeyType(CKK_X9_42_DH,                "CKK_X9_42_DH");
        addKeyType(CKK_KEA,                     "CKK_KEA");
        addKeyType(CKK_GENERIC_SECRET,          "CKK_GENERIC_SECRET");
        addKeyType(CKK_RC2,                     "CKK_RC2");
        addKeyType(CKK_RC4,                     "CKK_RC4");
        addKeyType(CKK_DES,                     "CKK_DES");
        addKeyType(CKK_DES2,                    "CKK_DES2");
        addKeyType(CKK_DES3,                    "CKK_DES3");
        addKeyType(CKK_CAST,                    "CKK_CAST");
        addKeyType(CKK_CAST3,                   "CKK_CAST3");
        addKeyType(CKK_CAST128,                 "CKK_CAST128");
        addKeyType(CKK_RC5,                     "CKK_RC5");
        addKeyType(CKK_IDEA,                    "CKK_IDEA");
        addKeyType(CKK_SKIPJACK,                "CKK_SKIPJACK");
        addKeyType(CKK_BATON,                   "CKK_BATON");
        addKeyType(CKK_JUNIPER,                 "CKK_JUNIPER");
        addKeyType(CKK_CDMF,                    "CKK_CDMF");
        addKeyType(CKK_AES,                     "CKK_AES");
        addKeyType(CKK_BLOWFISH,                "CKK_BLOWFISH");
        addKeyType(CKK_VENDOR_DEFINED,          "CKK_VENDOR_DEFINED");

        addKeyType(PCKK_ANY,                    "*");

        addAttribute(CKA_CLASS,                 "CKA_CLASS");
        addAttribute(CKA_TOKEN,                 "CKA_TOKEN");
        addAttribute(CKA_PRIVATE,               "CKA_PRIVATE");
        addAttribute(CKA_LABEL,                 "CKA_LABEL");
        addAttribute(CKA_APPLICATION,           "CKA_APPLICATION");
        addAttribute(CKA_VALUE,                 "CKA_VALUE");
        addAttribute(CKA_OBJECT_ID,             "CKA_OBJECT_ID");
        addAttribute(CKA_CERTIFICATE_TYPE,      "CKA_CERTIFICATE_TYPE");
        addAttribute(CKA_ISSUER,                "CKA_ISSUER");
        addAttribute(CKA_SERIAL_NUMBER,         "CKA_SERIAL_NUMBER");
        addAttribute(CKA_AC_ISSUER,             "CKA_AC_ISSUER");
        addAttribute(CKA_OWNER,                 "CKA_OWNER");
        addAttribute(CKA_ATTR_TYPES,            "CKA_ATTR_TYPES");
        addAttribute(CKA_TRUSTED,               "CKA_TRUSTED");
        addAttribute(CKA_KEY_TYPE,              "CKA_KEY_TYPE");
        addAttribute(CKA_SUBJECT,               "CKA_SUBJECT");
        addAttribute(CKA_ID,                    "CKA_ID");
        addAttribute(CKA_SENSITIVE,             "CKA_SENSITIVE");
        addAttribute(CKA_ENCRYPT,               "CKA_ENCRYPT");
        addAttribute(CKA_DECRYPT,               "CKA_DECRYPT");
        addAttribute(CKA_WRAP,                  "CKA_WRAP");
        addAttribute(CKA_UNWRAP,                "CKA_UNWRAP");
        addAttribute(CKA_SIGN,                  "CKA_SIGN");
        addAttribute(CKA_SIGN_RECOVER,          "CKA_SIGN_RECOVER");
        addAttribute(CKA_VERIFY,                "CKA_VERIFY");
        addAttribute(CKA_VERIFY_RECOVER,        "CKA_VERIFY_RECOVER");
        addAttribute(CKA_DERIVE,                "CKA_DERIVE");
        addAttribute(CKA_START_DATE,            "CKA_START_DATE");
        addAttribute(CKA_END_DATE,              "CKA_END_DATE");
        addAttribute(CKA_MODULUS,               "CKA_MODULUS");
        addAttribute(CKA_MODULUS_BITS,          "CKA_MODULUS_BITS");
        addAttribute(CKA_PUBLIC_EXPONENT,       "CKA_PUBLIC_EXPONENT");
        addAttribute(CKA_PRIVATE_EXPONENT,      "CKA_PRIVATE_EXPONENT");
        addAttribute(CKA_PRIME_1,               "CKA_PRIME_1");
        addAttribute(CKA_PRIME_2,               "CKA_PRIME_2");
        addAttribute(CKA_EXPONENT_1,            "CKA_EXPONENT_1");
        addAttribute(CKA_EXPONENT_2,            "CKA_EXPONENT_2");
        addAttribute(CKA_COEFFICIENT,           "CKA_COEFFICIENT");
        addAttribute(CKA_PRIME,                 "CKA_PRIME");
        addAttribute(CKA_SUBPRIME,              "CKA_SUBPRIME");
        addAttribute(CKA_BASE,                  "CKA_BASE");
        addAttribute(CKA_PRIME_BITS,            "CKA_PRIME_BITS");
        addAttribute(CKA_SUB_PRIME_BITS,        "CKA_SUB_PRIME_BITS");
        addAttribute(CKA_VALUE_BITS,            "CKA_VALUE_BITS");
        addAttribute(CKA_VALUE_LEN,             "CKA_VALUE_LEN");
        addAttribute(CKA_EXTRACTABLE,           "CKA_EXTRACTABLE");
        addAttribute(CKA_LOCAL,                 "CKA_LOCAL");
        addAttribute(CKA_NEVER_EXTRACTABLE,     "CKA_NEVER_EXTRACTABLE");
        addAttribute(CKA_ALWAYS_SENSITIVE,      "CKA_ALWAYS_SENSITIVE");
        addAttribute(CKA_KEY_GEN_MECHANISM,     "CKA_KEY_GEN_MECHANISM");
        addAttribute(CKA_MODIFIABLE,            "CKA_MODIFIABLE");
        addAttribute(CKA_EC_PARAMS,             "CKA_EC_PARAMS");
        addAttribute(CKA_EC_POINT,              "CKA_EC_POINT");
        addAttribute(CKA_SECONDARY_AUTH,        "CKA_SECONDARY_AUTH");
        addAttribute(CKA_AUTH_PIN_FLAGS,        "CKA_AUTH_PIN_FLAGS");
        addAttribute(CKA_HW_FEATURE_TYPE,       "CKA_HW_FEATURE_TYPE");
        addAttribute(CKA_RESET_ON_INIT,         "CKA_RESET_ON_INIT");
        addAttribute(CKA_HAS_RESET,             "CKA_HAS_RESET");
        addAttribute(CKA_VENDOR_DEFINED,        "CKA_VENDOR_DEFINED");
        addAttribute(CKA_NETSCAPE_DB,           "CKA_NETSCAPE_DB");

        addAttribute(CKA_NETSCAPE_TRUST_SERVER_AUTH,      "CKA_NETSCAPE_TRUST_SERVER_AUTH");
        addAttribute(CKA_NETSCAPE_TRUST_CLIENT_AUTH,      "CKA_NETSCAPE_TRUST_CLIENT_AUTH");
        addAttribute(CKA_NETSCAPE_TRUST_CODE_SIGNING,     "CKA_NETSCAPE_TRUST_CODE_SIGNING");
        addAttribute(CKA_NETSCAPE_TRUST_EMAIL_PROTECTION, "CKA_NETSCAPE_TRUST_EMAIL_PROTECTION");
        addAttribute(CKA_NETSCAPE_CERT_SHA1_HASH,         "CKA_NETSCAPE_CERT_SHA1_HASH");
        addAttribute(CKA_NETSCAPE_CERT_MD5_HASH,          "CKA_NETSCAPE_CERT_MD5_HASH");

        addObjectClass(CKO_DATA,                "CKO_DATA");
        addObjectClass(CKO_CERTIFICATE,         "CKO_CERTIFICATE");
        addObjectClass(CKO_PUBLIC_KEY,          "CKO_PUBLIC_KEY");
        addObjectClass(CKO_PRIVATE_KEY,         "CKO_PRIVATE_KEY");
        addObjectClass(CKO_SECRET_KEY,          "CKO_SECRET_KEY");
        addObjectClass(CKO_HW_FEATURE,          "CKO_HW_FEATURE");
        addObjectClass(CKO_DOMAIN_PARAMETERS,   "CKO_DOMAIN_PARAMETERS");
        addObjectClass(CKO_VENDOR_DEFINED,      "CKO_VENDOR_DEFINED");

        addObjectClass(PCKO_ANY,                "*");

    }

}
