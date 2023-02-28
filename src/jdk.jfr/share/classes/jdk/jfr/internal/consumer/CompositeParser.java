/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.consumer;

import java.io.IOException;
import java.util.ArrayList;

public final class CompositeParser extends Parser {
    final Parser[] parsers;

    public CompositeParser(Parser[] valueParsers) {
        this.parsers = valueParsers;
    }

    @Override
    public Object parse(RecordingInput input) throws IOException {
        final Object[] values = new Object[parsers.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = parsers[i].parse(input);
        }
        return values;
    }

    @Override
    public void skip(RecordingInput input) throws IOException {
        for (int i = 0; i < parsers.length; i++) {
            parsers[i].skip(input);
        }
    }

    @Override
    public Object parseReferences(RecordingInput input) throws IOException {
        return parseReferences(input, parsers);
    }

    static Object parseReferences(RecordingInput input, Parser[] parsers) throws IOException {
        ArrayList<Object> refs = new ArrayList<>(parsers.length);
        for (int i = 0; i < parsers.length; i++) {
            Object ref = parsers[i].parseReferences(input);
            if (ref != null) {
                refs.add(ref);
            }
        }
        if (refs.isEmpty()) {
            return null;
        }
        if (refs.size() == 1) {
            return refs.get(0);
        }
        return refs.toArray();
    }
}