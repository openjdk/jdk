/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;
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
    private static final String COLLATION_DATA_CACHEKEY = "COLD";
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

    // TimeZoneNamesBundle exemplar city prefix
    private static final String TZNB_EXCITY_PREFIX = "timezone.excity.";

    // null singleton cache value
    private static final Object NULLOBJECT = new Object();

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
        String coldata = "";

        removeEmptyReferences();
        ResourceReference data = cache.get(COLLATION_DATA_CACHEKEY);
        if (data == null || ((coldata = (String) data.get()) == null)) {
            ResourceBundle rb = localeData.getCollationData(locale);
            if (rb.containsKey(key)) {
                coldata = rb.getString(key);
            }
            cache.put(COLLATION_DATA_CACHEKEY,
                      new ResourceReference(COLLATION_DATA_CACHEKEY, coldata, referenceQueue));
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
        // Use a LinkedHashSet to preseve the order
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
     * Returns the actual format pattern string based on the requested pattern
     * and calendar type for this locale.
     *
     * @param requested requested pattern
     * @param calType calendar type
     * @throws IllegalArgumentException if the requested pattern is invalid
     * @return format pattern string for this locale, null if not found
     */
    public String getLocalizedPattern(String requested, String calType) {
        initSkeletonIfNeeded();

        // input skeleton substitution
        final String skeleton = substituteInputSkeletons(requested);

        // validity check
        if (!validSkeletons.contains(skeleton)) {
            throw new IllegalArgumentException("Requested pattern is invalid: " + requested);
        }

        // Expand it with possible inferred skeleton stream based on its priority
        var inferred = possibleInferred(skeleton);
        ResourceBundle r = localeData.getDateFormatData(locale);

        // Search the closest format pattern string from the resource bundle
        var matched = inferred
//.peek(s -> System.out.println("inf: "+s))
                .map(s -> skeletonFromRB(r, CalendarDataUtility.normalizeCalendarType(calType), s))
                .filter(Objects::nonNull)
//.peek(s -> System.out.println("mch: "+s))
                .findFirst()
                .map(s -> adjustSkeletonLength(skeleton, s))
                .orElse(null);

System.out.println("requested: " + requested + ", locale: " + locale + ", caltype: " + calType + ", matched: "+matched);
        return matched;
    }

    // Possible valid skeleton patterns. Retrieved from
    private static Set<String> validSkeletons;
    // Input Skeleton map for "preferred" and "allowed"
    // Map<"preferred"/"allowed", Map<"region", "skeleton">>>
    private static Map<String, String> preferredInputSkeletons;
    private static Map<String, String> allowedInputSkeletons;
    // Skeletons for "j" and "C" input skeleton symbols for this locale
    private String jPattern;
    private String CPattern;
    private void initSkeletonIfNeeded() {
        if (validSkeletons == null) {
            preferredInputSkeletons = new HashMap<>();
            allowedInputSkeletons = new HashMap<>();
            Pattern p = Pattern.compile("([^:]+):([^;]+);");
            ResourceBundle r = localeData.getDateFormatData(Locale.ROOT);
            validSkeletons = Arrays.stream(r.getString("DateFormatItemValidPatterns").split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
            p.matcher(r.getString("DateFormatItemInputRegions.preferred")).results()
                .forEach(mr -> {
                    Arrays.stream(mr.group(2).split(" "))
                        .forEach(region -> preferredInputSkeletons.put(region, mr.group(1)));
                });
            p.matcher(r.getString("DateFormatItemInputRegions.allowed")).results()
                .forEach(mr -> {
                    Arrays.stream(mr.group(2).split(" "))
                        .forEach(region -> allowedInputSkeletons.put(region, mr.group(1)));
                });
        }
        if (jPattern == null) {
            jPattern = resolveInputSkeleton(preferredInputSkeletons);
            CPattern = resolveInputSkeleton(allowedInputSkeletons);
            // hack: "allowed" contains reversed order for hour/period, e.g, "hB" which should be "Bh" as a skeleton
            if (CPattern.length() == 2) {
                CPattern = String.format("%c%c", CPattern.charAt(1), CPattern.charAt(0));
            }
        }
    }

    /**
     * Resolve locale specific input skeletons. Fall back method is different from usual
     * resource bundle's, as it has to be "lang-region" -> "region" -> "lang-001" -> "001"
     * @param inputSkeletons
     * @return
     */
    private String resolveInputSkeleton(Map<String, String> inputSkeletons) {
        return inputSkeletons.getOrDefault(locale.getLanguage() + "-" + locale.getCountry(),
            inputSkeletons.getOrDefault(locale.getCountry(),
                inputSkeletons.getOrDefault(locale.getLanguage() + "-001",
                    inputSkeletons.get("001"))));
    }

    /**
     * Replace 'j' and 'C' input skeletons with locale specific patterns. Note that 'j'
     * is guaranteed to be replaced with one char [hkHK], while 'C' may be replaced with
     * multiple chars. Repeat each as much as 'C' count.
     * @param requested requested skeleton
     * @return skeleton with j/C substituted with concrete patterns
     */
    private String substituteInputSkeletons(String requested) {
        return requested.replaceAll("j", jPattern)
                .replaceFirst("C+",
                    CPattern.chars()
                            .mapToObj(c -> String.valueOf((char)c).repeat(requested.lastIndexOf('C') - requested.indexOf('C') + 1))
                            .collect(Collectors.joining()));
    }

    private String skeletonFromRB(ResourceBundle r, String calT, String key) {
        String skeleton = null;
        key = ("gregory".equals(calT) ? "" : calT + ".") + "DateFormatItem." + key;
        if (r.containsKey(key)) {
            skeleton = r.getString(key);
        }
        return skeleton;
    }

    /**
     * Returns a stream of possible skeletons, inferring standalone/format (M/L and/or E/c) patterns
     * and their styles.
     *
     * @param skeleton original skeleton
     * @return inferred Stream of skeletons in its priority order
     */
    private Stream<String> possibleInferred(String skeleton) {
        return List.of(skeleton).stream()
            .flatMap(s -> {
                if (s.indexOf('M') >= 0) {
                    return Stream.concat(priorityList(s, "M", "M"), priorityList(s, "M", "L"));
                } else if (s.indexOf('L') >= 0) {
                    return Stream.concat(priorityList(s, "L", "L"), priorityList(s, "L", "M"));
                }
                return List.of(s).stream();
            })
            .flatMap(s -> {
                if (s.indexOf('E') >= 0) {
                    return Stream.concat(priorityList(s, "E", "E"), priorityList(s, "E", "c"));
                } else if (s.indexOf('c') >= 0) {
                    return Stream.concat(priorityList(s, "c", "c"), priorityList(s, "c", "E"));
                }
                return List.of(s).stream();
            })
            .distinct()
            .filter(validSkeletons::contains);
    }

    /**
     * Inferring the possible format styles in priority order, based on the original
     * skeleton length.
     *
     * @param skeleton skeleton
     * @param pChar pattern character string
     * @param subChar substitute character string
     * @return stream of skeletons
     */
    private Stream<String> priorityList(String skeleton, String pChar, String subChar) {
        List<String> list;
        int last = skeleton.lastIndexOf(pChar);
        if (last >= 0) {
            // Priority are based on this chart. First column is the original count of `pChar`,
            // then it is followed by inferred skeletons base on priority.
            //
            // 1->2->3->4 (number form (1-digit) -> number form (2-digit) -> Abbr. form -> Full form)
            // 2->1->3->4
            // 3->4->2->1
            // 4->3->2->1
            final String s1 = skeleton.replaceFirst(pChar + "+", subChar);
            final String s2 = skeleton.replaceFirst(pChar + "+", subChar.repeat(2));
            final String s3 = skeleton.replaceFirst(pChar + "+", subChar.repeat(3));
            final String s4 = skeleton.replaceFirst(pChar + "+", subChar.repeat(4));
            list = switch (last - skeleton.indexOf(pChar) + 1) {
                case 1 -> List.of(s1, s2, s3, s4);
                case 2 -> List.of(s2, s1, s3, s4);
                case 3 -> List.of(s3, s4, s2, s1);
                case 4 -> List.of(s4, s3, s2, s1);
                default -> List.of(skeleton);
            };
        } else {
            list = List.of(skeleton);
        }
//System.out.println("priorityList: "+list);
        return list.stream();
    }

    private String adjustSkeletonLength(String requested, String matched) {
        if (false) {
//        if (!requested.equals(matched)) {
//            // adjust the lengths
//            int monthsR = Math.max(requested.lastIndexOf('M') - requested.indexOf('M'),
//                                    requested.lastIndexOf('L') - requested.indexOf('L')) + 1;
//            int daysR = Math.max(requested.lastIndexOf('E') - requested.indexOf('E'),
//                    requested.lastIndexOf('c') - requested.indexOf('c')) + 1;
//            int monthsM = Math.max(matched.lastIndexOf('M') - matched.indexOf('M'),
//                    matched.lastIndexOf('L') - matched.indexOf('L')) + 1;
//            int daysM = Math.max(matched.lastIndexOf('E') - matched.indexOf('E'),
//                    matched.lastIndexOf('c') - matched.indexOf('c')) + 1;
//            // do not cross number/text boundary.
//            if (monthsR >= 3 && monthsM <= 2) {
//                monthsR = 2;
//            }
//            if (daysR >= 3 && daysM <= 2) {
//                daysR = 2;
//            }
//            matched = matched.replaceFirst("M+", "M".repeat(monthsR))
//                    .replaceFirst("L+", "L".repeat(monthsR))
//                    .replaceFirst("E+", "E".repeat(daysR))
//                    .replaceFirst("c+", "c".repeat(daysR != 2 ? daysR : 1)); // "cc" is not allowed in JDK

//            var inta = matched.codePoints()
//                    .distinct()
//                    .mapMulti((c, consumer) -> {
//                        int first = requested.indexOf(c);
//                        int last = requested.lastIndexOf(c);
//                        if (first >= 0) {
//                            int num = Math.max(last - first, matched.lastIndexOf(c) - matched.indexOf(c)) + 1;
//                            while (num-- > 0) {
//                                consumer.accept(c);
//                            }
//                        } else {
//                            consumer.accept(c);
//                        }
//                    })
//                    .toArray();

// <dateFormatItem id="yMMMd">d MMM y</dateFormatItem>
// If this is the best match for yMMMMd, pattern is automatically expanded to produce the pattern "d MMMM y" in response to the request.

            var inta = matched.codePoints()
                    .distinct()
                    .mapMulti((c, consumer) -> {
                        if (Character.isAlphabetic(c)) {
                            int first = requested.lastIndexOf(c);
                            int requestLen = requested.lastIndexOf(c) - first + 1;
                            int matchedLen = matched.lastIndexOf(c) - matched.indexOf(c) + 1;
                            if (first >= 0) {
                                int num = Math.max(requestLen, matchedLen);
                                while (num-- > 0) {
                                    consumer.accept(c);
                                }
                            } else {
                                consumer.accept(c);
                            }
                        } else {
                            consumer.accept(c);
                        }
                    })
                    .toArray();
            var ret = new String(inta, 0, inta.length);
            return ret;
        }
        return matched;
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
