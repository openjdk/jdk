/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.imageio.plugins.tiff;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.plugins.tiff.TIFFTagSet;

public class TIFFIFD extends TIFFDirectory {
    private static final long MAX_SAMPLES_PER_PIXEL = 0xffff;
    private static final long MAX_ASCII_SIZE  = 0xffff;

    private long stripOrTileByteCountsPosition = -1;
    private long stripOrTileOffsetsPosition = -1;
    private long lastPosition = -1;

    public static TIFFTag getTag(int tagNumber, List<TIFFTagSet> tagSets) {
        Iterator<TIFFTagSet> iter = tagSets.iterator();
        while (iter.hasNext()) {
            TIFFTagSet tagSet = iter.next();
            TIFFTag tag = tagSet.getTag(tagNumber);
            if (tag != null) {
                return tag;
            }
        }

        return null;
    }

    public static TIFFTag getTag(String tagName, List<TIFFTagSet> tagSets) {
        Iterator<TIFFTagSet> iter = tagSets.iterator();
        while (iter.hasNext()) {
            TIFFTagSet tagSet = iter.next();
            TIFFTag tag = tagSet.getTag(tagName);
            if (tag != null) {
                return tag;
            }
        }

        return null;
    }

    private static void writeTIFFFieldToStream(TIFFField field,
                                               ImageOutputStream stream)
        throws IOException {
        int count = field.getCount();
        Object data = field.getData();

        switch (field.getType()) {
        case TIFFTag.TIFF_ASCII:
            for (int i = 0; i < count; i++) {
                String s = ((String[])data)[i];
                int length = s.length();
                for (int j = 0; j < length; j++) {
                    stream.writeByte(s.charAt(j) & 0xff);
                }
                stream.writeByte(0);
            }
            break;
        case TIFFTag.TIFF_UNDEFINED:
        case TIFFTag.TIFF_BYTE:
        case TIFFTag.TIFF_SBYTE:
            stream.write((byte[])data);
            break;
        case TIFFTag.TIFF_SHORT:
            stream.writeChars((char[])data, 0, ((char[])data).length);
            break;
        case TIFFTag.TIFF_SSHORT:
            stream.writeShorts((short[])data, 0, ((short[])data).length);
            break;
        case TIFFTag.TIFF_SLONG:
            stream.writeInts((int[])data, 0, ((int[])data).length);
            break;
        case TIFFTag.TIFF_LONG:
            for (int i = 0; i < count; i++) {
                stream.writeInt((int)(((long[])data)[i]));
            }
            break;
        case TIFFTag.TIFF_IFD_POINTER:
            stream.writeInt(0); // will need to be backpatched
            break;
        case TIFFTag.TIFF_FLOAT:
            stream.writeFloats((float[])data, 0, ((float[])data).length);
            break;
        case TIFFTag.TIFF_DOUBLE:
            stream.writeDoubles((double[])data, 0, ((double[])data).length);
            break;
        case TIFFTag.TIFF_SRATIONAL:
            for (int i = 0; i < count; i++) {
                stream.writeInt(((int[][])data)[i][0]);
                stream.writeInt(((int[][])data)[i][1]);
            }
            break;
        case TIFFTag.TIFF_RATIONAL:
            for (int i = 0; i < count; i++) {
                long num = ((long[][])data)[i][0];
                long den = ((long[][])data)[i][1];
                stream.writeInt((int)num);
                stream.writeInt((int)den);
            }
            break;
        default:
            // error
        }
    }

    public TIFFIFD(List<TIFFTagSet> tagSets, TIFFTag parentTag) {
        super(tagSets.toArray(new TIFFTagSet[tagSets.size()]),
              parentTag);
    }

    public TIFFIFD(List<TIFFTagSet> tagSets) {
        this(tagSets, null);
    }

    public List<TIFFTagSet> getTagSetList() {
        return Arrays.asList(getTagSets());
    }

    /**
     * Returns an <code>Iterator</code> over the TIFF fields. The
     * traversal is in the order of increasing tag number.
     */
    // Note: the sort is guaranteed for low fields by the use of an
    // array wherein the index corresponds to the tag number and for
    // the high fields by the use of a TreeMap with tag number keys.
    public Iterator<TIFFField> iterator() {
        return Arrays.asList(getTIFFFields()).iterator();
    }

