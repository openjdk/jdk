/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Linker;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LinkerOptions {

    private static final LinkerOptions EMPTY = LinkerOptions.of();
    private final Map<Class<?>, Linker.Option> optionsMap;

    private LinkerOptions(Map<Class<?>, Linker.Option> optionsMap) {
        this.optionsMap = optionsMap;
    }

    public static LinkerOptions of(Linker.Option... options) {
        Map<Class<?>, Linker.Option> optionMap = new HashMap<>();

        for (Linker.Option option : options) {
            if (optionMap.containsKey(option.getClass())) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
            optionMap.put(option.getClass(), option);
        }

        return new LinkerOptions(optionMap);
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

    public record FirstVariadicArg(int index) implements Linker.Option { }
}
