/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package nsk.share.aod;

import nsk.share.*;
import java.util.*;

public class AODRunnerArgParser extends ArgumentParser {

    public static final String jarAgentParam = "ja";

    public static final String nativeAgentParam = "na";

    public static final String targetAppParam = "target";

    public static final String javaOptsParam = "javaOpts";

    public static final String testedJdkParam = "jdk";

    private static List<String> supportedOptions;

    static {
        supportedOptions = new ArrayList<String>();
        supportedOptions.add(jarAgentParam);
        supportedOptions.add(nativeAgentParam);
        supportedOptions.add(targetAppParam);
        supportedOptions.add(javaOptsParam);
        supportedOptions.add(testedJdkParam);
    }

    private List<AgentInformation> agents;

    public AODRunnerArgParser(String[] args) {
        super(args);
    }

    protected boolean checkOption(String option, String value) {
        if (super.checkOption(option, value))
            return true;

        if (!supportedOptions.contains(option))
            return false;

        if (option.equals(jarAgentParam)) {
            addAgentInfo(true, value);
        }

        if (option.equals(nativeAgentParam)) {
            addAgentInfo(false, value);
        }

        return true;
    }

    protected void checkOptions() {
        if (agents == null) {
            agents = new ArrayList<AgentInformation>();
        }
    }

    private void addAgentInfo(boolean jarAgent, String unsplittedAgentsString) {
        if (agents == null) {
            agents = new ArrayList<AgentInformation>();
        }

        String agentStrings[];

        if (unsplittedAgentsString.contains(","))
            agentStrings = unsplittedAgentsString.split(",");
        else
            agentStrings = new String[]{unsplittedAgentsString};

        for (String agentString : agentStrings) {
            int index = agentString.indexOf('=');

            if (index > 0) {
                String pathToAgent = agentString.substring(0, index);
                String options = agentString.substring(index + 1);
                agents.add(new AgentInformation(jarAgent, pathToAgent, options));
            } else {
                agents.add(new AgentInformation(jarAgent, agentString, null));
            }
        }
    }

    public String getTargetApp() {
        if (!options.containsKey(targetAppParam))
            throw new TestBug("Target application isn't specified");

        return options.getProperty(targetAppParam);
    }

    public String getTestedJDK() {
        if (!options.containsKey(testedJdkParam))
            throw new TestBug("Tested JDK isn't specified");

        return options.getProperty(testedJdkParam);
    }

    public String getJavaOpts() {
        return options.getProperty(javaOptsParam, "");
    }

    public List<AgentInformation> getAgents() {
        return agents;
    }
}
