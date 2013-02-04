/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.zone;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.nio.file.FileSystems;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.DateTimeException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.zip.ZipFile;

/**
 * Loads time-zone rules for 'TZDB'.
 * <p>
 * This class is public for the service loader to access.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
final class TzdbZoneRulesProvider extends ZoneRulesProvider {
    // service loader seems to need it to be public

    /**
     * All the regions that are available.
     */
    private final Set<String> regionIds = new CopyOnWriteArraySet<>();
    /**
     * All the versions that are available.
     */
    private final ConcurrentNavigableMap<String, Version> versions = new ConcurrentSkipListMap<>();

    /**
     * Creates an instance.
     * Created by the {@code ServiceLoader}.
     *
     * @throws ZoneRulesException if unable to load
     */
    public TzdbZoneRulesProvider() {
        super();
        if (load(ClassLoader.getSystemClassLoader()) == false) {
            throw new ZoneRulesException("No time-zone rules found for 'TZDB'");
        }
    }

    //-----------------------------------------------------------------------
    @Override
    protected Set<String> provideZoneIds() {
        return new HashSet<>(regionIds);
    }

    @Override
    protected ZoneRules provideRules(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        ZoneRules rules = versions.lastEntry().getValue().getRules(zoneId);
        if (rules == null) {
            throw new ZoneRulesException("Unknown time-zone ID: " + zoneId);
        }
        return rules;
    }

    @Override
    protected NavigableMap<String, ZoneRules> provideVersions(String zoneId) {
        TreeMap<String, ZoneRules> map = new TreeMap<>();
        for (Version version : versions.values()) {
            ZoneRules rules = version.getRules(zoneId);
            if (rules != null) {
                map.put(version.versionId, rules);
            }
        }
        return map;
    }

    //-------------------------------------------------------------------------
    /**
     * Loads the rules.
     *
     * @param classLoader  the class loader to use, not null
     * @return true if updated
     * @throws ZoneRulesException if unable to load
     */
    private boolean load(ClassLoader classLoader) {
        Object updated = Boolean.FALSE;
        try {
            updated = AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IOException, ClassNotFoundException {
                    File tzdbJar = null;
                    // TBD: workaround for now, so test/java/time tests can be
                    //      run against Java runtime that does not have tzdb
                    String tzdbProp = System.getProperty("java.time.zone.tzdbjar");
                    if (tzdbProp != null) {
                        tzdbJar = new File(tzdbProp);
                    } else {
                        String libDir = System.getProperty("java.home") + File.separator + "lib";
                        try {
                            libDir = FileSystems.getDefault().getPath(libDir).toRealPath().toString();
                        } catch(Exception e) {}
                        tzdbJar = new File(libDir, "tzdb.jar");
                    }
                    try (ZipFile zf = new ZipFile(tzdbJar);
                         DataInputStream dis = new DataInputStream(
                             zf.getInputStream(zf.getEntry("TZDB.dat")))) {
                        Iterable<Version> loadedVersions = load(dis);
                        for (Version loadedVersion : loadedVersions) {
                            if (versions.putIfAbsent(loadedVersion.versionId, loadedVersion) != null) {
                                throw new DateTimeException(
                                    "Data already loaded for TZDB time-zone rules version: " +
                                    loadedVersion.versionId);
                            }
                        }
                    }
                    return Boolean.TRUE;
                }
            });
        } catch (Exception ex) {
            throw new ZoneRulesException("Unable to load TZDB time-zone rules", ex);
        }
        return updated == Boolean.TRUE;
    }

    /**
     * Loads the rules from a DateInputStream, often in a jar file.
     *
     * @param dis  the DateInputStream to load, not null
     * @throws Exception if an error occurs
     */
    private Iterable<Version> load(DataInputStream dis) throws ClassNotFoundException, IOException {
        if (dis.readByte() != 1) {
            throw new StreamCorruptedException("File format not recognised");
        }
        // group
        String groupId = dis.readUTF();
        if ("TZDB".equals(groupId) == false) {
            throw new StreamCorruptedException("File format not recognised");
        }
        // versions
        int versionCount = dis.readShort();
        String[] versionArray = new String[versionCount];
        for (int i = 0; i < versionCount; i++) {
            versionArray[i] = dis.readUTF();
        }
        // regions
        int regionCount = dis.readShort();
        String[] regionArray = new String[regionCount];
        for (int i = 0; i < regionCount; i++) {
            regionArray[i] = dis.readUTF();
        }
        regionIds.addAll(Arrays.asList(regionArray));
        // rules
        int ruleCount = dis.readShort();
        Object[] ruleArray = new Object[ruleCount];
        for (int i = 0; i < ruleCount; i++) {
            byte[] bytes = new byte[dis.readShort()];
            dis.readFully(bytes);
            ruleArray[i] = bytes;
        }
        AtomicReferenceArray<Object> ruleData = new AtomicReferenceArray<>(ruleArray);
        // link version-region-rules
        Set<Version> versionSet = new HashSet<Version>(versionCount);
        for (int i = 0; i < versionCount; i++) {
            int versionRegionCount = dis.readShort();
            String[] versionRegionArray = new String[versionRegionCount];
            short[] versionRulesArray = new short[versionRegionCount];
            for (int j = 0; j < versionRegionCount; j++) {
                versionRegionArray[j] = regionArray[dis.readShort()];
                versionRulesArray[j] = dis.readShort();
            }
            versionSet.add(new Version(versionArray[i], versionRegionArray, versionRulesArray, ruleData));
        }
        return versionSet;
    }

    @Override
    public String toString() {
        return "TZDB";
    }

    //-----------------------------------------------------------------------
    /**
     * A version of the TZDB rules.
     */
    static class Version {
        private final String versionId;
        private final String[] regionArray;
        private final short[] ruleIndices;
        private final AtomicReferenceArray<Object> ruleData;

        Version(String versionId, String[] regionIds, short[] ruleIndices, AtomicReferenceArray<Object> ruleData) {
            this.ruleData = ruleData;
            this.versionId = versionId;
            this.regionArray = regionIds;
            this.ruleIndices = ruleIndices;
        }

        ZoneRules getRules(String regionId) {
            int regionIndex = Arrays.binarySearch(regionArray, regionId);
            if (regionIndex < 0) {
                return null;
            }
            try {
                return createRule(ruleIndices[regionIndex]);
            } catch (Exception ex) {
                throw new ZoneRulesException("Invalid binary time-zone data: TZDB:" + regionId + ", version: " + versionId, ex);
            }
        }

        ZoneRules createRule(short index) throws Exception {
            Object obj = ruleData.get(index);
            if (obj instanceof byte[]) {
                byte[] bytes = (byte[]) obj;
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
                obj = Ser.read(dis);
                ruleData.set(index, obj);
            }
            return (ZoneRules) obj;
        }

        @Override
        public String toString() {
            return versionId;
        }
    }

}
