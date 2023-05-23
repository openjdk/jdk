// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2004-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import jdk.internal.icu.util.ICUException;
import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.UResourceBundle;
import jdk.internal.icu.util.UResourceTypeMismatchException;
import jdk.internal.icu.util.VersionInfo;

/**
 * This class reads the *.res resource bundle format.
 *
 * For the file format documentation see ICU4C's source/common/uresdata.h file.
 */
public final class ICUResourceBundleReader {
    /**
     * File format version that this class understands.
     * "ResB"
     */
    private static final int DATA_FORMAT = 0x52657342;
    private static final class IsAcceptable implements ICUBinary.Authenticate {
        @Override
        public boolean isDataVersionAcceptable(byte formatVersion[]) {
            return
                    (formatVersion[0] == 1 && (formatVersion[1] & 0xff) >= 1) ||
                    (2 <= formatVersion[0] && formatVersion[0] <= 3);
        }
    }
    private static final IsAcceptable IS_ACCEPTABLE = new IsAcceptable();

    /* indexes[] value names; indexes are generally 32-bit (Resource) indexes */
    /**
     * [0] contains the length of indexes[]
     * which is at most URES_INDEX_TOP of the latest format version
     *
     * formatVersion==1: all bits contain the length of indexes[]
     *   but the length is much less than 0xff;
     * formatVersion>1:
     *   only bits  7..0 contain the length of indexes[],
     *        bits 31..8 are reserved and set to 0
     * formatVersion>=3:
     *        bits 31..8 poolStringIndexLimit bits 23..0
     */
    private static final int URES_INDEX_LENGTH           = 0;
    /**
     * [1] contains the top of the key strings,
     *     same as the bottom of resources or UTF-16 strings, rounded up
     */
    private static final int URES_INDEX_KEYS_TOP         = 1;
    /** [2] contains the top of all resources */
    //ivate static final int URES_INDEX_RESOURCES_TOP    = 2;
    /**
     * [3] contains the top of the bundle,
     *     in case it were ever different from [2]
     */
    private static final int URES_INDEX_BUNDLE_TOP       = 3;
    /** [4] max. length of any table */
    private static final int URES_INDEX_MAX_TABLE_LENGTH = 4;
    /**
     * [5] attributes bit set, see URES_ATT_* (new in formatVersion 1.2)
     *
     * formatVersion>=3:
     *   bits 31..16 poolStringIndex16Limit
     *   bits 15..12 poolStringIndexLimit bits 27..24
     */
    private static final int URES_INDEX_ATTRIBUTES       = 5;
    /**
     * [6] top of the 16-bit units (UTF-16 string v2 UChars, URES_TABLE16, URES_ARRAY16),
     *     rounded up (new in formatVersion 2.0, ICU 4.4)
     */
    private static final int URES_INDEX_16BIT_TOP        = 6;
    /** [7] checksum of the pool bundle (new in formatVersion 2.0, ICU 4.4) */
    private static final int URES_INDEX_POOL_CHECKSUM    = 7;
    //ivate static final int URES_INDEX_TOP              = 8;

    /*
     * Nofallback attribute, attribute bit 0 in indexes[URES_INDEX_ATTRIBUTES].
     * New in formatVersion 1.2 (ICU 3.6).
     *
     * If set, then this resource bundle is a standalone bundle.
     * If not set, then the bundle participates in locale fallback, eventually
     * all the way to the root bundle.
     * If indexes[] is missing or too short, then the attribute cannot be determined
     * reliably. Dependency checking should ignore such bundles, and loading should
     * use fallbacks.
     */
    private static final int URES_ATT_NO_FALLBACK = 1;

    /*
     * Attributes for bundles that are, or use, a pool bundle.
     * A pool bundle provides key strings that are shared among several other bundles
     * to reduce their total size.
     * New in formatVersion 2 (ICU 4.4).
     */
    private static final int URES_ATT_IS_POOL_BUNDLE = 2;
    private static final int URES_ATT_USES_POOL_BUNDLE = 4;

    private static final CharBuffer EMPTY_16_BIT_UNITS = CharBuffer.wrap("\0");  // read-only

    /**
     * Objects with more value bytes are stored in SoftReferences.
     * Smaller objects (which are not much larger than a SoftReference)
     * are stored directly, avoiding the overhead of the reference.
     */
    static final int LARGE_SIZE = 24;

    private static final boolean DEBUG = false;

    private int /* formatVersion, */ dataVersion;

    // See the ResourceData struct in ICU4C/source/common/uresdata.h.
    /**
     * Buffer of all of the resource bundle bytes after the header.
     * (equivalent of C++ pRoot)
     */
    private ByteBuffer bytes;
    private byte[] keyBytes;
    private CharBuffer b16BitUnits;
    private ICUResourceBundleReader poolBundleReader;
    private int rootRes;
    private int localKeyLimit;
    private int poolStringIndexLimit;
    private int poolStringIndex16Limit;
    private boolean noFallback; /* see URES_ATT_NO_FALLBACK */
    private boolean isPoolBundle;
    private boolean usesPoolBundle;
    private int poolCheckSum;

    private ResourceCache resourceCache;

    private static ReaderCache CACHE = new ReaderCache();
    private static final ICUResourceBundleReader NULL_READER = new ICUResourceBundleReader();

    private static class ReaderCacheKey {
        final String baseName;
        final String localeID;

