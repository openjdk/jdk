/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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



package com.sun.org.glassfish.external.probe.provider;

/**
 *
 * @author abbagani
 */
public class StatsProviderInfo {

    public StatsProviderInfo(String configElement, PluginPoint pp,
                                    String subTreeRoot, Object statsProvider){
        this(configElement, pp, subTreeRoot, statsProvider, null);
    }

    public StatsProviderInfo(String configElement, PluginPoint pp,
                                    String subTreeRoot, Object statsProvider,
                                    String invokerId){
        this.configElement = configElement;
        this.pp = pp;
        this.subTreeRoot = subTreeRoot;
        this.statsProvider = statsProvider;
        this.invokerId = invokerId;
    }

    private String configElement;
    private PluginPoint pp;
    private String subTreeRoot;
    private Object statsProvider;
    private String configLevelStr = null;
    private final String invokerId;

    public String getConfigElement() {
        return configElement;
    }

    public PluginPoint getPluginPoint() {
        return pp;
    }

    public String getSubTreeRoot() {
        return subTreeRoot;
    }

    public Object getStatsProvider() {
        return statsProvider;
    }

    public String getConfigLevel() {
        return configLevelStr;
    }

    public void setConfigLevel(String configLevelStr) {
        this.configLevelStr = configLevelStr;
    }

    public String getInvokerId() {
        return invokerId;
    }

}
