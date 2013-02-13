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

package sun.util.calendar;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/**
 * Loads TZDB time-zone rules for j.u.TimeZone
 * <p>
 * @since 1.8
 */
public final class ZoneInfoFile {

    /**
     * Gets all available IDs supported in the Java run-time.
     *
     * @return a set of time zone IDs.
     */
    public static Set<String> getZoneIds() {
        return zones.keySet();
    }

    /**
     * Gets all available IDs that have the same value as the
     * specified raw GMT offset.
     *
     * @param rawOffset  the GMT offset in milliseconds. This
     *                   value should not include any daylight saving time.
     * @return an array of time zone IDs.
     */
    public static String[] getZoneIds(int rawOffset) {
        List<String> ids = new ArrayList<>();
        for (String id : zones.keySet()) {
            ZoneInfo zi = getZoneInfo0(id);
            if (zi.getRawOffset() == rawOffset) {
                ids.add(id);
            }
        }
        // It appears the "zi" implementation returns the
        // sorted list, though the specification does not
        // specify it. Keep the same behavior for better
        // compatibility.
        String[] list = ids.toArray(new String[ids.size()]);
        Arrays.sort(list);
        return list;
    }

    public static ZoneInfo getZoneInfo(String zoneId) {
        if (!zones.containsKey(zoneId)) {
            return null;
        }
        // ZoneInfo is mutable, return the copy

        ZoneInfo zi = getZoneInfo0(zoneId);
        zi = (ZoneInfo)zi.clone();
        zi.setID(zoneId);
        return zi;
    }

    /**
     * Returns a Map from alias time zone IDs to their standard
     * time zone IDs.
     *
     * @return an unmodified alias mapping
     */
    public static Map<String, String> getAliasMap() {
        return aliases;
    }

    /**
     * Gets the version of this tz data.
     *
     * @return the tzdb version
     */
    public static String getVersion() {
        return versionId;
    }

    /**
     * Gets a ZoneInfo with the given GMT offset. The object
     * has its ID in the format of GMT{+|-}hh:mm.
     *
     * @param originalId  the given custom id (before normalized such as "GMT+9")
     * @param gmtOffset   GMT offset <em>in milliseconds</em>
     * @return a ZoneInfo constructed with the given GMT offset
     */
    public static ZoneInfo getCustomTimeZone(String originalId, int gmtOffset) {
        String id = toCustomID(gmtOffset);
        return new ZoneInfo(id, gmtOffset);
/*
        ZoneInfo zi = getFromCache(id);
        if (zi == null) {
            zi = new ZoneInfo(id, gmtOffset);
            zi = addToCache(id, zi);
            if (!id.equals(originalId)) {
                zi = addToCache(originalId, zi);
            }
        }
        return (ZoneInfo) zi.clone();
*/
    }

    public static String toCustomID(int gmtOffset) {
        char sign;
        int offset = gmtOffset / 60000;

        if (offset >= 0) {
            sign = '+';
        } else {
            sign = '-';
            offset = -offset;
        }
        int hh = offset / 60;
        int mm = offset % 60;

        char[] buf = new char[] { 'G', 'M', 'T', sign, '0', '0', ':', '0', '0' };
        if (hh >= 10) {
            buf[4] += hh / 10;
        }
        buf[5] += hh % 10;
        if (mm != 0) {
            buf[7] += mm / 10;
            buf[8] += mm % 10;
        }
        return new String(buf);
    }


    ///////////////////////////////////////////////////////////

    private static ZoneInfo getZoneInfo0(String zoneId) {
        try {

            Object obj = zones.get(zoneId);
            if (obj instanceof byte[]) {
                byte[] bytes = (byte[]) obj;
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
                obj = getZoneInfo(dis, zoneId);
                zones.put(zoneId, obj);
            }
            return (ZoneInfo)obj;
        } catch (Exception ex) {
            throw new RuntimeException("Invalid binary time-zone data: TZDB:" +
                zoneId + ", version: " + versionId, ex);
        }
    }

