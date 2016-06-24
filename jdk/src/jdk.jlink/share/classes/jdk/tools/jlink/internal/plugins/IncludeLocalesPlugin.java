/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.plugin.LinkModule;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.TransformerPlugin;

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
public final class IncludeLocalesPlugin implements TransformerPlugin, ResourcePrevisitor {

    public static final String NAME = "include-locales";
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
    private List<Locale> available;
    private List<String> filtered;

    // Special COMPAT provider locales
    private static final String jaJPJPTag = "ja-JP-JP";
    private static final String noNONYTag = "no-NO-NY";
    private static final String thTHTHTag = "th-TH-TH";
    private static final Locale jaJPJP = new Locale("ja", "JP", "JP");
    private static final Locale noNONY = new Locale("no", "NO", "NY");
    private static final Locale thTHTH = new Locale("th", "TH", "TH");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void visit(ModulePool in, ModulePool out) {
        in.transformAndCopy((resource) -> {
            if (resource.getModule().equals(MODULENAME)) {
                String path = resource.getPath();
                resource = predicate.test(path) ? resource: null;
                if (resource != null &&
                    resource.getType().equals(ModuleEntry.Type.CLASS_OR_RESOURCE)) {
                    byte[] bytes = resource.getBytes();
                    ClassReader cr = new ClassReader(bytes);
                    if (Arrays.stream(cr.getInterfaces())
                        .anyMatch(i -> i.contains(METAINFONAME)) &&
                        stripUnsupportedLocales(bytes, cr)) {
                        resource = resource.create(bytes);
                    }
                }
            }
            return resource;
        }, out);
    }

