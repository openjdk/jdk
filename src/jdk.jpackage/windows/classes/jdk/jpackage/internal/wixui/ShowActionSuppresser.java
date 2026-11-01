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

import static java.util.Comparator.comparing;

import java.util.Comparator;
import java.util.Objects;

public record ShowActionSuppresser(Dialog dialog, Dialog anchor, Order order) {

    public enum Order {
        AFTER,
        ;
    }

    public ShowActionSuppresser {
        Objects.requireNonNull(order);
        validate(dialog);
        validate(anchor);
    }

    static Builder suppressShowAction(WixDialog dialog) {
        return new Builder(dialog);
    }

    static final class Builder {

        private Builder(WixDialog dialog) {
            this.dialog = Objects.requireNonNull(dialog);
        }

        ShowActionSuppresser after(WixDialog anchor) {
            return new ShowActionSuppresser(dialog, anchor, Order.AFTER);
        }

        private final WixDialog dialog;
    }

    private static void validate(Dialog v) {
        if (!(Objects.requireNonNull(v) instanceof WixDialog)) {
            throw new IllegalArgumentException();
        }
    }

    public static final Comparator<ShowActionSuppresser> DEFAULT_COMPARATOR =
            comparing(ShowActionSuppresser::dialog, Dialog.DEFAULT_COMPARATOR)
                    .thenComparing(comparing(ShowActionSuppresser::anchor, Dialog.DEFAULT_COMPARATOR))
                    .thenComparing(comparing(ShowActionSuppresser::order));
}
