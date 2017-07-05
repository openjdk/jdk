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
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
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
package java.time;

import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.Queries;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesException;
import java.time.zone.ZoneRulesProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/**
 * A time-zone ID, such as {@code Europe/Paris}.
 * <p>
 * A {@code ZoneId} is used to identify the rules used to convert between
 * an {@link Instant} and a {@link LocalDateTime}.
 * There are two distinct types of ID:
 * <p><ul>
 * <li>Fixed offsets - a fully resolved offset from UTC/Greenwich, that uses
 *  the same offset for all local date-times
 * <li>Geographical regions - an area where a specific set of rules for finding
 *  the offset from UTC/Greenwich apply
 * </ul><p>
 * Most fixed offsets are represented by {@link ZoneOffset}.
 * <p>
 * The actual rules, describing when and how the offset changes, are defined by {@link ZoneRules}.
 * This class is simply an ID used to obtain the underlying rules.
 * This approach is taken because rules are defined by governments and change
 * frequently, whereas the ID is stable.
 * <p>
 * The distinction has other effects. Serializing the {@code ZoneId} will only send
 * the ID, whereas serializing the rules sends the entire data set.
 * Similarly, a comparison of two IDs only examines the ID, whereas
 * a comparison of two rules examines the entire data set.
 * <p>
 * The code supports loading a {@code ZoneId} on a JVM which does not have available rules
 * for that ID. This allows the date-time object, such as {@link ZonedDateTime},
 * to still be queried.
 *
 * <h3>Time-zone IDs</h3>
 * The ID is unique within the system.
 * The formats for offset and region IDs differ.
 * <p>
 * An ID is parsed as an offset ID if it starts with 'UTC', 'GMT', 'UT' '+' or '-', or
 * is a single letter. For example, 'Z', '+02:00', '-05:00', 'UTC+05', 'GMT-6' and
 * 'UT+01:00' are all valid offset IDs.
 * Note that some IDs, such as 'D' or '+ABC' meet the criteria to be parsed as offset IDs,
 * but have an invalid offset.
 * <p>
 * All other IDs are considered to be region IDs.
 * <p>
 * Region IDs are defined by configuration, which can be thought of as a {@code Map}
 * from region ID to {@code ZoneRules}, see {@link ZoneRulesProvider}.
 * <p>
 * Time-zones are defined by governments and change frequently. There are a number of
 * organizations, known here as groups, that monitor time-zone changes and collate them.
 * The default group is the IANA Time Zone Database (TZDB).
 * Other organizations include IATA (the airline industry body) and Microsoft.
 * <p>
 * Each group defines its own format for the region ID it provides.
 * The TZDB group defines IDs such as 'Europe/London' or 'America/New_York'.
 * TZDB IDs take precedence over other groups.
 * <p>
 * It is strongly recommended that the group name is included in all IDs supplied by
 * groups other than TZDB to avoid conflicts. For example, IATA airline time-zone
 * region IDs are typically the same as the three letter airport code.
 * However, the airport of Utrecht has the code 'UTC', which is obviously a conflict.
 * The recommended format for region IDs from groups other than TZDB is 'group~region'.
 * Thus if IATA data were defined, Utrecht airport would be 'IATA~UTC'.
 *
 * <h3>Specification for implementors</h3>
 * This abstract class has two implementations, both of which are immutable and thread-safe.
 * One implementation models region-based IDs, the other is {@code ZoneOffset} modelling
 * offset-based IDs. This difference is visible in serialization.
 *
 * @since 1.8
 */
public abstract class ZoneId implements Serializable {

