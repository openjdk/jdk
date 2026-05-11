/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jlink.internal;

import jdk.internal.jimage.ImageHeader;
import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.ImageStream;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static jdk.internal.jimage.ImageLocation.ATTRIBUTE_PREVIEW_FLAGS;

public final class BasicImageWriter {
    public static final String MODULES_IMAGE_NAME = "modules";

    private final ByteOrder byteOrder;
    private final ImageStringsWriter strings;
    private int length;
    private int[] redirect;
    private ImageLocationWriter[] locations;
    private final Map<String, ImageLocationWriter> input;
    private final ImageStream headerStream;
    private final ImageStream redirectStream;
    private final ImageStream locationOffsetStream;
    private final ImageStream locationStream;
    private final ImageStream allIndexStream;

    public BasicImageWriter(ByteOrder byteOrder) {
        this.byteOrder = Objects.requireNonNull(byteOrder);
        // Linked hashmap preserves order of adding to builder.
        // TODO(review): This might not be necessary...
        this.input = new LinkedHashMap<>();
        this.strings = new ImageStringsWriter();
        this.headerStream = new ImageStream(byteOrder);
        this.redirectStream = new ImageStream(byteOrder);
        this.locationOffsetStream = new ImageStream(byteOrder);
        this.locationStream = new ImageStream(byteOrder);
        this.allIndexStream = new ImageStream(byteOrder);
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public int addString(String string) {
        return strings.add(string);
    }

    public String getString(int offset) {
        return strings.get(offset);
    }

    public ImageLocationWriter addLocation(
            String fullname,
            long contentOffset,
            long compressedSize,
            long uncompressedSize) {
        ImageLocationWriter location = ImageLocationWriter.newLocation(
                fullname, strings, contentOffset, compressedSize, uncompressedSize);
        input.put(fullname, location);
        length++;
        return location;
    }

    ImageLocationWriter[] getLocations() {
        return locations;
    }

    private void generatePerfectHash() {
        PerfectHashBuilder<ImageLocationWriter> builder =
            new PerfectHashBuilder<>(
                        PerfectHashBuilder.Entry.class,
                        PerfectHashBuilder.Bucket.class);

        input.forEach(builder::put);

        builder.generate();

        length = builder.getCount();
        redirect = builder.getRedirect();
        PerfectHashBuilder.Entry<ImageLocationWriter>[] order = builder.getOrder();
        locations = new ImageLocationWriter[length];

        for (int i = 0; i < length; i++) {
            locations[i] = order[i].getValue();
        }
    }

    private void prepareStringBytes() {
        strings.getStream().align(2);
    }

    private void prepareRedirectBytes() {
        for (int i = 0; i < length; i++) {
            redirectStream.putInt(redirect[i]);
        }
    }

    private void generateLocationFlags() {
        Set<String> allNames = input.keySet();
        for (String name : allNames) {
            // Note that flags for "/packages/xxx" entries are already set, so we
            // must not unconditionally write zero here if the returned flag is zero.
            int previewFlags = ImageLocation.getPreviewFlags(name, allNames::contains);
            if (previewFlags != 0) {
                input.get(name).addAttribute(ATTRIBUTE_PREVIEW_FLAGS, previewFlags);
            }
        }
    }

    private void prepareLocationBytes() {
        // Reserve location offset zero for empty locations
        locationStream.put(ImageLocationWriter.ATTRIBUTE_END << 3);

        for (int i = 0; i < length; i++) {
            ImageLocationWriter location = locations[i];

            if (location != null) {
                location.writeTo(locationStream);
            }
        }

        locationStream.align(2);
    }

    private void prepareOffsetBytes() {
        for (int i = 0; i < length; i++) {
            ImageLocationWriter location = locations[i];
            int offset = location != null ? location.getLocationOffset() : 0;
            locationOffsetStream.putInt(offset);
        }
    }

    private void prepareHeaderBytes() {
        ImageHeader header = new ImageHeader(input.size(), length,
                locationStream.getSize(), strings.getSize());
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
            generateLocationFlags();
            prepareLocationBytes();
            prepareOffsetBytes();
            prepareHeaderBytes();
            prepareTableBytes();
        }

        return allIndexStream.toArray();
    }
}