    /**
     * Read the value of a field. The <code>data</code> parameter should be
     * an array of length 1 of Object.
     *
     * @param stream the input stream
     * @param type the type as read from the stream
     * @param count the count read from the stream
     * @param data a container for the data
     * @return the updated count
     * @throws IOException
     */
    private static int readFieldValue(ImageInputStream stream,
        int type, int count, Object[] data) throws IOException {
        Object obj;

        switch (type) {
            case TIFFTag.TIFF_BYTE:
            case TIFFTag.TIFF_SBYTE:
            case TIFFTag.TIFF_UNDEFINED:
            case TIFFTag.TIFF_ASCII:
                byte[] bvalues = new byte[count];
                stream.readFully(bvalues, 0, count);

                if (type == TIFFTag.TIFF_ASCII) {
                    // Can be multiple strings
                    ArrayList<String> v = new ArrayList<>();
                    boolean inString = false;
                    int prevIndex = 0;
                    for (int index = 0; index <= count; index++) {
                        if (index < count && bvalues[index] != 0) {
                            if (!inString) {
                                // start of string
                                prevIndex = index;
                                inString = true;
                            }
                        } else { // null or special case at end of string
                            if (inString) {
                                // end of string
                                String s = new String(bvalues, prevIndex,
                                        index - prevIndex,
                                        StandardCharsets.US_ASCII);
                                v.add(s);
                                inString = false;
                            }
                        }
                    }

                    count = v.size();
                    String[] strings;
                    if (count != 0) {
                        strings = new String[count];
                        for (int c = 0; c < count; c++) {
                            strings[c] = v.get(c);
                        }
                    } else {
                        // This case has been observed when the value of
                        // 'count' recorded in the field is non-zero but
                        // the value portion contains all nulls.
                        count = 1;
                        strings = new String[]{""};
                    }

                    obj = strings;
                } else {
                    obj = bvalues;
                }
                break;

            case TIFFTag.TIFF_SHORT:
                char[] cvalues = new char[count];
                for (int j = 0; j < count; j++) {
                    cvalues[j] = (char) (stream.readUnsignedShort());
                }
                obj = cvalues;
                break;

            case TIFFTag.TIFF_LONG:
            case TIFFTag.TIFF_IFD_POINTER:
                long[] lvalues = new long[count];
                for (int j = 0; j < count; j++) {
                    lvalues[j] = stream.readUnsignedInt();
                }
                obj = lvalues;
                break;

            case TIFFTag.TIFF_RATIONAL:
                long[][] llvalues = new long[count][2];
                for (int j = 0; j < count; j++) {
                    llvalues[j][0] = stream.readUnsignedInt();
                    llvalues[j][1] = stream.readUnsignedInt();
                }
                obj = llvalues;
                break;

            case TIFFTag.TIFF_SSHORT:
                short[] svalues = new short[count];
                for (int j = 0; j < count; j++) {
                    svalues[j] = stream.readShort();
                }
                obj = svalues;
                break;

            case TIFFTag.TIFF_SLONG:
                int[] ivalues = new int[count];
                for (int j = 0; j < count; j++) {
                    ivalues[j] = stream.readInt();
                }
                obj = ivalues;
                break;

            case TIFFTag.TIFF_SRATIONAL:
                int[][] iivalues = new int[count][2];
                for (int j = 0; j < count; j++) {
                    iivalues[j][0] = stream.readInt();
                    iivalues[j][1] = stream.readInt();
                }
                obj = iivalues;
                break;

            case TIFFTag.TIFF_FLOAT:
                float[] fvalues = new float[count];
                for (int j = 0; j < count; j++) {
                    fvalues[j] = stream.readFloat();
                }
                obj = fvalues;
                break;

            case TIFFTag.TIFF_DOUBLE:
                double[] dvalues = new double[count];
                for (int j = 0; j < count; j++) {
                    dvalues[j] = stream.readDouble();
                }
                obj = dvalues;
                break;

            default:
                obj = null;
                break;
        }

        data[0] = obj;

        return count;
    }

    //
    // Class to represent an IFD entry where the actual content is at an offset
    // in the stream somewhere outside the IFD itself. This occurs when the
    // value cannot be contained within four bytes. Seeking is required to read
    // such field values.
    //
    private static class TIFFIFDEntry {
        public final TIFFTag tag;
        public final int type;
        public final int count;
        public final long offset;

