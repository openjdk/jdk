/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.AnnotatedText;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Annotate implements Action {

    private final Runnable action;

    public <S> Annotate(S obj, Pattern pattern, AnnotatedText<S> text) {
        // This *constructor* is generified and the generic parameter is
        // captured by the Runnable to type-safely call text.annotate(obj)
        // later. An alternative would be to generify this *class* so as to
        // capture the generic parameter in this class' instance fields.
        // However, generifying the class would unduly force its clients to deal
        // with the generic parameter, whose *sole* purpose is to ensure that
        // the passed obj is of the type of objects that the passed text can be
        // annotated with.
        action = new Runnable() {
            @Override
            public void run() {
                Set<S> s = Set.of(obj);
                Matcher matcher = pattern.matcher(text.asCharSequence());
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    text.subText(start, end).annotate(s);
                }
            }
        };
    }

    @Override
    public void perform() {
        action.run();
    }
}
