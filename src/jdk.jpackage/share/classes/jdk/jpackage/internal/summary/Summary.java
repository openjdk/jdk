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
package jdk.jpackage.internal.summary;

import static jdk.jpackage.internal.util.IdentityWrapper.wrapIdentity;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.StandardBundlingOperation;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.BundleSpec;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.util.IdentityWrapper;
import jdk.jpackage.internal.util.PathUtils;

/**
 * Summary of the operation jpackage will perform.
 */
public final class Summary implements SummaryAccumulator {

    public void print(Consumer<String> sinkInfo, Consumer<String> sinkWarnings) {

        // Properties
        properties.entrySet().stream().sorted(comparator()).map(e -> {
            return String.format("%s: %s", e.getKey().value().formatLabel(), e.getValue());
        }).forEach(sinkInfo);

        // Warnings
        warnings.entrySet().stream().sorted(comparator()).map(e -> {
            switch (e.getValue()) {
                case SingleLineContent c -> {
                    return I18N.format("summary.warning", c.str());
                }
                case MultiLineContent c -> {
                    return Stream.concat(
                            Stream.of(I18N.format("summary.multi-line-warning", c.header())),
                            StreamSupport.stream(c.items().spliterator(), false).map(msg -> {
                                // Add indentation.
                                return "  " + msg;
                            })
                    ).collect(Collectors.joining("\n"));
                }
            }
        }).forEach(sinkWarnings);
    }

    @Override
    public void putIfAbsent(SummaryItem k, String value) {
        Objects.requireNonNull(value);
        switch (k) {
            case Property prop -> {
                properties.putIfAbsent(wrapIdentity(prop), value);
            }
            case Warning warn -> {
                warnings.putIfAbsent(wrapIdentity(warn), new SingleLineContent(value));
            }
        }
    }

    @Override
    public void put(SummaryItem k, String value) {
        Objects.requireNonNull(value);
        switch (k) {
            case Property prop -> {
                properties.put(wrapIdentity(prop), value);
            }
            case Warning warn -> {
                warnings.put(wrapIdentity(warn), new SingleLineContent(value));
            }
        }
    }

    @Override
    public void putMultiValue(Warning k, String header, Iterable<String> items) {
        if (!items.iterator().hasNext()) {
            throw new IllegalArgumentException();
        }
        warnings.put(wrapIdentity(k), new MultiLineContent(header, items));
    }

    public Summary putStandardPropertiesIfAbsent(StandardBundlingOperation op, Path outputDir, BundleSpec bundle) {
        Objects.requireNonNull(op);
        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(bundle);

        if (op != StandardBundlingOperation.SIGN_MAC_APP_IMAGE) {
            putIfAbsent(StandardProperty.OPERATION, (Object)op.bundleType().label());
            putIfAbsent(StandardProperty.OUTPUT_BUNDLE, PathUtils.normalizedAbsolutePath(outputDir.resolve(outputBundleName(bundle))));
        }

        putIfAbsent(StandardProperty.VERSION, version(bundle));

        return this;
    }

    private static Path outputBundleName(BundleSpec bundle) {
        return getProperty(bundle, Application::appImageDirName, pkg -> {
            return Path.of(pkg.packageFileNameWithSuffix());
        });
    }

    private static String version(BundleSpec bundle) {
        return getProperty(bundle, Application::version, Package::version);
    }

    private static <T> T getProperty(BundleSpec bundle,
            Function<Application, T> appPropertyGetter,
            Function<Package, T> pkgPropertyGetter) {

        Objects.requireNonNull(appPropertyGetter);
        Objects.requireNonNull(pkgPropertyGetter);

        switch (bundle) {
            case Application app -> {
                return appPropertyGetter.apply(app);
            }
            case Package pkg -> {
                return pkgPropertyGetter.apply(pkg);
            }
        }
    }

    private static <T extends SummaryItem> Comparator<Map.Entry<IdentityWrapper<T>, ?>> comparator() {
        return Comparator.comparing(
                e -> {
                    return e.getKey().value();
                },
                Comparator.comparingInt(SummaryItem::ordinal)
        );
    }

    private sealed interface Content {
    }

    record SingleLineContent(String str) implements Content {
        SingleLineContent {
            Objects.requireNonNull(str);
        }
    }

    record MultiLineContent(String header, Iterable<String> items) implements Content {
        MultiLineContent {
            Objects.requireNonNull(header);
            Objects.requireNonNull(items);
        }
    }

    private final Map<IdentityWrapper<Property>, String> properties = new HashMap<>();
    private final Map<IdentityWrapper<Warning>, Content> warnings = new HashMap<>();
}
