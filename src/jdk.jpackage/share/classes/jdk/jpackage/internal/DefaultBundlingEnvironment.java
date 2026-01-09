/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.CliBundlingEnvironment;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardBundlingOperation;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;

class DefaultBundlingEnvironment implements CliBundlingEnvironment {

    DefaultBundlingEnvironment(Builder builder) {
        this(Optional.ofNullable(builder.defaultOperationSupplier), builder.bundlers);
    }

    DefaultBundlingEnvironment(Optional<Supplier<Optional<BundlingOperationDescriptor>>> defaultOperationSupplier,
            Map<BundlingOperationDescriptor, Supplier<Result<Consumer<Options>>>> bundlers) {

        this.bundlers = bundlers.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            return runOnce(e.getValue());
        }));

        this.defaultOperationSupplier = Objects.requireNonNull(defaultOperationSupplier).map(DefaultBundlingEnvironment::runOnce);
    }


    static final class Builder {

        Builder defaultOperation(Supplier<Optional<BundlingOperationDescriptor>> v) {
            defaultOperationSupplier = v;
            return this;
        }

        Builder defaultOperation(StandardBundlingOperation v) {
            return defaultOperation(() -> Optional.of(v.descriptor()));
        }

        Builder bundler(StandardBundlingOperation op, Supplier<Result<Consumer<Options>>> bundlerSupplier) {
            bundlers.put(Objects.requireNonNull(op.descriptor()), Objects.requireNonNull(bundlerSupplier));
            return this;
        }

        <T extends SystemEnvironment> Builder bundler(StandardBundlingOperation op,
                Supplier<Result<T>> sysEnvResultSupplier, BiConsumer<Options, T> bundler) {
            return bundler(op, createBundlerSupplier(sysEnvResultSupplier, bundler));
        }

        Builder bundler(StandardBundlingOperation op, Consumer<Options> bundler) {
            Objects.requireNonNull(bundler);
            return bundler(op, () -> Result.ofValue(bundler));
        }

        Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        private Supplier<Optional<BundlingOperationDescriptor>> defaultOperationSupplier;
        private final Map<BundlingOperationDescriptor, Supplier<Result<Consumer<Options>>>> bundlers = new HashMap<>();
    }


    static Builder build() {
        return new Builder();
    }

    static <T> Supplier<T> runOnce(Supplier<T> supplier) {
        return new CachingSupplier<>(supplier);
    }

    static <T extends SystemEnvironment> Supplier<Result<Consumer<Options>>> createBundlerSupplier(
            Supplier<Result<T>> sysEnvResultSupplier, BiConsumer<Options, T> bundler) {
        Objects.requireNonNull(sysEnvResultSupplier);
        Objects.requireNonNull(bundler);
        return () -> {
            return sysEnvResultSupplier.get().map(sysEnv -> {
                return options -> {
                    bundler.accept(options, sysEnv);
                };
            });
        };
    }

    static void createApplicationImage(Options options, Application app, PackagingPipeline.Builder pipelineBuilder) {
        Objects.requireNonNull(options);
        Objects.requireNonNull(app);
        Objects.requireNonNull(pipelineBuilder);

        final var outputDir = OptionUtils.outputDir(options).resolve(app.appImageDirName());

        IOUtils.writableOutputDir(outputDir.getParent());

        final var env = new BuildEnvFromOptions()
                .predefinedAppImageLayout(app.asApplicationLayout().orElseThrow())
                .create(options, app);

        Log.verbose(I18N.format("message.creating-app-bundle", outputDir.getFileName(), outputDir.toAbsolutePath().getParent()));

        if (Files.exists(outputDir)) {
            throw new JPackageException(I18N.format("error.root-exists", outputDir.toAbsolutePath()));
        }

        pipelineBuilder.excludeDirFromCopying(outputDir.getParent())
                .create().execute(BuildEnv.withAppImageDir(env, outputDir), app);
    }

    static <T extends Package> void createNativePackage(Options options,
            T pkg,
            BiFunction<Options, T, BuildEnv> createBuildEnv,
            PackagingPipeline.Builder pipelineBuilder,
            Packager.PipelineBuilderMutatorFactory<T> pipelineBuilderMutatorFactory) {

        Objects.requireNonNull(pipelineBuilder);
        createNativePackage(options, pkg, createBuildEnv, _ -> pipelineBuilder, pipelineBuilderMutatorFactory);
    }

    static <T extends Package> void createNativePackage(Options options,
            T pkg,
            BiFunction<Options, T, BuildEnv> createBuildEnv,
            Function<T, PackagingPipeline.Builder> createPipelineBuilder,
            Packager.PipelineBuilderMutatorFactory<T> pipelineBuilderMutatorFactory) {

        Objects.requireNonNull(options);
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(createBuildEnv);
        Objects.requireNonNull(createPipelineBuilder);
        Objects.requireNonNull(pipelineBuilderMutatorFactory);

        Packager.<T>build().pkg(pkg)
            .outputDir(OptionUtils.outputDir(options))
            .env(Objects.requireNonNull(createBuildEnv.apply(options, pkg)))
            .pipelineBuilderMutatorFactory(pipelineBuilderMutatorFactory)
            .execute(Objects.requireNonNull(createPipelineBuilder.apply(pkg)));
    }

    @Override
    public Optional<BundlingOperationDescriptor> defaultOperation() {
        return defaultOperationSupplier.flatMap(Supplier::get);
    }

    @Override
    public void createBundle(BundlingOperationDescriptor op, Options cmdline) {
        final var bundler = getBundlerSupplier(op).get().orElseThrow();
        Optional<Path> permanentWorkDirectory = Optional.empty();
        try (var tempDir = new TempDirectory(cmdline)) {
            if (!tempDir.deleteOnClose()) {
                permanentWorkDirectory = Optional.of(tempDir.path());
            }
            bundler.accept(tempDir.options());

            var packageType = OptionUtils.bundlingOperation(cmdline).packageType();

            Log.verbose(I18N.format("message.bundle-created", I18N.getString(bundleTypeDescription(packageType, op.os()))));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            permanentWorkDirectory.ifPresent(workDir -> {
                Log.verbose(I18N.format("message.debug-working-directory", workDir.toAbsolutePath()));
            });
        }
    }

    @Override
    public Collection<? extends Exception> configurationErrors(BundlingOperationDescriptor op) {
        return getBundlerSupplier(op).get().errors();
    }

    private Supplier<Result<Consumer<Options>>> getBundlerSupplier(BundlingOperationDescriptor op) {
        return Optional.ofNullable(bundlers.get(op)).orElseThrow(() -> {
            throw new NoSuchElementException(String.format("Unsupported bundling operation: %s", op));
        });
    }

    private String bundleTypeDescription(PackageType type, OperatingSystem os) {
        switch (type) {
            case StandardPackageType stdType -> {
                switch (stdType) {
                    case WIN_MSI -> {
                        return "bundle-type.win-msi";
                    }
                    case WIN_EXE -> {
                        return "bundle-type.win-exe";
                    }
                    case LINUX_DEB -> {
                        return "bundle-type.linux-deb";
                    }
                    case LINUX_RPM -> {
                        return "bundle-type.linux-rpm";
                    }
                    case MAC_DMG -> {
                        return "bundle-type.mac-dmg";
                    }
                    case MAC_PKG -> {
                        return "bundle-type.mac-pkg";
                    }
                    default -> {
                        throw new AssertionError();
                    }
                }
            }
            case AppImagePackageType appImageType -> {
                switch (os) {
                    case WINDOWS -> {
                        return "bundle-type.win-app";
                    }
                    case LINUX -> {
                        return "bundle-type.linux-app";
                    }
                    case MACOS -> {
                        return "bundle-type.mac-app";
                    }
                    default -> {
                        throw new AssertionError();
                    }
                }
            }
            default -> {
                throw new AssertionError();
            }
        }
    }


    private static final class CachingSupplier<T> implements Supplier<T> {

        CachingSupplier(Supplier<T> getter) {
            this.getter = Objects.requireNonNull(getter);
        }

        @Override
        public T get() {
            return cachedValue.updateAndGet(v -> {
                return Optional.ofNullable(v).orElseGet(getter);
            });
        }

        private final Supplier<T> getter;
        private final AtomicReference<T> cachedValue = new AtomicReference<>();
    }


    private final Map<BundlingOperationDescriptor, Supplier<Result<Consumer<Options>>>> bundlers;
    private final Optional<Supplier<Optional<BundlingOperationDescriptor>>> defaultOperationSupplier;
}