    /**
     * A map of zone overrides to enable the older US time-zone names to be used.
     * <p>
     * This maps as follows:
     * <p><ul>
     * <li>EST - America/New_York</li>
     * <li>MST - America/Denver</li>
     * <li>HST - Pacific/Honolulu</li>
     * <li>ACT - Australia/Darwin</li>
     * <li>AET - Australia/Sydney</li>
     * <li>AGT - America/Argentina/Buenos_Aires</li>
     * <li>ART - Africa/Cairo</li>
     * <li>AST - America/Anchorage</li>
     * <li>BET - America/Sao_Paulo</li>
     * <li>BST - Asia/Dhaka</li>
     * <li>CAT - Africa/Harare</li>
     * <li>CNT - America/St_Johns</li>
     * <li>CST - America/Chicago</li>
     * <li>CTT - Asia/Shanghai</li>
     * <li>EAT - Africa/Addis_Ababa</li>
     * <li>ECT - Europe/Paris</li>
     * <li>IET - America/Indiana/Indianapolis</li>
     * <li>IST - Asia/Kolkata</li>
     * <li>JST - Asia/Tokyo</li>
     * <li>MIT - Pacific/Apia</li>
     * <li>NET - Asia/Yerevan</li>
     * <li>NST - Pacific/Auckland</li>
     * <li>PLT - Asia/Karachi</li>
     * <li>PNT - America/Phoenix</li>
     * <li>PRT - America/Puerto_Rico</li>
     * <li>PST - America/Los_Angeles</li>
     * <li>SST - Pacific/Guadalcanal</li>
     * <li>VST - Asia/Ho_Chi_Minh</li>
     * </ul><p>
     * The map is unmodifiable.
     */
    public static final Map<String, String> OLD_IDS_PRE_2005;
    /**
     * A map of zone overrides to enable the older US time-zone names to be used.
     * <p>
     * This maps as follows:
     * <p><ul>
     * <li>EST - -05:00</li>
     * <li>HST - -10:00</li>
     * <li>MST - -07:00</li>
     * <li>ACT - Australia/Darwin</li>
     * <li>AET - Australia/Sydney</li>
     * <li>AGT - America/Argentina/Buenos_Aires</li>
     * <li>ART - Africa/Cairo</li>
     * <li>AST - America/Anchorage</li>
     * <li>BET - America/Sao_Paulo</li>
     * <li>BST - Asia/Dhaka</li>
     * <li>CAT - Africa/Harare</li>
     * <li>CNT - America/St_Johns</li>
     * <li>CST - America/Chicago</li>
     * <li>CTT - Asia/Shanghai</li>
     * <li>EAT - Africa/Addis_Ababa</li>
     * <li>ECT - Europe/Paris</li>
     * <li>IET - America/Indiana/Indianapolis</li>
     * <li>IST - Asia/Kolkata</li>
     * <li>JST - Asia/Tokyo</li>
     * <li>MIT - Pacific/Apia</li>
     * <li>NET - Asia/Yerevan</li>
     * <li>NST - Pacific/Auckland</li>
     * <li>PLT - Asia/Karachi</li>
     * <li>PNT - America/Phoenix</li>
     * <li>PRT - America/Puerto_Rico</li>
     * <li>PST - America/Los_Angeles</li>
     * <li>SST - Pacific/Guadalcanal</li>
     * <li>VST - Asia/Ho_Chi_Minh</li>
     * </ul><p>
     * The map is unmodifiable.
     */
    public static final Map<String, String> OLD_IDS_POST_2005;
    static {
        Map<String, String> base = new HashMap<>();
        base.put("ACT", "Australia/Darwin");
        base.put("AET", "Australia/Sydney");
        base.put("AGT", "America/Argentina/Buenos_Aires");
        base.put("ART", "Africa/Cairo");
        base.put("AST", "America/Anchorage");
        base.put("BET", "America/Sao_Paulo");
        base.put("BST", "Asia/Dhaka");
        base.put("CAT", "Africa/Harare");
        base.put("CNT", "America/St_Johns");
        base.put("CST", "America/Chicago");
        base.put("CTT", "Asia/Shanghai");
        base.put("EAT", "Africa/Addis_Ababa");
        base.put("ECT", "Europe/Paris");
        base.put("IET", "America/Indiana/Indianapolis");
        base.put("IST", "Asia/Kolkata");
        base.put("JST", "Asia/Tokyo");
        base.put("MIT", "Pacific/Apia");
        base.put("NET", "Asia/Yerevan");
        base.put("NST", "Pacific/Auckland");
        base.put("PLT", "Asia/Karachi");
        base.put("PNT", "America/Phoenix");
        base.put("PRT", "America/Puerto_Rico");
        base.put("PST", "America/Los_Angeles");
        base.put("SST", "Pacific/Guadalcanal");
        base.put("VST", "Asia/Ho_Chi_Minh");
        Map<String, String> pre = new HashMap<>(base);
        pre.put("EST", "America/New_York");
        pre.put("MST", "America/Denver");
        pre.put("HST", "Pacific/Honolulu");
        OLD_IDS_PRE_2005 = Collections.unmodifiableMap(pre);
        Map<String, String> post = new HashMap<>(base);
        post.put("EST", "-05:00");
        post.put("MST", "-07:00");
        post.put("HST", "-10:00");
        OLD_IDS_POST_2005 = Collections.unmodifiableMap(post);
    }
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 8352817235686L;

