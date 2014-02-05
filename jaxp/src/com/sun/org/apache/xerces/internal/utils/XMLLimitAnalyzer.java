/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.org.apache.xerces.internal.utils;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.utils.XMLSecurityManager.Limit;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper for analyzing entity expansion limits
 *
 * @author Joe Wang Oracle Corp.
 *
 */
public final class XMLLimitAnalyzer {

    /**
     * Map old property names with the new ones
     */
    public static enum NameMap {
        ENTITY_EXPANSION_LIMIT(Constants.SP_ENTITY_EXPANSION_LIMIT, Constants.ENTITY_EXPANSION_LIMIT),
        MAX_OCCUR_NODE_LIMIT(Constants.SP_MAX_OCCUR_LIMIT, Constants.MAX_OCCUR_LIMIT),
        ELEMENT_ATTRIBUTE_LIMIT(Constants.SP_ELEMENT_ATTRIBUTE_LIMIT, Constants.ELEMENT_ATTRIBUTE_LIMIT);

        final String newName;
        final String oldName;

        NameMap(String newName, String oldName) {
            this.newName = newName;
            this.oldName = oldName;
        }

        String getOldName(String newName) {
            if (newName.equals(this.newName)) {
                return oldName;
            }
            return null;
        }
    }

    /**
     * Max value accumulated for each property
     */
    private final int[] values;
    /**
     * Names of the entities corresponding to their max values
     */
    private final String[] names;
    /**
     * Total value of accumulated entities
     */
    private final int[] totalValue;

    /**
     * Maintain values of the top 10 elements in the process of parsing
     */
    private final Map[] caches;

    private String entityStart, entityEnd;
    /**
     * Default constructor. Establishes default values for known security
     * vulnerabilities.
     */
    public XMLLimitAnalyzer() {
        values = new int[Limit.values().length];
        totalValue = new int[Limit.values().length];
        names = new String[Limit.values().length];
        caches = new Map[Limit.values().length];
    }

    /**
     * Add the value to the current max count for the specified property
     * To find the max value of all entities, set no limit
     *
     * @param limit the type of the property
     * @param entityName the name of the entity
     * @param value the value of the entity
     */
    public void addValue(Limit limit, String entityName, int value) {
        addValue(limit.ordinal(), entityName, value);
    }

    /**
     * Add the value to the current count by the index of the property
     * @param index the index of the property
     * @param entityName the name of the entity
     * @param value the value of the entity
     */
    public void addValue(int index, String entityName, int value) {
        if (index == Limit.ENTITY_EXPANSION_LIMIT.ordinal() ||
                index == Limit.MAX_OCCUR_NODE_LIMIT.ordinal() ||
                index == Limit.ELEMENT_ATTRIBUTE_LIMIT.ordinal()) {
            totalValue[index] += value;
            return;
        }

        Map<String, Integer> cache;
        if (caches[index] == null) {
            cache = new HashMap<String, Integer>(10);
            caches[index] = cache;
        } else {
            cache = caches[index];
        }

        int accumulatedValue = value;
        if (cache.containsKey(entityName)) {
            accumulatedValue += cache.get(entityName).intValue();
            cache.put(entityName, Integer.valueOf(accumulatedValue));
        } else {
            cache.put(entityName, Integer.valueOf(value));
        }

        if (accumulatedValue > values[index]) {
            values[index] = accumulatedValue;
            names[index] = entityName;
        }


        if (index == Limit.GENERAL_ENTITY_SIZE_LIMIT.ordinal() ||
                index == Limit.PARAMETER_ENTITY_SIZE_LIMIT.ordinal()) {
            totalValue[Limit.TOTAL_ENTITY_SIZE_LIMIT.ordinal()] += value;
        }
    }

    /**
     * Return the value of the current max count for the specified property
     *
     * @param limit the property
     * @return the value of the property
     */
    public int getValue(Limit limit) {
        return values[limit.ordinal()];
    }

    public int getValue(int index) {
        return values[index];
    }
    /**
     * Return the total value accumulated so far
     *
     * @param limit the property
     * @return the accumulated value of the property
     */
    public int getTotalValue(Limit limit) {
        return totalValue[limit.ordinal()];
    }

    public int getTotalValue(int index) {
        return totalValue[index];
    }
    /**
     * Return the current max value (count or length) by the index of a property
     * @param index the index of a property
     * @return count of a property
     */
    public int getValueByIndex(int index) {
        return values[index];
    }

    public void startEntity(String name) {
        entityStart = name;
    }

    public boolean isTracking(String name) {
        if (entityStart == null) {
            return false;
        }
        return entityStart.equals(name);
    }
    /**
     * Stop tracking the entity
     * @param limit the limit property
     * @param name the name of an entity
     */
    public void endEntity(Limit limit, String name) {
        entityStart = "";
        Map<String, Integer> cache = caches[limit.ordinal()];
        if (cache != null) {
            cache.remove(name);
        }
    }

    public void debugPrint(XMLSecurityManager securityManager) {
        Formatter formatter = new Formatter();
        System.out.println(formatter.format("%30s %15s %15s %15s %30s",
                "Property","Limit","Total size","Size","Entity Name"));

        for (Limit limit : Limit.values()) {
            formatter = new Formatter();
            System.out.println(formatter.format("%30s %15d %15d %15d %30s",
                    limit.name(),
                    securityManager.getLimit(limit),
                    totalValue[limit.ordinal()],
                    values[limit.ordinal()],
                    names[limit.ordinal()]));
        }
    }
}
