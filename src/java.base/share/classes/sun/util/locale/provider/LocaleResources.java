/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package sun.util.locale.provider;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.text.ListFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import sun.security.action.GetPropertyAction;
import sun.util.resources.LocaleData;
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;
import sun.util.resources.TimeZoneNamesBundle;

/**
 * Central accessor to locale-dependent resources for JRE/CLDR provider adapters.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class LocaleResources {

    private final Locale locale;
    private final LocaleData localeData;
    private final LocaleProviderAdapter.Type type;

    // Resource cache
    private final ConcurrentMap<String, ResourceReference> cache = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    // cache key prefixes
    private static final String BREAK_ITERATOR_INFO = "BII.";
    private static final String CALENDAR_DATA = "CALD.";
    private static final String COLLATION_DATA = "COLD.";
    private static final String DECIMAL_FORMAT_SYMBOLS_DATA_CACHEKEY = "DFSD";
    private static final String CURRENCY_NAMES = "CN.";
    private static final String LOCALE_NAMES = "LN.";
    private static final String TIME_ZONE_NAMES = "TZN.";
    private static final String ZONE_IDS_CACHEKEY = "ZID";
    private static final String CALENDAR_NAMES = "CALN.";
    private static final String NUMBER_PATTERNS_CACHEKEY = "NP";
    private static final String COMPACT_NUMBER_PATTERNS_CACHEKEY = "CNP";
    private static final String DATE_TIME_PATTERN = "DTP.";
    private static final String RULES_CACHEKEY = "RULE";
    private static final String SKELETON_PATTERN = "SP.";
    private static final String LIST_PATTERN = "LP.";

    // ResourceBundle key names for skeletons
    private static final String SKELETON_INPUT_REGIONS_KEY = "DateFormatItemInputRegions";

    // TimeZoneNamesBundle exemplar city prefix
    private static final String TZNB_EXCITY_PREFIX = "timezone.excity.";

    // null singleton cache value
    private static final Object NULLOBJECT = new Object();

    // RegEx pattern for skeleton validity checking
    private static final Pattern VALID_SKELETON_PATTERN = Pattern.compile(
        "(?<date>" +
        "G{0,5}" +        // Era
        "y*" +            // Year
        "Q{0,5}" +        // Quarter
        "M{0,5}" +        // Month
        "w*" +            // Week of Week Based Year
        "E{0,5}" +        // Day of Week
        "d{0,2})" +       // Day of Month
        "(?<time>" +
        "B{0,5}" +        // Period/AmPm of Day
        "[hHjC]{0,2}" +   // Hour of Day/AmPm
        "m{0,2}" +        // Minute of Hour
        "s{0,2}" +        // Second of Minute
        "[vz]{0,4})");    // Zone

    // Input Skeleton map for "preferred" and "allowed"
    // Map<"preferred"/"allowed", Map<"region", "skeleton">>
    private static Map<String, Map<String, String>> inputSkeletons;

    // Skeletons for "j" and "C" input skeleton symbols for this locale
    private String jPattern;
    private String CPattern;

    LocaleResources(ResourceBundleBasedAdapter adapter, Locale locale) {
        this.locale = locale;
        this.localeData = adapter.getLocaleData();
        type = ((LocaleProviderAdapter)adapter).getAdapterType();
    }

    private void removeEmptyReferences() {
        Object ref;
        while ((ref = referenceQueue.poll()) != null) {
            cache.remove(((ResourceReference)ref).getCacheKey());
        }
    }

    Object getBreakIteratorInfo(String key) {
        Object biInfo;
        String cacheKey = BREAK_ITERATOR_INFO + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);
        if (data == null || ((biInfo = data.get()) == null)) {
           biInfo = localeData.getBreakIteratorInfo(locale).getObject(key);
           cache.put(cacheKey, new ResourceReference(cacheKey, biInfo, referenceQueue));
        }

       return biInfo;
    }

    byte[] getBreakIteratorResources(String key) {
        return (byte[]) localeData.getBreakIteratorResources(locale).getObject(key);
    }

    public String getCalendarData(String key) {
        String caldata = "";
        String cacheKey = CALENDAR_DATA  + key;

        removeEmptyReferences();

        ResourceReference data = cache.get(cacheKey);
        if (data == null || ((caldata = (String) data.get()) == null)) {
            ResourceBundle rb = localeData.getCalendarData(locale);
            if (rb.containsKey(key)) {
                caldata = rb.getString(key);
            }

            cache.put(cacheKey,
                      new ResourceReference(cacheKey, caldata, referenceQueue));
        }

        return caldata;
    }

    public String getCollationData() {
        String key = "Rule";
        String cacheKey = COLLATION_DATA;
        String coldata = "";

        try {
            var type = locale.getUnicodeLocaleType("co");
            if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("standard")) {
                key += "." + type;
                cacheKey += type;
            }
        } catch (IllegalArgumentException ignore) {}

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);
        if (data == null || ((coldata = (String) data.get()) == null)) {
            ResourceBundle rb = localeData.getCollationData(locale);
            if (rb.containsKey(key)) {
                coldata = rb.getString(key);
            }
            cache.put(cacheKey, new ResourceReference(cacheKey, coldata, referenceQueue));
        }

        return coldata;
    }

    public Object[] getDecimalFormatSymbolsData() {
        Object[] dfsdata;

        removeEmptyReferences();
        ResourceReference data = cache.get(DECIMAL_FORMAT_SYMBOLS_DATA_CACHEKEY);
        if (data == null || ((dfsdata = (Object[]) data.get()) == null)) {
            // Note that only dfsdata[0] is prepared here in this method. Other
            // elements are provided by the caller, yet they are cached here.
            ResourceBundle rb = localeData.getNumberFormatData(locale);
            dfsdata = new Object[3];
            dfsdata[0] = getNumberStrings(rb, "NumberElements");

            cache.put(DECIMAL_FORMAT_SYMBOLS_DATA_CACHEKEY,
                      new ResourceReference(DECIMAL_FORMAT_SYMBOLS_DATA_CACHEKEY, dfsdata, referenceQueue));
        }

        return dfsdata;
    }

    private String[] getNumberStrings(ResourceBundle rb, String type) {
        String[] ret = null;
        String key;
        String numSys;

        // Number strings look up. First, try the Unicode extension
        numSys = locale.getUnicodeLocaleType("nu");
        if (numSys != null) {
            key = numSys + "." + type;
            if (rb.containsKey(key)) {
                ret = rb.getStringArray(key);
            }
        }

        // Next, try DefaultNumberingSystem value
        if (ret == null && rb.containsKey("DefaultNumberingSystem")) {
            key = rb.getString("DefaultNumberingSystem") + "." + type;
            if (rb.containsKey(key)) {
                ret = rb.getStringArray(key);
            }
        }

        // Last resort. No need to check the availability.
        // Just let it throw MissingResourceException when needed.
        if (ret == null) {
            ret = rb.getStringArray(type);
        }

        return ret;
    }

    public String getCurrencyName(String key) {
        Object currencyName = null;
        String cacheKey = CURRENCY_NAMES + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data != null && ((currencyName = data.get()) != null)) {
            if (currencyName.equals(NULLOBJECT)) {
                currencyName = null;
            }

            return (String) currencyName;
        }

        OpenListResourceBundle olrb = localeData.getCurrencyNames(locale);

        if (olrb.containsKey(key)) {
            currencyName = olrb.getObject(key);
            cache.put(cacheKey,
                      new ResourceReference(cacheKey, currencyName, referenceQueue));
        }

        return (String) currencyName;
    }

    public String getLocaleName(String key) {
        Object localeName = null;
        String cacheKey = LOCALE_NAMES + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data != null && ((localeName = data.get()) != null)) {
            if (localeName.equals(NULLOBJECT)) {
                localeName = null;
            }

            return (String) localeName;
        }

        OpenListResourceBundle olrb = localeData.getLocaleNames(locale);

        if (olrb.containsKey(key)) {
            localeName = olrb.getObject(key);
            cache.put(cacheKey,
                      new ResourceReference(cacheKey, localeName, referenceQueue));
        }

        return (String) localeName;
    }

    public Object getTimeZoneNames(String key) {
        Object val = null;
        String cacheKey = TIME_ZONE_NAMES + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (Objects.isNull(data) || Objects.isNull(val = data.get())) {
            TimeZoneNamesBundle tznb = localeData.getTimeZoneNames(locale);
            if (key.startsWith(TZNB_EXCITY_PREFIX)) {
                if (tznb.containsKey(key)) {
                    val = tznb.getString(key);
                    assert val instanceof String;
                    trace("tznb: %s key: %s, val: %s\n", tznb, key, val);
                }
            } else {
                String[] names = null;
                if (tznb.containsKey(key)) {
                    names = tznb.getStringArray(key);
                } else {
                    var tz = TimeZoneNameUtility.canonicalTZID(key).orElse(key);
                    if (tznb.containsKey(tz)) {
                        names = tznb.getStringArray(tz);
                    }
                }

                if (names != null) {
                    names[0] = key;
                    trace("tznb: %s key: %s, names: %s, %s, %s, %s, %s, %s, %s\n", tznb, key,
                        names[0], names[1], names[2], names[3], names[4], names[5], names[6]);
                    val = names;
                }
            }
            if (val != null) {
                cache.put(cacheKey,
                          new ResourceReference(cacheKey, val, referenceQueue));
            }
        }

        return val;
    }

    @SuppressWarnings("unchecked")
    Set<String> getZoneIDs() {
        Set<String> zoneIDs;

        removeEmptyReferences();
        ResourceReference data = cache.get(ZONE_IDS_CACHEKEY);
        if (data == null || ((zoneIDs = (Set<String>) data.get()) == null)) {
            TimeZoneNamesBundle rb = localeData.getTimeZoneNames(locale);
            zoneIDs = rb.keySet();
            cache.put(ZONE_IDS_CACHEKEY,
                      new ResourceReference(ZONE_IDS_CACHEKEY, zoneIDs, referenceQueue));
        }

        return zoneIDs;
    }

    // zoneStrings are cached separately in TimeZoneNameUtility.
    String[][] getZoneStrings() {
        TimeZoneNamesBundle rb = localeData.getTimeZoneNames(locale);
        Set<String> keyset = getZoneIDs();
        // Use a LinkedHashSet to preserve the order
        Set<String[]> value = new LinkedHashSet<>();
        Set<String> tzIds = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
        for (String key : keyset) {
            if (!key.startsWith(TZNB_EXCITY_PREFIX)) {
                value.add(rb.getStringArray(key));
                tzIds.remove(key);
            }
        }

        if (type == LocaleProviderAdapter.Type.CLDR) {
            // Note: TimeZoneNamesBundle creates a String[] on each getStringArray call.

            // Add timezones which are not present in this keyset,
            // so that their fallback names will be generated at runtime.
            tzIds.stream().filter(i -> (!i.startsWith("Etc/GMT")
                    && !i.startsWith("GMT")
                    && !i.startsWith("SystemV")))
                    .forEach(tzid -> {
                        String[] val = new String[7];
                        if (keyset.contains(tzid)) {
                            val = rb.getStringArray(tzid);
                        } else {
                            var canonID = TimeZoneNameUtility.canonicalTZID(tzid)
                                            .orElse(tzid);
                            if (keyset.contains(canonID)) {
                                val = rb.getStringArray(canonID);
                            }
                        }
                        val[0] = tzid;
                        value.add(val);
                    });
        }
        return value.toArray(new String[0][]);
    }

    String[] getCalendarNames(String key) {
        String[] names = null;
        String cacheKey = CALENDAR_NAMES + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data == null || ((names = (String[]) data.get()) == null)) {
            ResourceBundle rb = localeData.getDateFormatData(locale);
            if (rb.containsKey(key)) {
                names = rb.getStringArray(key);
                cache.put(cacheKey,
                          new ResourceReference(cacheKey, names, referenceQueue));
            }
        }

        return names;
    }

    String[] getJavaTimeNames(String key) {
        String[] names = null;
        String cacheKey = CALENDAR_NAMES + key;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data == null || ((names = (String[]) data.get()) == null)) {
            ResourceBundle rb = getJavaTimeFormatData();
            if (rb.containsKey(key)) {
                names = rb.getStringArray(key);
                cache.put(cacheKey,
                          new ResourceReference(cacheKey, names, referenceQueue));
            }
        }

        return names;
    }

    public String getDateTimePattern(int timeStyle, int dateStyle, Calendar cal) {
        if (cal == null) {
            cal = Calendar.getInstance(locale);
        }
        return getDateTimePattern(null, timeStyle, dateStyle, cal.getCalendarType());
    }

    /**
     * Returns a date-time format pattern
     * @param timeStyle style of time; one of FULL, LONG, MEDIUM, SHORT in DateFormat,
     *                  or -1 if not required
     * @param dateStyle style of time; one of FULL, LONG, MEDIUM, SHORT in DateFormat,
     *                  or -1 if not required
     * @param calType   the calendar type for the pattern
     * @return the pattern string
     */
    public String getJavaTimeDateTimePattern(int timeStyle, int dateStyle, String calType) {
        calType = CalendarDataUtility.normalizeCalendarType(calType);
        String pattern;
        pattern = getDateTimePattern("java.time.", timeStyle, dateStyle, calType);
        if (pattern == null) {
            pattern = getDateTimePattern(null, timeStyle, dateStyle, calType);
        }
        return pattern;
    }

    private String getDateTimePattern(String prefix, int timeStyle, int dateStyle, String calType) {
        String pattern;
        String timePattern = null;
        String datePattern = null;

        if (timeStyle >= 0) {
            if (prefix != null) {
                timePattern = getDateTimePattern(prefix, "TimePatterns", timeStyle, calType);
            }
            if (timePattern == null) {
                timePattern = getDateTimePattern(null, "TimePatterns", timeStyle, calType);
            }
        }
        if (dateStyle >= 0) {
            if (prefix != null) {
                datePattern = getDateTimePattern(prefix, "DatePatterns", dateStyle, calType);
            }
            if (datePattern == null) {
                datePattern = getDateTimePattern(null, "DatePatterns", dateStyle, calType);
            }
        }
        if (timeStyle >= 0) {
            if (dateStyle >= 0) {
                String dateTimePattern = null;
                int dateTimeStyle = Math.max(dateStyle, timeStyle);
                if (prefix != null) {
                    dateTimePattern = getDateTimePattern(prefix, "DateTimePatterns", dateTimeStyle, calType);
                }
                if (dateTimePattern == null) {
                    dateTimePattern = getDateTimePattern(null, "DateTimePatterns", dateTimeStyle, calType);
                }
                pattern = switch (Objects.requireNonNull(dateTimePattern)) {
                    case "{1} {0}" -> datePattern + " " + timePattern;
                    case "{0} {1}" -> timePattern + " " + datePattern;
                    default -> MessageFormat.format(dateTimePattern.replaceAll("'", "''"), timePattern, datePattern);
                };
            } else {
                pattern = timePattern;
            }
        } else if (dateStyle >= 0) {
            pattern = datePattern;
        } else {
            throw new IllegalArgumentException("No date or time style specified");
        }
        return pattern;
    }

    public String[] getNumberPatterns() {
        String[] numberPatterns;

        removeEmptyReferences();
        ResourceReference data = cache.get(NUMBER_PATTERNS_CACHEKEY);

        if (data == null || ((numberPatterns = (String[]) data.get()) == null)) {
            ResourceBundle resource = localeData.getNumberFormatData(locale);
            numberPatterns = getNumberStrings(resource, "NumberPatterns");
            cache.put(NUMBER_PATTERNS_CACHEKEY,
                      new ResourceReference(NUMBER_PATTERNS_CACHEKEY, numberPatterns, referenceQueue));
        }

        return numberPatterns;
    }

    /**
     * Returns the compact number format patterns.
     * @param formatStyle the style for formatting a number
     * @return an array of compact number patterns
     */
    public String[] getCNPatterns(NumberFormat.Style formatStyle) {

        Objects.requireNonNull(formatStyle);
        String[] compactNumberPatterns;
        removeEmptyReferences();
        String width = (formatStyle == NumberFormat.Style.LONG) ? "long" : "short";
        String cacheKey = width + "." + COMPACT_NUMBER_PATTERNS_CACHEKEY;
        ResourceReference data = cache.get(cacheKey);
        if (data == null || ((compactNumberPatterns
                = (String[]) data.get()) == null)) {
            ResourceBundle resource = localeData.getNumberFormatData(locale);
            compactNumberPatterns = (String[]) resource
                    .getObject(width + ".CompactNumberPatterns");
            cache.put(cacheKey, new ResourceReference(cacheKey, compactNumberPatterns, referenceQueue));
        }
        return compactNumberPatterns;
    }


    /**
     * Returns the FormatData resource bundle of this LocaleResources.
     * The FormatData should be used only for accessing extra
     * resources required by JSR 310.
     */
    public ResourceBundle getJavaTimeFormatData() {
        ResourceBundle rb = localeData.getDateFormatData(locale);
        if (rb instanceof ParallelListResourceBundle) {
            localeData.setSupplementary((ParallelListResourceBundle) rb);
        }
        return rb;
    }

    /**
     * Returns the actual format pattern string based on the requested template
     * and calendar type for this locale.
     *
     * @param requestedTemplate requested template
     * @param calType calendar type
     * @throws IllegalArgumentException if the requested template is invalid
     * @return format pattern string for this locale, null if not found
     */
    public String getLocalizedPattern(String requestedTemplate, String calType) {
        String pattern;
        String cacheKey = SKELETON_PATTERN + calType + "." + requestedTemplate;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data == null || ((pattern = (String) data.get()) == null)) {
            pattern = getLocalizedPatternImpl(requestedTemplate, calType);
            cache.put(cacheKey,
                new ResourceReference(cacheKey, pattern != null ? pattern : "", referenceQueue));
        } else if ("".equals(pattern)) {
            // non-existent pattern
            pattern = null;
        }

        return pattern;
    }

    private String getLocalizedPatternImpl(String requestedTemplate, String calType) {
        initSkeletonIfNeeded();

        // input skeleton substitution
        var skeleton = substituteInputSkeletons(requestedTemplate);

        // validity check
        var matcher = VALID_SKELETON_PATTERN.matcher(skeleton);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Requested template \"%s\" is invalid".formatted(requestedTemplate) +
                    (requestedTemplate.equals(skeleton) ? "." : ", which translated into \"%s\"".formatted(skeleton) +
                            " after the 'j' or 'C' substitution."));
        }

        // try to match entire requested template first
        String matched = matchSkeleton(skeleton, calType);
        if (matched == null) {
            // 2.6.2.2 Missing Skeleton Fields
            var dateMatched = matchSkeleton(matcher.group("date"), calType);
            var timeMatched = matchSkeleton(matcher.group("time"), calType);
            if (dateMatched != null && timeMatched != null) {
                // combine both matches
                var style = switch (requestedTemplate.replaceAll("[^M]+", "").length()) {
                    case 4 -> requestedTemplate.indexOf('E') >= 0 ? 0 : 1;
                    case 3 -> 2;
                    default -> 3;
                };
                var dateTimePattern = getDateTimePattern(null, "DateTimePatterns", style, calType);
                matched = MessageFormat.format(dateTimePattern.replaceAll("'", "''"), timeMatched, dateMatched);
            }
        }

        trace("requested: %s, locale: %s, calType: %s, matched: %s\n", requestedTemplate, locale, calType, matched);

        return matched;
    }

    private String matchSkeleton(String skeleton, String calType) {
        // Expand it with possible inferred skeleton stream based on its priority
        var inferred = possibleInferred(skeleton);

        // Search the closest format pattern string from the resource bundle
        ResourceBundle r = localeData.getDateFormatData(locale);
        return inferred
            .map(s -> ("gregory".equals(calType) ? "" : calType + ".") + "DateFormatItem." + s)
            .map(key -> r.containsKey(key) ? r.getString(key) : null)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private void initSkeletonIfNeeded() {
        // "preferred"/"allowed" input skeleton maps
        if (inputSkeletons == null) {
            inputSkeletons = new HashMap<>();
            Pattern p = Pattern.compile("([^:]+):([^;]+);");
            ResourceBundle r = localeData.getDateFormatData(Locale.ROOT);
            Stream.of("preferred", "allowed").forEach(type -> {
                var inputRegionsKey = SKELETON_INPUT_REGIONS_KEY + "." + type;
                Map<String, String> typeMap = new HashMap<>();

                if (r.containsKey(inputRegionsKey)) {
                    p.matcher(r.getString(inputRegionsKey)).results()
                        .forEach(mr ->
                            Arrays.stream(mr.group(2).split(" "))
                                .forEach(region -> typeMap.put(region, mr.group(1))));
                }
                inputSkeletons.put(type, typeMap);
            });
        }

        // j/C patterns for this locale
        if (jPattern == null) {
            jPattern = resolveInputSkeleton("preferred");
            CPattern = resolveInputSkeleton("allowed");
            // hack: "allowed" contains reversed order for hour/period, e.g, "hB" which should be "Bh" as a skeleton
            if (CPattern.length() == 2) {
                var ba = new byte[2];
                ba[0] = (byte)CPattern.charAt(1);
                ba[1] = (byte)CPattern.charAt(0);
                CPattern = new String(ba);
            }
        }
    }

    /**
     * Resolve locale specific input skeletons. Fall back method is different from usual
     * resource bundle's, as it has to be "lang-region" -> "region" -> "lang-001" -> "001"
     * @param type type of the input skeleton
     * @return resolved skeletons for this locale, defaults to "h" if none found.
     */
    private String resolveInputSkeleton(String type) {
        var regionToSkeletonMap = inputSkeletons.get(type);
        return regionToSkeletonMap.getOrDefault(locale.getLanguage() + "-" + locale.getCountry(),
            regionToSkeletonMap.getOrDefault(locale.getCountry(),
                regionToSkeletonMap.getOrDefault(locale.getLanguage() + "-001",
                    regionToSkeletonMap.getOrDefault("001", "h"))));
    }

    /**
     * Replace 'j' and 'C' input skeletons with locale specific patterns. Note that 'j'
     * is guaranteed to be replaced with one char [hkHK], while 'C' may be replaced with
     * multiple chars. Repeat each as much as 'C' count.
     * @param requestedTemplate requested skeleton
     * @return skeleton with j/C substituted with concrete patterns
     */
    private String substituteInputSkeletons(String requestedTemplate) {
        var cCount = requestedTemplate.chars().filter(c -> c == 'C').count();
        return requestedTemplate.replaceAll("j", jPattern)
                .replaceFirst("C+", CPattern.replaceAll("([hkHK])", "$1".repeat((int)cCount)));
    }

    /**
     * Returns a stream of possible skeletons, inferring standalone/format (M/L and/or E/c) patterns
     * and their styles. (cf. 2.6.2.1 Matching Skeletons)
     *
     * @param skeleton original skeleton
     * @return inferred Stream of skeletons in its priority order
     */
    private Stream<String> possibleInferred(String skeleton) {
        return priorityList(skeleton, "M", "L").stream()
                .flatMap(s -> priorityList(s, "E", "c").stream())
                .distinct();
    }

    /**
     * Inferring the possible format styles in priority order, based on the original
     * skeleton length.
     *
     * @param skeleton skeleton
     * @param pChar pattern character string
     * @param subChar substitute character string
     * @return list of skeletons
     */
    private List<String> priorityList(String skeleton, String pChar, String subChar) {
        int first = skeleton.indexOf(pChar);
        int last = skeleton.lastIndexOf(pChar);

        if (first >= 0) {
            var prefix = skeleton.substring(0, first);
            var suffix = skeleton.substring(last + 1);

            // Priority are based on this chart. First column is the original count of `pChar`,
            // then it is followed by inferred skeletons base on priority.
            //
            // 1->2->3->4 (number form (1-digit) -> number form (2-digit) -> Abbr. form -> Full form)
            // 2->1->3->4
            // 3->4->2->1
            // 4->3->2->1
            var o1 = prefix + pChar + suffix;
            var o2 = prefix + pChar.repeat(2) + suffix;
            var o3 = prefix + pChar.repeat(3) + suffix;
            var o4 = prefix + pChar.repeat(4) + suffix;
            var s1 = prefix + subChar + suffix;
            var s2 = prefix + subChar.repeat(2) + suffix;
            var s3 = prefix + subChar.repeat(3) + suffix;
            var s4 = prefix + subChar.repeat(4) + suffix;
            return switch (last - first) {
                case 1 -> List.of(skeleton, o1, o2, o3, o4, s1, s2, s3, s4);
                case 2 -> List.of(skeleton, o2, o1, o3, o4, s2, s1, s3, s4);
                case 3 -> List.of(skeleton, o3, o4, o2, o1, s3, s4, s2, s1);
                default -> List.of(skeleton, o4, o3, o2, o1, s4, s3, s2, s1);
            };
        } else {
            return List.of(skeleton);
        }
    }

    private String getDateTimePattern(String prefix, String key, int styleIndex, String calendarType) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix);
        }
        if (!"gregory".equals(calendarType)) {
            sb.append(calendarType).append('.');
        }
        sb.append(key);
        String resourceKey = sb.toString();
        String cacheKey = sb.insert(0, DATE_TIME_PATTERN).toString();

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);
        Object value = NULLOBJECT;

        if (data == null || ((value = data.get()) == null)) {
            ResourceBundle r = (prefix != null) ? getJavaTimeFormatData() : localeData.getDateFormatData(locale);
            if (r.containsKey(resourceKey)) {
                value = r.getStringArray(resourceKey);
            } else {
                assert !resourceKey.equals(key);
                if (r.containsKey(key)) {
                    value = r.getStringArray(key);
                }
            }
            cache.put(cacheKey,
                      new ResourceReference(cacheKey, value, referenceQueue));
        }
        if (value == NULLOBJECT) {
            assert prefix != null;
            return null;
        }

        // for DateTimePatterns. CLDR has multiple styles, while JRE has one.
        String[] styles = (String[])value;
        return (styles.length > 1 ? styles[styleIndex] : styles[0]);
    }

    public String[] getRules() {
        String[] rules;

        removeEmptyReferences();
        ResourceReference data = cache.get(RULES_CACHEKEY);

        if (data == null || ((rules = (String[]) data.get()) == null)) {
            ResourceBundle rb = localeData.getDateFormatData(locale);
            rules = new String[2];
            rules[0] = rules[1] = "";
            if (rb.containsKey("PluralRules")) {
                rules[0] = rb.getString("PluralRules");
            }
            if (rb.containsKey("DayPeriodRules")) {
                rules[1] = rb.getString("DayPeriodRules");
            }
            cache.put(RULES_CACHEKEY, new ResourceReference(RULES_CACHEKEY, rules, referenceQueue));
        }

        return rules;
    }

    /**
     * {@return the list patterns for the locale}
     *
     * @param type a {@link ListFormat.Type}
     * @param style a {@link ListFormat.Style}
     */
    public String[] getListPatterns(ListFormat.Type type, ListFormat.Style style) {
        String typeStr = type.toString().toLowerCase(Locale.ROOT);
        String styleStr = style.toString().toLowerCase(Locale.ROOT);
        String[] lpArray;
        String cacheKey = LIST_PATTERN + typeStr + styleStr;

        removeEmptyReferences();
        ResourceReference data = cache.get(cacheKey);

        if (data == null || ((lpArray = (String[]) data.get()) == null)) {
            var rbKey = "ListPatterns_" + typeStr + (style == ListFormat.Style.FULL ? "" : "-" + styleStr);
            lpArray = localeData.getDateFormatData(locale).getStringArray(rbKey);

            if (lpArray[0].isEmpty() || lpArray[1].isEmpty() || lpArray[2].isEmpty()) {
                if (LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR)
                        instanceof ResourceBundleBasedAdapter rbba) {
                    var candList = rbba.getCandidateLocales("", locale);
                    if (!candList.isEmpty()) {
                        for (var p : candList.subList(1, candList.size())) {
                            var parentPatterns = localeData.getDateFormatData(p).getStringArray(rbKey);
                            for (int i = 0; i < 3; i++) { // exclude optional ones, ie, "two"/"three"
                                if (lpArray[i].isEmpty()) {
                                    lpArray[i] = parentPatterns[i];
                                }
                            }
                        }
                    }
                }
            }
            cache.put(cacheKey, new ResourceReference(cacheKey, lpArray, referenceQueue));
        }

        return lpArray;
    }

    private static class ResourceReference extends SoftReference<Object> {
        private final String cacheKey;

        ResourceReference(String cacheKey, Object o, ReferenceQueue<Object> q) {
            super(o, q);
            this.cacheKey = cacheKey;
        }

        String getCacheKey() {
            return cacheKey;
        }
    }

    private static final boolean TRACE_ON = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("locale.resources.debug", "false"));

    public static void trace(String format, Object... params) {
        if (TRACE_ON) {
            System.out.format(format, params);
        }
    }
}
