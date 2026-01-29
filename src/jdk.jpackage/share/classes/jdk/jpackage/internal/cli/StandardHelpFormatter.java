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

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.cli.HelpFormatter.eol;
import static jdk.jpackage.internal.cli.StandardOption.platformOption;
import static jdk.jpackage.internal.cli.StandardOption.sharedOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.HelpFormatter.ConsoleOptionFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.ConsoleOptionGroupFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.OptionFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.OptionGroup;
import jdk.jpackage.internal.cli.HelpFormatter.OptionGroupFormatter;
import jdk.jpackage.internal.util.StringBundle;

/**
 * jpackage help formatter
 */
final class StandardHelpFormatter {

    enum OptionGroupID {
        SAMPLES("sample", OptionGroupContent::sampleGroupContent),

        GENERIC_OPTIONS("generic", OptionGroupContent::genericGroupContent),

        RUNTIME_IMAGE_OPTIONS("runtime-image", OptionGroupContent::runtimeImageGroupContent),

        APPLICATION_IMAGE_OPTIONS("app-image", OptionGroupContent::appImageGroupContent),

        LAUNCHER_OPTIONS_SHARED("launcher", OptionGroupContent::launcherGroupContent),
        LAUNCHER_OPTIONS_PLATFORM("launcher-platform", OptionGroupContent::launcherPlatformGroupContent),

        PACKAGE_OPTIONS_SHARED("package", OptionGroupContent::nativePackageGroupContent),
        PACKAGE_OPTIONS_PLATFORM("package-platform", OptionGroupContent::nativePackagePlatformGroupContent),
        ;

        OptionGroupID(String name, Function<OperatingSystem, OptionGroupContent> optionGroupContentCreator) {
            this.name = "help.option-group." + Objects.requireNonNull(name);
            this.optionGroupContentCreator = Objects.requireNonNull(optionGroupContentCreator);
        }

        String groupName() {
            return name;
        }

        Optional<OptionGroup> createNonEmptyOptionGroup(OperatingSystem os) {
            var optionGroup = optionGroupContentCreator.apply(os).createOptionGroup(name);
            if (optionGroup.options().isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(optionGroup);
            }
        }

        private final String name;
        private final Function<OperatingSystem, OptionGroupContent> optionGroupContentCreator;
    }

    static final class OptionGroupContent {

        static OptionGroupContent sampleGroupContent(OperatingSystem os) {
            List<String> ids = new ArrayList<>();
            ids.add("help.option-group.sample.create-native-package");
            ids.add("help.option-group.sample.create-app-image");
            ids.add("help.option-group.sample.create-runtime-installer");
            if (os.equals(OperatingSystem.MACOS)) {
                ids.add("help.option-group.sample.sign-app-image");
            }
            return new OptionGroupContent(ids.stream().map(descriptionId -> {
                return dummyOptionSpec(descriptionId, descriptionId);
            }).toList());
        }

        static OptionGroupContent genericGroupContent(OperatingSystem os) {
            return shared(os).options(genericOptions()).create();
        }

        static OptionGroupContent runtimeImageGroupContent(OperatingSystem os) {
            return shared(os).options(runtimeImageOptions()).create();
        }

        static OptionGroupContent appImageGroupContent(OperatingSystem os) {
            return shared(os).options(appImageOptions()).create();
        }

        static OptionGroupContent launcherGroupContent(OperatingSystem os) {
            return shared(os).options(launcherOptions(os)).create();
        }

        static OptionGroupContent launcherPlatformGroupContent(OperatingSystem os) {
            return platform(os).options(launcherOptions(os)).create();
        }

        static OptionGroupContent nativePackageGroupContent(OperatingSystem os) {
            return shared(os).options(nativePackageOptions(os)).create();
        }

        static OptionGroupContent nativePackagePlatformGroupContent(OperatingSystem os) {
            return platform(os).options(nativePackageOptions(os)).create();
        }

        private OptionGroupContent(List<? extends OptionSpec<?>> optionSpecs) {
            this.optionSpecs = List.copyOf(optionSpecs);
        }

        private OptionGroup createOptionGroup(String name) {
            return new OptionGroup(name, optionSpecs);
        }

        private static Builder shared(OperatingSystem os) {
            return new Builder(os, sharedOption()).optionSpecDescriptionGetter(standardOptionSpecDescriptionGetter(os));
        }