        TIFFIFDEntry(TIFFTag tag, int type, int count, long offset) {
            this.tag = tag;
            this.type = type;
            this.count = count;
            this.offset = offset;
        }
    }

    //
    // Verify that data pointed to outside of the IFD itself are within the
    // stream. To be called after all fields have been read and populated.
    //
    private void checkFieldOffsets(long streamLength) throws IIOException {
        if (streamLength < 0) {
            return;
        }

        // StripOffsets
        List<TIFFField> offsets = new ArrayList<>();
        TIFFField f = getTIFFField(BaselineTIFFTagSet.TAG_STRIP_OFFSETS);
        int count = 0;
        if (f != null) {
            count = f.getCount();
            offsets.add(f);
        }

        // TileOffsets
        f = getTIFFField(BaselineTIFFTagSet.TAG_TILE_OFFSETS);
        if (f != null) {
            int sz = offsets.size();
            int newCount = f.getCount();
            if (sz > 0 && newCount != count) {
                throw new IIOException
                    ("StripOffsets count != TileOffsets count");
            }

            if (sz == 0) {
                count = newCount;
            }
            offsets.add(f);
        }

        if (offsets.size() > 0) {
            // StripByteCounts
            List<TIFFField> byteCounts = new ArrayList<>();
            f = getTIFFField(BaselineTIFFTagSet.TAG_STRIP_BYTE_COUNTS);
            if (f != null) {
                if (f.getCount() != count) {
                    throw new IIOException
                        ("StripByteCounts count != number of offsets");
                }
                byteCounts.add(f);
            }

            // TileByteCounts
            f = getTIFFField(BaselineTIFFTagSet.TAG_TILE_BYTE_COUNTS);
            if (f != null) {
                if (f.getCount() != count) {
                    throw new IIOException
                        ("TileByteCounts count != number of offsets");
                }
                byteCounts.add(f);
            }

            if (byteCounts.size() > 0) {
                for (TIFFField offset : offsets) {
                    for (TIFFField byteCount : byteCounts) {
                        for (int i = 0; i < count; i++) {
                            long dataOffset = offset.getAsLong(i);
                            long dataByteCount = byteCount.getAsLong(i);
                            if (dataOffset + dataByteCount > streamLength) {
                                throw new IIOException
                                    ("Data segment out of stream");
                            }
                        }
                    }
                }
            }
        }

        // JPEGInterchangeFormat and JPEGInterchangeFormatLength
        TIFFField jpegOffset =
            getTIFFField(BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT);
        if (jpegOffset != null) {
            TIFFField jpegLength =
                getTIFFField(BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH);
            if (jpegLength != null) {
                if (jpegOffset.getAsLong(0) + jpegLength.getAsLong(0)
                    > streamLength) {
                    throw new IIOException
                        ("JPEGInterchangeFormat data out of stream");
                }
            }
        }

        // JPEGQTables - one 64-byte table for each offset.
        f = getTIFFField(BaselineTIFFTagSet.TAG_JPEG_Q_TABLES);
        if (f != null) {
            long[] tableOffsets = f.getAsLongs();
            for (long off : tableOffsets) {
                if (off + 64 > streamLength) {
                    throw new IIOException("JPEGQTables data out of stream");
                }
            }
        }

        // JPEGDCTables
        f = getTIFFField(BaselineTIFFTagSet.TAG_JPEG_DC_TABLES);
        if (f != null) {
            long[] tableOffsets = f.getAsLongs();
            for (long off : tableOffsets) {
                if (off + 16 > streamLength) {
                    throw new IIOException("JPEGDCTables data out of stream");
                }
            }
        }

        // JPEGACTables
        f = getTIFFField(BaselineTIFFTagSet.TAG_JPEG_AC_TABLES);
        if (f != null) {
            long[] tableOffsets = f.getAsLongs();
            for (long off : tableOffsets) {
                if (off + 16 > streamLength) {
                    throw new IIOException("JPEGACTables data out of stream");
                }
            }
        }
    }