    @Override
    public Category getType() {
        return Category.FILTER;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        userParam = config.get(NAME);
        priorityList = Arrays.stream(userParam.split(","))
            .map(s -> {
                try {
                    return new Locale.LanguageRange(s);
                } catch (IllegalArgumentException iae) {
                    throw new IllegalArgumentException(String.format(
                        PluginsResourceBundle.getMessage(NAME + ".invalidtag"), s));
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public void previsit(ModulePool resources, StringTable strings) {
        final Pattern p = Pattern.compile(".*((Data_)|(Names_))(?<tag>.*)\\.class");
        Optional<LinkModule> optMod = resources.findModule(MODULENAME);

        // jdk.localedata module validation
        if (optMod.isPresent()) {
            LinkModule module = optMod.get();
            Set<String> packages = module.getAllPackages();
            if (!packages.containsAll(LOCALEDATA_PACKAGES)) {
                throw new PluginException(PluginsResourceBundle.getMessage(NAME + ".missingpackages") +
                    LOCALEDATA_PACKAGES.stream()
                        .filter(pn -> !packages.contains(pn))
                        .collect(Collectors.joining(",\n\t")));
            }

            available = Stream.concat(module.entries()
                                        .map(md -> p.matcher(md.getPath()))
                                        .filter(m -> m.matches())
                                        .map(m -> m.group("tag").replaceAll("_", "-")),
                                    Stream.concat(Stream.of(jaJPJPTag), Stream.of(thTHTHTag)))
                .distinct()
                .sorted()
                .map(IncludeLocalesPlugin::tagToLocale)
                .collect(Collectors.toList());
        } else {
            // jdk.localedata is not added.
            throw new PluginException(PluginsResourceBundle.getMessage(NAME + ".localedatanotfound"));
        }
        filtered = filterLocales(available);

        if (filtered.isEmpty()) {
            throw new PluginException(
                String.format(PluginsResourceBundle.getMessage(NAME + ".nomatchinglocales"), userParam));
        }

        List<String> value = Stream.concat(
                META_FILES.stream(),
                filtered.stream().flatMap(s -> includeLocaleFilePatterns(s).stream()))
            .map(s -> "regex:" + s)
            .collect(Collectors.toList());
        predicate = ResourceFilter.includeFilter(value);
    }

    private List<String> includeLocaleFilePatterns(String tag) {
        List<String> files = new ArrayList<>();
        String pTag = tag.replaceAll("-", "_");
        int lastDelimiter = tag.length();
        String isoSpecial = pTag.matches("^(he|yi|id).*") ?
                            pTag.replaceFirst("he", "iw")
                                .replaceFirst("yi", "ji")
                                .replaceFirst("id", "in") : "";

        // Add tag patterns including parents
        while (true) {
            pTag = pTag.substring(0, lastDelimiter);
            files.addAll(includeLocaleFiles(pTag));

            if (!isoSpecial.isEmpty()) {
                isoSpecial = isoSpecial.substring(0, lastDelimiter);
                files.addAll(includeLocaleFiles(isoSpecial));
            }

            lastDelimiter = pTag.lastIndexOf('_');
            if (lastDelimiter == -1) {
                break;
            }
        }

        final String lang = pTag;

        // Add possible special locales of the COMPAT provider
        Set.of(jaJPJPTag, noNONYTag, thTHTHTag).stream()
            .filter(stag -> lang.equals(stag.substring(0,2)))
            .map(t -> includeLocaleFiles(t.replaceAll("-", "_")))
            .forEach(files::addAll);

        // Add possible UN.M49 files (unconditional for now) for each language
        files.addAll(includeLocaleFiles(lang + "_[0-9]{3}"));
        if (!isoSpecial.isEmpty()) {
            files.addAll(includeLocaleFiles(isoSpecial + "_[0-9]{3}"));
        }

        // Add Thai BreakIterator related data files
        if (lang.equals("th")) {
            files.add(".+sun/text/resources/thai_dict");
            files.add(".+sun/text/resources/[^_]+BreakIteratorData_th");
        }

        // Add Taiwan resource bundles for Hong Kong
        if (tag.startsWith("zh-HK")) {
            files.addAll(includeLocaleFiles("zh_TW"));
        }

        return files;
    }

    private List<String> includeLocaleFiles(String localeStr) {
        return INCLUDE_LOCALE_FILES.stream()
            .map(s -> s + localeStr + ".class")
            .collect(Collectors.toList());
    }

    private boolean stripUnsupportedLocales(byte[] bytes, ClassReader cr) {
        char[] buf = new char[cr.getMaxStringLength()];
        boolean[] modified = new boolean[1];

        IntStream.range(1, cr.getItemCount())
            .map(item -> cr.getItem(item))
            .forEach(itemIndex -> {
                if (bytes[itemIndex - 1] == 1 &&         // UTF-8
                    bytes[itemIndex + 2] == (byte)' ') { // fast check for leading space
                    int length = cr.readUnsignedShort(itemIndex);
                    byte[] b = new byte[length];
                    System.arraycopy(bytes, itemIndex + 2, b, 0, length);
                    if (filterOutUnsupportedTags(b)) {
                        // copy back
                        System.arraycopy(b, 0, bytes, itemIndex + 2, length);
                        modified[0] = true;
                    }
                }
            });

        return modified[0];
    }

    private boolean filterOutUnsupportedTags(byte[] b) {
        List<Locale> locales;

        try {
            locales = Arrays.asList(new String(b).split(" ")).stream()
                .filter(tag -> !tag.isEmpty())
                .map(IncludeLocalesPlugin::tagToLocale)
                .collect(Collectors.toList());
        } catch (IllformedLocaleException ile) {
            // Seems not an available locales string literal.
            return false;
        }

        byte[] filteredBytes = filterLocales(locales).stream()
            .collect(Collectors.joining(" "))
            .getBytes();
        System.arraycopy(filteredBytes, 0, b, 0, filteredBytes.length);
        Arrays.fill(b, filteredBytes.length, b.length, (byte)' ');
        return true;
    }

    private List<String> filterLocales(List<Locale> locales) {
        List<String> ret =
            Locale.filter(priorityList, locales, Locale.FilteringMode.EXTENDED_FILTERING).stream()
                .map(loc ->
                    // Locale.filter() does not preserve the case, which is
                    // significant for "variant" equality. Retrieve the original
                    // locales from the pre-filtered list.
                    locales.stream()
                        .filter(l -> l.toString().equalsIgnoreCase(loc.toString()))
                        .findAny()
                        .orElse(Locale.ROOT)
                        .toLanguageTag())
                .collect(Collectors.toList());

        // no-NO-NY.toLanguageTag() returns "nn-NO", so specially handle it here
        if (ret.contains("no-NO")) {
            ret.add(noNONYTag);
        }

        return ret;
    }

    private static final Locale.Builder LOCALE_BUILDER = new Locale.Builder();
    private static Locale tagToLocale(String tag) {
        // ISO3166 compatibility
        tag = tag.replaceFirst("^iw", "he").replaceFirst("^ji", "yi").replaceFirst("^in", "id");

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
}
