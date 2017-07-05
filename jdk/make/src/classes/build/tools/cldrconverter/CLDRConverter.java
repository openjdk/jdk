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

import static build.tools.cldrconverter.Bundle.jreTimeZoneNames;
import build.tools.cldrconverter.BundleGenerator.BundleType;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.ResourceBundle.Control;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;


/**
 * Converts locale data from "Locale Data Markup Language" format to
 * JRE resource bundle format. LDML is the format used by the Common
 * Locale Data Repository maintained by the Unicode Consortium.
 */
public class CLDRConverter {

    static final String LDML_DTD_SYSTEM_ID = "http://www.unicode.org/cldr/dtd/2.0/ldml.dtd";
    static final String SPPL_LDML_DTD_SYSTEM_ID = "http://www.unicode.org/cldr/dtd/2.0/ldmlSupplemental.dtd";

    private static String CLDR_BASE = "../CLDR/21.0.1/";
    static String LOCAL_LDML_DTD;
    static String LOCAL_SPPL_LDML_DTD;
    private static String SOURCE_FILE_DIR;
    private static String SPPL_SOURCE_FILE;
    private static String NUMBERING_SOURCE_FILE;
    private static String METAZONES_SOURCE_FILE;
    static String DESTINATION_DIR = "build/gensrc";

    static final String LOCALE_NAME_PREFIX = "locale.displayname.";
    static final String CURRENCY_SYMBOL_PREFIX = "currency.symbol.";
    static final String CURRENCY_NAME_PREFIX = "currency.displayname.";
    static final String CALENDAR_NAME_PREFIX = "calendarname.";
    static final String TIMEZONE_ID_PREFIX = "timezone.id.";
    static final String ZONE_NAME_PREFIX = "timezone.displayname.";
    static final String METAZONE_ID_PREFIX = "metazone.id.";
    static final String PARENT_LOCALE_PREFIX = "parentLocale.";

    private static SupplementDataParseHandler handlerSuppl;
    static NumberingSystemsParseHandler handlerNumbering;
    static MetaZonesParseHandler handlerMetaZones;
    private static BundleGenerator bundleGenerator;

    // java.base module related
    static boolean isBaseModule = false;
    static final Set<Locale> BASE_LOCALES = new HashSet<>();

    // "parentLocales" map
    private static final Map<String, SortedSet<String>> parentLocalesMap = new HashMap<>();
    private static final ResourceBundle.Control defCon =
        ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

    static enum DraftType {
        UNCONFIRMED,
        PROVISIONAL,
        CONTRIBUTED,
        APPROVED;

        private static final Map<String, DraftType> map = new HashMap<>();
        static {
            for (DraftType dt : values()) {
                map.put(dt.getKeyword(), dt);
            }
        }
        static private DraftType defaultType = CONTRIBUTED;

        private final String keyword;

        private DraftType() {
            keyword = this.name().toLowerCase(Locale.ROOT);

        }

        static DraftType forKeyword(String keyword) {
            return map.get(keyword);
        }

        static DraftType getDefault() {
            return defaultType;
        }

        static void setDefault(String keyword) {
            defaultType = Objects.requireNonNull(forKeyword(keyword));
        }

        String getKeyword() {
            return keyword;
        }
    }

    static boolean USE_UTF8 = false;
    private static boolean verbose;

    private CLDRConverter() {
       // no instantiation
    }

