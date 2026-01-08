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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;


public final class Codesign {

    public static final class CodesignException extends Exception {

        CodesignException(String[] output) {
            this.output = output;
        }

        String[] getOutput() {
            return output;
        }

        private final String[] output;

        private static final long serialVersionUID = 1L;
    }

    public static final class Builder {

        private Builder(Supplier<List<String>> args) {
            this.args = Objects.requireNonNull(args);
        }

        Codesign create() {
            List<String> cmdline = new ArrayList<>();
            cmdline.add("/usr/bin/codesign");
            cmdline.addAll(args.get());
            if (force) {
                cmdline.add("--force");
            }

            return new Codesign(cmdline, quiet ? exec -> {
                exec.setQuiet(true);
            } : null);
        }

        public Builder force(boolean v) {
            force = v;
            return this;
        }

        public Builder quiet(boolean v) {
            quiet = v;
            return this;
        }

        private final Supplier<List<String>> args;
        private boolean force;
        private boolean quiet;
    }

    public static Builder build(Supplier<List<String>> args) {
        return new Builder(args);
    }

    public void applyTo(Path path) throws IOException, CodesignException {

        var exec = Executor.of(Stream.concat(
                cmdline.stream(),
                Stream.of(path.toString())).toArray(String[]::new)
        ).saveOutput(true);
        configureExecutor.ifPresent(configure -> configure.accept(exec));

        if (exec.execute() != 0) {
            throw new CodesignException(exec.getOutput().toArray(String[]::new));
        }
    }

    public Consumer<Path> asConsumer() {
        return toConsumer(this::applyTo);
    }

    private Codesign(List<String> cmdline, Consumer<Executor> configureExecutor) {
        this.cmdline = Objects.requireNonNull(cmdline);
        this.configureExecutor = Optional.ofNullable(configureExecutor);
    }

    private final List<String> cmdline;
    private final Optional<Consumer<Executor>> configureExecutor;
}
