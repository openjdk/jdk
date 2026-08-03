/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;

final class MockupCliBundlingEnvironment implements CliBundlingEnvironment {

    static Builder build() {
        return new Builder();
    }

    private MockupCliBundlingEnvironment(
            Optional<BundlingOperationDescriptor> defaultOperation,
            BiConsumer<BundlingOperationDescriptor, Options> createBundleCallback,
            Map<BundlingOperationDescriptor, List<Exception>> knownOperations) {

        this.defaultOperation = Objects.requireNonNull(defaultOperation);
        this.createBundleCallback = Objects.requireNonNull(createBundleCallback);
        this.knownOperations = Map.copyOf(knownOperations);
    }

    @Override
    public Optional<BundlingOperationDescriptor> defaultOperation() {
        return defaultOperation;
    }

    @Override
    public void createBundle(BundlingOperationDescriptor op, Options cmdline) {
        createBundleCallback.accept(throwIfUnknownBundlingOperation(op), cmdline);
    }

    @Override
    public Collection<? extends Exception> configurationErrors(BundlingOperationDescriptor op) {
        throwIfUnknownBundlingOperation(op);
        return Optional.ofNullable(knownOperations.get(op)).orElseGet(List::of);
    }

    private BundlingOperationDescriptor throwIfUnknownBundlingOperation(BundlingOperationDescriptor op) {
        if (!knownOperations.containsKey(Objects.requireNonNull(op))) {
            throw new NoSuchElementException(String.format("Unknown bunbdling operation: [%s]", op));
        }
        return op;
    }

    static StandardBundlingOperation createAppImageBundlingOperation(OperatingSystem os) {
        Objects.requireNonNull(os);
        return StandardBundlingOperation.CREATE_APP_IMAGE.stream()
                .map(StandardBundlingOperation.class::cast)
                .filter(StandardBundlingOperation.platform(os))
                .findFirst().orElseThrow();
    }


    static final class Builder {

        CliBundlingEnvironment create() {
            return new MockupCliBundlingEnvironment(
                    defaultOperation(),
                    createBundleCallback().orElse((_, _) -> {}),
                    knownOperations);
        }

        Builder defaultOperation(BundlingOperationDescriptor v) {
            defaultOperation = v;
            return this;
        }

        Builder knownOperation(BundlingOperationDescriptor v) {
            knownOperations.putIfAbsent(Objects.requireNonNull(v), List.of());
            return this;
        }

        Builder createAppImageByDefault(OperatingSystem os) {
            return defaultOperation(createAppImageBundlingOperation(os).descriptor());
        }

        Builder createAppImageByDefault() {
            return createAppImageByDefault(OperatingSystem.current());
        }

        Builder createBundleCallback(Consumer<Options> v) {
            return createBundleCallback((_, options) -> v.accept(options));
        }

        Builder createBundleCallback(BiConsumer<BundlingOperationDescriptor, Options> v) {
            createBundleCallback = v;
            return this;
        }

        Builder configurationErrors(BundlingOperationDescriptor targetOperation, List<Exception> v) {
            if (targetOperation != null) {
                knownOperations.merge(targetOperation, v, (x, y) -> {
                    List<Exception> errors = new ArrayList<>(x);
                    errors.addAll(y);
                    return errors;
                });
            } else if (!knownOperations.isEmpty()) {
                knownOperations.keySet().forEach(op -> {
                    configurationErrors(op, v);
                });
            } else {
                throw new UnsupportedOperationException("Can not set errors for unknown bundling operations");
            }
            return this;
        }

        Builder configurationErrors(BundlingOperationDescriptor targetOperation, Exception... errors) {
            return configurationErrors(targetOperation, List.of(errors));
        }

        private Optional<BiConsumer<BundlingOperationDescriptor, Options>> createBundleCallback() {
            return Optional.ofNullable(createBundleCallback);
        }

        private Optional<BundlingOperationDescriptor> defaultOperation() {
            return Optional.ofNullable(defaultOperation);
        }

        private BundlingOperationDescriptor defaultOperation;
        private BiConsumer<BundlingOperationDescriptor, Options> createBundleCallback;
        private final Map<BundlingOperationDescriptor, List<Exception>> knownOperations = new HashMap<>();
    }


    private final Optional<BundlingOperationDescriptor> defaultOperation;
    private final BiConsumer<BundlingOperationDescriptor, Options> createBundleCallback;
    private final Map<BundlingOperationDescriptor, List<Exception>> knownOperations;
}