        ReaderCacheKey(String baseName, String localeID) {
            this.baseName = (baseName == null) ? "" : baseName;
            this.localeID = (localeID == null) ? "" : localeID;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReaderCacheKey)) {
                return false;
            }
            ReaderCacheKey info = (ReaderCacheKey)obj;
            return this.baseName.equals(info.baseName)
                    && this.localeID.equals(info.localeID);
        }

        @Override
        public int hashCode() {
            return baseName.hashCode() ^ localeID.hashCode();
        }
    }

    private static class ReaderCache extends SoftCache<ReaderCacheKey, ICUResourceBundleReader, ClassLoader> {
        /* (non-Javadoc)
         * @see jdk.internal.icu.impl.CacheBase#createInstance(java.lang.Object, java.lang.Object)
         */
        @Override
        protected ICUResourceBundleReader createInstance(ReaderCacheKey key, ClassLoader loader) {
            String fullName = ICUResourceBundleReader.getFullName(key.baseName, key.localeID);
            try {
                ByteBuffer inBytes;
                if (key.baseName != null && key.baseName.startsWith(ICUData.ICU_BASE_NAME)) {
                    String itemPath = fullName.substring(ICUData.ICU_BASE_NAME.length() + 1);
                    inBytes = ICUBinary.getData(loader, fullName, itemPath);
                    if (inBytes == null) {
                        return NULL_READER;
                    }
                } else {
                    @SuppressWarnings("resource")  // Closed by getByteBufferFromInputStreamAndCloseStream().
                    InputStream stream = ICUData.getStream(loader, fullName);
                    if (stream == null) {
                        return NULL_READER;
                    }
                    inBytes = ICUBinary.getByteBufferFromInputStreamAndCloseStream(stream);
                }
                return new ICUResourceBundleReader(inBytes, key.baseName, key.localeID, loader);
            } catch (IOException ex) {
                throw new UncheckedIOException("Data file " + fullName + " is corrupt - " + ex.getMessage(), ex);
            }
        }
    }

    /*
     * Default constructor, just used for NULL_READER.
     */
    private ICUResourceBundleReader() {
    }

    private ICUResourceBundleReader(ByteBuffer inBytes,
            String baseName, String localeID,
            ClassLoader loader) throws IOException {
        init(inBytes);

        // set pool bundle if necessary
        if (usesPoolBundle) {
            poolBundleReader = getReader(baseName, "pool", loader);
            if (poolBundleReader == null || !poolBundleReader.isPoolBundle) {
                throw new IllegalStateException("pool.res is not a pool bundle");
            }
            if (poolBundleReader.poolCheckSum != poolCheckSum) {
                throw new IllegalStateException("pool.res has a different checksum than this bundle");
            }
        }
    }

    static ICUResourceBundleReader getReader(String baseName, String localeID, ClassLoader root) {
        ReaderCacheKey info = new ReaderCacheKey(baseName, localeID);
        ICUResourceBundleReader reader = CACHE.getInstance(info, root);
        if (reader == NULL_READER) {
            return null;
        }
        return reader;
    }

    // See res_init() in ICU4C/source/common/uresdata.c.
    private void init(ByteBuffer inBytes) throws IOException {
        dataVersion = ICUBinary.readHeader(inBytes, DATA_FORMAT, IS_ACCEPTABLE);
        int majorFormatVersion = inBytes.get(16);
        bytes = ICUBinary.sliceWithOrder(inBytes);
        int dataLength = bytes.remaining();

        if(DEBUG) System.out.println("The ByteBuffer is direct (memory-mapped): " + bytes.isDirect());
        if(DEBUG) System.out.println("The available bytes in the buffer before reading the data: " + dataLength);

        rootRes = bytes.getInt(0);

        // Bundles with formatVersion 1.1 and later contain an indexes[] array.
        // We need it so that we can read the key string bytes up front, for lookup performance.

        // read the variable-length indexes[] array
        int indexes0 = getIndexesInt(URES_INDEX_LENGTH);
        int indexLength = indexes0 & 0xff;
        if(indexLength <= URES_INDEX_MAX_TABLE_LENGTH) {
            throw new ICUException("not enough indexes");
        }
        int bundleTop;
        if(dataLength < ((1 + indexLength) << 2) ||
                dataLength < ((bundleTop = getIndexesInt(URES_INDEX_BUNDLE_TOP)) << 2)) {
            throw new ICUException("not enough bytes");
        }
        int maxOffset = bundleTop - 1;

        if (majorFormatVersion >= 3) {
            // In formatVersion 1, the indexLength took up this whole int.
            // In version 2, bits 31..8 were reserved and always 0.
            // In version 3, they contain bits 23..0 of the poolStringIndexLimit.
            // Bits 27..24 are in indexes[URES_INDEX_ATTRIBUTES] bits 15..12.
            poolStringIndexLimit = indexes0 >>> 8;
        }
        if(indexLength > URES_INDEX_ATTRIBUTES) {
            // determine if this resource bundle falls back to a parent bundle
            // along normal locale ID fallback
            int att = getIndexesInt(URES_INDEX_ATTRIBUTES);
            noFallback = (att & URES_ATT_NO_FALLBACK) != 0;
            isPoolBundle = (att & URES_ATT_IS_POOL_BUNDLE) != 0;
            usesPoolBundle = (att & URES_ATT_USES_POOL_BUNDLE) != 0;
            poolStringIndexLimit |= (att & 0xf000) << 12;  // bits 15..12 -> 27..24
            poolStringIndex16Limit = att >>> 16;
        }

        int keysBottom = 1 + indexLength;
        int keysTop = getIndexesInt(URES_INDEX_KEYS_TOP);
        if(keysTop > keysBottom) {
            // Deserialize the key strings up front.
            // Faster table item search at the cost of slower startup and some heap memory.
            if(isPoolBundle) {
                // Shift the key strings down:
                // Pool bundle key strings are used with a 0-based index,
                // unlike regular bundles' key strings for which indexes
                // are based on the start of the bundle data.
                keyBytes = new byte[(keysTop - keysBottom) << 2];
                bytes.position(keysBottom << 2);
            } else {
                localKeyLimit = keysTop << 2;
                keyBytes = new byte[localKeyLimit];
            }
            bytes.get(keyBytes);
        }

        // Read the array of 16-bit units.
        if(indexLength > URES_INDEX_16BIT_TOP) {
            int _16BitTop = getIndexesInt(URES_INDEX_16BIT_TOP);
            if(_16BitTop > keysTop) {
                int num16BitUnits = (_16BitTop - keysTop) * 2;
                bytes.position(keysTop << 2);
                b16BitUnits = bytes.asCharBuffer();
                b16BitUnits.limit(num16BitUnits);
                maxOffset |= num16BitUnits - 1;
            } else {
                b16BitUnits = EMPTY_16_BIT_UNITS;
            }
        } else {
            b16BitUnits = EMPTY_16_BIT_UNITS;
        }

        if(indexLength > URES_INDEX_POOL_CHECKSUM) {
            poolCheckSum = getIndexesInt(URES_INDEX_POOL_CHECKSUM);
        }

        if(!isPoolBundle || b16BitUnits.length() > 1) {
            resourceCache = new ResourceCache(maxOffset);
        }

        // Reset the position for future .asCharBuffer() etc.
        bytes.position(0);
    }

    private int getIndexesInt(int i) {
        return bytes.getInt((1 + i) << 2);
    }

    VersionInfo getVersion() {
        return ICUBinary.getVersionInfoFromCompactInt(dataVersion);
    }

    int getRootResource() {
        return rootRes;
    }
    boolean getNoFallback() {
        return noFallback;
    }
    boolean getUsesPoolBundle() {
        return usesPoolBundle;
    }

    static int RES_GET_TYPE(int res) {
        return res >>> 28;
    }
    private static int RES_GET_OFFSET(int res) {
        return res & 0x0fffffff;
    }
    private int getResourceByteOffset(int offset) {
        return offset << 2;
    }
    /* get signed and unsigned integer values directly from the Resource handle */
    static int RES_GET_INT(int res) {
        return (res << 4) >> 4;
    }
    static int RES_GET_UINT(int res) {
        return res & 0x0fffffff;
    }
    static boolean URES_IS_ARRAY(int type) {
        return type == UResourceBundle.ARRAY || type == ICUResourceBundle.ARRAY16;
    }
    static boolean URES_IS_TABLE(int type) {
        return type==UResourceBundle.TABLE || type==ICUResourceBundle.TABLE16 || type==ICUResourceBundle.TABLE32;
    }

    private static final byte[] emptyBytes = new byte[0];
    private static final ByteBuffer emptyByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer();
    private static final char[] emptyChars = new char[0];
    private static final int[] emptyInts = new int[0];
    private static final String emptyString = "";
    private static final Array EMPTY_ARRAY = new Array();
    private static final Table EMPTY_TABLE = new Table();

    private char[] getChars(int offset, int count) {
        char[] chars = new char[count];
        if (count <= 16) {
            for(int i = 0; i < count; offset += 2, ++i) {
                chars[i] = bytes.getChar(offset);
            }
        } else {
            CharBuffer temp = bytes.asCharBuffer();
            temp.position(offset / 2);
            temp.get(chars);
        }
        return chars;
    }
    private int getInt(int offset) {
        return bytes.getInt(offset);
    }
    private int[] getInts(int offset, int count) {
        int[] ints = new int[count];
        if (count <= 16) {
            for(int i = 0; i < count; offset += 4, ++i) {
                ints[i] = bytes.getInt(offset);
            }
        } else {
            IntBuffer temp = bytes.asIntBuffer();
            temp.position(offset / 4);
            temp.get(ints);
        }
        return ints;
    }
    private char[] getTable16KeyOffsets(int offset) {
        int length = b16BitUnits.charAt(offset++);
        if(length > 0) {
            char[] result = new char[length];
            if(length <= 16) {
                for(int i = 0; i < length; ++i) {
                    result[i] = b16BitUnits.charAt(offset++);
                }
            } else {
                CharBuffer temp = b16BitUnits.duplicate();
                temp.position(offset);
                temp.get(result);
            }
            return result;
        } else {
            return emptyChars;
        }
    }
    private char[] getTableKeyOffsets(int offset) {
        int length = bytes.getChar(offset);
        if(length > 0) {
            return getChars(offset + 2, length);
        } else {
            return emptyChars;
        }
    }
    private int[] getTable32KeyOffsets(int offset) {
        int length = getInt(offset);
        if(length > 0) {
            return getInts(offset + 4, length);
        } else {
            return emptyInts;
        }
    }

    private static String makeKeyStringFromBytes(byte[] keyBytes, int keyOffset) {
        StringBuilder sb = new StringBuilder();
        byte b;
        while((b = keyBytes[keyOffset]) != 0) {
            ++keyOffset;
            sb.append((char)b);
        }
        return sb.toString();
    }
    private String getKey16String(int keyOffset) {
        if(keyOffset < localKeyLimit) {
            return makeKeyStringFromBytes(keyBytes, keyOffset);
        } else {
            return makeKeyStringFromBytes(poolBundleReader.keyBytes, keyOffset - localKeyLimit);
        }
    }
    private String getKey32String(int keyOffset) {
        if(keyOffset >= 0) {
            return makeKeyStringFromBytes(keyBytes, keyOffset);
        } else {
            return makeKeyStringFromBytes(poolBundleReader.keyBytes, keyOffset & 0x7fffffff);
        }
    }
    private void setKeyFromKey16(int keyOffset, UResource.Key key) {
        if(keyOffset < localKeyLimit) {
            key.setBytes(keyBytes, keyOffset);
        } else {
            key.setBytes(poolBundleReader.keyBytes, keyOffset - localKeyLimit);
        }
    }
    private void setKeyFromKey32(int keyOffset, UResource.Key key) {
        if(keyOffset >= 0) {
            key.setBytes(keyBytes, keyOffset);
        } else {
            key.setBytes(poolBundleReader.keyBytes, keyOffset & 0x7fffffff);
        }
    }
    private int compareKeys(CharSequence key, char keyOffset) {
        if(keyOffset < localKeyLimit) {
            return ICUBinary.compareKeys(key, keyBytes, keyOffset);
        } else {
            return ICUBinary.compareKeys(key, poolBundleReader.keyBytes, keyOffset - localKeyLimit);
        }
    }
    private int compareKeys32(CharSequence key, int keyOffset) {
        if(keyOffset >= 0) {
            return ICUBinary.compareKeys(key, keyBytes, keyOffset);
        } else {
            return ICUBinary.compareKeys(key, poolBundleReader.keyBytes, keyOffset & 0x7fffffff);
        }
    }

    /**
     * @return a string from the local bundle's b16BitUnits at the local offset
     */
    String getStringV2(int res) {
        // Use the pool bundle's resource cache for pool bundle strings;
        // use the local bundle's cache for local strings.
        // The cache requires a resource word with the proper type,
        // and with an offset that is local to this bundle so that the offset fits
        // within the maximum number of bits for which the cache was constructed.
        assert RES_GET_TYPE(res) == ICUResourceBundle.STRING_V2;
        int offset = RES_GET_OFFSET(res);
        assert offset != 0;  // handled by the caller
        Object value = resourceCache.get(res);
        if(value != null) {
            return (String)value;
        }
        String s;
        int first = b16BitUnits.charAt(offset);
        if((first&0xfffffc00)!=0xdc00) {  // C: if(!U16_IS_TRAIL(first)) {
            if(first==0) {
                return emptyString;  // Should not occur, but is not forbidden.
            }
            StringBuilder sb = new StringBuilder();
            sb.append((char)first);
            char c;
            while((c = b16BitUnits.charAt(++offset)) != 0) {
                sb.append(c);
            }
            s = sb.toString();
        } else {
            int length;
            if(first<0xdfef) {
                length=first&0x3ff;
                ++offset;
            } else if(first<0xdfff) {
                length=((first-0xdfef)<<16)|b16BitUnits.charAt(offset+1);
                offset+=2;
            } else {
                length=(b16BitUnits.charAt(offset+1)<<16)|b16BitUnits.charAt(offset+2);
                offset+=3;
            }
            // Cast up to CharSequence to insulate against the CharBuffer.subSequence() return type change
            // which makes code compiled for a newer JDK (7 and up) not run on an older one (6 and below).
            s = ((CharSequence) b16BitUnits).subSequence(offset, offset + length).toString();
        }
        return (String)resourceCache.putIfAbsent(res, s, s.length() * 2);
    }

    private String makeStringFromBytes(int offset, int length) {
        if (length <= 16) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; offset += 2, ++i) {
                sb.append(bytes.getChar(offset));
            }
            return sb.toString();
        } else {
            CharSequence cs = bytes.asCharBuffer();
            offset /= 2;
            return cs.subSequence(offset, offset + length).toString();
        }
    }

    String getString(int res) {
        int offset=RES_GET_OFFSET(res);
        if(res != offset /* RES_GET_TYPE(res) != URES_STRING */ &&
                RES_GET_TYPE(res) != ICUResourceBundle.STRING_V2) {
            return null;
        }
        if(offset == 0) {
            return emptyString;
        }
        if (res != offset) {  // STRING_V2
            if (offset < poolStringIndexLimit) {
                return poolBundleReader.getStringV2(res);
            } else {
                return getStringV2(res - poolStringIndexLimit);
            }
        }
        Object value = resourceCache.get(res);
        if(value != null) {
            return (String)value;
        }
        offset=getResourceByteOffset(offset);
        int length = getInt(offset);
        String s = makeStringFromBytes(offset+4, length);
        return (String)resourceCache.putIfAbsent(res, s, s.length() * 2);
    }

    /**
     * CLDR string value "\u2205\u2205\u2205"=="\u2205\u2205\u2205" prevents fallback to the parent bundle.
     */
    private boolean isNoInheritanceMarker(int res) {
        int offset = RES_GET_OFFSET(res);
        if (offset == 0) {
            // empty string
        } else if (res == offset) {
            offset = getResourceByteOffset(offset);
            return getInt(offset) == 3 && bytes.getChar(offset + 4) == 0x2205 &&
                    bytes.getChar(offset + 6) == 0x2205 && bytes.getChar(offset + 8) == 0x2205;
        } else if (RES_GET_TYPE(res) == ICUResourceBundle.STRING_V2) {
            if (offset < poolStringIndexLimit) {
                return poolBundleReader.isStringV2NoInheritanceMarker(offset);
            } else {
                return isStringV2NoInheritanceMarker(offset - poolStringIndexLimit);
            }
        }
        return false;
    }

    private boolean isStringV2NoInheritanceMarker(int offset) {
        int first = b16BitUnits.charAt(offset);
        if (first == 0x2205) {  // implicit length
            return b16BitUnits.charAt(offset + 1) == 0x2205 &&
                    b16BitUnits.charAt(offset + 2) == 0x2205 &&
                    b16BitUnits.charAt(offset + 3) == 0;
        } else if (first == 0xdc03) {  // explicit length 3 (should not occur)
            return b16BitUnits.charAt(offset + 1) == 0x2205 &&
                    b16BitUnits.charAt(offset + 2) == 0x2205 &&
                    b16BitUnits.charAt(offset + 3) == 0x2205;
        } else {
            // Assume that the string has not been stored with more length units than necessary.
            return false;
        }
    }

    String getAlias(int res) {
        int offset=RES_GET_OFFSET(res);
        int length;
        if(RES_GET_TYPE(res)==ICUResourceBundle.ALIAS) {
            if(offset==0) {
                return emptyString;
            } else {
                Object value = resourceCache.get(res);
                if(value != null) {
                    return (String)value;
                }
                offset=getResourceByteOffset(offset);
                length=getInt(offset);
                String s = makeStringFromBytes(offset + 4, length);
                return (String)resourceCache.putIfAbsent(res, s, length * 2);
            }
        } else {
            return null;
        }
    }

    byte[] getBinary(int res, byte[] ba) {
        int offset=RES_GET_OFFSET(res);
        int length;
        if(RES_GET_TYPE(res)==UResourceBundle.BINARY) {
            if(offset==0) {
                return emptyBytes;
            } else {
                offset=getResourceByteOffset(offset);
                length=getInt(offset);
                if(length==0) {
                    return emptyBytes;
                }
                // Not cached: The array would have to be cloned anyway because
                // the cache must not be writable via the returned reference.
                if(ba==null || ba.length!=length) {
                    ba=new byte[length];
                }
                offset += 4;
                if(length <= 16) {
                    for(int i = 0; i < length; ++i) {
                        ba[i] = bytes.get(offset++);
                    }
                } else {
                    ByteBuffer temp = bytes.duplicate();
                    temp.position(offset);
                    temp.get(ba);
                }
                return ba;
            }
        } else {
            return null;
        }
    }

    ByteBuffer getBinary(int res) {
        int offset=RES_GET_OFFSET(res);
        int length;
        if(RES_GET_TYPE(res)==UResourceBundle.BINARY) {
            if(offset==0) {
                // Don't just
                //   return emptyByteBuffer;
                // in case it matters whether the buffer's mark is defined or undefined.
                return emptyByteBuffer.duplicate();
            } else {
                // Not cached: The returned buffer is small (shares its bytes with the bundle)
                // and usually quickly discarded after use.
                // Also, even a cached buffer would have to be cloned because it is mutable
                // (position & mark).
                offset=getResourceByteOffset(offset);
                length=getInt(offset);
                if(length == 0) {
                    return emptyByteBuffer.duplicate();
                }
                offset += 4;
                ByteBuffer result = bytes.duplicate();
                result.position(offset).limit(offset + length);
                result = ICUBinary.sliceWithOrder(result);
                if(!result.isReadOnly()) {
                    result = result.asReadOnlyBuffer();
                }
                return result;
            }
        } else {
            return null;
        }
    }

    int[] getIntVector(int res) {
        int offset=RES_GET_OFFSET(res);
        int length;
        if(RES_GET_TYPE(res)==UResourceBundle.INT_VECTOR) {
            if(offset==0) {
                return emptyInts;
            } else {
                // Not cached: The array would have to be cloned anyway because
                // the cache must not be writable via the returned reference.
                offset=getResourceByteOffset(offset);
                length=getInt(offset);
                return getInts(offset+4, length);
            }
        } else {
            return null;
        }
    }

    Array getArray(int res) {
        int type=RES_GET_TYPE(res);
        if(!URES_IS_ARRAY(type)) {
            return null;
        }
        int offset=RES_GET_OFFSET(res);
        if(offset == 0) {
            return EMPTY_ARRAY;
        }
        Object value = resourceCache.get(res);
        if(value != null) {
            return (Array)value;
        }
        Array array = (type == UResourceBundle.ARRAY) ?
                new Array32(this, offset) : new Array16(this, offset);
        return (Array)resourceCache.putIfAbsent(res, array, 0);
    }

    Table getTable(int res) {
        int type = RES_GET_TYPE(res);
        if(!URES_IS_TABLE(type)) {
            return null;
        }
        int offset = RES_GET_OFFSET(res);
        if(offset == 0) {
            return EMPTY_TABLE;
        }
        Object value = resourceCache.get(res);
        if(value != null) {
            return (Table)value;
        }
        Table table;
        int size;  // Use size = 0 to never use SoftReferences for Tables?
        if(type == UResourceBundle.TABLE) {
            table = new Table1632(this, offset);
            size = table.getSize() * 2;
        } else if(type == ICUResourceBundle.TABLE16) {
            table = new Table16(this, offset);
            size = table.getSize() * 2;
        } else /* type == ICUResourceBundle.TABLE32 */ {
            table = new Table32(this, offset);
            size = table.getSize() * 4;
        }
        return (Table)resourceCache.putIfAbsent(res, table, size);
    }

    // ICUResource.Value --------------------------------------------------- ***

    /**
     * From C++ uresdata.c gPublicTypes[URES_LIMIT].
     */
    private static int PUBLIC_TYPES[] = {
        UResourceBundle.STRING,
        UResourceBundle.BINARY,
        UResourceBundle.TABLE,
        ICUResourceBundle.ALIAS,

        UResourceBundle.TABLE,     /* URES_TABLE32 */
        UResourceBundle.TABLE,     /* URES_TABLE16 */
        UResourceBundle.STRING,    /* URES_STRING_V2 */
        UResourceBundle.INT,

        UResourceBundle.ARRAY,
        UResourceBundle.ARRAY,     /* URES_ARRAY16 */
        UResourceBundle.NONE,
        UResourceBundle.NONE,

        UResourceBundle.NONE,
        UResourceBundle.NONE,
        UResourceBundle.INT_VECTOR,
        UResourceBundle.NONE
    };

    static class ReaderValue extends UResource.Value {
        ICUResourceBundleReader reader;
        int res;

        @Override
        public int getType() {
            return PUBLIC_TYPES[RES_GET_TYPE(res)];
        }

        @Override
        public String getString() {
            String s = reader.getString(res);
            if (s == null) {
                throw new UResourceTypeMismatchException("");
            }
            return s;
        }

        @Override
        public String getAliasString() {
            String s = reader.getAlias(res);
            if (s == null) {
                throw new UResourceTypeMismatchException("");
            }
            return s;
        }

        @Override
        public int getInt() {
            if (RES_GET_TYPE(res) != UResourceBundle.INT) {
                throw new UResourceTypeMismatchException("");
            }
            return RES_GET_INT(res);
        }

        @Override
        public int getUInt() {
            if (RES_GET_TYPE(res) != UResourceBundle.INT) {
                throw new UResourceTypeMismatchException("");
            }
            return RES_GET_UINT(res);
        }

        @Override
        public int[] getIntVector() {
            int[] iv = reader.getIntVector(res);
            if (iv == null) {
                throw new UResourceTypeMismatchException("");
            }
            return iv;
        }

        @Override
        public ByteBuffer getBinary() {
            ByteBuffer bb = reader.getBinary(res);
            if (bb == null) {
                throw new UResourceTypeMismatchException("");
            }
            return bb;
        }

        @Override
        public jdk.internal.icu.impl.UResource.Array getArray() {
            Array array = reader.getArray(res);
            if (array == null) {
                throw new UResourceTypeMismatchException("");
            }
            return array;
        }

        @Override
        public jdk.internal.icu.impl.UResource.Table getTable() {
            Table table = reader.getTable(res);
            if (table == null) {
                throw new UResourceTypeMismatchException("");
            }
            return table;
        }

        @Override
        public boolean isNoInheritanceMarker() {
            return reader.isNoInheritanceMarker(res);
        }

        @Override
        public String[] getStringArray() {
            Array array = reader.getArray(res);
            if (array == null) {
                throw new UResourceTypeMismatchException("");
            }
            return getStringArray(array);
        }

        @Override
        public String[] getStringArrayOrStringAsArray() {
            Array array = reader.getArray(res);
            if (array != null) {
                return getStringArray(array);
            }
            String s = reader.getString(res);
            if (s != null) {
                return new String[] { s };
            }
            throw new UResourceTypeMismatchException("");
        }

        @Override
        public String getStringOrFirstOfArray() {
            String s = reader.getString(res);
            if (s != null) {
                return s;
            }
            Array array = reader.getArray(res);
            if (array != null && array.size > 0) {
                int r = array.getContainerResource(reader, 0);
                s = reader.getString(r);
                if (s != null) {
                    return s;
                }
            }
            throw new UResourceTypeMismatchException("");
        }

        private String[] getStringArray(Array array) {
            String[] result = new String[array.size];
            for (int i = 0; i < array.size; ++i) {
                int r = array.getContainerResource(reader, i);
                String s = reader.getString(r);
                if (s == null) {
                    throw new UResourceTypeMismatchException("");
                }
                result[i] = s;
            }
            return result;
        }
    }

    // Container value classes --------------------------------------------- ***

    static class Container {
        protected int size;
        protected int itemsOffset;

        public final int getSize() {
            return size;
        }
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return ICUResourceBundle.RES_BOGUS;
        }
        protected int getContainer16Resource(ICUResourceBundleReader reader, int index) {
            if (index < 0 || size <= index) {
                return ICUResourceBundle.RES_BOGUS;
            }
            int res16 = reader.b16BitUnits.charAt(itemsOffset + index);
            if (res16 < reader.poolStringIndex16Limit) {
                // Pool string, nothing to do.
            } else {
                // Local string, adjust the 16-bit offset to a regular one,
                // with a larger pool string index limit.
                res16 = res16 - reader.poolStringIndex16Limit + reader.poolStringIndexLimit;
            }
            return (ICUResourceBundle.STRING_V2 << 28) | res16;
        }
        protected int getContainer32Resource(ICUResourceBundleReader reader, int index) {
            if (index < 0 || size <= index) {
                return ICUResourceBundle.RES_BOGUS;
            }
            return reader.getInt(itemsOffset + 4 * index);
        }
        int getResource(ICUResourceBundleReader reader, String resKey) {
            return getContainerResource(reader, Integer.parseInt(resKey));
        }
        Container() {
        }
    }
    static class Array extends Container implements UResource.Array {
        Array() {}
        @Override
        public boolean getValue(int i, UResource.Value value) {
            if (0 <= i && i < size) {
                ReaderValue readerValue = (ReaderValue)value;
                readerValue.res = getContainerResource(readerValue.reader, i);
                return true;
            }
            return false;
        }
    }
    private static final class Array32 extends Array {
        @Override
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return getContainer32Resource(reader, index);
        }
        Array32(ICUResourceBundleReader reader, int offset) {
            offset = reader.getResourceByteOffset(offset);
            size = reader.getInt(offset);
            itemsOffset = offset + 4;
        }
    }
    private static final class Array16 extends Array {
        @Override
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return getContainer16Resource(reader, index);
        }
        Array16(ICUResourceBundleReader reader, int offset) {
            size = reader.b16BitUnits.charAt(offset);
            itemsOffset = offset + 1;
        }
    }
    static class Table extends Container implements UResource.Table {
        protected char[] keyOffsets;
        protected int[] key32Offsets;

        Table() {
        }
        String getKey(ICUResourceBundleReader reader, int index) {
            if (index < 0 || size <= index) {
                return null;
            }
            return keyOffsets != null ?
                        reader.getKey16String(keyOffsets[index]) :
                        reader.getKey32String(key32Offsets[index]);
        }
        private static final int URESDATA_ITEM_NOT_FOUND = -1;
        int findTableItem(ICUResourceBundleReader reader, CharSequence key) {
            int mid, start, limit;
            int result;

            /* do a binary search for the key */
            start=0;
            limit=size;
            while(start<limit) {
                mid = (start + limit) >>> 1;
                if (keyOffsets != null) {
                    result = reader.compareKeys(key, keyOffsets[mid]);
                } else {
                    result = reader.compareKeys32(key, key32Offsets[mid]);
                }
                if (result < 0) {
                    limit = mid;
                } else if (result > 0) {
                    start = mid + 1;
                } else {
                    /* We found it! */
                    return mid;
                }
            }
            return URESDATA_ITEM_NOT_FOUND;  /* not found or table is empty. */
        }
        @Override
        int getResource(ICUResourceBundleReader reader, String resKey) {
            return getContainerResource(reader, findTableItem(reader, resKey));
        }
        @Override
        public boolean getKeyAndValue(int i, UResource.Key key, UResource.Value value) {
            if (0 <= i && i < size) {
                ReaderValue readerValue = (ReaderValue)value;
                if (keyOffsets != null) {
                    readerValue.reader.setKeyFromKey16(keyOffsets[i], key);
                } else {
                    readerValue.reader.setKeyFromKey32(key32Offsets[i], key);
                }
                readerValue.res = getContainerResource(readerValue.reader, i);
                return true;
            }
            return false;
        }
        @Override
        public boolean findValue(CharSequence key, UResource.Value value) {
            ReaderValue readerValue = (ReaderValue)value;
            int i = findTableItem(readerValue.reader, key);
            if (i >= 0) {
                readerValue.res = getContainerResource(readerValue.reader, i);
                return true;
            } else {
                return false;
            }
        }
    }
    private static final class Table1632 extends Table {
        @Override
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return getContainer32Resource(reader, index);
        }
        Table1632(ICUResourceBundleReader reader, int offset) {
            offset = reader.getResourceByteOffset(offset);
            keyOffsets = reader.getTableKeyOffsets(offset);
            size = keyOffsets.length;
            itemsOffset = offset + 2 * ((size + 2) & ~1);  // Skip padding for 4-alignment.
        }
    }
    private static final class Table16 extends Table {
        @Override
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return getContainer16Resource(reader, index);
        }
        Table16(ICUResourceBundleReader reader, int offset) {
            keyOffsets = reader.getTable16KeyOffsets(offset);
            size = keyOffsets.length;
            itemsOffset = offset + 1 + size;
        }
    }
    private static final class Table32 extends Table {
        @Override
        int getContainerResource(ICUResourceBundleReader reader, int index) {
            return getContainer32Resource(reader, index);
        }
        Table32(ICUResourceBundleReader reader, int offset) {
            offset = reader.getResourceByteOffset(offset);
            key32Offsets = reader.getTable32KeyOffsets(offset);
            size = key32Offsets.length;
            itemsOffset = offset + 4 * (1 + size);
        }
    }

    // Resource cache ------------------------------------------------------ ***

    /**
     * Cache of some of one resource bundle's resources.
     * Avoids creating multiple Java objects for the same resource items,
     * including multiple copies of their contents.
     *
     * <p>Mutable objects must not be cached and then returned to the caller
     * because the cache must not be writable via the returned reference.
     *
     * <p>Resources are mapped by their resource integers.
     * Empty resources with offset 0 cannot be mapped.
     * Integers need not and should not be cached.
     * Multiple .res items may share resource offsets (genrb eliminates some duplicates).
     *
     * <p>This cache uses int[] and Object[] arrays to minimize object creation
     * and avoid auto-boxing.
     *
     * <p>Large resource objects are usually stored in SoftReferences.
     *
     * <p>For few resources, a small table is used with binary search.
     * When more resources are cached, then the data structure changes to be faster
     * but also use more memory.
     */
    private static final class ResourceCache {
        // Number of items to be stored in a simple array with binary search and insertion sort.
        private static final int SIMPLE_LENGTH = 32;

        // When more than SIMPLE_LENGTH items are cached,
        // then switch to a trie-like tree of levels with different array lengths.
        private static final int ROOT_BITS = 7;
        private static final int NEXT_BITS = 6;

        // Simple table, used when length >= 0.
        private int[] keys = new int[SIMPLE_LENGTH];
        private Object[] values = new Object[SIMPLE_LENGTH];
        private int length;

        // Trie-like tree of levels, used when length < 0.
        private int maxOffsetBits;
        /**
         * Number of bits in each level, each stored in a nibble.
         */
        private int levelBitsList;
        private Level rootLevel;

        private static boolean storeDirectly(int size) {
            return size < LARGE_SIZE || CacheValue.futureInstancesWillBeStrong();
        }

        @SuppressWarnings("unchecked")
        private static final Object putIfCleared(Object[] values, int index, Object item, int size) {
            Object value = values[index];
            if(!(value instanceof SoftReference)) {
                // The caller should be consistent for each resource,
                // that is, create equivalent objects of equal size every time,
                // but the CacheValue "strength" may change over time.
                // assert size < LARGE_SIZE;
                return value;
            }
            assert size >= LARGE_SIZE;
            value = ((SoftReference<Object>)value).get();
            if(value != null) {
                return value;
            }
            values[index] = CacheValue.futureInstancesWillBeStrong() ?
                    item : new SoftReference<>(item);
            return item;
        }

        private static final class Level {
            int levelBitsList;
            int shift;
            int mask;
            int[] keys;
            Object[] values;

            Level(int levelBitsList, int shift) {
                this.levelBitsList = levelBitsList;
                this.shift = shift;
                int bits = levelBitsList & 0xf;
                assert bits != 0;
                int length = 1 << bits;
                mask = length - 1;
                keys = new int[length];
                values = new Object[length];
            }

            Object get(int key) {
                int index = (key >> shift) & mask;
                int k = keys[index];
                if(k == key) {
                    return values[index];
                }
                if(k == 0) {
                    Level level = (Level)values[index];
                    if(level != null) {
                        return level.get(key);
                    }
                }
                return null;
            }

            Object putIfAbsent(int key, Object item, int size) {
                int index = (key >> shift) & mask;
                int k = keys[index];
                if(k == key) {
                    return putIfCleared(values, index, item, size);
                }
                if(k == 0) {
                    Level level = (Level)values[index];
                    if(level != null) {
                        return level.putIfAbsent(key, item, size);
                    }
                    keys[index] = key;
                    values[index] = storeDirectly(size) ? item : new SoftReference<>(item);
                    return item;
                }
                // Collision: Add a child level, move the old item there,
                // and then insert the current item.
                Level level = new Level(levelBitsList >> 4, shift + (levelBitsList & 0xf));
                int i = (k >> level.shift) & level.mask;
                level.keys[i] = k;
                level.values[i] = values[index];
                keys[index] = 0;
                values[index] = level;
                return level.putIfAbsent(key, item, size);
            }
        }

        ResourceCache(int maxOffset) {
            assert maxOffset != 0;
            maxOffsetBits = 28;
            while(maxOffset <= 0x7ffffff) {
                maxOffset <<= 1;
                --maxOffsetBits;
            }
            int keyBits = maxOffsetBits + 2;  // +2 for mini type: at most 30 bits used in a key
            // Precompute for each level the number of bits it handles.
            if(keyBits <= ROOT_BITS) {
                levelBitsList = keyBits;
            } else if(keyBits < (ROOT_BITS + 3)) {
                levelBitsList = 0x30 | (keyBits - 3);
            } else {
                levelBitsList = ROOT_BITS;
                keyBits -= ROOT_BITS;
                int shift = 4;
                for(;;) {
                    if(keyBits <= NEXT_BITS) {
                        levelBitsList |= keyBits << shift;
                        break;
                    } else if(keyBits < (NEXT_BITS + 3)) {
                        levelBitsList |= (0x30 | (keyBits - 3)) << shift;
                        break;
                    } else {
                        levelBitsList |= NEXT_BITS << shift;
                        keyBits -= NEXT_BITS;
                        shift += 4;
                    }
                }
            }
        }

        /**
         * Turns a resource integer (with unused bits in the middle)
         * into a key with fewer bits (at most keyBits).
         */
        private int makeKey(int res) {
            // It is possible for resources of different types in the 16-bit array
            // to share a start offset; distinguish between those with a 2-bit value,
            // as a tie-breaker in the bits just above the highest possible offset.
            // It is not possible for "regular" resources of different types
            // to share a start offset with each other,
            // but offsets for 16-bit and "regular" resources overlap;
            // use 2-bit value 0 for "regular" resources.
            int type = RES_GET_TYPE(res);
            int miniType =
                    (type == ICUResourceBundle.STRING_V2) ? 1 :
                        (type == ICUResourceBundle.TABLE16) ? 3 :
                            (type == ICUResourceBundle.ARRAY16) ? 2 : 0;
            return RES_GET_OFFSET(res) | (miniType << maxOffsetBits);
        }

        private int findSimple(int key) {
            return Arrays.binarySearch(keys, 0, length, key);
        }

        @SuppressWarnings("unchecked")
        synchronized Object get(int res) {
            // Integers and empty resources need not be cached.
            // The cache itself uses res=0 for "no match".
            assert RES_GET_OFFSET(res) != 0;
            Object value;
            if(length >= 0) {
                int index = findSimple(res);
                if(index >= 0) {
                    value = values[index];
                } else {
                    return null;
                }
            } else {
                value = rootLevel.get(makeKey(res));
                if(value == null) {
                    return null;
                }
            }
            if(value instanceof SoftReference) {
                value = ((SoftReference<Object>)value).get();
            }
            return value;  // null if the reference was cleared
        }

        synchronized Object putIfAbsent(int res, Object item, int size) {
            if(length >= 0) {
                int index = findSimple(res);
                if(index >= 0) {
                    return putIfCleared(values, index, item, size);
                } else if(length < SIMPLE_LENGTH) {
                    index = ~index;
                    if(index < length) {
                        System.arraycopy(keys, index, keys, index + 1, length - index);
                        System.arraycopy(values, index, values, index + 1, length - index);
                    }
                    ++length;
                    keys[index] = res;
                    values[index] = storeDirectly(size) ? item : new SoftReference<>(item);
                    return item;
                } else /* not found && length == SIMPLE_LENGTH */ {
                    // Grow to become trie-like.
                    rootLevel = new Level(levelBitsList, 0);
                    for(int i = 0; i < SIMPLE_LENGTH; ++i) {
                        rootLevel.putIfAbsent(makeKey(keys[i]), values[i], 0);
                    }
                    keys = null;
                    values = null;
                    length = -1;
                }
            }
            return rootLevel.putIfAbsent(makeKey(res), item, size);
        }
    }

    private static final String ICU_RESOURCE_SUFFIX = ".res";

    /**
     * Gets the full name of the resource with suffix.
     */
    public static String getFullName(String baseName, String localeName) {
        if (baseName == null || baseName.length() == 0) {
            if (localeName.length() == 0) {
                return localeName = ULocale.getDefault().toString();
            }
            return localeName + ICU_RESOURCE_SUFFIX;
        } else {
            if (baseName.indexOf('.') == -1) {
                if (baseName.charAt(baseName.length() - 1) != '/') {
                    return baseName + "/" + localeName + ICU_RESOURCE_SUFFIX;
                } else {
                    return baseName + localeName + ICU_RESOURCE_SUFFIX;
                }
            } else {
                baseName = baseName.replace('.', '/');
                if (localeName.length() == 0) {
                    return baseName + ICU_RESOURCE_SUFFIX;
                } else {
                    return baseName + "_" + localeName + ICU_RESOURCE_SUFFIX;
                }
            }
        }
    }
}
