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

package build.tools.cldrconverter;

import build.tools.cldrconverter.BundleGenerator.BundleType;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private static SupplementDataParseHandler handlerSuppl;
    static NumberingSystemsParseHandler handlerNumbering;
    static MetaZonesParseHandler handlerMetaZones;
    private static BundleGenerator bundleGenerator;

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

        bundleGenerator = new ResourceBundleGenerator();

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
        ResourceBundle.Control defCon = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);
        List<Bundle> retList = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath(SOURCE_FILE_DIR);
        try (DirectoryStream<Path> dirStr = Files.newDirectoryStream(path)) {
            for (Path entry : dirStr) {
                String fileName = entry.getFileName().toString();
                if (fileName.endsWith(".xml")) {
                    String id = fileName.substring(0, fileName.indexOf('.'));
                    Locale cldrLoc = Locale.forLanguageTag(toLanguageTag(id));
                    List<Locale> candList = defCon.getCandidateLocales("", cldrLoc);
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
                    // Insert the bundle for en at the top so that it will get
                    // processed first.
                    if ("en".equals(id)) {
                        retList.add(0, b);
                    } else {
                        retList.add(b);
                    }
                }
            }
        }
        return retList;
    }

    private static Map<String, Map<String, Object>> cldrBundles = new HashMap<>();

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

    private static void convertBundles(List<Bundle> bundles) throws Exception {
        // Parse SupplementalData file and store the information in the HashMap
        // Calendar information such as firstDay and minDay are stored in
        // supplementalData.xml as of CLDR1.4. Individual territory is listed
        // with its ISO 3166 country code while default is listed using UNM49
        // region and composition numerical code (001 for World.)
        SAXParserFactory factorySuppl = SAXParserFactory.newInstance();
        factorySuppl.setValidating(true);
        SAXParser parserSuppl = factorySuppl.newSAXParser();
        enableFileAccess(parserSuppl);
        handlerSuppl = new SupplementDataParseHandler();
        File fileSupply = new File(SPPL_SOURCE_FILE);
        parserSuppl.parse(fileSupply, handlerSuppl);

        // Parse numberingSystems to get digit zero character information.
        SAXParserFactory numberingParser = SAXParserFactory.newInstance();
        numberingParser.setValidating(true);
        SAXParser parserNumbering = numberingParser.newSAXParser();
        enableFileAccess(parserNumbering);
        handlerNumbering = new NumberingSystemsParseHandler();
        File fileNumbering = new File(NUMBERING_SOURCE_FILE);
        parserNumbering.parse(fileNumbering, handlerNumbering);

        // Parse metaZones to create mappings between Olson tzids and CLDR meta zone names
        SAXParserFactory metazonesParser = SAXParserFactory.newInstance();
        metazonesParser.setValidating(true);
        SAXParser parserMetaZones = metazonesParser.newSAXParser();
        enableFileAccess(parserMetaZones);
        handlerMetaZones = new MetaZonesParseHandler();
        File fileMetaZones = new File(METAZONES_SOURCE_FILE);
        parserNumbering.parse(fileMetaZones, handlerMetaZones);

        // For generating information on supported locales.
        Map<String, SortedSet<String>> metaInfo = new HashMap<>();
        metaInfo.put("LocaleNames", new TreeSet<String>());
        metaInfo.put("CurrencyNames", new TreeSet<String>());
        metaInfo.put("TimeZoneNames", new TreeSet<String>());
        metaInfo.put("CalendarData", new TreeSet<String>());
        metaInfo.put("FormatData", new TreeSet<String>());

        for (Bundle bundle : bundles) {
            // Get the target map, which contains all the data that should be
            // visible for the bundle's locale

            Map<String, Object> targetMap = bundle.getTargetMap();

            EnumSet<Bundle.Type> bundleTypes = bundle.getBundleTypes();

            // Fill in any missing resources in the base bundle from en and en-US data.
            // This is because CLDR root.xml is supposed to be language neutral and doesn't
            // provide some resource data. Currently, the runtime assumes that there are all
            // resources though the parent resource bundle chain.
            if (bundle.isRoot()) {
                Map<String, Object> enData = new HashMap<>();
                // Create a superset of en-US and en bundles data in order to
                // fill in any missing resources in the base bundle.
                enData.putAll(Bundle.getBundle("en").getTargetMap());
                enData.putAll(Bundle.getBundle("en_US").getTargetMap());
                for (String key : enData.keySet()) {
                    if (!targetMap.containsKey(key)) {
                        targetMap.put(key, enData.get(key));
                    }
                }
                // Add DateTimePatternChars because CLDR no longer supports localized patterns.
                targetMap.put("DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ");
            }

            // Now the map contains just the entries that need to be in the resources bundles.
            // Go ahead and generate them.
            if (bundleTypes.contains(Bundle.Type.LOCALENAMES)) {
                Map<String, Object> localeNamesMap = extractLocaleNames(targetMap, bundle.getID());
                if (!localeNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("LocaleNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "LocaleNames", bundle.getID(), true, localeNamesMap, BundleType.OPEN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.CURRENCYNAMES)) {
                Map<String, Object> currencyNamesMap = extractCurrencyNames(targetMap, bundle.getID(), bundle.getCurrencies());
                if (!currencyNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("CurrencyNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "CurrencyNames", bundle.getID(), true, currencyNamesMap, BundleType.OPEN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.TIMEZONENAMES)) {
                Map<String, Object> zoneNamesMap = extractZoneNames(targetMap, bundle.getID());
                if (!zoneNamesMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("TimeZoneNames").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "TimeZoneNames", bundle.getID(), true, zoneNamesMap, BundleType.TIMEZONE);
                }
            }
            if (bundleTypes.contains(Bundle.Type.CALENDARDATA)) {
                Map<String, Object> calendarDataMap = extractCalendarData(targetMap, bundle.getID());
                if (!calendarDataMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("CalendarData").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("util", "CalendarData", bundle.getID(), true, calendarDataMap, BundleType.PLAIN);
                }
            }
            if (bundleTypes.contains(Bundle.Type.FORMATDATA)) {
                Map<String, Object> formatDataMap = extractFormatData(targetMap, bundle.getID());
                // LocaleData.getAvailableLocales depends on having FormatData bundles around
                if (!formatDataMap.isEmpty() || bundle.isRoot()) {
                    metaInfo.get("FormatData").add(toLanguageTag(bundle.getID()));
                    bundleGenerator.generateBundle("text", "FormatData", bundle.getID(), true, formatDataMap, BundleType.PLAIN);
                }
            }

            // For testing
            SortedSet<String> allLocales = new TreeSet<>();
            allLocales.addAll(metaInfo.get("CurrencyNames"));
            allLocales.addAll(metaInfo.get("LocaleNames"));
            allLocales.addAll(metaInfo.get("CalendarData"));
            allLocales.addAll(metaInfo.get("FormatData"));
            metaInfo.put("AvailableLocales", allLocales);
        }

        bundleGenerator.generateMetaInfo(metaInfo);
    }

    /*
     * Returns the language portion of the given id.
     * If id is "root", "" is returned.
     */
    static String getLanguageCode(String id) {
        int index = id.indexOf('_');
        String lang = null;
        if (index != -1) {
            lang = id.substring(0, index);
        } else {
            lang = "root".equals(id) ? "" : id;
        }
        return lang;
    }

    /**
     * Examine if the id includes the country (territory) code. If it does, it returns
     * the country code.
     * Otherwise, it returns null. eg. when the id is "zh_Hans_SG", it return "SG".
     */
    private static String getCountryCode(String id) {
        //Truncate a variant code with '@' if there is any
        //(eg. de_DE@collation=phonebook,currency=DOM)
        if (id.indexOf('@') != -1) {
            id = id.substring(0, id.indexOf('@'));
        }
        String[] tokens = id.split("_");
        for (int index = 1; index < tokens.length; ++index) {
            if (tokens[index].length() == 2
                    && Character.isLetter(tokens[index].charAt(0))
                    && Character.isLetter(tokens[index].charAt(1))) {
                return tokens[index];
            }
        }
        return null;
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
            String prefix = calendarType.keyElementName();
            for (String element : FORMAT_DATA_ELEMENTS) {
                String key = prefix + element;
                copyIfPresent(map, "java.time." + key, formatData);
                copyIfPresent(map, key, formatData);
            }
        }
        // Workaround for islamic-umalqura name support (JDK-8015986)
        switch (id) {
        case "ar":
            map.put(CLDRConverter.CALENDAR_NAME_PREFIX
                    + CalendarType.ISLAMIC_UMALQURA.lname(),
                    // derived from CLDR 24 draft
                    "\u0627\u0644\u062a\u0642\u0648\u064a\u0645 "
                    +"\u0627\u0644\u0625\u0633\u0644\u0627\u0645\u064a "
                    +"[\u0623\u0645 \u0627\u0644\u0642\u0631\u0649]");
            break;
        case "en":
            map.put(CLDRConverter.CALENDAR_NAME_PREFIX
                    + CalendarType.ISLAMIC_UMALQURA.lname(),
                    // derived from CLDR 24 draft
                    "Islamic Calendar [Umm al-Qura]");
            break;
        }
        // Copy available calendar names
        for (String key : map.keySet()) {
            if (key.startsWith(CLDRConverter.CALENDAR_NAME_PREFIX)) {
                String type = key.substring(CLDRConverter.CALENDAR_NAME_PREFIX.length());
                for (CalendarType calendarType : CalendarType.values()) {
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
}