        private static Builder platform(OperatingSystem os) {
            return new Builder(os, Predicate.not(sharedOption()));
        }

        private static final class Builder {

            private Builder(OperatingSystem os, Predicate<OptionSpec<?>> platformFilter) {
                this.context = new StandardOptionContext(os);
                this.platformFilter = Objects.requireNonNull(platformFilter);
            }

            OptionGroupContent create() {
                return new OptionGroupContent(optionSpecs.stream()
                        .filter(platformFilter).sorted(optionSpecSorter()).toList());
            }

            Builder optionSpecDescriptionGetter(Function<OptionSpec<?>, String> v) {
                descriptionGetter = v;
                return this;
            }

            Builder options(Stream<? extends OptionSpec<?>> v) {
                optionSpecs.addAll(v.<OptionSpec<?>>map(context::mapOptionSpec).map(optionSpec -> {
                    return optionSpec.copyWithDescription(getDescription(optionSpec));
                }).toList());
                return this;
            }

            private String getDescription(OptionSpec<?> optionSpec) {
                Objects.requireNonNull(optionSpec);
                return Optional.ofNullable(descriptionGetter).orElse(
                        OptionGroupContent::getDefaultOptionSpecDescription).apply(optionSpec);
            }

            private final StandardOptionContext context;
            private final Predicate<OptionSpec<?>> platformFilter;
            private Function<OptionSpec<?>, String> descriptionGetter;
            private List<OptionSpec<?>> optionSpecs = new ArrayList<>();
        }

