/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.wixui;

import java.util.Objects;

public record Publish(Control control, String condition, int order) {

    public Publish {
        Objects.requireNonNull(control);
        Objects.requireNonNull(condition);
        if (order < 0) {
            throw new IllegalArgumentException("Negative order");
        }
    }

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder build() {
        return new Builder();
    }

    static final class Builder {

        private Builder() {
            order(0);
            next();
            condition("1");
        }

        private Builder(Publish publish) {
            order(publish.order);
            control(publish.control);
            condition(publish.condition);
        }

        Publish create() {
            return new Publish(control, condition, order);
        }

        Builder control(Control v) {
            control = v;
            return this;
        }

        Builder next() {
            return control(StandardControl.NEXT);
        }

        Builder back() {
            return control(StandardControl.BACK);
        }

        Builder condition(String v) {
            condition = v;
            return this;
        }

        Builder order(int v) {
            order = v;
            return this;
        }

        private int order;
        private Control control;
        private String condition;
    }
}
