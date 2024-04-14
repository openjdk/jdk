/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
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

package jdk.jfr.internal.settings;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.util.Tokenizer;

import java.text.ParseException;
import java.util.EnumSet;

public enum SelectorValue {
    CONTEXT("if-context", (byte)0x1),
    TRIGGERED("if-triggered", (byte)0x2),
    ALL("all", (byte)0x0);

    public final String key;
    public final byte value;

    SelectorValue(String key, byte value) {
        this.key = key;
        this.value = value;
    }

    public static EnumSet<SelectorValue> of(String option) {
        EnumSet<SelectorValue> set = EnumSet.noneOf(SelectorValue.class);
        try (Tokenizer tokenizer = new Tokenizer(option, ',')) {
            while (tokenizer.hasNext()) {
                String token = tokenizer.next();
                if (token.equals(ALL.key)) {
                    set.add(ALL);
                } else if (token.equals(CONTEXT.key)) {
                    set.add(CONTEXT);
                } else if (token.equals(TRIGGERED.key)) {
                    set.add(TRIGGERED);
                } else {
                    Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Unknown option \"" + token + "\". Using \"" + SelectorValue.ALL + "\" instead");
                    set.add(ALL);
                }
            }
        } catch (ParseException e) {
            Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "Error while parsing \"" + option + "\". Using \" + " + SelectorValue.ALL + "\" instead.\n" + e);
        }
        return set;
    }

    public byte getValue() {
        return value;
    }
}
