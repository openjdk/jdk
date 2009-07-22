/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.beans.finder;

import java.beans.PersistenceDelegate;
import java.util.HashMap;
import java.util.Map;

/**
 * This is utility class that provides functionality
 * to find a {@link PersistenceDelegate} for a JavaBean specified by its type.
 *
 * @since 1.7
 *
 * @author Sergey A. Malenkov
 */
public final class PersistenceDelegateFinder
        extends InstanceFinder<PersistenceDelegate> {

    private final Map<Class<?>, PersistenceDelegate> registry;

    public PersistenceDelegateFinder() {
        super(PersistenceDelegate.class, true, "PersistenceDelegate");
        this.registry = new HashMap<Class<?>, PersistenceDelegate>();
    }

    public void register(Class<?> type, PersistenceDelegate delegate) {
        if (delegate != null) {
            this.registry.put(type, delegate);
        }
        else {
            this.registry.remove(type);
        }
    }

    @Override
    public PersistenceDelegate find(Class<?> type) {
        PersistenceDelegate delegate = this.registry.get(type);
        return (delegate != null) ? delegate : super.find(type);
    }
}