    //-----------------------------------------------------------------------
    /**
     * Gets the system default time-zone.
     * <p>
     * This queries {@link TimeZone#getDefault()} to find the default time-zone
     * and converts it to a {@code ZoneId}. If the system default time-zone is changed,
     * then the result of this method will also change.
     *
     * @return the zone ID, not null
     * @throws DateTimeException if the converted zone ID has an invalid format
     * @throws ZoneRulesException if the converted zone region ID cannot be found
     */
    public static ZoneId systemDefault() {
        return ZoneId.of(TimeZone.getDefault().getID(), OLD_IDS_POST_2005);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneId} using its ID using a map
     * of aliases to supplement the standard zone IDs.
     * <p>
     * Many users of time-zones use short abbreviations, such as PST for
     * 'Pacific Standard Time' and PDT for 'Pacific Daylight Time'.
     * These abbreviations are not unique, and so cannot be used as IDs.
     * This method allows a map of string to time-zone to be setup and reused
     * within an application.
     *
     * @param zoneId  the time-zone ID, not null
     * @param aliasMap  a map of alias zone IDs (typically abbreviations) to real zone IDs, not null
     * @return the zone ID, not null
     * @throws DateTimeException if the zone ID has an invalid format
     * @throws ZoneRulesException if the zone ID is a region ID that cannot be found
     */
    public static ZoneId of(String zoneId, Map<String, String> aliasMap) {
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(aliasMap, "aliasMap");
        String id = aliasMap.get(zoneId);
        id = (id != null ? id : zoneId);
        return of(id);
    }

    /**
     * Obtains an instance of {@code ZoneId} from an ID ensuring that the
     * ID is valid and available for use.
     * <p>
     * This method parses the ID, applies any appropriate normalization, and validates it
     * against the known set of IDs for which rules are available.
     * <p>
     * An ID is parsed as though it is an offset ID if it starts with 'UTC', 'GMT', 'UT', '+'
     * or '-', or if it has less then two letters.
     * The offset of {@linkplain ZoneOffset#UTC zero} may be represented in multiple ways,
     * including 'Z', 'UTC', 'GMT', 'UT', 'UTC0', 'GMT0', 'UT0', '+00:00', '-00:00' and 'UTC+00:00'.
     * <p>
     * Six forms of ID are recognized:
     * <p><ul>
     * <li><code>Z</code> - an offset of zero, which is {@code ZoneOffset.UTC}
     * <li><code>{offset}</code> - a {@code ZoneOffset} ID, such as '+02:00'
     * <li><code>{utcPrefix}</code> - a {@code ZoneOffset} ID equal to 'Z'
     * <li><code>{utcPrefix}0</code> - a {@code ZoneOffset} ID equal to 'Z'
     * <li><code>{utcPrefix}{offset}</code> - a {@code ZoneOffset} ID equal to '{offset}'
     * <li><code>{regionID}</code> - full region ID, loaded from configuration
     * </ul><p>
     * The {offset} is a valid format for {@link ZoneOffset#of(String)}, excluding 'Z'.
     * The {utcPrefix} is 'UTC', 'GMT' or 'UT'.
     * Region IDs must match the regular expression <code>[A-Za-z][A-Za-z0-9~/._+-]+</code>.
     * <p>
     * The detailed format of the region ID depends on the group supplying the data.
     * The default set of data is supplied by the IANA Time Zone Database (TZDB)
     * This has region IDs of the form '{area}/{city}', such as 'Europe/Paris' or 'America/New_York'.
     * This is compatible with most IDs from {@link java.util.TimeZone}.
     *
     * @param zoneId  the time-zone ID, not null
     * @return the zone ID, not null
     * @throws DateTimeException if the zone ID has an invalid format
     * @throws ZoneRulesException if the zone ID is a region ID that cannot be found
     */
    public static ZoneId of(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        if (zoneId.length() <= 1 || zoneId.startsWith("+") || zoneId.startsWith("-")) {
            return ZoneOffset.of(zoneId);
        } else if (zoneId.startsWith("UTC") || zoneId.startsWith("GMT")) {
            return ofWithPrefix(zoneId, 3);
        } else if (zoneId.startsWith("UT")) {
            return ofWithPrefix(zoneId, 2);
        }
        return ZoneRegion.ofId(zoneId, true);
    }

