/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.PrintStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BasicImageWriter {
    private final static int RETRY_LIMIT = 1000;

    private ByteOrder byteOrder;
    private ImageStrings strings;
    private int count;
    private int[] redirect;
    private ImageLocation[] locations;
    private List<ImageLocation> input;
    private ImageStream headerStream;
    private ImageStream redirectStream;
    private ImageStream locationOffsetStream;
    private ImageStream locationStream;
    private ImageStream allIndexStream;

    static class ImageBucket implements Comparable<ImageBucket> {
        final List<ImageLocation> list;

        ImageBucket() {
            this.list = new ArrayList<>();
        }

        void add(ImageLocation location) {
            list.add(location);
        }

        int getSize() {
            return list.size();
        }

        List<ImageLocation> getList() {
            return list;
        }

        ImageLocation getFirst() {
            assert !list.isEmpty() : "bucket should never be empty";
            return list.get(0);
        }

        @Override
        public int hashCode() {
            return getFirst().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int compareTo(ImageBucket o) {
            return o.getSize() - getSize();
        }
    }

    public BasicImageWriter() {
        this(ByteOrder.nativeOrder());
    }

    public BasicImageWriter(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.input = new ArrayList<>();
        this.strings = new ImageStrings();
        this.headerStream = new ImageStream(byteOrder);
        this.redirectStream = new ImageStream(byteOrder);
        this.locationOffsetStream = new ImageStream(byteOrder);
        this.locationStream = new ImageStream(byteOrder);
        this.allIndexStream = new ImageStream(byteOrder);
    }

    public int addString(String string) {
        return addString(new UTF8String(string));
    }

    public int addString(UTF8String string) {
        return strings.add(string);
    }

    public void addLocation(String fullname, long contentOffset, long compressedSize, long uncompressedSize) {
        ImageLocation location = ImageLocation.newLocation(new UTF8String(fullname), strings, contentOffset, compressedSize, uncompressedSize);
        input.add(location);
        count++;
    }

    private void generatePerfectHash() {
        redo:
        while(true) {
            redirect = new int[count];
            locations = new ImageLocation[count];

            ImageBucket[] sorted = createBuckets();

            int free = 0;

            for (ImageBucket bucket : sorted) {
                if (bucket.getSize() != 1) {
                    if (!packCollidedEntries(bucket, count)) {
                        count = (count + 1) | 1;

                        continue redo;
                    }
                } else {
                    for ( ; free < count && locations[free] != null; free++) {}
                    assert free < count : "no free slots";
                    locations[free] = bucket.getFirst();
                    redirect[bucket.hashCode() % count] = -1 - free;
                    free++;
                }
            }

            break;
        }
    }

    private ImageBucket[] createBuckets() {
        ImageBucket[] buckets = new ImageBucket[count];

        input.stream().forEach((location) -> {
            int index = location.hashCode() % count;
            ImageBucket bucket = buckets[index];

            if (bucket == null) {
                buckets[index] = bucket = new ImageBucket();
            }

            bucket.add(location);
        });

        ImageBucket[] sorted = Arrays.asList(buckets).stream()
                .filter((bucket) -> (bucket != null))
                .sorted()
                .toArray(ImageBucket[]::new);

        return sorted;
    }

    private boolean packCollidedEntries(ImageBucket bucket, int count) {
        List<Integer> undo = new ArrayList<>();
        int base = UTF8String.HASH_MULTIPLIER + 1;

        int retry = 0;

        redo:
        while (true) {
            for (ImageLocation location : bucket.getList()) {
                int index = location.hashCode(base) % count;

                if (locations[index] != null) {
                    undo.stream().forEach((i) -> {
                        locations[i] = null;
                    });

                    undo.clear();
                    base++;

                    if (base == 0) {
                        base = 1;
                    }

                    if (++retry > RETRY_LIMIT) {
                        return false;
                    }

                    continue redo;
                }

                locations[index] = location;
                undo.add(index);
            }

            redirect[bucket.hashCode() % count] = base;

            break;
        }

        return true;
    }

    private void prepareStringBytes() {
        strings.getStream().align(2);
    }

    private void prepareRedirectBytes() {
        for (int i = 0; i < count; i++) {
            redirectStream.putInt(redirect[i]);
        }
    }

    private void prepareLocationBytes() {
        // Reserve location offset zero for empty locations
        locationStream.put(ImageLocation.ATTRIBUTE_END << 3);

        for (int i = 0; i < count; i++) {
            ImageLocation location = locations[i];

            if (location != null) {
                location.writeTo(locationStream);
            }
        }

        locationStream.align(2);
    }

    private void prepareOffsetBytes() {
        for (int i = 0; i < count; i++) {
            ImageLocation location = locations[i];
            locationOffsetStream.putInt(location != null ? location.getLocationOffset() : 0);
        }
    }

    private void prepareHeaderBytes() {
        ImageHeader header = new ImageHeader(count, locationStream.getSize(), strings.getSize());
        header.writeTo(headerStream);
    }

    private void prepareTableBytes() {
        allIndexStream.put(headerStream);
        allIndexStream.put(redirectStream);
        allIndexStream.put(locationOffsetStream);
        allIndexStream.put(locationStream);
        allIndexStream.put(strings.getStream());
    }

    public byte[] getBytes() {
        if (allIndexStream.getSize() == 0) {
            generatePerfectHash();
            prepareStringBytes();
            prepareRedirectBytes();
            prepareLocationBytes();
            prepareOffsetBytes();
            prepareHeaderBytes();
            prepareTableBytes();
        }

        return allIndexStream.toArray();
    }

    ImageLocation find(UTF8String key) {
        int index = key.hashCode() % count;
        index = redirect[index];

        if (index < 0) {
            index = -index - 1;
            ImageLocation location = locations[index];

            return location;
        } else {
            index = key.hashCode(index) % count;
            ImageLocation location = locations[index];

            return location;
        }
    }

    public void statistics() {
        getBytes();
        PrintStream out = System.out;
        out.println("Count: " + count);
        out.println("Header bytes size: " + headerStream.getSize());
        out.println("Redirect bytes size: " + redirectStream.getSize());
        out.println("Offset bytes size: " + locationOffsetStream.getSize());
        out.println("Location bytes size: " + locationStream.getSize());
        out.println("String count: " + strings.getCount());
        out.println("String bytes size: " + strings.getSize());
        out.println("Total bytes size: " + allIndexStream.getSize());
    }
}
