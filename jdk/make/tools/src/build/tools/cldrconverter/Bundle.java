/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;

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
        "DateTimePatterns/date-time"
    };

    private final static String[] ERA_KEYS = {
        "long.Eras",
        "Eras",
        "short.Eras"
    };

    private final String id;
    private final String cldrPath;
    private final EnumSet<Type> bundleTypes;
    private final String currencies;

    static Bundle getBundle(String id) {
        return bundles.get(id);
    }

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
        String[] cldrBundles = getCLDRPath().split(",");

        // myMap contains resources for id.
        Map<String, Object> myMap = new HashMap<>();
        int index;
        for (index = 0; index < cldrBundles.length; index++) {
            if (cldrBundles[index].equals(id)) {
                myMap.putAll(CLDRConverter.getCLDRBundle(cldrBundles[index]));
                break;
            }
        }

        // parentsMap contains resources from id's parents.
        Map<String, Object> parentsMap = new HashMap<>();
        for (int i = cldrBundles.length - 1; i > index; i--) {
            if (!("no".equals(cldrBundles[i]) || cldrBundles[i].startsWith("no_"))) {
                parentsMap.putAll(CLDRConverter.getCLDRBundle(cldrBundles[i]));
            }
        }
        // Duplicate myMap as parentsMap for "root" so that the
        // fallback works. This is a huck, though.
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
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "DayNames");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "DayAbbreviations");
            handleMultipleInheritance(myMap, parentsMap, calendarPrefix + "AmPmMarkers");

            adjustEraNames(myMap, calendarType);

            handleDateTimeFormatPatterns(TIME_PATTERN_KEYS, myMap, parentsMap, calendarType, "TimePatterns");
            handleDateTimeFormatPatterns(DATE_PATTERN_KEYS, myMap, parentsMap, calendarType, "DatePatterns");
            handleDateTimeFormatPatterns(DATETIME_PATTERN_KEYS, myMap, parentsMap, calendarType, "DateTimePatterns");
        }

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
     * ERA value indexes in the Buddhist and Japanese Imperial calendars.
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
                }
                if (!key.equals(realKey)) {
                    map.put(realKey, value);
                }
            }
            realKeys[index] = realKey;
            eraNames[index++] = value;
        }
        if (eraNames[0] != null) {
            if (eraNames[1] != null) {
                if (eraNames[2] == null) {
                    // Eras -> short.Eras
                    // long.Eras -> Eras
                    map.put(realKeys[2], map.get(realKeys[1]));
                    map.put(realKeys[1], map.get(realKeys[0]));
                }
            } else {
                // long.Eras -> Eras
                map.put(realKeys[1], map.get(realKeys[0]));
            }
            // remove long.Eras
            map.remove(realKeys[0]);
        }
    }

    private void handleDateTimeFormatPatterns(String[] patternKeys, Map<String, Object> myMap, Map<String, Object> parentsMap,
                                              CalendarType calendarType, String name) {
        String calendarPrefix = calendarType.keyElementName();
        for (String k : patternKeys) {
            if (myMap.containsKey(calendarPrefix + k)) {
                int len = patternKeys.length;
                List<String> patterns = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    String key = calendarPrefix + patternKeys[i];
                    String pattern = (String) myMap.remove(key);
                    if (pattern == null) {
                        pattern = (String) parentsMap.remove(key);
                    }
                    if (pattern != null) {
                        patterns.add(i, translateDateFormatLetters(calendarType, pattern));
                    }
                }
                if (patterns.isEmpty()) {
                    return;
                }
                String key = calendarPrefix + name;
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

        case 'v':
        case 'V':
            appendN('z', count, sb);
            break;

        case 'Z':
            if (count == 4 || count == 5) {
                sb.append("XXX");
            }
            break;

        case 'u':
        case 'U':
        case 'q':
        case 'Q':
        case 'l':
        case 'g':
        case 'j':
        case 'A':
            throw new InternalError(String.format("Unsupported letter: '%c', count=%d%n",
                                                  cldrLetter, count));
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
}