    // Stream position initially at beginning, left at end
    // if ignoreUnknownFields is true, do not load fields for which
    // a tag cannot be found in an allowed TagSet.
    public void initialize(ImageInputStream stream, boolean isPrimaryIFD,
        boolean ignoreUnknownFields) throws IOException {

        removeTIFFFields();

        long streamLength = stream.length();
        boolean haveStreamLength = streamLength != -1;

        List<TIFFTagSet> tagSetList = getTagSetList();

        List<Object> entries = new ArrayList<>();
        Object[] entryData = new Object[1]; // allocate once for later reuse.

        // Read the IFD entries, loading the field values which are no more than
        // four bytes long, and storing the 4-byte offsets for the others.
        int numEntries = stream.readUnsignedShort();
        for (int i = 0; i < numEntries; i++) {
            // Read tag number, value type, and value count.
            int tagNumber = stream.readUnsignedShort();
            int type = stream.readUnsignedShort();
            int count = (int)stream.readUnsignedInt();

            // Get the associated TIFFTag.
            TIFFTag tag = getTag(tagNumber, tagSetList);

            // Ignore unknown fields.
            if((tag == null && ignoreUnknownFields)
                || (tag != null && !tag.isDataTypeOK(type))) {
                // Skip the value/offset so as to leave the stream
                // position at the start of the next IFD entry.
                stream.skipBytes(4);

                // Continue with the next IFD entry.
                continue;
            }

            if (tag == null) {
                tag = new TIFFTag(TIFFTag.UNKNOWN_TAG_NAME, tagNumber,
                    1 << type, count);
            } else {
                int expectedCount = tag.getCount();
                if (expectedCount > 0) {
                    // If the tag count is positive then the tag defines a
                    // specific, fixed count that the field must match.
                    if (count != expectedCount) {
                        throw new IIOException("Unexpected count "
                            + count + " for " + tag.getName() + " field");
                    }
                } else if (type == TIFFTag.TIFF_ASCII) {
                    // Clamp the size of ASCII fields of unspecified length
                    // to a maximum value.
                    int asciiSize = TIFFTag.getSizeOfType(TIFFTag.TIFF_ASCII);
                    if (count*asciiSize > MAX_ASCII_SIZE) {
                        count = (int)(MAX_ASCII_SIZE/asciiSize);
                    }
                }
            }

            int size = count*TIFFTag.getSizeOfType(type);
            if (size > 4 || tag.isIFDPointer()) {
                // The IFD entry value is a pointer to the actual field value.
                long offset = stream.readUnsignedInt();

                // Check whether the the field value is within the stream.
                if (haveStreamLength && offset + size > streamLength) {
                    throw new IIOException("Field data is past end-of-stream");
                }

                // Add a TIFFIFDEntry as a placeholder. This avoids a mark,
                // seek to the data, and a reset.
                entries.add(new TIFFIFDEntry(tag, type, count, offset));
            } else {
                // The IFD entry value is the actual field value of no more than
                // four bytes.
                Object obj = null;
                try {
                    // Read the field value and update the count.
                    count = readFieldValue(stream, type, count, entryData);
                    obj = entryData[0];
                } catch (EOFException eofe) {
                    // The TIFF 6.0 fields have tag numbers less than or equal
                    // to 532 (ReferenceBlackWhite) or equal to 33432 (Copyright).
                    // If there is an error reading a baseline tag, then re-throw
                    // the exception and fail; otherwise continue with the next
                    // field.
                    if (BaselineTIFFTagSet.getInstance().getTag(tagNumber) == null) {
                        throw eofe;
                    }
                }

                // If the field value is smaller than four bytes then skip
                // the remaining, unused bytes.
                if (size < 4) {
                    stream.skipBytes(4 - size);
                }

                // Add the populated TIFFField to the list of entries.
                entries.add(new TIFFField(tag, type, count, obj));
            }
        }

        // After reading the IFD entries the stream is positioned at an unsigned
        // four byte integer containing either the offset of the next IFD or
        // zero if this is the last IFD.
        long nextIFDOffset = stream.getStreamPosition();

        Object[] fieldData = new Object[1];
        for (Object entry : entries) {
            if (entry instanceof TIFFField) {
                // Add the populated field directly.
                addTIFFField((TIFFField)entry);
            } else {
                TIFFIFDEntry e = (TIFFIFDEntry)entry;
                TIFFTag tag = e.tag;
                int tagNumber = tag.getNumber();
                int type = e.type;
                int count = e.count;

                stream.seek(e.offset);

                if (tag.isIFDPointer()) {
                    List<TIFFTagSet> tagSets = new ArrayList<TIFFTagSet>(1);
                    tagSets.add(tag.getTagSet());
                    TIFFIFD subIFD = new TIFFIFD(tagSets);

                    subIFD.initialize(stream, false, ignoreUnknownFields);
                    TIFFField f = new TIFFField(tag, type, e.offset, subIFD);
                    addTIFFField(f);
                } else {
                    if (tagNumber == BaselineTIFFTagSet.TAG_STRIP_BYTE_COUNTS
                            || tagNumber == BaselineTIFFTagSet.TAG_TILE_BYTE_COUNTS
                            || tagNumber == BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH) {
                        this.stripOrTileByteCountsPosition
                                = stream.getStreamPosition();
                    } else if (tagNumber == BaselineTIFFTagSet.TAG_STRIP_OFFSETS
                            || tagNumber == BaselineTIFFTagSet.TAG_TILE_OFFSETS
                            || tagNumber == BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT) {
                        this.stripOrTileOffsetsPosition
                                = stream.getStreamPosition();
                    }

                    Object obj = null;
                    try {
                        count = readFieldValue(stream, type, count, fieldData);
                        obj = fieldData[0];
                    } catch (EOFException eofe) {
                        // The TIFF 6.0 fields have tag numbers less than or equal
                        // to 532 (ReferenceBlackWhite) or equal to 33432 (Copyright).
                        // If there is an error reading a baseline tag, then re-throw
                        // the exception and fail; otherwise continue with the next
                        // field.
                        if (BaselineTIFFTagSet.getInstance().getTag(tagNumber) == null) {
                            throw eofe;
                        }
                    }

                    if (obj == null) {
                        continue;
                    }

                    TIFFField f = new TIFFField(tag, type, count, obj);
                    addTIFFField(f);
                }
            }
        }

        if(isPrimaryIFD && haveStreamLength) {
            checkFieldOffsets(streamLength);
        }

        stream.seek(nextIFDOffset);
        this.lastPosition = stream.getStreamPosition();
    }

