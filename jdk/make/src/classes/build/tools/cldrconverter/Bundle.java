/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.cldrconverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class Bundle {
    static enum Type {
        LOCALENAMES, CURRENCYNAMES, TIMEZONENAMES, CALENDARDATA, FORMATDATA;

        static EnumSet<Type> ALL_TYPES = EnumSet.of(LOCALENAMES,
                                                    CURRENCYNAMES,
                                                    TIMEZONENAMES,
                                                    CALENDARDATA,
                                                    FORMATDATA);
    }

    private final static Map<String, Bundle> bundles = new HashMap<>();

    private final static String[] NUMBER_PATTERN_KEYS = {
        "NumberPatterns/decimal",
        "NumberPatterns/currency",
        "NumberPatterns/percent"
    };

    private final static String[] NUMBER_ELEMENT_KEYS = {
        "NumberElements/decimal",
        "NumberElements/group",
        "NumberElements/list",
        "NumberElements/percent",
        "NumberElements/zero",
        "NumberElements/pattern",
        "NumberElements/minus",
        "NumberElements/exponential",
        "NumberElements/permille",
        "NumberElements/infinity",
        "NumberElements/nan"
    };

    private final static String[] TIME_PATTERN_KEYS = {
        "DateTimePatterns/full-time",
        "DateTimePatterns/long-time",
        "DateTimePatterns/medium-time",
        "DateTimePatterns/short-time",
    };

    private final static String[] DATE_PATTERN_KEYS = {
        "DateTimePatterns/full-date",
        "DateTimePatterns/long-date",
        "DateTimePatterns/medium-date",
        "DateTimePatterns/short-date",
    };

    private final static String[] DATETIME_PATTERN_KEYS = {
        "DateTimePatterns/full-dateTime",
        "DateTimePatterns/long-dateTime",
        "DateTimePatterns/medium-dateTime",
        "DateTimePatterns/short-dateTime",
    };

    private final static String[] ERA_KEYS = {
        "long.Eras",
        "Eras",
        "narrow.Eras"
    };

    // Keys for individual time zone names
    private final static String TZ_GEN_LONG_KEY = "timezone.displayname.generic.long";
    private final static String TZ_GEN_SHORT_KEY = "timezone.displayname.generic.short";
    private final static String TZ_STD_LONG_KEY = "timezone.displayname.standard.long";
    private final static String TZ_STD_SHORT_KEY = "timezone.displayname.standard.short";
    private final static String TZ_DST_LONG_KEY = "timezone.displayname.daylight.long";
    private final static String TZ_DST_SHORT_KEY = "timezone.displayname.daylight.short";
    private final static String[] ZONE_NAME_KEYS = {
        TZ_STD_LONG_KEY,
        TZ_STD_SHORT_KEY,
        TZ_DST_LONG_KEY,
        TZ_DST_SHORT_KEY,
        TZ_GEN_LONG_KEY,
        TZ_GEN_SHORT_KEY
    };

    private final String id;
    private final String cldrPath;
    private final EnumSet<Type> bundleTypes;
    private final String currencies;
    private Map<String, Object> targetMap;

    static Bundle getBundle(String id) {
        return bundles.get(id);
    }

    @SuppressWarnings("ConvertToStringSwitch")
    Bundle(String id, String cldrPath, String bundles, String currencies) {
        this.id = id;
        this.cldrPath = cldrPath;
        if ("localenames".equals(bundles)) {
            bundleTypes = EnumSet.of(Type.LOCALENAMES);
        } else if ("currencynames".equals(bundles)) {
            bundleTypes = EnumSet.of(Type.CURRENCYNAMES);
        } else {
            bundleTypes = Type.ALL_TYPES;
        }
        if (currencies == null) {
            currencies = "local";
        }
        this.currencies = currencies;
        addBundle();
    }

    private void addBundle() {
        Bundle.bundles.put(id, this);
    }

    String getID() {
        return id;
    }

    String getJavaID() {
        // Tweak ISO compatibility for bundle generation
        return id.replaceFirst("^he", "iw")
            .replaceFirst("^id", "in")
            .replaceFirst("^yi", "ji");
    }

    boolean isRoot() {
        return "root".equals(id);
    }

    String getCLDRPath() {
        return cldrPath;
    }

    EnumSet<Type> getBundleTypes() {
        return bundleTypes;
    }

    String getCurrencies() {
        return currencies;
    }

    /**
     * Generate a map that contains all the data that should be
     * visible for the bundle's locale
     */
    Map<String, Object> getTargetMap() throws Exception {
        if (targetMap != null) {
            return targetMap;
        }

        String[] cldrBundles = getCLDRPath().split(",");

        // myMap contains resources for id.
        Map<String, Object> myMap = new HashMap<>();
        int index;
        for (index = 0; index < cldrBundles.length; index++) {
            if (cldrBundles[index].equals(id)) {
                myMap.putAll(CLDRConverter.getCLDRBundle(cldrBundles[index]));
                CLDRConverter.handleAliases(myMap);
                break;
            }
        }

        // parentsMap contains resources from id's parents.
        Map<String, Object> parentsMap = new HashMap<>();
        for (int i = cldrBundles.length - 1; i > index; i--) {
            if (!("no".equals(cldrBundles[i]) || cldrBundles[i].startsWith("no_"))) {
                parentsMap.putAll(CLDRConverter.getCLDRBundle(cldrBundles[i]));
                CLDRConverter.handleAliases(parentsMap);
            }
        }
        // Duplicate myMap as parentsMap for "root" so that the
        // fallback works. This is a hack, though.
        if ("root".equals(cldrBundles[0])) {
            assert parentsMap.isEmpty();
            parentsMap.putAll(myMap);
        }

        // merge individual strings into arrays

        // if myMap has any of the NumberPatterns members
        for (String k : NUMBER_PATTERN_KEYS) {
            if (myMap.containsKey(k)) {
                String[] numberPatterns = new String[NUMBER_PATTERN_KEYS.length];
                for (int i = 0; i < NUMBER_PATTERN_KEYS.length; i++) {
                    String key = NUMBER_PATTERN_KEYS[i];
                    String value = (String) myMap.remove(key);
                    if (value == null) {
                        value = (String) parentsMap.remove(key);
                    }
                    if (value.length() == 0) {
                        CLDRConverter.warning("empty pattern for " + key);
                    }
                    numberPatterns[i] = value;
                }
                myMap.put("NumberPatterns", numberPatterns);
                break;
            }
        }

        // if myMap has any of NUMBER_ELEMENT_KEYS, create a complete NumberElements.
        String defaultScript = (String) myMap.get("DefaultNumberingSystem");
        @SuppressWarnings("unchecked")
        List<String> scripts = (List<String>) myMap.get("numberingScripts");
        if (defaultScript == null && scripts != null) {
            // Some locale data has no default script for numbering even with mutiple scripts.
            // Take the first one as default in that case.
            defaultScript = scripts.get(0);
            myMap.put("DefaultNumberingSystem", defaultScript);
        }
        if (scripts != null) {
            for (String script : scripts) {
                for (String k : NUMBER_ELEMENT_KEYS) {
                    String[] numberElements = new String[NUMBER_ELEMENT_KEYS.length];
                    for (int i = 0; i < NUMBER_ELEMENT_KEYS.length; i++) {
                        String key = script + "." + NUMBER_ELEMENT_KEYS[i];
                        String value = (String) myMap.remove(key);
                        if (value == null) {
                            if (key.endsWith("/pattern")) {
                                value = "#";
                            } else {
                                value = (String) parentsMap.get(key);
                                if (value == null) {
                                    // the last resort is "latn"
                                    key = "latn." + NUMBER_ELEMENT_KEYS[i];
                                    value = (String) parentsMap.get(key);
                                    if (value == null) {
                                        throw new InternalError("NumberElements: null for " + key);
                                    }
                                }
                            }
                        }
                        numberElements[i] = value;
                    }
                    myMap.put(script + "." + "NumberElements", numberElements);
                    break;
                }
            }
        }

        // another hack: parentsMap is not used for date-time resources.
        if ("root".equals(id)) {
            parentsMap = null;
        }

        for (CalendarType calendarType : CalendarType.values()) {
            String calendarPrefix = calendarType.keyElementName();
            // handle multiple inheritance for month and day names
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "MonthNames");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "MonthAbbreviations");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "MonthNarrows");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "DayNames");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "DayAbbreviations");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "DayNarrows");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "AmPmMarkers");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "narrow.AmPmMarkers");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "QuarterNames");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "QuarterAbbreviations");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "QuarterNarrows");

            adjustEraNames(myMap, calendarType);

            handleDateTimeFormatPatterns(TIME_PATTERN_KEYS, myMap, parentsMap, calendarType, "TimePatterns");
            handleDateTimeFormatPatterns(DATE_PATTERN_KEYS, myMap, parentsMap, calendarType, "DatePatterns");
            handleDateTimeFormatPatterns(DATETIME_PATTERN_KEYS, myMap, parentsMap, calendarType, "DateTimePatterns");
        }

        // First, weed out any empty timezone or metazone names from myMap.
        // Fill in any missing abbreviations if locale is "en".
        for (Iterator<String> it = myMap.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            if (key.startsWith(CLDRConverter.TIMEZONE_ID_PREFIX)
                    || key.startsWith(CLDRConverter.METAZONE_ID_PREFIX)) {
                @SuppressWarnings("unchecked")
                Map<String, String> nameMap = (Map<String, String>) myMap.get(key);
                if (nameMap.isEmpty()) {
                    // Some zones have only exemplarCity, which become empty.
                    // Remove those from the map.
                    it.remove();
                    continue;
                }

                if (id.equals("en")) {
                    fillInJREs(key, nameMap);
                }
            }
        }
        for (Iterator<String> it = myMap.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            if (key.startsWith(CLDRConverter.TIMEZONE_ID_PREFIX)
                    || key.startsWith(CLDRConverter.METAZONE_ID_PREFIX)) {
                @SuppressWarnings("unchecked")
                Map<String, String> nameMap = (Map<String, String>) myMap.get(key);
                // Convert key/value pairs to an array.
                String[] names = new String[ZONE_NAME_KEYS.length];
                int ix = 0;
                for (String nameKey : ZONE_NAME_KEYS) {
                    String name = nameMap.get(nameKey);
                    if (name == null) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> parentNames = (Map<String, String>) parentsMap.get(key);
                        if (parentNames != null) {
                            name = parentNames.get(nameKey);
                        }
                    }
                    names[ix++] = name;
                }
                if (hasNulls(names)) {
                    String metaKey = toMetaZoneKey(key);
                    if (metaKey != null) {
                        Object obj = myMap.get(metaKey);
                        if (obj instanceof String[]) {
                            String[] metaNames = (String[]) obj;
                            for (int i = 0; i < names.length; i++) {
                                if (names[i] == null) {
                                    names[i] = metaNames[i];
                                }
                            }
                        } else if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> m = (Map<String, String>) obj;
                            for (int i = 0; i < names.length; i++) {
                                if (names[i] == null) {
                                    names[i] = m.get(ZONE_NAME_KEYS[i]);
                                }
                            }
                        }
                    }
                    // If there are still any nulls, try filling in them from en data.
                    if (hasNulls(names) && !id.equals("en")) {
                        @SuppressWarnings("unchecked")
                        String[] enNames = (String[]) Bundle.getBundle("en").getTargetMap().get(key);
                        if (enNames == null) {
                            if (metaKey != null) {
                                @SuppressWarnings("unchecked")
                                String[] metaNames = (String[]) Bundle.getBundle("en").getTargetMap().get(metaKey);
                                enNames = metaNames;
                            }
                        }
                        if (enNames != null) {
                            for (int i = 0; i < names.length; i++) {
                                if (names[i] == null) {
                                    names[i] = enNames[i];
                                }
                            }
                        }
                        // If there are still nulls, give up names.
                        if (hasNulls(names)) {
                            names = null;
                        }
                    }
                }
                // replace the Map with the array
                if (names != null) {
                    myMap.put(key, names);
                } else {
                    it.remove();
                }
            }
        }

        // Remove all duplicates
        if (Objects.nonNull(parentsMap)) {
            for (Iterator<String> it = myMap.keySet().iterator(); it.hasNext();) {
                String key = it.next();
                if (!key.equals("numberingScripts") && // real body "NumberElements" may differ
                    Objects.deepEquals(parentsMap.get(key), myMap.get(key))) {
                    it.remove();
                }
            }
        }

        targetMap = myMap;
        return myMap;
    }

    private void handleMultipleInheritance(Map<String, Object> map, Map<String, Object> parents, String key) {
        String formatKey = key + "/format";
        Object format = map.get(formatKey);
        if (format != null) {
            map.remove(formatKey);
            map.put(key, format);
            if (fillInElements(parents, formatKey, format)) {
                map.remove(key);
            }
        }
        String standaloneKey = key + "/stand-alone";
        Object standalone = map.get(standaloneKey);
        if (standalone != null) {
            map.remove(standaloneKey);
            String realKey = key;
            if (format != null) {
                realKey = "standalone." + key;
            }
            map.put(realKey, standalone);
            if (fillInElements(parents, standaloneKey, standalone)) {
                map.remove(realKey);
            }
        }
    }

    /**
     * Fills in any empty elements with its parent element. Returns true if the resulting array is
     * identical to its parent array.
     *
     * @param parents
     * @param key
     * @param value
     * @return true if the resulting array is identical to its parent array.
     */
    private boolean fillInElements(Map<String, Object> parents, String key, Object value) {
        if (parents == null) {
            return false;
        }
        if (value instanceof String[]) {
            Object pvalue = parents.get(key);
            if (pvalue != null && pvalue instanceof String[]) {
                String[] strings = (String[]) value;
                String[] pstrings = (String[]) pvalue;
                for (int i = 0; i < strings.length; i++) {
                    if (strings[i] == null || strings[i].length() == 0) {
                        strings[i] = pstrings[i];
                    }
                }
                return Arrays.equals(strings, pstrings);
            }
        }
        return false;
    }

    /*
     * Adjusts String[] for era names because JRE's Calendars use different
     * ERA value indexes in the Buddhist, Japanese Imperial, and Islamic calendars.
     */
    private void adjustEraNames(Map<String, Object> map, CalendarType type) {
        String[][] eraNames = new String[ERA_KEYS.length][];
        String[] realKeys = new String[ERA_KEYS.length];
        int index = 0;
        for (String key : ERA_KEYS) {
            String realKey = type.keyElementName() + key;
            String[] value = (String[]) map.get(realKey);
            if (value != null) {
                switch (type) {
                case GREGORIAN:
                    break;

                case JAPANESE:
                    {
                        String[] newValue = new String[value.length + 1];
                        String[] julianEras = (String[]) map.get(key);
                        if (julianEras != null && julianEras.length >= 2) {
                            newValue[0] = julianEras[1];
                        } else {
                            newValue[0] = "";
                        }
                        System.arraycopy(value, 0, newValue, 1, value.length);
                        value = newValue;
                    }
                    break;

                case BUDDHIST:
                    // Replace the value
                    value = new String[] {"BC", value[0]};
                    break;

                case ISLAMIC:
                    // Replace the value
                    value = new String[] {"", value[0]};
                    break;
                }
                if (!key.equals(realKey)) {
                    map.put(realKey, value);
                }
            }
            realKeys[index] = realKey;
            eraNames[index++] = value;
        }
        for (int i = 0; i < eraNames.length; i++) {
            if (eraNames[i] == null) {
                map.put(realKeys[i], null);
            }
        }
    }

    private void handleDateTimeFormatPatterns(String[] patternKeys, Map<String, Object> myMap, Map<String, Object> parentsMap,
                                              CalendarType calendarType, String name) {
        String calendarPrefix = calendarType.keyElementName();
        for (String k : patternKeys) {
            if (myMap.containsKey(calendarPrefix + k)) {
                int len = patternKeys.length;
                List<String> rawPatterns = new ArrayList<>(len);
                List<String> patterns = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    String key = calendarPrefix + patternKeys[i];
                    String pattern = (String) myMap.remove(key);
                    if (pattern == null) {
                        pattern = (String) parentsMap.remove(key);
                    }
                    rawPatterns.add(i, pattern);
                    if (pattern != null) {
                        patterns.add(i, translateDateFormatLetters(calendarType, pattern));
                    } else {
                        patterns.add(i, null);
                    }
                }
                // If patterns is empty or has any nulls, discard patterns.
                if (patterns.isEmpty()) {
                    return;
                }
                String key = calendarPrefix + name;
                if (!rawPatterns.equals(patterns)) {
                    myMap.put("java.time." + key, rawPatterns.toArray(new String[len]));
                }
                myMap.put(key, patterns.toArray(new String[len]));
                break;
            }
        }
    }

    private String translateDateFormatLetters(CalendarType calendarType, String cldrFormat) {
        String pattern = cldrFormat;
        int length = pattern.length();
        boolean inQuote = false;
        StringBuilder jrePattern = new StringBuilder(length);
        int count = 0;
        char lastLetter = 0;

        for (int i = 0; i < length; i++) {
            char c = pattern.charAt(i);

            if (c == '\'') {
                // '' is treated as a single quote regardless of being
                // in a quoted section.
                if ((i + 1) < length) {
                    char nextc = pattern.charAt(i + 1);
                    if (nextc == '\'') {
                        i++;
                        if (count != 0) {
                            convert(calendarType, lastLetter, count, jrePattern);
                            lastLetter = 0;
                            count = 0;
                        }
                        jrePattern.append("''");
                        continue;
                    }
                }
                if (!inQuote) {
                    if (count != 0) {
                        convert(calendarType, lastLetter, count, jrePattern);
                        lastLetter = 0;
                        count = 0;
                    }
                    inQuote = true;
                } else {
                    inQuote = false;
                }
                jrePattern.append(c);
                continue;
            }
            if (inQuote) {
                jrePattern.append(c);
                continue;
            }
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z')) {
                if (count != 0) {
                    convert(calendarType, lastLetter, count, jrePattern);
                    lastLetter = 0;
                    count = 0;
                }
                jrePattern.append(c);
                continue;
            }

            if (lastLetter == 0 || lastLetter == c) {
                lastLetter = c;
                count++;
                continue;
            }
            convert(calendarType, lastLetter, count, jrePattern);
            lastLetter = c;
            count = 1;
        }

        if (inQuote) {
            throw new InternalError("Unterminated quote in date-time pattern: " + cldrFormat);
        }

        if (count != 0) {
            convert(calendarType, lastLetter, count, jrePattern);
        }
        if (cldrFormat.contentEquals(jrePattern)) {
            return cldrFormat;
        }
        return jrePattern.toString();
    }

    private String toMetaZoneKey(String tzKey) {
        if (tzKey.startsWith(CLDRConverter.TIMEZONE_ID_PREFIX)) {
            String tz = tzKey.substring(CLDRConverter.TIMEZONE_ID_PREFIX.length());
            String meta = CLDRConverter.handlerMetaZones.get(tz);
            if (meta != null) {
                return CLDRConverter.METAZONE_ID_PREFIX + meta;
            }
        }
        return null;
    }

    static List<Object[]> jreTimeZoneNames = Arrays.asList(TimeZoneNames.getContents());
    private void fillInJREs(String key, Map<String, String> map) {
        String tzid = null;

        if (key.startsWith(CLDRConverter.METAZONE_ID_PREFIX)) {
            // Look for tzid
            String meta = key.substring(CLDRConverter.METAZONE_ID_PREFIX.length());
            if (meta.equals("GMT")) {
                tzid = meta;
            } else {
                for (String tz : CLDRConverter.handlerMetaZones.keySet()) {
                    if (CLDRConverter.handlerMetaZones.get(tz).equals(meta)) {
                        tzid = tz;
                        break;
                        }
                    }
                }
        } else {
            tzid = key.substring(CLDRConverter.TIMEZONE_ID_PREFIX.length());
    }

        if (tzid != null) {
            for (Object[] jreZone : jreTimeZoneNames) {
                if (jreZone[0].equals(tzid)) {
                    for (int i = 0; i < ZONE_NAME_KEYS.length; i++) {
                        if (map.get(ZONE_NAME_KEYS[i]) == null) {
                            String[] jreNames = (String[])jreZone[1];
                            map.put(ZONE_NAME_KEYS[i], jreNames[i]);
                }
            }
                    break;
        }
    }
            }
        }

    private void convert(CalendarType calendarType, char cldrLetter, int count, StringBuilder sb) {
        switch (cldrLetter) {
        case 'G':
            if (calendarType != CalendarType.GREGORIAN) {
                // Adjust the number of 'G's for JRE SimpleDateFormat
                if (count == 5) {
                    // CLDR narrow -> JRE short
                    count = 1;
                } else if (count == 1) {
                    // CLDR abbr -> JRE long
                    count = 4;
                }
            }
            appendN(cldrLetter, count, sb);
            break;

        // TODO: support 'c' and 'e' in JRE SimpleDateFormat
        // Use 'u' and 'E' for now.
        case 'c':
        case 'e':
            switch (count) {
            case 1:
                sb.append('u');
                break;
            case 3:
            case 4:
                appendN('E', count, sb);
                break;
            case 5:
                appendN('E', 3, sb);
                break;
            }
            break;

        case 'l':
            // 'l' is deprecated as a pattern character. Should be ignored.
            break;

        case 'u':
            // Use 'y' for now.
            appendN('y', count, sb);
            break;

        case 'v':
        case 'V':
            appendN('z', count, sb);
            break;

        case 'Z':
            if (count == 4 || count == 5) {
                sb.append("XXX");
            }
            break;

        case 'U':
        case 'q':
        case 'Q':
        case 'g':
        case 'j':
        case 'A':
            throw new InternalError(String.format("Unsupported letter: '%c', count=%d, id=%s%n",
                                                  cldrLetter, count, id));
        default:
            appendN(cldrLetter, count, sb);
            break;
        }
    }

    private void appendN(char c, int n, StringBuilder sb) {
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
    }

    private static boolean hasNulls(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
                return true;
            }
        }
        return false;
    }
}
