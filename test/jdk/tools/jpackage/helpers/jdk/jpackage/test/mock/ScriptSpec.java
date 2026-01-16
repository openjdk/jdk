/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Specification of a {@link Script}.
 */
public record ScriptSpec(List<Item> items, boolean loop) {

    public ScriptSpec {
        Objects.requireNonNull(items);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(items.toString());
        if (loop) {
            // Append "Clockwise Gapped Circle Arrow" Unicode symbol.
            sb.append('(').appendCodePoint(0x27F3).append(')');
        }
        return sb.toString();
    }

    public Script create() {
        var script = Script.build();
        items.forEach(item -> {
            item.applyTo(script, loop);
        });
        if (loop) {
            return script.createLoop();
        } else {
            return script.createSequence();
        }
    }

    public Collection<Path> commandNames() {
        return items.stream().map(Item::mockSpec).map(CommandMockSpec::name).distinct().toList();
    }

    private record Item(CommandMockSpec mockSpec, int repeatCount, boolean detailedDescription) {

        private Item {
            Objects.requireNonNull(mockSpec);
            if (repeatCount < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            if (detailedDescription) {
                sb.append(mockSpec);
            } else if (mockSpec.isDefaultMockName()) {
                sb.append(mockSpec.name());
            } else {
                sb.append(mockSpec.mockName());
            }
            if (repeatCount > 0) {
                sb.append('(').append(repeatCount + 1).append(')');
            }
            return sb.toString();
        }

        void applyTo(Script.Builder script, boolean loopScript) {
            var pred = Script.cmdlineStartsWith(mockSpec.name());

            var mockBuilder = mockSpec.toCommandMockBuilder();
            if (loopScript) {
                script.map(pred, mockBuilder.repeat(repeatCount).create());
            } else {
                mockBuilder.repeat(0);
                IntStream.rangeClosed(0, repeatCount).forEach(_ -> {
                    script.map(pred, mockBuilder.create());
                });
            }
        }

    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        private Builder() {
        }

        public ScriptSpec create() {
            return new ScriptSpec(List.copyOf(items), loop);
        }

        public Builder loop(boolean v) {
            loop = v;
            return this;
        }

        public Builder loop() {
            return loop(true);
        }

        public final class ItemBuilder {

            private ItemBuilder(CommandMockSpec mockSpec) {
                this.mockSpec = Objects.requireNonNull(mockSpec);
            }

            public Builder add() {
                items.add(new Item(mockSpec, repeat, detailedDescription));
                return Builder.this;
            }

            public ItemBuilder repeat(int v) {
                if (repeat < 0) {
                    throw new IllegalArgumentException();
                }
                repeat = v;
                return this;
            }

            public ItemBuilder detailedDescription(boolean v) {
                detailedDescription = v;
                return this;
            }

            public ItemBuilder detailedDescription() {
                return detailedDescription(true);
            }

            private final CommandMockSpec mockSpec;
            private int repeat;
            private boolean detailedDescription;
        }

        public Builder add(CommandMockSpec mockSpec) {
            return build(mockSpec).add();
        }

        public Builder addLoop(CommandMockSpec mockSpec) {
            return build(mockSpec).add();
        }

        public ItemBuilder build(CommandMockSpec mockSpec) {
            return new ItemBuilder(mockSpec);
        }

        private final List<Item> items = new ArrayList<>();
        private boolean loop;
    }
}