    @SuppressWarnings("AssignmentToForLoopParameter")
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            String currentArg = null;
            try {
                for (int i = 0; i < args.length; i++) {
                    currentArg = args[i];
                    switch (currentArg) {
                    case "-draft":
                        String draftDataType = args[++i];
                        try {
                            DraftType.setDefault(draftDataType);
                        } catch (NullPointerException e) {
                            severe("Error: incorrect draft value: %s%n", draftDataType);
                            System.exit(1);
                        }
                        info("Using the specified data type: %s%n", draftDataType);
                        break;

                    case "-base":
                        // base directory for input files
                        CLDR_BASE = args[++i];
                        if (!CLDR_BASE.endsWith("/")) {
                            CLDR_BASE += "/";
                        }
                        break;

                    case "-baselocales":
                        // base locales
                        setupBaseLocales(args[++i]);
                        break;

                    case "-basemodule":
                        // indicates java.base module resource generation
                        isBaseModule = true;
                        break;

                    case "-o":
                        // output directory
                        DESTINATION_DIR = args[++i];
                        break;

                    case "-utf8":
                        USE_UTF8 = true;
                        break;

                    case "-verbose":
                        verbose = true;
                        break;

                    case "-help":
                        usage();
                        System.exit(0);
                        break;

                    default:
                        throw new RuntimeException();
                    }
                }
            } catch (RuntimeException e) {
                severe("unknown or imcomplete arg(s): " + currentArg);
                usage();
                System.exit(1);
            }
        }

        // Set up path names
        LOCAL_LDML_DTD = CLDR_BASE + "common/dtd/ldml.dtd";
        LOCAL_SPPL_LDML_DTD = CLDR_BASE + "common/dtd/ldmlSupplemental.dtd";
        SOURCE_FILE_DIR = CLDR_BASE + "common/main";
        SPPL_SOURCE_FILE = CLDR_BASE + "common/supplemental/supplementalData.xml";
        NUMBERING_SOURCE_FILE = CLDR_BASE + "common/supplemental/numberingSystems.xml";
        METAZONES_SOURCE_FILE = CLDR_BASE + "common/supplemental/metaZones.xml";

        if (BASE_LOCALES.isEmpty()) {
            setupBaseLocales("en-US");
        }

        bundleGenerator = new ResourceBundleGenerator();

        // Parse data independent of locales
        parseSupplemental();

        List<Bundle> bundles = readBundleList();
        convertBundles(bundles);
    }

    private static void usage() {
        errout("Usage: java CLDRConverter [options]%n"
                + "\t-help          output this usage message and exit%n"
                + "\t-verbose       output information%n"
                + "\t-draft [approved | provisional | unconfirmed]%n"
                + "\t\t       draft level for using data (default: approved)%n"
                + "\t-base dir      base directory for CLDR input files%n"
                + "\t-basemodule    generates bundles that go into java.base module%n"
                + "\t-baselocales loc(,loc)*      locales that go into the base module%n"
                + "\t-o dir         output directory (default: ./build/gensrc)%n"
                + "\t-o dir         output directory (defaut: ./build/gensrc)%n"
                + "\t-utf8          use UTF-8 rather than \\uxxxx (for debug)%n");
    }

    static void info(String fmt, Object... args) {
        if (verbose) {
            System.out.printf(fmt, args);
        }
    }

    static void info(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }

    static void warning(String fmt, Object... args) {
        System.err.print("Warning: ");
        System.err.printf(fmt, args);
    }

    static void warning(String msg) {
        System.err.print("Warning: ");
        errout(msg);
    }

    static void severe(String fmt, Object... args) {
        System.err.print("Error: ");
        System.err.printf(fmt, args);
    }

    static void severe(String msg) {
        System.err.print("Error: ");
        errout(msg);
    }

    private static void errout(String msg) {
        if (msg.contains("%n")) {
            System.err.printf(msg);
        } else {
            System.err.println(msg);
        }
    }

    /**
     * Configure the parser to allow access to DTDs on the file system.
     */
    private static void enableFileAccess(SAXParser parser) throws SAXNotSupportedException {
        try {
            parser.setProperty("http://javax.xml.XMLConstants/property/accessExternalDTD", "file");
        } catch (SAXNotRecognizedException ignore) {
            // property requires >= JAXP 1.5
        }
    }

    private static List<Bundle> readBundleList() throws Exception {
        List<Bundle> retList = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath(SOURCE_FILE_DIR);
        try (DirectoryStream<Path> dirStr = Files.newDirectoryStream(path)) {
            for (Path entry : dirStr) {
                String fileName = entry.getFileName().toString();
                if (fileName.endsWith(".xml")) {
                    String id = fileName.substring(0, fileName.indexOf('.'));
                    Locale cldrLoc = Locale.forLanguageTag(toLanguageTag(id));
                    List<Locale> candList = applyParentLocales("", defCon.getCandidateLocales("", cldrLoc));
                    StringBuilder sb = new StringBuilder();
                    for (Locale loc : candList) {
                        if (!loc.equals(Locale.ROOT)) {
                            sb.append(toLocaleName(loc.toLanguageTag()));
                            sb.append(",");
                        }
                    }
                    if (sb.indexOf("root") == -1) {
                        sb.append("root");
                    }
                    Bundle b = new Bundle(id, sb.toString(), null, null);
                    // Insert the bundle for root at the top so that it will get
                    // processed first.
                    if ("root".equals(id)) {
                        retList.add(0, b);
                    } else {
                        retList.add(b);
                    }
                }
            }
        }
        return retList;
    }

    private static final Map<String, Map<String, Object>> cldrBundles = new HashMap<>();

    static Map<String, Object> getCLDRBundle(String id) throws Exception {
        Map<String, Object> bundle = cldrBundles.get(id);
        if (bundle != null) {
            return bundle;
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        SAXParser parser = factory.newSAXParser();
        enableFileAccess(parser);
        LDMLParseHandler handler = new LDMLParseHandler(id);
        File file = new File(SOURCE_FILE_DIR + File.separator + id + ".xml");
        if (!file.exists()) {
            // Skip if the file doesn't exist.
            return Collections.emptyMap();
        }

        info("..... main directory .....");
        info("Reading file " + file);
        parser.parse(file, handler);

        bundle = handler.getData();
        cldrBundles.put(id, bundle);
        String country = getCountryCode(id);
        if (country != null) {
            bundle = handlerSuppl.getData(country);
            if (bundle != null) {
                //merge two maps into one map
                Map<String, Object> temp = cldrBundles.remove(id);
                bundle.putAll(temp);
                cldrBundles.put(id, bundle);
            }
        }
        return bundle;
    }

    // Parsers for data in "supplemental" directory
    //
    private static void parseSupplemental() throws Exception {
        // Parse SupplementalData file and store the information in the HashMap
        // Calendar information such as firstDay and minDay are stored in
        // supplementalData.xml as of CLDR1.4. Individual territory is listed
        // with its ISO 3166 country code while default is listed using UNM49
        // region and composition numerical code (001 for World.)
        //
        // SupplementalData file also provides the "parent" locales which
        // are othrwise not to be fallen back. Process them here as well.
        //
        info("..... Parsing supplementalData.xml .....");
        SAXParserFactory factorySuppl = SAXParserFactory.newInstance();
        factorySuppl.setValidating(true);
        SAXParser parserSuppl = factorySuppl.newSAXParser();
        enableFileAccess(parserSuppl);
        handlerSuppl = new SupplementDataParseHandler();
        File fileSupply = new File(SPPL_SOURCE_FILE);
        parserSuppl.parse(fileSupply, handlerSuppl);
        Map<String, Object> parentData = handlerSuppl.getData("root");
        parentData.keySet().forEach(key -> {
                parentLocalesMap.put(key, new TreeSet(
                    Arrays.asList(((String)parentData.get(key)).split(" "))));
            });

        // Parse numberingSystems to get digit zero character information.
        info("..... Parsing numberingSystem.xml .....");
        SAXParserFactory numberingParser = SAXParserFactory.newInstance();
        numberingParser.setValidating(true);
        SAXParser parserNumbering = numberingParser.newSAXParser();
        enableFileAccess(parserNumbering);
        handlerNumbering = new NumberingSystemsParseHandler();
        File fileNumbering = new File(NUMBERING_SOURCE_FILE);
        parserNumbering.parse(fileNumbering, handlerNumbering);

        // Parse metaZones to create mappings between Olson tzids and CLDR meta zone names
        info("..... Parsing metaZones.xml .....");
        SAXParserFactory metazonesParser = SAXParserFactory.newInstance();
        metazonesParser.setValidating(true);
        SAXParser parserMetaZones = metazonesParser.newSAXParser();
        enableFileAccess(parserMetaZones);
        handlerMetaZones = new MetaZonesParseHandler();
        File fileMetaZones = new File(METAZONES_SOURCE_FILE);
        parserNumbering.parse(fileMetaZones, handlerMetaZones);
    }

    private static void convertBundles(List<Bundle> bundles) throws Exception {
        // For generating information on supported locales.
        Map<String, SortedSet<String>> metaInfo = new HashMap<>();
        metaInfo.put("LocaleNames", new TreeSet<>());
        metaInfo.put("CurrencyNames", new TreeSet<>());
        metaInfo.put("TimeZoneNames", new TreeSet<>());
        metaInfo.put("CalendarData", new TreeSet<>());
        metaInfo.put("FormatData", new TreeSet<>());
        metaInfo.put("AvailableLocales", new TreeSet<>());

        // parent locales map. The mappings are put in base metaInfo file
        // for now.
        if (isBaseModule) {
            metaInfo.putAll(parentLocalesMap);
        }

        for (Bundle bundle : bundles) {
            // Get the target map, which contains all the data that should be
            // visible for the bundle's locale

            Map<String, Object> targetMap = bundle.getTargetMap();

            EnumSet<Bundle.Type> bundleTypes = bundle.getBundleTypes();

            if (bundle.isRoot()) {
                // Add DateTimePatternChars because CLDR no longer supports localized patterns.
                targetMap.put("DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ");
            }

            // Now the map contains just the entries that need to be in the resources bundles.
            // Go ahead and generate them.
            if (bundleTypes.contains(Bundle.Type.LOCALENAMES)) {
                Map<String, Object> localeNamesMap = extractLocaleNames(targetMap, bundle.getID());
                if (!localeNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("LocaleNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "LocaleNames", bundle.getJavaID(), true, localeNamesMap, BundleType.OPEN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.CURRENCYNAMES)) {
                Map<String, Object> currencyNamesMap = extractCurrencyNames(targetMap, bundle.getID(), bundle.getCurrencies());
                if (!currencyNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("CurrencyNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "CurrencyNames", bundle.getJavaID(), true, currencyNamesMap, BundleType.OPEN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.TIMEZONENAMES)) {
                Map<String, Object> zoneNamesMap = extractZoneNames(targetMap, bundle.getID());
                if (!zoneNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("TimeZoneNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "TimeZoneNames", bundle.getJavaID(), true, zoneNamesMap, BundleType.TIMEZONE);
                }
            }
            if (bundleTypes.contains(Bundle.Type.CALENDARDATA)) {
                Map<String, Object> calendarDataMap = extractCalendarData(targetMap, bundle.getID());
                if (!calendarDataMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("CalendarData").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "CalendarData", bundle.getJavaID(), true, calendarDataMap, BundleType.PLAIN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.FORMATDATA)) {
                Map<String, Object> formatDataMap = extractFormatData(targetMap, bundle.getID());
                if (!formatDataMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("FormatData").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("text", "FormatData", bundle.getJavaID(), true, formatDataMap, BundleType.PLAIN);
                }
            }

            // For AvailableLocales
            metaInfo.get("AvailableLocales").add(toLanguageTag(bundle.getID()));
        }

        bundleGenerator.generateMetaInfo(metaInfo);
    }

    static final Map<String, String> aliases = new HashMap<>();

    /**
     * Translate the aliases into the real entries in the bundle map.
     */
    static void handleAliases(Map<String, Object> bundleMap) {
        Set bundleKeys = bundleMap.keySet();
        try {
            for (String key : aliases.keySet()) {
                String targetKey = aliases.get(key);
                if (bundleKeys.contains(targetKey)) {
                    bundleMap.putIfAbsent(key, bundleMap.get(targetKey));
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(CLDRConverter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Returns the language portion of the given id.
     * If id is "root", "" is returned.
     */
    static String getLanguageCode(String id) {
        return "root".equals(id) ? "" : Locale.forLanguageTag(id.replaceAll("_", "-")).getLanguage();
    }

    /**
     * Examine if the id includes the country (territory) code. If it does, it returns
     * the country code.
     * Otherwise, it returns null. eg. when the id is "zh_Hans_SG", it return "SG".
     * It does NOT return UN M.49 code, e.g., '001', as those three digit numbers cannot
     * be translated into package names.
     */
    static String getCountryCode(String id) {
        String rgn = getRegionCode(id);
        return rgn.length() == 2 ? rgn: null;
    }

    /**
     * Examine if the id includes the region code. If it does, it returns
     * the region code.
     * Otherwise, it returns null. eg. when the id is "zh_Hans_SG", it return "SG".
     * It DOES return UN M.49 code, e.g., '001', as well as ISO 3166 two letter country codes.
     */
    static String getRegionCode(String id) {
        return Locale.forLanguageTag(id.replaceAll("_", "-")).getCountry();
    }

    private static class KeyComparator implements Comparator<String> {
        static KeyComparator INSTANCE = new KeyComparator();

        private KeyComparator() {
        }

        @Override
        public int compare(String o1, String o2) {
            int len1 = o1.length();
            int len2 = o2.length();
            if (!isDigit(o1.charAt(0)) && !isDigit(o2.charAt(0))) {
                // Shorter string comes first unless either starts with a digit.
                if (len1 < len2) {
                    return -1;
                }
                if (len1 > len2) {
                    return 1;
                }
            }
            return o1.compareTo(o2);
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }

    private static Map<String, Object> extractLocaleNames(Map<String, Object> map, String id) {
        Map<String, Object> localeNames = new TreeMap<>(KeyComparator.INSTANCE);
        for (String key : map.keySet()) {
            if (key.startsWith(LOCALE_NAME_PREFIX)) {
                localeNames.put(key.substring(LOCALE_NAME_PREFIX.length()), map.get(key));
            }
        }
        return localeNames;
    }

    @SuppressWarnings("AssignmentToForLoopParameter")
    private static Map<String, Object> extractCurrencyNames(Map<String, Object> map, String id, String names)
            throws Exception {
        Map<String, Object> currencyNames = new TreeMap<>(KeyComparator.INSTANCE);
        for (String key : map.keySet()) {
            if (key.startsWith(CURRENCY_NAME_PREFIX)) {
                currencyNames.put(key.substring(CURRENCY_NAME_PREFIX.length()), map.get(key));
            } else if (key.startsWith(CURRENCY_SYMBOL_PREFIX)) {
                currencyNames.put(key.substring(CURRENCY_SYMBOL_PREFIX.length()), map.get(key));
            }
        }
        return currencyNames;
    }

    private static Map<String, Object> extractZoneNames(Map<String, Object> map, String id) {
        Map<String, Object> names = new HashMap<>();

        // Copy over missing time zone ids from JRE for English locale
        if (id.equals("en")) {
            Map<String[], String> jreMetaMap = new HashMap<>();
            jreTimeZoneNames.stream().forEach(e -> {
                String tzid = (String)e[0];
                String[] data = (String[])e[1];

                if (map.get(TIMEZONE_ID_PREFIX + tzid) == null &&
                    handlerMetaZones.get(tzid) == null) {
                    // First, check the CLDR meta key
                    Optional<Map.Entry<String, String>> cldrMeta =
                        handlerMetaZones.getData().entrySet().stream()
                            .filter(me ->
                                Arrays.deepEquals(data,
                                    (String[])map.get(METAZONE_ID_PREFIX + me.getValue())))
                            .findAny();
                    if (cldrMeta.isPresent()) {
                        names.put(tzid, cldrMeta.get().getValue());
                    } else {
                        // check the JRE meta key, add if there is not.
                        Optional<Map.Entry<String[], String>> jreMeta =
                            jreMetaMap.entrySet().stream()
                                .filter(jm -> Arrays.deepEquals(data, jm.getKey()))
                                .findAny();
                        if (jreMeta.isPresent()) {
                            names.put(tzid, jreMeta.get().getValue());
                        } else {
                            String metaName = "JRE_" + tzid.replaceAll("[/-]", "_");
                            names.put(METAZONE_ID_PREFIX + metaName, data);
                            names.put(tzid, metaName);
                            jreMetaMap.put(data, metaName);
                        }
                    }
                }
            });
        }

        for (String tzid : handlerMetaZones.keySet()) {
            String tzKey = TIMEZONE_ID_PREFIX + tzid;
            Object data = map.get(tzKey);
            if (data instanceof String[]) {
                names.put(tzid, data);
            } else {
                String meta = handlerMetaZones.get(tzid);
                if (meta != null) {
                    String metaKey = METAZONE_ID_PREFIX + meta;
                    data = map.get(metaKey);
                    if (data instanceof String[]) {
                        // Keep the metazone prefix here.
                        names.put(metaKey, data);
                        names.put(tzid, meta);
                    }
                }
            }
        }
        return names;
    }

    private static Map<String, Object> extractCalendarData(Map<String, Object> map, String id) {
        Map<String, Object> calendarData = new LinkedHashMap<>();
        copyIfPresent(map, "firstDayOfWeek", calendarData);
        copyIfPresent(map, "minimalDaysInFirstWeek", calendarData);
        return calendarData;
    }

    static final String[] FORMAT_DATA_ELEMENTS = {
        "MonthNames",
        "standalone.MonthNames",
        "MonthAbbreviations",
        "standalone.MonthAbbreviations",
        "MonthNarrows",
        "standalone.MonthNarrows",
        "DayNames",
        "standalone.DayNames",
        "DayAbbreviations",
        "standalone.DayAbbreviations",
        "DayNarrows",
        "standalone.DayNarrows",
        "QuarterNames",
        "standalone.QuarterNames",
        "QuarterAbbreviations",
        "standalone.QuarterAbbreviations",
        "QuarterNarrows",
        "standalone.QuarterNarrows",
        "AmPmMarkers",
        "narrow.AmPmMarkers",
        "long.Eras",
        "Eras",
        "narrow.Eras",
        "field.era",
        "field.year",
        "field.month",
        "field.week",
        "field.weekday",
        "field.dayperiod",
        "field.hour",
        "field.minute",
        "field.second",
        "field.zone",
        "TimePatterns",
        "DatePatterns",
        "DateTimePatterns",
        "DateTimePatternChars"
    };

    private static Map<String, Object> extractFormatData(Map<String, Object> map, String id) {
        Map<String, Object> formatData = new LinkedHashMap<>();
        for (CalendarType calendarType : CalendarType.values()) {
            if (calendarType == CalendarType.GENERIC) {
                continue;
            }
            String prefix = calendarType.keyElementName();
            for (String element : FORMAT_DATA_ELEMENTS) {
                String key = prefix + element;
                copyIfPresent(map, "java.time." + key, formatData);
                copyIfPresent(map, key, formatData);
            }
        }

        for (String key : map.keySet()) {
        // Copy available calendar names
            if (key.startsWith(CLDRConverter.CALENDAR_NAME_PREFIX)) {
                String type = key.substring(CLDRConverter.CALENDAR_NAME_PREFIX.length());
                for (CalendarType calendarType : CalendarType.values()) {
                    if (calendarType == CalendarType.GENERIC) {
                        continue;
                    }
                    if (type.equals(calendarType.lname())) {
                        Object value = map.get(key);
                        formatData.put(key, value);
                        String ukey = CLDRConverter.CALENDAR_NAME_PREFIX + calendarType.uname();
                        if (!key.equals(ukey)) {
                            formatData.put(ukey, value);
                        }
                    }
                }
            }
        }

        copyIfPresent(map, "DefaultNumberingSystem", formatData);

        @SuppressWarnings("unchecked")
        List<String> numberingScripts = (List<String>) map.remove("numberingScripts");
        if (numberingScripts != null) {
            for (String script : numberingScripts) {
                copyIfPresent(map, script + "." + "NumberElements", formatData);
            }
        } else {
            copyIfPresent(map, "NumberElements", formatData);
        }
        copyIfPresent(map, "NumberPatterns", formatData);
        return formatData;
    }

    private static void copyIfPresent(Map<String, Object> src, String key, Map<String, Object> dest) {
        Object value = src.get(key);
        if (value != null) {
            dest.put(key, value);
        }
    }

    // --- code below here is adapted from java.util.Properties ---
    private static final String specialSaveCharsJava = "\"";
    private static final String specialSaveCharsProperties = "=: \t\r\n\f#!";

    /*
     * Converts unicodes to encoded &#92;uxxxx
     * and writes out any of the characters in specialSaveChars
     * with a preceding slash
     */
    static String saveConvert(String theString, boolean useJava) {
        if (theString == null) {
            return "";
        }

        String specialSaveChars;
        if (useJava) {
            specialSaveChars = specialSaveCharsJava;
        } else {
            specialSaveChars = specialSaveCharsProperties;
        }
        boolean escapeSpace = false;

        int len = theString.length();
        StringBuilder outBuffer = new StringBuilder(len * 2);
        Formatter formatter = new Formatter(outBuffer, Locale.ROOT);

        for (int x = 0; x < len; x++) {
            char aChar = theString.charAt(x);
            switch (aChar) {
            case ' ':
                if (x == 0 || escapeSpace) {
                    outBuffer.append('\\');
                }
                outBuffer.append(' ');
                break;
            case '\\':
                outBuffer.append('\\');
                outBuffer.append('\\');
                break;
            case '\t':
                outBuffer.append('\\');
                outBuffer.append('t');
                break;
            case '\n':
                outBuffer.append('\\');
                outBuffer.append('n');
                break;
            case '\r':
                outBuffer.append('\\');
                outBuffer.append('r');
                break;
            case '\f':
                outBuffer.append('\\');
                outBuffer.append('f');
                break;
            default:
                if (aChar < 0x0020 || (!USE_UTF8 && aChar > 0x007e)) {
                    formatter.format("\\u%04x", (int)aChar);
                } else {
                    if (specialSaveChars.indexOf(aChar) != -1) {
                        outBuffer.append('\\');
                    }
                    outBuffer.append(aChar);
                }
            }
        }
        return outBuffer.toString();
    }

    private static String toLanguageTag(String locName) {
        if (locName.indexOf('_') == -1) {
            return locName;
        }
        String tag = locName.replaceAll("_", "-");
        Locale loc = Locale.forLanguageTag(tag);
        return loc.toLanguageTag();
    }

    private static String toLocaleName(String tag) {
        if (tag.indexOf('-') == -1) {
            return tag;
        }
        return tag.replaceAll("-", "_");
    }

    private static void setupBaseLocales(String localeList) {
        Arrays.stream(localeList.split(","))
            .map(Locale::forLanguageTag)
            .map(l -> Control.getControl(Control.FORMAT_DEFAULT)
                             .getCandidateLocales("", l))
            .forEach(BASE_LOCALES::addAll);
}

    // applying parent locale rules to the passed candidates list
    // This has to match with the one in sun.util.cldr.CLDRLocaleProviderAdapter
    private static Map<Locale, Locale> childToParentLocaleMap = null;
    private static List<Locale> applyParentLocales(String baseName, List<Locale> candidates) {
        if (Objects.isNull(childToParentLocaleMap)) {
            childToParentLocaleMap = new HashMap<>();
            parentLocalesMap.keySet().forEach(key -> {
                String parent = key.substring(PARENT_LOCALE_PREFIX.length()).replaceAll("_", "-");
                parentLocalesMap.get(key).stream().forEach(child -> {
                    childToParentLocaleMap.put(Locale.forLanguageTag(child),
                        "root".equals(parent) ? Locale.ROOT : Locale.forLanguageTag(parent));
                });
            });
        }

        // check irregular parents
        for (int i = 0; i < candidates.size(); i++) {
            Locale l = candidates.get(i);
            Locale p = childToParentLocaleMap.get(l);
            if (!l.equals(Locale.ROOT) &&
                Objects.nonNull(p) &&
                !candidates.get(i+1).equals(p)) {
                List<Locale> applied = candidates.subList(0, i+1);
                applied.addAll(applyParentLocales(baseName, defCon.getCandidateLocales(baseName, p)));
                return applied;
            }
        }

        return candidates;
    }
}