    public void writeToStream(ImageOutputStream stream)
        throws IOException {

        int numFields = getNumTIFFFields();
        stream.writeShort(numFields);

        long nextSpace = stream.getStreamPosition() + 12*numFields + 4;

        Iterator<TIFFField> iter = iterator();
        while (iter.hasNext()) {
            TIFFField f = iter.next();

            TIFFTag tag = f.getTag();

            int type = f.getType();
            int count = f.getCount();

            // Deal with unknown tags
            if (type == 0) {
                type = TIFFTag.TIFF_UNDEFINED;
            }
            int size = count*TIFFTag.getSizeOfType(type);

            if (type == TIFFTag.TIFF_ASCII) {
                int chars = 0;
                for (int i = 0; i < count; i++) {
                    chars += f.getAsString(i).length() + 1;
                }
                count = chars;
                size = count;
            }

            int tagNumber = f.getTagNumber();
            stream.writeShort(tagNumber);
            stream.writeShort(type);
            stream.writeInt(count);

            // Write a dummy value to fill space
            stream.writeInt(0);
            stream.mark(); // Mark beginning of next field
            stream.skipBytes(-4);

            long pos;

            if (size > 4 || tag.isIFDPointer()) {
                // Ensure IFD or value is written on a word boundary
                nextSpace = (nextSpace + 3) & ~0x3;

                stream.writeInt((int)nextSpace);
                stream.seek(nextSpace);
                pos = nextSpace;

                if (tag.isIFDPointer() && f.hasDirectory()) {
                    TIFFIFD subIFD = (TIFFIFD)f.getDirectory();
                    subIFD.writeToStream(stream);
                    nextSpace = subIFD.lastPosition;
                } else {
                    writeTIFFFieldToStream(f, stream);
                    nextSpace = stream.getStreamPosition();
                }
            } else {
                pos = stream.getStreamPosition();
                writeTIFFFieldToStream(f, stream);
            }

            // If we are writing the data for the
            // StripByteCounts, TileByteCounts, StripOffsets,
            // TileOffsets, JPEGInterchangeFormat, or
            // JPEGInterchangeFormatLength fields, record the current stream
            // position for backpatching
            if (tagNumber ==
                BaselineTIFFTagSet.TAG_STRIP_BYTE_COUNTS ||
                tagNumber == BaselineTIFFTagSet.TAG_TILE_BYTE_COUNTS ||
                tagNumber == BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH) {
                this.stripOrTileByteCountsPosition = pos;
            } else if (tagNumber ==
                       BaselineTIFFTagSet.TAG_STRIP_OFFSETS ||
                       tagNumber ==
                       BaselineTIFFTagSet.TAG_TILE_OFFSETS ||
                       tagNumber ==
                       BaselineTIFFTagSet.TAG_JPEG_INTERCHANGE_FORMAT) {
                this.stripOrTileOffsetsPosition = pos;
            }

            stream.reset(); // Go to marked position of next field
        }

        this.lastPosition = nextSpace;
    }

