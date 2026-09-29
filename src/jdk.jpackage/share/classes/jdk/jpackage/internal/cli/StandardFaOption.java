/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * jpackage file association options
 */
public final class StandardFaOption {

    private StandardFaOption() {
    }

    public static final OptionValue<String> DESCRIPTION = stringOption("description").create();

    public static final OptionValue<Path> ICON = fileOption("icon").create();

    public static final OptionValue<List<String>> EXTENSIONS = stringOption("extension").tokenizer("(,|\\s)+").createArray(toList());

    public static final OptionValue<List<String>> CONTENT_TYPE = stringOption("mime-type").tokenizer("(,|\\s)+").createArray(toList());

    //
    // MacOS-specific
    //

    public static final OptionValue<String> MAC_CFBUNDLETYPEROLE = stringOption("mac.CFBundleTypeRole").create();

    public static final OptionValue<String> MAC_LSHANDLERRANK = stringOption("mac.LSHandlerRank").create();

    public static final OptionValue<String> MAC_NSSTORETYPEKEY = stringOption("mac.NSPersistentStoreTypeKey").create();

    public static final OptionValue<String> MAC_NSDOCUMENTCLASS = stringOption("mac.NSDocumentClass").create();

    public static final OptionValue<Boolean> MAC_LSTYPEISPACKAGE = booleanOption("mac.LSTypeIsPackage").create();

    public static final OptionValue<Boolean> MAC_LSDOCINPLACE = booleanOption("mac.LSSupportsOpeningDocumentsInPlace").create();

    public static final OptionValue<Boolean> MAC_UIDOCBROWSER = booleanOption("mac.UISupportsDocumentBrowser").create();

    public static final OptionValue<List<String>> MAC_NSEXPORTABLETYPES = stringOption("mac.NSExportableTypes").tokenizer("(,|\\s)+").createArray(toList());

    public static final OptionValue<List<String>> MAC_UTTYPECONFORMSTO = stringOption("mac.UTTypeConformsTo").tokenizer("(,|\\s)+").createArray(toList());

    /**
     * Returns public and package-private options with option specs defined in
     * {@link StandardFaOption} class.
     *
     * @return public and package-private options defined in
     *         {@link StandardFaOption} class
     */
    static Set<Option> options() {
        return Utils.getOptionsWithSpecs(StandardFaOption.class).map(OptionValue::getOption).collect(toUnmodifiableSet());
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .description("")
                .scope(scopeFromOptionName(name))
                .valuePattern("");
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).mutate(StandardOption.stringOptionMutator());
    }

    private static OptionSpecBuilder<Path> fileOption(String name) {
        return option(name, Path.class).mutate(StandardOption.fileOptionMutator());
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).mutate(StandardOption.booleanOptionMutator()).defaultValue(null);
    }

    private static Set<? extends OptionScope> scopeFromOptionName(String name) {
        if (name.startsWith("mac.")) {
            return Set.of(StandardBundlingOperation.CREATE_MAC_PKG);
        } else {
            return StandardBundlingOperation.CREATE_NATIVE;
        }
    }
}
