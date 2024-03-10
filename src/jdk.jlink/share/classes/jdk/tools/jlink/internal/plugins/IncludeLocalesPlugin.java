/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static java.util.ResourceBundle.Control;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.lang.classfile.ClassFile.*;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.plugin.ResourcePoolModule;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import sun.util.cldr.CLDRBaseLocaleDataMetaInfo;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
import sun.util.locale.provider.ResourceBundleBasedAdapter;

/**
 * Plugin to explicitly specify the locale data included in jdk.localedata
 * module. This plugin provides a jlink command line option "--include-locales"
 * with an argument. The argument is a list of BCP 47 language tags separated
 * by a comma. E.g.,
 *
 *  "jlink --include-locales en,ja,*-IN"
 *
 * This option will include locale data for all available English and Japanese
 * languages, and ones for the country of India. All other locale data are
 * filtered out on the image creation.
 *
 * Here are a few assumptions:
 *
 *  0. All locale data in java.base are unconditionally included.
 *  1. All the selective locale data are in jdk.localedata module
 *  2. Their package names are constructed by appending ".ext" to
 *     the corresponding ones in java.base module.
 *  3. Available locales string in LocaleDataMetaInfo class should
 *     start with at least one white space character, e.g., " ar ar-EG ..."
 *                                                           ^
 */
public final class IncludeLocalesPlugin extends AbstractPlugin implements ResourcePrevisitor {

    private static final String MODULENAME = "jdk.localedata";
    private static final Set<String> LOCALEDATA_PACKAGES = Set.of(
        "sun.text.resources.cldr.ext",
        "sun.text.resources.ext",
        "sun.util.resources.cldr.ext",
        "sun.util.resources.cldr.provider",
        "sun.util.resources.ext",
        "sun.util.resources.provider");
    private static final String METAINFONAME = "LocaleDataMetaInfo";
    private static final List<String> META_FILES = List.of(
        ".+module-info.class",
        ".+LocaleDataProvider.class",
        ".+" + METAINFONAME + ".class");
    private static final List<String> INCLUDE_LOCALE_FILES = List.of(
        ".+sun/text/resources/ext/[^_]+_",
        ".+sun/util/resources/ext/[^_]+_",
        ".+sun/text/resources/cldr/ext/[^_]+_",
        ".+sun/util/resources/cldr/ext/[^_]+_");
    private Predicate<String> predicate;
    private String userParam;
    private List<Locale.LanguageRange> priorityList;

    private static final ResourceBundleBasedAdapter CLDR_ADAPTER =
        (ResourceBundleBasedAdapter)LocaleProviderAdapter.forType(Type.CLDR);
    private static final Map<Locale, String[]> CLDR_PARENT_LOCALES =
        new CLDRBaseLocaleDataMetaInfo().parentLocales();