        private static Stream<? extends OptionSpec<?>> genericOptions() {
            return Stream.of(
                    StandardOption.TYPE,
                    StandardOption.APP_VERSION,
                    StandardOption.COPYRIGHT,
                    StandardOption.DESCRIPTION,
                    StandardOption.HELP,
                    StandardOption.ICON,
                    StandardOption.NAME,
                    StandardOption.DEST,
                    StandardOption.TEMP_ROOT,
                    StandardOption.VENDOR,
                    StandardOption.VERBOSE,
                    StandardOption.VERSION
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> appImageOptions() {
            return Stream.of(
                    StandardOption.INPUT,
                    StandardOption.APP_CONTENT
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> runtimeImageOptions() {
            return Stream.of(
                    StandardOption.ADD_MODULES,
                    StandardOption.MODULE_PATH,
                    StandardOption.JLINK_OPTIONS,
                    StandardOption.PREDEFINED_RUNTIME_IMAGE
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> launcherOptions(OperatingSystem os) {
            final var fromPropertyFile = StandardOption.launcherOptions().stream()
                    .map(Option::spec)
                    .filter(platformOption(os))
                    .filter(spec -> {
                        // Want options applicable to the app image bundling on the current platform.
                        return StandardBundlingOperation.narrow(spec.scope().stream())
                                .filter(StandardBundlingOperation.CREATE_APP_IMAGE::contains)
                                .findFirst().isPresent();
                    })
                    .filter(Predicate.not(genericOptions().toList()::contains));

            final Stream<? extends OptionSpec<?>> additional = Stream.of(
                    StandardOption.ADD_LAUNCHER_INTERNAL
            ).map(OptionValue::getSpec);

            return Stream.concat(fromPropertyFile, additional);
        }

        private static Stream<? extends OptionSpec<?>> nativePackageOptions(OperatingSystem os) {
            // The most straightforward way to get the list of these options is to
            // subtract the options from other groups from the list of all supported options.
            // This presumes this method is called after the other enum elements have been initialized.
            final var base = StandardOption.options().stream().map(Option::spec).filter(platformOption(os))
                    .filter(Predicate.not(Stream.of(
                            genericOptions(),
                            appImageOptions(),
                            runtimeImageOptions(),
                            launcherOptions(os)).flatMap(x -> x).collect(toSet())::contains));

            return Stream.concat(base, Stream.of(StandardOption.RUNTIME_INSTALLER_RUNTIME_IMAGE));
        }

        private static String getDefaultOptionSpecDescription(OptionSpec<?> optionSpec) {
            return I18N.getString(optionSpec.description());
        }

        private static Function<OptionSpec<?>, String> standardOptionSpecDescriptionGetter(OperatingSystem os) {
            return optionSpec -> {
                return getDefaultOptionSpecDescription(optionSpec);
            };
        }

        private final List<OptionSpec<?>> optionSpecs;
    }


    private static final class GroupFormatter implements OptionGroupFormatter {

        @Override
        public void formatHeader(String groupName, Consumer<CharSequence> sink) {
            groupFormatter.formatHeader(I18N.getString(groupName), sink);
        }

        @Override
        public void formatBody(Iterable<? extends OptionSpec<?>> optionSpecs, Consumer<CharSequence> sink) {
            groupFormatter.formatBody(optionSpecs, sink);
        }

        @Override
        public void format(HelpFormatter.OptionGroup group, Consumer<CharSequence> sink) {
            formatHeader(group.name(), sink);
            if (group.name().equals(OptionGroupID.GENERIC_OPTIONS.groupName())) {
                optionSpecFormatter.format("@<filename>", Optional.empty(), I18N.getString("help.option.argument-file"), sink);
            }
            formatBody(group.options(), sink);
        }

        private final OptionFormatter optionSpecFormatter = new ConsoleOptionFormatter(2, 0);
        private final OptionGroupFormatter groupFormatter = new ConsoleOptionGroupFormatter(optionSpecFormatter);
    }


    private static final class SampleGroupFormatter implements OptionGroupFormatter {

        @Override
        public void formatHeader(String groupName, Consumer<CharSequence> sink) {
            var formattedName = I18N.getString(groupName) + ":";
            sink.accept(formattedName);
            eol(sink);
            sink.accept("-".repeat(formattedName.length()));
            eol(sink);
        }

        @Override
        public void formatBody(Iterable<? extends OptionSpec<?>> optionSpecs, Consumer<CharSequence> sink) {
            boolean first = true;
            for (var optionSpec : optionSpecs) {
                if (first) {
                    first = false;
                } else {
                    eol(sink);
                }
                sink.accept(I18N.getString(optionSpec.description()));
                eol(sink);
            }
        }
    }


    StandardHelpFormatter(OperatingSystem os) {

        samplesHelpFormatter = HelpFormatter.build()
                .groupFormatter(new SampleGroupFormatter())
                .groups(OptionGroupID.SAMPLES.createNonEmptyOptionGroup(os).orElseThrow())
                .create();

        final var builder = HelpFormatter.build().groupFormatter(new GroupFormatter());

        Stream.of(OptionGroupID.values())
                .filter(Predicate.<OptionGroupID>isEqual(OptionGroupID.SAMPLES).negate())
                .map(optionGroupID -> {
                    return optionGroupID.createNonEmptyOptionGroup(os);
                })
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .forEach(builder::groups);

        mainHelpFormatter = builder.create();
    }

    void format(Consumer<CharSequence> sink) {
        sink.accept(I18N.getString("help.header"));
        eol(sink);
        eol(sink);
        samplesHelpFormatter.format(sink);
        mainHelpFormatter.format(sink);
    }

    void formatNoArgsHelp(Consumer<CharSequence> sink) {
        sink.accept(I18N.getString("help.header"));
        eol(sink);
        sink.accept(I18N.format("help.short"));
        eol(sink);
    }

    static Comparator<OptionSpec<?>> optionSpecSorter() {
        // Sort alphabetically by the first name except of the "--type" option, it goes first.
        return Comparator.comparing(OptionSpec::name, new Comparator<OptionName>() {

            @Override
            public int compare(OptionName o1, OptionName o2) {
                if (o1.equals(TYPE) && o2.equals(TYPE)) {
                    return 0;
                } else if (o1.equals(TYPE)) {
                    return -1;
                } else if (o2.equals(TYPE)) {
                    return 1;
                } else {
                    return o1.compareTo(o2);
                }
            }

            private static final OptionName TYPE = StandardOption.TYPE.getSpec().name();
        });
    }

    private static OptionSpec<?> dummyOptionSpec(String name, String description) {
        return new OptionSpec<>(
                List.of(OptionName.of(name)),
                Optional.empty(),
                Set.copyOf(StandardBundlingOperation.CREATE_BUNDLE),
                OptionSpec.MergePolicy.USE_FIRST,
                Optional.empty(),
                Optional.empty(),
                description);
    }

    private final HelpFormatter samplesHelpFormatter;
    private final HelpFormatter mainHelpFormatter;

    private static final StringBundle I18N = StringBundle.fromResourceBundle(ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.HelpResources"));

    static final StandardHelpFormatter INSTANCE = new StandardHelpFormatter(OperatingSystem.current());
}