    public long getStripOrTileByteCountsPosition() {
        return stripOrTileByteCountsPosition;
    }

    public long getStripOrTileOffsetsPosition() {
        return stripOrTileOffsetsPosition;
    }

    public long getLastPosition() {
        return lastPosition;
    }

    void setPositions(long stripOrTileOffsetsPosition,
                      long stripOrTileByteCountsPosition,
                      long lastPosition) {
        this.stripOrTileOffsetsPosition = stripOrTileOffsetsPosition;
        this.stripOrTileByteCountsPosition = stripOrTileByteCountsPosition;
        this.lastPosition = lastPosition;
    }

    /**
     * Returns a <code>TIFFIFD</code> wherein all fields from the
     * <code>BaselineTIFFTagSet</code> are copied by value and all other
     * fields copied by reference.
     */
    public TIFFIFD getShallowClone() {
        // Get the baseline TagSet.
        TIFFTagSet baselineTagSet = BaselineTIFFTagSet.getInstance();

        // If the baseline TagSet is not included just return.
        List<TIFFTagSet> tagSetList = getTagSetList();
        if(!tagSetList.contains(baselineTagSet)) {
            return this;
        }

        // Create a new object.
        TIFFIFD shallowClone = new TIFFIFD(tagSetList, getParentTag());

        // Get the tag numbers in the baseline set.
        Set<Integer> baselineTagNumbers = baselineTagSet.getTagNumbers();

        // Iterate over the fields in this IFD.
        Iterator<TIFFField> fields = iterator();
        while(fields.hasNext()) {
            // Get the next field.
            TIFFField field = fields.next();

            // Get its tag number.
            Integer tagNumber = Integer.valueOf(field.getTagNumber());

            // Branch based on membership in baseline set.
            TIFFField fieldClone;
            if(baselineTagNumbers.contains(tagNumber)) {
                // Copy by value.
                Object fieldData = field.getData();

                int fieldType = field.getType();

                try {
                    switch (fieldType) {
                    case TIFFTag.TIFF_BYTE:
                    case TIFFTag.TIFF_SBYTE:
                    case TIFFTag.TIFF_UNDEFINED:
                        fieldData = ((byte[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_ASCII:
                        fieldData = ((String[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_SHORT:
                        fieldData = ((char[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_LONG:
                    case TIFFTag.TIFF_IFD_POINTER:
                        fieldData = ((long[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_RATIONAL:
                        fieldData = ((long[][])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_SSHORT:
                        fieldData = ((short[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_SLONG:
                        fieldData = ((int[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_SRATIONAL:
                        fieldData = ((int[][])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_FLOAT:
                        fieldData = ((float[])fieldData).clone();
                        break;
                    case TIFFTag.TIFF_DOUBLE:
                        fieldData = ((double[])fieldData).clone();
                        break;
                    default:
                        // Shouldn't happen but do nothing ...
                    }
                } catch(Exception e) {
                    // Ignore it and copy by reference ...
                }

                fieldClone = new TIFFField(field.getTag(), fieldType,
                                           field.getCount(), fieldData);
            } else {
                // Copy by reference.
                fieldClone = field;
            }

            // Add the field to the clone.
            shallowClone.addTIFFField(fieldClone);
        }

        // Set positions.
        shallowClone.setPositions(stripOrTileOffsetsPosition,
                                  stripOrTileByteCountsPosition,
                                  lastPosition);

        return shallowClone;
    }
}
