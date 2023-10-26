/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class LinkerOptions {

    private static final LinkerOptions EMPTY = new LinkerOptions(Map.of());
    private final Map<Class<?>, LinkerOptionImpl> optionsMap;

    private LinkerOptions(Map<Class<?>, LinkerOptionImpl> optionsMap) {
        this.optionsMap = optionsMap;
    }

    public static LinkerOptions forDowncall(FunctionDescriptor desc, Linker.Option... options) {
        return forShared(LinkerOptionImpl::validateForDowncall, desc, options);
    }

    public static LinkerOptions forUpcall(FunctionDescriptor desc, Linker.Option[] options) {
        return forShared(LinkerOptionImpl::validateForUpcall, desc, options);
    }

    private static LinkerOptions forShared(BiConsumer<LinkerOptionImpl, FunctionDescriptor> validator,
                                           FunctionDescriptor desc, Linker.Option... options) {
       Map<Class<?>, LinkerOptionImpl> optionMap = new HashMap<>();

        for (Linker.Option option : options) {
            if (optionMap.containsKey(option.getClass())) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
            LinkerOptionImpl opImpl = (LinkerOptionImpl) option;
            validator.accept(opImpl, desc);
            optionMap.put(option.getClass(), opImpl);
        }

        LinkerOptions linkerOptions = new LinkerOptions(optionMap);
        if (linkerOptions.hasCapturedCallState() && linkerOptions.isCritical()) {
            throw new IllegalArgumentException("Incompatible linker options: captureCallState, critical");
        }
        return linkerOptions;
    }

    public static LinkerOptions empty() {
        return EMPTY;
    }

    private <T extends Linker.Option> T getOption(Class<T> type) {
        return type.cast(optionsMap.get(type));
    }

    public boolean isVarargsIndex(int argIndex) {
        FirstVariadicArg fva = getOption(FirstVariadicArg.class);
        return fva != null && argIndex >= fva.index();
    }

    public boolean hasCapturedCallState() {
        return getOption(CaptureCallState.class) != null;
    }

    public Stream<CapturableState> capturedCallState() {
        CaptureCallState stl = getOption(CaptureCallState.class);
        return stl == null ? Stream.empty() : stl.saved().stream();
    }

    public boolean isVariadicFunction() {
        FirstVariadicArg fva = getOption(FirstVariadicArg.class);
        return fva != null;
    }

    public int firstVariadicArgIndex() {
        return getOption(FirstVariadicArg.class).index();
    }

    public boolean isCritical() {
        Critical c = getOption(Critical.class);
        return c != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LinkerOptions that
                && Objects.equals(optionsMap, that.optionsMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionsMap);
    }

    public sealed interface LinkerOptionImpl extends Linker.Option
            permits CaptureCallState, FirstVariadicArg, Critical {
        default void validateForDowncall(FunctionDescriptor descriptor) {
            throw new IllegalArgumentException("Not supported for downcall: " + this);
        }

        default void validateForUpcall(FunctionDescriptor descriptor) {
            throw new IllegalArgumentException("Not supported for upcall: " + this);
        }
    }

    public record FirstVariadicArg(int index) implements LinkerOptionImpl {
        @Override
        public void validateForDowncall(FunctionDescriptor descriptor) {
            if (index < 0 || index > descriptor.argumentLayouts().size()) {
                throw new IllegalArgumentException("Index '" + index + "' not in bounds for descriptor: " + descriptor);
            }
        }
    }

    public record CaptureCallState(Set<CapturableState> saved) implements LinkerOptionImpl {
        @Override
        public void validateForDowncall(FunctionDescriptor descriptor) {
            // done during construction
        }
    }

    public record Critical() implements LinkerOptionImpl {
        public static Critical INSTANCE = new Critical();

        @Override
        public void validateForDowncall(FunctionDescriptor descriptor) {
            // always allowed
        }
    }
}
