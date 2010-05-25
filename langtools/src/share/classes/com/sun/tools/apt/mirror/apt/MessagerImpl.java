/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.apt;

import javax.tools.JavaFileObject;
import com.sun.mirror.apt.Messager;
import com.sun.tools.apt.mirror.util.SourcePositionImpl;
import com.sun.mirror.util.SourcePosition;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;
import com.sun.tools.apt.util.Bark;


/**
 * Implementation of Messager.
 */
@SuppressWarnings("deprecation")
public class MessagerImpl implements Messager {
    private final Bark bark;

    private static final Context.Key<MessagerImpl> messagerKey =
            new Context.Key<MessagerImpl>();

    public static MessagerImpl instance(Context context) {
        MessagerImpl instance = context.get(messagerKey);
        if (instance == null) {
            instance = new MessagerImpl(context);
        }
        return instance;
    }

    private MessagerImpl(Context context) {
        context.put(messagerKey, this);
        bark = Bark.instance(context);
    }


    /**
     * {@inheritDoc}
     */
    public void printError(String msg) {
        bark.aptError("Messager", msg);
    }

    /**
     * {@inheritDoc}
     */
    public void printError(SourcePosition pos, String msg) {
        if (pos instanceof SourcePositionImpl) {
            SourcePositionImpl posImpl = (SourcePositionImpl) pos;
            JavaFileObject prev = bark.useSource(posImpl.getSource());
            bark.aptError(posImpl.getJavacPosition(), "Messager", msg);
            bark.useSource(prev);
        } else
            printError(msg);
    }

    /**
     * {@inheritDoc}
     */
    public void printWarning(String msg) {
        bark.aptWarning("Messager", msg);
    }

    /**
     * {@inheritDoc}
     */
    public void printWarning(SourcePosition pos, String msg) {
        if (pos instanceof SourcePositionImpl) {
            SourcePositionImpl posImpl = (SourcePositionImpl) pos;
            JavaFileObject prev = bark.useSource(posImpl.getSource());
            bark.aptWarning(posImpl.getJavacPosition(), "Messager", msg);
            bark.useSource(prev);
        } else
            printWarning(msg);
    }

    /**
     * {@inheritDoc}
     */
    public void printNotice(String msg) {
        bark.aptNote("Messager", msg);
    }

    /**
     * {@inheritDoc}
     */
    public void printNotice(SourcePosition pos, String msg) {
        if (pos instanceof SourcePositionImpl) {
            SourcePositionImpl posImpl = (SourcePositionImpl) pos;
            JavaFileObject prev = bark.useSource(posImpl.getSource());
            bark.aptNote(posImpl.getJavacPosition(), "Messager", msg);
            bark.useSource(prev);
        } else
            printNotice(msg);
    }
}
