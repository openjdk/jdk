/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.api;

import com.sun.tools.javac.util.ClientCodeException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.tools.DocumentationTool.DocumentationTask;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javadoc.Start;
import java.util.Collections;

import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

/**
 * Provides access to functionality specific to the JDK documentation tool,
 * javadoc.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class JavadocTaskImpl implements DocumentationTask {
    private final AtomicBoolean used = new AtomicBoolean();

    private final Context context;
    private Class<?> docletClass;
    private Iterable<String> options;
    private Iterable<? extends JavaFileObject> fileObjects;
    private Locale locale;

    public JavadocTaskImpl(Context context, Class<?> docletClass,
            Iterable<String> options, Iterable<? extends JavaFileObject> fileObjects) {
        this.context = context;
        this.docletClass = docletClass;

        this.options = (options == null) ? Collections.<String>emptySet()
                : nullCheck(options);
        this.fileObjects = (fileObjects == null) ? Collections.<JavaFileObject>emptySet()
                : nullCheck(fileObjects);
        setLocale(Locale.getDefault());
    }

    @DefinedBy(Api.COMPILER)
    public void setLocale(Locale locale) {
        if (used.get())
            throw new IllegalStateException();
        this.locale = locale;
    }

    @DefinedBy(Api.COMPILER)
    public Boolean call() {
        if (!used.getAndSet(true)) {
            initContext();
            Start jdoc = new Start(context);
            try {
                return jdoc.begin(docletClass, options, fileObjects);
            } catch (ClientCodeException e) {
                throw new RuntimeException(e.getCause());
            }
        } else {
            throw new IllegalStateException("multiple calls to method 'call'");
        }
    }

    private void initContext() {
        //initialize compiler's default locale
        context.put(Locale.class, locale);
    }

    private static <T> Iterable<T> nullCheck(Iterable<T> items) {
        for (T item: items) {
            if (item == null)
                throw new NullPointerException();
        }
        return items;
    }
}
