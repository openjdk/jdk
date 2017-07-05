/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.internal;

import com.sun.jmx.snmp.SnmpEngine;
import com.sun.jmx.snmp.SnmpUnknownModelException;
import java.util.Hashtable;
/**
 * SNMP sub system interface. To allow engine framework integration, a sub system must implement this interface. A sub system is a model manager. Every model is identified by an ID. A sub system can retrieve a previously registered model using this ID.
 * <P> Every sub system is associated to its SNMP engine.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */
public interface SnmpSubSystem {
    /**
     * Returns the associated engine.
     * @return The engine.
     */
    public SnmpEngine getEngine();

    /**
     * Adds a model to this sub system.
     * @param id The model ID.
     * @param model The model to add.
     */
    public void addModel(int id, SnmpModel model);

    /**
     * Removes a model from this sub system.
     * @param id The model ID to remove.
     * @return The removed model.
     */
    public SnmpModel removeModel(int id) throws SnmpUnknownModelException;

    /**
     * Gets a model from this sub system.
     * @param id The model ID to get.
     * @return The model.
     */
    public SnmpModel getModel(int id) throws SnmpUnknownModelException;

    /**
     * Returns the set of model Ids that have been registered within the sub system.
     */
    public int[] getModelIds();

    /**
     * Returns the set of model names that have been registered within the sub system.
     */
    public String[] getModelNames();
}
