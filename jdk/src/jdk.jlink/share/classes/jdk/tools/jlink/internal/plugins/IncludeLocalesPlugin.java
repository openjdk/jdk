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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.internal.Utils;

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
    private static final String META_FILES =
        "*module-info.class," +
        "*LocaleDataProvider*," +
        "*" + METAINFONAME + "*,";
    private static final String INCLUDE_LOCALE_FILES =
        "*sun/text/resources/ext/[^\\/]+_%%.class," +
        "*sun/util/resources/ext/[^\\/]+_%%.class," +
        "*sun/text/resources/cldr/ext/[^\\/]+_%%.class," +
        "*sun/util/resources/cldr/ext/[^\\/]+_%%.class,";
    private Predicate<String> predicate;
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
    public void visit(Pool in, Pool out) {
        in.visit((resource) -> {
            if (resource.getModule().equals(MODULENAME)) {
                String path = resource.getPath();
                resource = predicate.test(path) ? resource: null;
                if (resource != null) {
                    byte[] bytes = resource.getBytes();
                    ClassReader cr = new ClassReader(bytes);
                    if (Arrays.stream(cr.getInterfaces())
                        .anyMatch(i -> i.contains(METAINFONAME)) &&
                        stripUnsupportedLocales(bytes, cr)) {
                        resource = new Pool.ModuleData(MODULENAME, path,
                            resource.getType(),
                            new ByteArrayInputStream(bytes), bytes.length);
                    }
                }
            }
            return resource;
        }, out);
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.FILTER);
        return Collections.unmodifiableSet(set);
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
        try {
            priorityList = Arrays.stream(config.get(NAME).split(","))
                .map(Locale.LanguageRange::new)
                .collect(Collectors.toList());
        } catch (IllegalArgumentException iae) {
            throw new PluginException(iae.getLocalizedMessage());
        }
    }

    @Override
    public void previsit(Pool resources, StringTable strings) {
        final Pattern p = Pattern.compile(".*((Data_)|(Names_))(?<tag>.*)\\.class");
        Pool.Module module = resources.getModule(MODULENAME);

        // jdk.localedata module validation
        Set<String> packages = module.getAllPackages();
        if (!packages.containsAll(LOCALEDATA_PACKAGES)) {
            throw new PluginException(PluginsResourceBundle.getMessage(NAME + ".missingpackages") +
                LOCALEDATA_PACKAGES.stream()
                    .filter(pn -> !packages.contains(pn))
                    .collect(Collectors.joining(",\n\t")));
        }

        available = Stream.concat(module.getContent().stream()
                                    .map(md -> p.matcher(md.getPath()))
                                    .filter(m -> m.matches())
                                    .map(m -> m.group("tag").replaceAll("_", "-")),
                                Stream.concat(Stream.of(jaJPJPTag), Stream.of(thTHTHTag)))
            .distinct()
            .sorted()
            .map(IncludeLocalesPlugin::tagToLocale)
            .collect(Collectors.toList());

        filtered = filterLocales(available);

        if (filtered.isEmpty()) {
            throw new PluginException(PluginsResourceBundle.getMessage(NAME + ".nomatchinglocales"));
        }

        try {
            String value = META_FILES + filtered.stream()
                .map(s -> includeLocaleFilePatterns(s))
                .collect(Collectors.joining(","));
            predicate = new ResourceFilter(Utils.listParser.apply(value), false);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String includeLocaleFilePatterns(String tag) {
        String pTag = tag.replaceAll("-", "_");
        String files = "";
        int lastDelimiter = tag.length();
        String isoSpecial = pTag.matches("^(he|yi|id).*") ?
                            pTag.replaceFirst("he", "iw")
                                .replaceFirst("yi", "ji")
                                .replaceFirst("id", "in") : "";

        // Add tag patterns including parents
        while (true) {
            pTag = pTag.substring(0, lastDelimiter);
            files += INCLUDE_LOCALE_FILES.replaceAll("%%", pTag);

            if (!isoSpecial.isEmpty()) {
                isoSpecial = isoSpecial.substring(0, lastDelimiter);
                files += INCLUDE_LOCALE_FILES.replaceAll("%%", isoSpecial);
            }

            lastDelimiter = pTag.lastIndexOf('_');
            if (lastDelimiter == -1) {
                break;
            }
        }

        final String lang = pTag;

        // Add possible special locales of the COMPAT provider
        files += Set.of(jaJPJPTag, noNONYTag, thTHTHTag).stream()
            .filter(stag -> lang.equals(stag.substring(0,2)))
            .map(t -> INCLUDE_LOCALE_FILES.replaceAll("%%", t.replaceAll("-", "_")))
            .collect(Collectors.joining(","));

        // Add possible UN.M49 files (unconditional for now) for each language
        files += INCLUDE_LOCALE_FILES.replaceAll("%%", lang + "_[0-9]{3}");
        if (!isoSpecial.isEmpty()) {
            files += INCLUDE_LOCALE_FILES.replaceAll("%%", isoSpecial + "_[0-9]{3}");
        }

        // Add Thai BreakIterator related files
        if (lang.equals("th")) {
            files += "*sun/text/resources/thai_dict," +
                     "*sun/text/resources/[^\\/]+_th,";
        }

        // Add Taiwan resource bundles for Hong Kong
        if (tag.startsWith("zh-HK")) {
            files += INCLUDE_LOCALE_FILES.replaceAll("%%", "zh_TW");
        }

        return files;
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