    /**
     * Parse once a prefix is established.
     *
     * @param zoneId  the time-zone ID, not null
     * @param prefixLength  the length of the prefix, 2 or 3
     * @return the zone ID, not null
     * @return the zone ID, not null
     * @throws DateTimeException if the zone ID has an invalid format
     */
    private static ZoneId ofWithPrefix(String zoneId, int prefixLength) {
        if (zoneId.length() == prefixLength ||
                (zoneId.length() == prefixLength + 1 && zoneId.charAt(prefixLength) == '0')) {
            return ZoneOffset.UTC;
        }
        if (zoneId.charAt(prefixLength) == '+' || zoneId.charAt(prefixLength) == '-') {
            try {
                return ZoneOffset.of(zoneId.substring(prefixLength));
            } catch (DateTimeException ex) {
                throw new DateTimeException("Invalid ID for offset-based ZoneId: " + zoneId, ex);
            }
        }
        throw new DateTimeException("Invalid ID for offset-based ZoneId: " + zoneId);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneId} from a temporal object.
     * <p>
     * This obtains a zone based on the specified temporal.
     * A {@code TemporalAccessor} represents an arbitrary set of date and time information,
     * which this factory converts to an instance of {@code ZoneId}.
     * <p>
     * A {@code TemporalAccessor} represents some form of date and time information.
     * This factory converts the arbitrary temporal object to an instance of {@code ZoneId}.
     * <p>
     * The conversion will try to obtain the zone in a way that favours region-based
     * zones over offset-based zones using {@link Queries#zone()}.
     * <p>
     * This method matches the signature of the functional interface {@link TemporalQuery}
     * allowing it to be used in queries via method reference, {@code ZoneId::from}.
     *
     * @param temporal  the temporal object to convert, not null
     * @return the zone ID, not null
     * @throws DateTimeException if unable to convert to a {@code ZoneId}
     */
    public static ZoneId from(TemporalAccessor temporal) {
        ZoneId obj = temporal.query(Queries.zone());
        if (obj == null) {
            throw new DateTimeException("Unable to obtain ZoneId from TemporalAccessor: " + temporal.getClass());
        }
        return obj;
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor only accessible within the package.
     */
    ZoneId() {
        if (getClass() != ZoneOffset.class && getClass() != ZoneRegion.class) {
            throw new AssertionError("Invalid subclass");
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the unique time-zone ID.
     * <p>
     * This ID uniquely defines this object.
     * The format of an offset based ID is defined by {@link ZoneOffset#getId()}.
     *
     * @return the time-zone unique ID, not null
     */
    public abstract String getId();

    //-----------------------------------------------------------------------
    /**
     * Gets the time-zone rules for this ID allowing calculations to be performed.
     * <p>
     * The rules provide the functionality associated with a time-zone,
     * such as finding the offset for a given instant or local date-time.
     * <p>
     * A time-zone can be invalid if it is deserialized in a JVM which does not
     * have the same rules loaded as the JVM that stored it. In this case, calling
     * this method will throw an exception.
     * <p>
     * The rules are supplied by {@link ZoneRulesProvider}. An advanced provider may
     * support dynamic updates to the rules without restarting the JVM.
     * If so, then the result of this method may change over time.
     * Each individual call will be still remain thread-safe.
     * <p>
     * {@link ZoneOffset} will always return a set of rules where the offset never changes.
     *
     * @return the rules, not null
     * @throws ZoneRulesException if no rules are available for this ID
     */
    public abstract ZoneRules getRules();

    //-----------------------------------------------------------------------
    /**
     * Gets the textual representation of the zone, such as 'British Time' or
     * '+02:00'.
     * <p>
     * This returns the textual name used to identify the time-zone ID,
     * suitable for presentation to the user.
     * The parameters control the style of the returned text and the locale.
     * <p>
     * If no textual mapping is found then the {@link #getId() full ID} is returned.
     *
     * @param style  the length of the text required, not null
     * @param locale  the locale to use, not null
     * @return the text value of the zone, not null
     */
    public String getDisplayName(TextStyle style, Locale locale) {
        return new DateTimeFormatterBuilder().appendZoneText(style).toFormatter(locale).format(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                return false;
            }
            @Override
            public long getLong(TemporalField field) {
                throw new DateTimeException("Unsupported field: " + field);
            }
            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.zoneId()) {
                    return (R) ZoneId.this;
                }
                return TemporalAccessor.super.query(query);
            }
        });
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this time-zone ID is equal to another time-zone ID.
     * <p>
     * The comparison is based on the ID.
     *
     * @param obj  the object to check, null returns false
     * @return true if this is equal to the other time-zone ID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
           return true;
        }
        if (obj instanceof ZoneId) {
            ZoneId other = (ZoneId) obj;
            return getId().equals(other.getId());
        }
        return false;
    }

    /**
     * A hash code for this time-zone ID.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    //-----------------------------------------------------------------------
    /**
     * Outputs this zone as a {@code String}, using the ID.
     *
     * @return a string representation of this time-zone ID, not null
     */
    @Override
    public String toString() {
        return getId();
    }

    //-----------------------------------------------------------------------
    /**
     * Writes the object using a
     * <a href="../../serialized-form.html#java.time.Ser">dedicated serialized form</a>.
     * <pre>
     *  out.writeByte(7);  // identifies this as a ZoneId (not ZoneOffset)
     *  out.writeUTF(zoneId);
     * </pre>
     *
     * @return the instance of {@code Ser}, not null
     */
    // this is here for serialization Javadoc
    private Object writeReplace() {
        return new Ser(Ser.ZONE_REGION_TYPE, this);
    }

    abstract void write(DataOutput out) throws IOException;

}
