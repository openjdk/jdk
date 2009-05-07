/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.rngom.nc;

import javax.xml.namespace.QName;

/**
 * Visitor pattern over {@link NameClass} and its subclasses.
 */
public interface NameClassVisitor<V> {
    /**
     * Called for {@link ChoiceNameClass}
     */
    V visitChoice(NameClass nc1, NameClass nc2);
    /**
     * Called for {@link NsNameClass}
     */
    V visitNsName(String ns);
    /**
     * Called for {@link NsNameExceptNameClass}
     */
    V visitNsNameExcept(String ns, NameClass nc);
    /**
     * Called for {@link NameClass#ANY}
     */
    V visitAnyName();
    /**
     * Called for {@link AnyNameExceptNameClass}
     */
    V visitAnyNameExcept(NameClass nc);
    /**
     * Called for {@link SimpleNameClass}
     */
    V visitName(QName name);
    /**
     * Called for {@link NameClass#NULL}.
     */
    V visitNull();
}