    private ZoneInfoFile() {
    }

    private static String versionId;
    private final static Map<String, Object> zones = new ConcurrentHashMap<>();
    private static Map<String, String> aliases = new HashMap<>();

    // Flag for supporting JDK backward compatible IDs, such as "EST".
    private static final boolean USE_OLDMAPPING;

    static {
        String oldmapping = AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.timezone.ids.oldmapping", "false")).toLowerCase(Locale.ROOT);
        USE_OLDMAPPING = (oldmapping.equals("yes") || oldmapping.equals("true"));
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {

                    String libDir = System.getProperty("java.home") + File.separator + "lib";
                    File tzdbJar = new File(libDir, "tzdb.jar");
                    try (ZipFile zf = new ZipFile(tzdbJar);
                        DataInputStream dis = new DataInputStream(
                            zf.getInputStream(zf.getEntry("TZDB.dat")))) {
                        load(dis);
                    }
                } catch (Exception x) {
                    throw new Error(x);
                }
                return null;
            }
        });
    }

    // Must be invoked after loading in all data
    private static void addOldMapping() {
        String[][] oldMappings = new String[][] {
            { "ACT", "Australia/Darwin" },
            { "AET", "Australia/Sydney" },
            { "AGT", "America/Argentina/Buenos_Aires" },
            { "ART", "Africa/Cairo" },
            { "AST", "America/Anchorage" },
            { "BET", "America/Sao_Paulo" },
            { "BST", "Asia/Dhaka" },
            { "CAT", "Africa/Harare" },
            { "CNT", "America/St_Johns" },
            { "CST", "America/Chicago" },
            { "CTT", "Asia/Shanghai" },
            { "EAT", "Africa/Addis_Ababa" },
            { "ECT", "Europe/Paris" },
            { "IET", "America/Indiana/Indianapolis" },
            { "IST", "Asia/Kolkata" },
            { "JST", "Asia/Tokyo" },
            { "MIT", "Pacific/Apia" },
            { "NET", "Asia/Yerevan" },
            { "NST", "Pacific/Auckland" },
            { "PLT", "Asia/Karachi" },
            { "PNT", "America/Phoenix" },
            { "PRT", "America/Puerto_Rico" },
            { "PST", "America/Los_Angeles" },
            { "SST", "Pacific/Guadalcanal" },
            { "VST", "Asia/Ho_Chi_Minh" },
        };
        for (String[] alias : oldMappings) {
            String k = alias[0];
            String v = alias[1];
            if (zones.containsKey(v)) {  // make sure we do have the data
                aliases.put(k, v);
                zones.put(k, zones.get(v));
            }
        }
        if (USE_OLDMAPPING) {
            if (zones.containsKey("America/New_York")) {
                aliases.put("EST", "America/New_York");
                zones.put("EST", zones.get("America/New_York"));
            }
            if (zones.containsKey("America/Denver")) {
                aliases.put("MST", "America/Denver");
                zones.put("MST", zones.get("America/Denver"));
            }
            if (zones.containsKey("Pacific/Honolulu")) {
                aliases.put("HST", "Pacific/Honolulu");
                zones.put("HST", zones.get("Pacific/Honolulu"));
            }
        }
    }

    /**
     * Loads the rules from a DateInputStream
     *
     * @param dis  the DateInputStream to load, not null
     * @throws Exception if an error occurs
     */
    private static void load(DataInputStream dis) throws ClassNotFoundException, IOException {
        if (dis.readByte() != 1) {
            throw new StreamCorruptedException("File format not recognised");
        }
        // group
        String groupId = dis.readUTF();
        if ("TZDB".equals(groupId) == false) {
            throw new StreamCorruptedException("File format not recognised");
        }
        // versions, only keep the last one
        int versionCount = dis.readShort();
        for (int i = 0; i < versionCount; i++) {
            versionId = dis.readUTF();

        }
        // regions
        int regionCount = dis.readShort();
        String[] regionArray = new String[regionCount];
        for (int i = 0; i < regionCount; i++) {
            regionArray[i] = dis.readUTF();
        }
        // rules
        int ruleCount = dis.readShort();
        Object[] ruleArray = new Object[ruleCount];
        for (int i = 0; i < ruleCount; i++) {
            byte[] bytes = new byte[dis.readShort()];
            dis.readFully(bytes);
            ruleArray[i] = bytes;
        }
        // link version-region-rules, only keep the last version, if more than one
        for (int i = 0; i < versionCount; i++) {
            regionCount = dis.readShort();
            zones.clear();
            for (int j = 0; j < regionCount; j++) {
                String region = regionArray[dis.readShort()];
                Object rule = ruleArray[dis.readShort() & 0xffff];
                zones.put(region, rule);
            }
        }
        // remove the following ids from the map, they
        // are exclued from the "old" ZoneInfo
        zones.remove("ROC");
        for (int i = 0; i < versionCount; i++) {
            int aliasCount = dis.readShort();
            aliases.clear();
            for (int j = 0; j < aliasCount; j++) {
                String alias = regionArray[dis.readShort()];
                String region = regionArray[dis.readShort()];
                aliases.put(alias, region);
            }
        }
        // old us time-zone names
        addOldMapping();
        aliases = Collections.unmodifiableMap(aliases);
    }

    /////////////////////////Ser/////////////////////////////////
    public static ZoneInfo getZoneInfo(DataInput in, String zoneId) throws Exception {
        byte type = in.readByte();
        // TBD: assert ZRULES:
        int stdSize = in.readInt();
        long[] stdTrans = new long[stdSize];
        for (int i = 0; i < stdSize; i++) {
            stdTrans[i] = readEpochSec(in);
        }
        int [] stdOffsets = new int[stdSize + 1];
        for (int i = 0; i < stdOffsets.length; i++) {
            stdOffsets[i] = readOffset(in);
        }
        int savSize = in.readInt();
        long[] savTrans = new long[savSize];
        for (int i = 0; i < savSize; i++) {
            savTrans[i] = readEpochSec(in);
        }
        int[] savOffsets = new int[savSize + 1];
        for (int i = 0; i < savOffsets.length; i++) {
            savOffsets[i] = readOffset(in);
        }
        int ruleSize = in.readByte();
        ZoneOffsetTransitionRule[] rules = new ZoneOffsetTransitionRule[ruleSize];
        for (int i = 0; i < ruleSize; i++) {
            rules[i] = readZOTRule(in);
        }
        return getZoneInfo(zoneId, stdTrans, stdOffsets, savTrans, savOffsets, rules);
    }

    public static int readOffset(DataInput in) throws IOException {
        int offsetByte = in.readByte();
        return offsetByte == 127 ? in.readInt() : offsetByte * 900;
    }

    static long readEpochSec(DataInput in) throws IOException {
        int hiByte = in.readByte() & 255;
        if (hiByte == 255) {
            return in.readLong();
        } else {
            int midByte = in.readByte() & 255;
            int loByte = in.readByte() & 255;
            long tot = ((hiByte << 16) + (midByte << 8) + loByte);
            return (tot * 900) - 4575744000L;
        }
    }

    static ZoneOffsetTransitionRule readZOTRule(DataInput in) throws IOException {
        int data = in.readInt();
        Month month = Month.of(data >>> 28);
        int dom = ((data & (63 << 22)) >>> 22) - 32;
        int dowByte = (data & (7 << 19)) >>> 19;
        DayOfWeek dow = dowByte == 0 ? null : DayOfWeek.of(dowByte);
        int timeByte = (data & (31 << 14)) >>> 14;
        TimeDefinition defn = TimeDefinition.values()[(data & (3 << 12)) >>> 12];
        int stdByte = (data & (255 << 4)) >>> 4;
        int beforeByte = (data & (3 << 2)) >>> 2;
        int afterByte = (data & 3);
        LocalTime time = (timeByte == 31 ? LocalTime.ofSecondOfDay(in.readInt()) : LocalTime.of(timeByte % 24, 0));
        ZoneOffset std = (stdByte == 255 ? ZoneOffset.ofTotalSeconds(in.readInt()) : ZoneOffset.ofTotalSeconds((stdByte - 128) * 900));
        ZoneOffset before = (beforeByte == 3 ? ZoneOffset.ofTotalSeconds(in.readInt()) : ZoneOffset.ofTotalSeconds(std.getTotalSeconds() + beforeByte * 1800));
        ZoneOffset after = (afterByte == 3 ? ZoneOffset.ofTotalSeconds(in.readInt()) : ZoneOffset.ofTotalSeconds(std.getTotalSeconds() + afterByte * 1800));
        return ZoneOffsetTransitionRule.of(month, dom, dow, time, timeByte == 24, defn, std, before, after);
    }

    /////////////////////////ZoneRules --> ZoneInfo/////////////////////////////////

    // ZoneInfo starts with UTC1900
    private static final long UTC1900 = -2208988800L;
    // ZoneInfo ends with   UTC2037
    private static final long UTC2037 =
        LocalDateTime.of(2038, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC) - 1;

    /* Get a ZoneInfo instance.
     *
     * @param standardTransitions  the standard transitions, not null
     * @param standardOffsets  the standard offsets, not null
     * @param savingsInstantTransitions  the standard transitions, not null
     * @param wallOffsets  the wall offsets, not null
     * @param lastRules  the recurring last rules, size 15 or less, not null
     */
    private static ZoneInfo getZoneInfo(String zoneId,
                                        long[] standardTransitions,
                                        int[] standardOffsets,
                                        long[] savingsInstantTransitions,
                                        int[] wallOffsets,
                                        ZoneOffsetTransitionRule[] lastRules) {
        int rawOffset = 0;
        int dstSavings = 0;
        int checksum = 0;
        int[] params = null;
        boolean willGMTOffsetChange = false;

        // rawOffset, pick the last one
        if (standardTransitions.length > 0)
            rawOffset = standardOffsets[standardOffsets.length - 1] * 1000;
        else
            rawOffset = standardOffsets[0] * 1000;

        // transitions, offsets;
        long[] transitions = null;
        int[]  offsets = null;
        int    nOffsets = 0;
        int    nTrans = 0;

        if (savingsInstantTransitions.length != 0) {
            transitions = new long[250];
            offsets = new int[100];    // TBD: ZoneInfo actually can't handle
                                       // offsets.length > 16 (4-bit index limit)
            // last year in trans table
            // It should not matter to use before or after offset for year
            int lastyear = LocalDateTime.ofEpochSecond(
                savingsInstantTransitions[savingsInstantTransitions.length - 1], 0,
                ZoneOffset.ofTotalSeconds(wallOffsets[savingsInstantTransitions.length - 1])).getYear();
            // int lastyear = savingsLocalTransitions[savingsLocalTransitions.length - 1].getYear();

            int i = 0, k = 1;
            while (i < savingsInstantTransitions.length &&
                   savingsInstantTransitions[i] < UTC1900) {
                 i++;     // skip any date before UTC1900
            }
            if (i < savingsInstantTransitions.length) {
                // javazic writes the last GMT offset into index 0!
                if (i < savingsInstantTransitions.length) {
                    offsets[0] = standardOffsets[standardOffsets.length - 1] * 1000;
                    nOffsets = 1;
                }
                // ZoneInfo has a beginning entry for 1900.
                // Only add it if this is not the only one in table
                nOffsets = addTrans(transitions, nTrans++, offsets, nOffsets,
                                    UTC1900,
                                    wallOffsets[i],
                                    getStandardOffset(standardTransitions, standardOffsets, UTC1900));
            }
            for (; i < savingsInstantTransitions.length; i++) {
                //if (savingsLocalTransitions[i * 2].getYear() > LASTYEAR) {
                if (savingsInstantTransitions[i] > UTC2037) {
                    // no trans beyond LASTYEAR
                    lastyear = LASTYEAR;
                    break;
                }
                long trans = savingsInstantTransitions[i];
                while (k < standardTransitions.length) {
                    // some standard offset transitions don't exist in
                    // savingInstantTrans, if the offset "change" doesn't
                    // really change the "effectiveWallOffset". For example
                    // the 1999/2000 pair in Zone Arg/Buenos_Aires, in which
                    // the daylightsaving "happened" but it actually does
                    //  not result in the timezone switch. ZoneInfo however
                    // needs them in its transitions table
                    long trans_s = standardTransitions[k];
                    if (trans_s >= UTC1900) {
                        if (trans_s > trans)
                            break;
                        if (trans_s < trans) {
                            if (nOffsets + 2 >= offsets.length) {
                                offsets = Arrays.copyOf(offsets, offsets.length + 100);
                            }
                            if (nTrans + 1 >= transitions.length) {
                                transitions = Arrays.copyOf(transitions, transitions.length + 100);
                            }
                            nOffsets = addTrans(transitions, nTrans++, offsets, nOffsets,
                                                trans_s,
                                                wallOffsets[i],
                                                standardOffsets[k+1]);
                        }
                    }
                    k++;
                }
                if (nOffsets + 2 >= offsets.length) {
                    offsets = Arrays.copyOf(offsets, offsets.length + 100);
                }
                if (nTrans + 1 >= transitions.length) {
                    transitions = Arrays.copyOf(transitions, transitions.length + 100);
                }
                nOffsets = addTrans(transitions, nTrans++, offsets, nOffsets,
                                    trans,
                                    wallOffsets[i + 1],
                                    getStandardOffset(standardTransitions, standardOffsets, trans));
            }
            // append any leftover standard trans
            while (k < standardTransitions.length) {
                long trans = standardTransitions[k];
                if (trans >= UTC1900) {
                    int offset = wallOffsets[i];
                    int offsetIndex = indexOf(offsets, 0, nOffsets, offset);
                    if (offsetIndex == nOffsets)
                        nOffsets++;
                    transitions[nTrans++] = ((trans * 1000) << TRANSITION_NSHIFT) |
                                            (offsetIndex & OFFSET_MASK);
                }
                k++;
            }
            if (lastRules.length > 1) {
                // fill the gap between the last trans until LASTYEAR
                while (lastyear++ < LASTYEAR) {
                    for (ZoneOffsetTransitionRule zotr : lastRules) {
                        ZoneOffsetTransition zot = zotr.createTransition(lastyear);
                        //long trans = zot.getDateTimeBefore().toEpochSecond();
                        long trans = zot.toEpochSecond();
                        if (nOffsets + 2 >= offsets.length) {
                            offsets = Arrays.copyOf(offsets, offsets.length + 100);
                        }
                        if (nTrans + 1 >= transitions.length) {
                            transitions = Arrays.copyOf(transitions, transitions.length + 100);
                        }
                        nOffsets = addTrans(transitions, nTrans++, offsets, nOffsets,
                                            trans,
                                            zot.getOffsetAfter().getTotalSeconds(),
                                            getStandardOffset(standardTransitions, standardOffsets, trans));
                    }
                }
                ZoneOffsetTransitionRule startRule =  lastRules[lastRules.length - 2];
                ZoneOffsetTransitionRule endRule =  lastRules[lastRules.length - 1];
                params = new int[10];
                if (startRule.getOffsetBefore().compareTo(startRule.getOffsetAfter()) < 0 &&
                    endRule.getOffsetBefore().compareTo(endRule.getOffsetAfter()) > 0) {
                    ZoneOffsetTransitionRule tmp;
                    tmp = startRule;
                    startRule = endRule;
                    endRule = tmp;
                }
                params[0] = startRule.getMonth().getValue() - 1;
                // params[1] = startRule.getDayOfMonthIndicator();
                // params[2] = toCalendarDOW[startRule.getDayOfWeek().getValue()];
                int       dom = startRule.getDayOfMonthIndicator();
                DayOfWeek dow = startRule.getDayOfWeek();
                if (dow == null) {
                    params[1] = startRule.getDayOfMonthIndicator();
                    params[2] = 0;
                } else {
                    // ZoneRulesBuilder adjusts < 0 case (-1, for last, don't have
                    // "<=" case yet) to positive value if not February (it appears
                    // we don't have February cutoff in tzdata table yet)
                    // Ideally, if JSR310 can just pass in the nagative and
                    // we can then pass in the dom = -1, dow > 0 into ZoneInfo
                    //
                    // hacking, assume the >=24 is the result of ZRB optimization for
                    // "last", it works for now.
                    if (dom < 0 || dom >= 24) {
                        params[1] = -1;
                        params[2] = toCalendarDOW[dow.getValue()];
                    } else {
                        params[1] = dom;
                        // To specify a day of week on or after an exact day of month,
                        // set the month to an exact month value, day-of-month to the
                        // day on or after which the rule is applied, and day-of-week
                        // to a negative Calendar.DAY_OF_WEEK DAY_OF_WEEK field value.
                        params[2] = -toCalendarDOW[dow.getValue()];
                    }
                }
                params[3] = startRule.getLocalTime().toSecondOfDay() * 1000;
                params[4] = toSTZTime[startRule.getTimeDefinition().ordinal()];

                params[5] = endRule.getMonth().getValue() - 1;
                // params[6] = endRule.getDayOfMonthIndicator();
                // params[7] = toCalendarDOW[endRule.getDayOfWeek().getValue()];
                dom = endRule.getDayOfMonthIndicator();
                dow = endRule.getDayOfWeek();
                if (dow == null) {
                    params[6] = dom;
                    params[7] = 0;
                } else {
                    // hacking: see comment above
                    if (dom < 0 || dom >= 24) {
                        params[6] = -1;
                        params[7] = toCalendarDOW[dow.getValue()];
                    } else {
                        params[6] = dom;
                        params[7] = -toCalendarDOW[dow.getValue()];
                    }
                }
                params[8] = endRule.getLocalTime().toSecondOfDay() * 1000;
                params[9] = toSTZTime[endRule.getTimeDefinition().ordinal()];
                dstSavings = (startRule.getOffsetAfter().getTotalSeconds()
                             - startRule.getOffsetBefore().getTotalSeconds()) * 1000;
                // Note: known mismatching -> Asia/Amman
                // ZoneInfo :      startDayOfWeek=5     <= Thursday
                //                 startTime=86400000   <= 24 hours
                // This:           startDayOfWeek=6
                //                 startTime=0
                // Below is the workaround, it probably slows down everyone a little
                if (params[2] == 6 && params[3] == 0 && zoneId.equals("Asia/Amman")) {
                    params[2] = 5;
                    params[3] = 86400000;
                }
            } else if (nTrans > 0) {  // only do this if there is something in table already
                if (lastyear < LASTYEAR) {
                    // ZoneInfo has an ending entry for 2037
                    long trans = OffsetDateTime.of(LASTYEAR, Month.JANUARY.getValue(), 1, 0, 0, 0, 0,
                                                   ZoneOffset.ofTotalSeconds(rawOffset/1000))
                                               .toEpochSecond();
                    int offsetIndex = indexOf(offsets, 0, nOffsets, rawOffset/1000);
                    if (offsetIndex == nOffsets)
                        nOffsets++;
                    transitions[nTrans++] = (trans * 1000) << TRANSITION_NSHIFT |
                                       (offsetIndex & OFFSET_MASK);
                } else if (savingsInstantTransitions.length > 2) {
                    // Workaround: create the params based on the last pair for
                    // zones like Israel and Iran which have trans defined
                    // up until 2037, but no "transition rule" defined
                    //
                    // Note: Known mismatching for Israel, Asia/Jerusalem/Tel Aviv
                    // ZoneInfo:        startMode=3
                    //                  startMonth=2
                    //                  startDay=26
                    //                  startDayOfWeek=6
                    //
                    // This:            startMode=1
                    //                  startMonth=2
                    //                  startDay=27
                    //                  startDayOfWeek=0
                    // these two are actually the same for 2037, the SimpleTimeZone
                    // for the last "known" year
                    int m = savingsInstantTransitions.length;
                    long startTrans = savingsInstantTransitions[m - 2];
                    int startOffset = wallOffsets[m - 2 + 1];
                    int startStd = getStandardOffset(standardTransitions, standardOffsets, startTrans);
                    long endTrans =  savingsInstantTransitions[m - 1];
                    int endOffset = wallOffsets[m - 1 + 1];
                    int endStd = getStandardOffset(standardTransitions, standardOffsets, endTrans);

                    if (startOffset > startStd && endOffset == endStd) {
                        /*
                        m = savingsLocalTransitions.length;
                        LocalDateTime startLDT = savingsLocalTransitions[m -4];  //gap
                        LocalDateTime endLDT = savingsLocalTransitions[m - 1];   //over
                         */
                        // last - 1 trans
                        m = savingsInstantTransitions.length - 2;
                        ZoneOffset before = ZoneOffset.ofTotalSeconds(wallOffsets[m]);
                        ZoneOffset after = ZoneOffset.ofTotalSeconds(wallOffsets[m + 1]);
                        ZoneOffsetTransition trans = ZoneOffsetTransition.of(
                            LocalDateTime.ofEpochSecond(savingsInstantTransitions[m], 0, before),
                            before,
                            after);
                        LocalDateTime startLDT;
                        if (trans.isGap()) {
                            startLDT = trans.getDateTimeBefore();
                        } else {
                            startLDT = trans.getDateTimeAfter();
                        }
                        // last trans
                        m = savingsInstantTransitions.length - 1;
                        before = ZoneOffset.ofTotalSeconds(wallOffsets[m]);
                        after = ZoneOffset.ofTotalSeconds(wallOffsets[m + 1]);
                        trans = ZoneOffsetTransition.of(
                            LocalDateTime.ofEpochSecond(savingsInstantTransitions[m], 0, before),
                            before,
                            after);
                        LocalDateTime endLDT;
                        if (trans.isGap()) {
                            endLDT = trans.getDateTimeAfter();
                        } else {
                            endLDT = trans.getDateTimeBefore();
                        }
                        params = new int[10];
                        params[0] = startLDT.getMonthValue() - 1;
                        params[1] = startLDT.getDayOfMonth();
                        params[2] = 0;
                        params[3] = startLDT.toLocalTime().toSecondOfDay() * 1000;
                        params[4] = SimpleTimeZone.WALL_TIME;
                        params[5] = endLDT.getMonthValue() - 1;
                        params[6] = endLDT.getDayOfMonth();
                        params[7] = 0;
                        params[8] = endLDT.toLocalTime().toSecondOfDay() * 1000;
                        params[9] = SimpleTimeZone.WALL_TIME;
                        dstSavings = (startOffset - startStd) * 1000;
                    }
                }
            }
            if (transitions != null && transitions.length != nTrans) {
                if (nTrans == 0) {
                   transitions = null;
                } else {
                    transitions = Arrays.copyOf(transitions, nTrans);
                }
            }
            if (offsets != null && offsets.length != nOffsets) {
                if (nOffsets == 0) {
                   offsets = null;
                } else {
                    offsets = Arrays.copyOf(offsets, nOffsets);
                }
            }
            if (transitions != null) {
                Checksum sum = new Checksum();
                for (i = 0; i < transitions.length; i++) {
                    long val = transitions[i];
                    int dst = (int)((val >>> DST_NSHIFT) & 0xfL);
                    int saving = (dst == 0) ? 0 : offsets[dst];
                    int index = (int)(val & OFFSET_MASK);
                    int offset = offsets[index];
                    long second = (val >> TRANSITION_NSHIFT);
                    // javazic uses "index of the offset in offsets",
                    // instead of the real offset value itself to
                    // calculate the checksum. Have to keep doing
                    // the same thing, checksum is part of the
                    // ZoneInfo serialization form.
                    sum.update(second + index);
                    sum.update(index);
                    sum.update(dst == 0 ? -1 : dst);
                }
                checksum = (int)sum.getValue();
            }
        }
        return new ZoneInfo(zoneId, rawOffset, dstSavings, checksum, transitions,
                            offsets, params, willGMTOffsetChange);
    }

    private static int getStandardOffset(long[] standardTransitions,
                                         int[] standardOffsets,
                                         long epochSec) {
        int index  = Arrays.binarySearch(standardTransitions, epochSec);
        if (index < 0) {
            // switch negative insert position to start of matched range
            index = -index - 2;
        }
        return standardOffsets[index + 1];
    }

    private static int toCalendarDOW[] = new int[] {
        -1,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    };

    private static int toSTZTime[] = new int[] {
        SimpleTimeZone.UTC_TIME,
        SimpleTimeZone.WALL_TIME,
        SimpleTimeZone.STANDARD_TIME,
    };

    private static final long OFFSET_MASK = 0x0fL;
    private static final long DST_MASK = 0xf0L;
    private static final int  DST_NSHIFT = 4;
    private static final int  TRANSITION_NSHIFT = 12;
    private static final int  LASTYEAR = 2037;

    // from: 0 for offset lookup, 1 for dstsvings lookup
    private static int indexOf(int[] offsets, int from, int nOffsets, int offset) {
        offset *= 1000;
        for (; from < nOffsets; from++) {
            if (offsets[from] == offset)
                return from;
        }
        offsets[from] = offset;
        return from;
    }

    // return updated nOffsets
    private static int addTrans(long transitions[], int nTrans,
                                int offsets[], int nOffsets,
                                long trans, int offset, int stdOffset) {
        int offsetIndex = indexOf(offsets, 0, nOffsets, offset);
        if (offsetIndex == nOffsets)
            nOffsets++;
        int dstIndex = 0;
        if (offset != stdOffset) {
            dstIndex = indexOf(offsets, 1, nOffsets, offset - stdOffset);
            if (dstIndex == nOffsets)
                nOffsets++;
        }
        transitions[nTrans] = ((trans * 1000) << TRANSITION_NSHIFT) |
                              ((dstIndex << DST_NSHIFT) & DST_MASK) |
                              (offsetIndex & OFFSET_MASK);
        return nOffsets;
    }

    /////////////////////////////////////////////////////////////
    // ZoneInfo checksum, copy/pasted from javazic
    private static class Checksum extends CRC32 {
        public void update(int val) {
            byte[] b = new byte[4];
            b[0] = (byte)((val >>> 24) & 0xff);
            b[1] = (byte)((val >>> 16) & 0xff);
            b[2] = (byte)((val >>> 8) & 0xff);
            b[3] = (byte)(val & 0xff);
            update(b);
        }
        void update(long val) {
            byte[] b = new byte[8];
            b[0] = (byte)((val >>> 56) & 0xff);
            b[1] = (byte)((val >>> 48) & 0xff);
            b[2] = (byte)((val >>> 40) & 0xff);
            b[3] = (byte)((val >>> 32) & 0xff);
            b[4] = (byte)((val >>> 24) & 0xff);
            b[5] = (byte)((val >>> 16) & 0xff);
            b[6] = (byte)((val >>> 8) & 0xff);
            b[7] = (byte)(val & 0xff);
            update(b);
        }
    }
}
