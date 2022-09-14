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

package build.tools.cldrconverter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Handles parsing of files in Locale Data Markup Language for SupplementData.xml
 * and produces a map that uses the keys and values of JRE locale data.
 */

class SupplementDataParseHandler extends AbstractLDMLHandler<Object> {
    //UNM49 region and composition code used in supplementalData.xml
    private static final String WORLD = "001";

    private static final String JAVA_FIRSTDAY = "firstDayOfWeek";
    private static final String JAVA_MINDAY = "minimalDaysInFirstWeek";

    // The weekData is now in supplementalData.xml,
    // which is not a locale specific file.
    // Map for JRE is created locale specific way.
    // When parsing the locale neutral file (supplementalData.xml),
    // we need to rely on the country code because
    // the weekData is listed using country code.
    //
    // weekData are generated per each country
    private final Map<String, Object> firstDayMap;
    private final Map<String, Object> minDaysMap;

    // Parent locales. These information will only be
    // generated towards the base meta info, with the format of
    //
    // parentLocale.<parent_locale_id>=<child_locale_id>(" "<child_locale_id>)+
    private final Map<String, String> parentLocalesMap;

    // Input Skeleton map for "preferred" and "allowed"
    // Map<"preferred"/"allowed", Map<"skeleton", SortedSet<"regions">>>
    private final Map<String, Map<String, SortedSet<String>>> inputSkeletonMap;

    SupplementDataParseHandler() {
        firstDayMap = new HashMap<>();
        minDaysMap = new HashMap<>();
        parentLocalesMap = new HashMap<>();
        inputSkeletonMap = new HashMap<>();
    }

    /**
     * It returns Map that contains the firstDay and minDays information for
     * the country. The Map is created in JRE format after obtaining the data
     * from two Maps, firstDayMap and minDaysMap.
     *
     * It returns null when there is no firstDay and minDays for the country
     * although this should not happen because supplementalData.xml includes
     * default value for the world ("001") for firstDay and minDays.
     *
     * This method also returns Maps for "preferred" and "allowed" skeletons,
     * which are grouped by regions. E.g, "h:XX YY ZZ;" which means 'h' pattern
     * is "preferred"/"allowed" in "XX", "YY", and "ZZ" regions.
     */
    Map<String, Object> getData(String id) {
        Map<String, Object> values = new HashMap<>();
        if ("root".equals(id)) {
            parentLocalesMap.forEach((k, v) -> values.put(CLDRConverter.PARENT_LOCALE_PREFIX + k, v));
            firstDayMap.forEach((k, v) -> values.put(CLDRConverter.CALENDAR_FIRSTDAY_PREFIX + v, k));
            minDaysMap.forEach((k, v) -> values.put(CLDRConverter.CALENDAR_MINDAYS_PREFIX + v, k));
            inputSkeletonMap.get("preferred").forEach((k, v) ->
                    values.merge(Bundle.DATEFORMATITEM_INPUT_REGIONS_PREFIX + "preferred",
                            k + ":" + v.stream().collect(Collectors.joining(" ")) + ";",
                            (old, newVal) -> old + (String)newVal));
            inputSkeletonMap.get("allowed").forEach((k, v) ->
                    values.merge(Bundle.DATEFORMATITEM_INPUT_REGIONS_PREFIX + "allowed",
                            k + ":" + v.stream().collect(Collectors.joining(" ")) + ";",
                            (old, newVal) -> old + (String)newVal));
        }
        return values.isEmpty() ? null : values;
    }

    @Override
    public InputSource resolveEntity(String publicID, String systemID) throws IOException, SAXException {
        // avoid HTTP traffic to unicode.org
        if (systemID.startsWith(CLDRConverter.SPPL_LDML_DTD_SYSTEM_ID)) {
            return new InputSource((new File(CLDRConverter.LOCAL_SPPL_LDML_DTD)).toURI().toString());
        }
        return null;
    }

    /**
     * JRE requires all the data to be organized by the locale while CLDR 1.4 list
     * Calendar related data (weekData)in SupplementalData.xml.
     * startElement stores JRE required data into two Maps,
     * firstDayMap and minDaysMap.
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // elements we need to actively ignore
        switch (qName) {
        case "firstDay":
            if (!isIgnored(attributes)) {
                String fd;

                switch (attributes.getValue("day")) {
                    case "sun":
                        fd = "1";
                        break;
                    default:
                    case "mon":
                        fd = "2";
                        break;
                    case "tue":
                        fd = "3";
                        break;
                    case "wed":
                        fd = "4";
                        break;
                    case "thu":
                        fd = "5";
                        break;
                    case "fri":
                        fd = "6";
                        break;
                    case "sat":
                        fd = "7";
                        break;
                }
                firstDayMap.put(attributes.getValue("territories"), fd);
            }
            break;
        case "minDays":
            if (!isIgnored(attributes)) {
                minDaysMap.put(attributes.getValue("territories"), attributes.getValue("count"));
            }
            break;
        case "parentLocale":
            if (!isIgnored(attributes)) {
                parentLocalesMap.put(
                    attributes.getValue("parent").replaceAll("_", "-"),
                    attributes.getValue("locales").replaceAll("_", "-"));
            }
            break;
        case "hours":
            if (!isIgnored(attributes)) {
                var preferred = attributes.getValue("preferred");
                var allowed = attributes.getValue("allowed").replaceFirst(" .*", "").replaceFirst("b", "B"); // take only the first one, "b" -> "B"
                var regions = Arrays.stream(attributes.getValue("regions").split(" "))
                        .map(r -> r.replaceAll("_", "-"))
                        .collect(Collectors.toSet());
                var pmap = inputSkeletonMap.computeIfAbsent("preferred", k -> new HashMap<>());
                var amap = inputSkeletonMap.computeIfAbsent("allowed", k -> new HashMap<>());
                pmap.computeIfAbsent(preferred, k -> new TreeSet<>()).addAll(regions);
                amap.computeIfAbsent(allowed, k -> new TreeSet<>()).addAll(regions);
            }
            break;
        default:
            // treat anything else as a container
            pushContainer(qName, attributes);
            break;
        }
    }
}