    // Equivalent map
    private static final Map<String, List<String>> EQUIV_MAP =
        Stream.concat(
            // COMPAT equivalence
            Map.of(
                "zh-Hans", List.of("zh-Hans", "zh-CN", "zh-SG"),
                "zh-Hant", List.of("zh-Hant", "zh-HK", "zh-MO", "zh-TW"))
                .entrySet()
                .stream(),

            // CLDR parent locales
            CLDR_PARENT_LOCALES.entrySet().stream()
                .map(entry -> {
                    String parent = entry.getKey().toLanguageTag();
                    List<String> children = new ArrayList<>();
                    children.add(parent);

                    Arrays.stream(entry.getValue())
                        .filter(child -> !child.isEmpty())
                        .flatMap(child ->
                            Stream.concat(
                                Arrays.stream(CLDR_PARENT_LOCALES.getOrDefault(
                                    Locale.forLanguageTag(child), new String[0]))
                                        .filter(grandchild -> !grandchild.isEmpty()),
                                Stream.of(child)))
                        .distinct()
                        .forEach(children::add);
                    return new AbstractMap.SimpleEntry<>(parent, children);
                })
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).distinct().toList()));

    // Special COMPAT provider locales
    private static final String jaJPJPTag = "ja-JP-JP";
    private static final String noNONYTag = "no-NO-NY";
    private static final String thTHTHTag = "th-TH-TH";
    private static final Locale jaJPJP = Locale.of("ja", "JP", "JP");
    private static final Locale noNONY = Locale.of("no", "NO", "NY");
    private static final Locale thTHTH = Locale.of("th", "TH", "TH");

    public IncludeLocalesPlugin() {
        super("include-locales");
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        in.transformAndCopy((resource) -> {
            if (resource.moduleName().equals(MODULENAME)) {
                String path = resource.path();
                resource = predicate.test(path) ? resource: null;
                if (resource != null &&
                    resource.type().equals(ResourcePoolEntry.Type.CLASS_OR_RESOURCE) &&
                    path.endsWith(".class")) {
                    byte[] bytes = resource.contentBytes();
                    if (newClassReader(path, bytes).interfaces().stream()
                        .anyMatch(i -> i.asInternalName().contains(METAINFONAME)) &&
                        stripUnsupportedLocales(bytes)) {
                        resource = resource.copyWithContent(bytes);
                    }
                }
            }
            return resource;
        }, out);

        return out.build();
    }

    @Override
    public Category getType() {
        return Category.FILTER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        userParam = config.get(getName());

        try {
            priorityList = Locale.LanguageRange.parse(userParam, EQUIV_MAP);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException(String.format(
                PluginsResourceBundle.getMessage(getName() + ".invalidtag"),
                    iae.getMessage().replaceFirst("^range=", "")));
        }
    }

    @Override
    public void previsit(ResourcePool resources, StringTable strings) {
        final Pattern p = Pattern.compile(".*((Data_)|(Names_))(?<tag>.*)\\.class");
        Optional<ResourcePoolModule> optMod = resources.moduleView().findModule(MODULENAME);

        // jdk.localedata module validation
        List<Locale> available;
        if (optMod.isPresent()) {
            ResourcePoolModule module = optMod.get();
            Set<String> packages = module.packages();
            if (!packages.containsAll(LOCALEDATA_PACKAGES)) {
                throw new PluginException(PluginsResourceBundle.getMessage(getName()+ ".missingpackages") +
                    LOCALEDATA_PACKAGES.stream()
                        .filter(pn -> !packages.contains(pn))
                        .collect(Collectors.joining(",\n\t")));
            }

            available = Stream.concat(module.entries()
                                        .map(md -> p.matcher(md.path()))
                                        .filter(Matcher::matches)
                                        .map(m -> m.group("tag").replaceAll("_", "-")),
                                    Stream.of(jaJPJPTag, thTHTHTag, "und"))
                .distinct()
                .sorted()
                .map(IncludeLocalesPlugin::tagToLocale)
                .toList();
        } else {
            // jdk.localedata is not added.
            throw new PluginException(PluginsResourceBundle.getMessage(getName() + ".localedatanotfound"));
        }

        List<String> filtered = filterLocales(available);

        if (filtered.isEmpty()) {
            throw new PluginException(
                String.format(PluginsResourceBundle.getMessage(getName() + ".nomatchinglocales"), userParam));
        }

        List<String> value = Stream.concat(
                META_FILES.stream(),
                filtered.stream().flatMap(s -> includeLocaleFilePatterns(s).stream()))
            .map(s -> "regex:" + s)
            .toList();

        predicate = ResourceFilter.includeFilter(value);
    }

    private List<String> includeLocaleFilePatterns(String tag) {
        // Ignore extension variations
        if (tag.matches(".+-[a-z]-.+")) {
            return List.of();
        }

        List<String> files = new ArrayList<>(includeLocaleFiles(tag.replaceAll("-", "_")));

        // Add Thai BreakIterator related data files
        if (tag.equals("th")) {
            files.add(".+sun/text/resources/ext/thai_dict");
            files.add(".+sun/text/resources/ext/[^_]+BreakIteratorData_th");
        }

        // Add Taiwan resource bundles for Hong Kong
        if (tag.equals("zh-HK")) {
            files.addAll(includeLocaleFiles("zh_TW"));
        }

        // Make sure to retain sun.text/util.resources.ext packages
        if (tag.equals("und")) {
            files.add(".+sun/text/resources/ext/FormatData.class");
            files.add(".+sun/util/resources/ext/TimeZoneNames.class");
        }

        return files;
    }

    private List<String> includeLocaleFiles(String localeStr) {
        return INCLUDE_LOCALE_FILES.stream()
            .map(s -> s + localeStr + ".class")
            .toList();
    }

    private boolean stripUnsupportedLocales(byte[] bytes) {
        boolean modified = false;
        // scan CP entries directly to read the bytes of UTF8 entries and
        // patch in place with unsupported locale tags stripped
        IntUnaryOperator readU2 = p -> ((bytes[p] & 0xff) << 8) + (bytes[p + 1] & 0xff);
        int cpLength = readU2.applyAsInt(8);
        int offset = 10;
        for (int cpSlot=1; cpSlot<cpLength; cpSlot++) {
            switch (bytes[offset]) { //entry tag
                case TAG_UTF8 -> {
                    int length = readU2.applyAsInt(offset + 1);
                    if (bytes[offset + 3] == (byte)' ') { // fast check for leading space
                        byte[] b = new byte[length];
                        System.arraycopy(bytes, offset + 3, b, 0, length);
                        if (filterOutUnsupportedTags(b)) {
                            // copy back
                            System.arraycopy(b, 0, bytes, offset + 3, length);
                            modified = true;
                        }
                    }
                    offset += 3 + length;
                }
                case TAG_CLASS,
                     TAG_STRING,
                     TAG_METHODTYPE,
                     TAG_MODULE,
                     TAG_PACKAGE -> offset += 3;
                case TAG_METHODHANDLE -> offset += 4;
                case TAG_INTEGER,
                     TAG_FLOAT,
                     TAG_FIELDREF,
                     TAG_METHODREF,
                     TAG_INTERFACEMETHODREF,
                     TAG_NAMEANDTYPE,
                     TAG_CONSTANTDYNAMIC,
                     TAG_INVOKEDYNAMIC -> offset += 5;
                case TAG_LONG,
                     TAG_DOUBLE -> {offset += 9; cpSlot++;} //additional slot for double and long entries
                default -> throw new IllegalArgumentException("Unknown constant pool entry: 0x"
                        + Integer.toHexString(Byte.toUnsignedInt(bytes[offset])).toUpperCase(Locale.ROOT));
            }
        }
        return modified;
    }

    private boolean filterOutUnsupportedTags(byte[] b) {
        List<Locale> locales;
        List<String> originalTags = Arrays.asList(new String(b).split(" "));

        try {
            locales = originalTags.stream()
                .filter(tag -> !tag.isEmpty())
                .map(IncludeLocalesPlugin::tagToLocale)
                .toList();
        } catch (IllformedLocaleException ile) {
            // Seems not an available locales string literal.
            return false;
        }

        byte[] filteredBytes = filterLocales(locales).stream()
            // Make sure the filtered language tags do exist in the
            // original supported tags for compatibility codes, e.g., "iw"
            .filter(originalTags::contains)
            .collect(Collectors.joining(" "))
            .getBytes();

        if (filteredBytes.length > b.length) {
            throw new InternalError("Size of filtered locales is bigger than the original one");
        }

        System.arraycopy(filteredBytes, 0, b, 0, filteredBytes.length);
        Arrays.fill(b, filteredBytes.length, b.length, (byte)' ');
        return true;
    }

    /*
     * Filter list of locales according to the specified priorityList. Note
     * that returned list of language tags may include extra ones, such as
     * compatibility ones (e.g., "iw" -> "iw", "he").
     */
    private List<String> filterLocales(List<Locale> locales) {
        return Locale.filter(priorityList, locales, Locale.FilteringMode.EXTENDED_FILTERING).stream()
                .flatMap(loc -> Stream.concat(Control.getNoFallbackControl(Control.FORMAT_DEFAULT)
                                     .getCandidateLocales("", loc).stream(),
                                CLDR_ADAPTER.getCandidateLocales("", loc).stream()))
                .map(loc ->
                    // Locale.filter() does not preserve the case, which is
                    // significant for "variant" equality. Retrieve the original
                    // locales from the pre-filtered list.
                    locales.stream()
                        .filter(l -> l.toString().equalsIgnoreCase(loc.toString()))
                        .findAny())
                .flatMap(Optional::stream)
                .flatMap(IncludeLocalesPlugin::localeToTags)
                .distinct()
                .toList();
    }

    private static final Locale.Builder LOCALE_BUILDER = new Locale.Builder();
    private static Locale tagToLocale(String tag) {
        // ISO3166 compatibility
        tag = tag.replaceFirst("^iw", "he").replaceFirst("^ji", "yi").replaceFirst("^in", "id");

        // Special COMPAT provider locales
        switch (tag) {
            case jaJPJPTag:
                return jaJPJP;
            case noNONYTag:
                return noNONY;
            case thTHTHTag:
                return thTHTH;
            default:
                LOCALE_BUILDER.clear();
                LOCALE_BUILDER.setLanguageTag(tag);
                return LOCALE_BUILDER.build();
        }
    }

    private static Stream<String> localeToTags(Locale loc) {
        Objects.requireNonNull(loc);

        String tag = loc.toLanguageTag();
        List<String> tags = null;

        switch (loc.getLanguage()) {
            // ISO3166 compatibility
            case "iw":
                tags = List.of(tag, tag.replaceFirst("^he", "iw"));
                break;
            case "in":
                tags = List.of(tag, tag.replaceFirst("^id", "in"));
                break;
            case "ji":
                tags = List.of(tag, tag.replaceFirst("^yi", "ji"));
                break;

            // Special COMPAT provider locales
            case "ja":
                if (loc.getCountry() == "JP") {
                    tags = List.of(tag, jaJPJPTag);
                }
                break;
            case "no":
            case "nn":
                if (loc.getCountry() == "NO") {
                    tags = List.of(tag, noNONYTag);
                }
                break;
            case "th":
                if (loc.getCountry() == "TH") {
                    tags = List.of(tag, thTHTHTag);
                }
                break;
        }

        return tags == null ? Stream.of(tag) : tags.stream();
    }
}
