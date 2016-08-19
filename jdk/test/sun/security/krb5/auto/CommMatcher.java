/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches the krb5 debug output:
 * >>> KDCCommunication: kdc=host UDP:11555, timeout=100,Attempt =1, #bytes=138
 *
 * Example:
 *   CommMatcher cm = new CommMatcher();
 *   cm.addPort(12345).addPort(23456);
 *   for (String line : debugOutput) {
 *       if (cm.match(line)) {
 *           println("KDC: %c, %s, Timeout: %d\n",
 *              cm.kdc(), cm.protocol(), cm.timeout());
 *       }
 *   }
 */
public class CommMatcher {

    static final Pattern re = Pattern.compile(
            ">>> KDCCommunication: kdc=\\S+ (TCP|UDP):(\\d+), " +
                    "timeout=(\\d+),Attempt\\s*=(\\d+)");

    List<Integer> kdcPorts = new ArrayList<>();
    Matcher matcher;

    /**
     * Add KDC ports one by one. The 1st KDC will be 'a' in {@link #kdc()},
     * 2nd is 'b', etc, etc.
     */
    public CommMatcher addPort(int port) {
        if (port > 0) {
            kdcPorts.add(port);
        } else {
            kdcPorts.clear();
        }
        return this;
    }

    public boolean match(String line) {
        matcher = re.matcher(line);
        return matcher.find();
    }

    public String protocol() {
        return matcher.group(1);
    }

    public char kdc() {
        int port = Integer.parseInt(matcher.group(2));
        return (char)(kdcPorts.indexOf(port) + 'a');
    }

    public int timeout() {
        return BadKdc.toSymbolicSec(Integer.parseInt(matcher.group(3)));
    }

    public int attempt() {
        return Integer.parseInt(matcher.group(4));
    }
}
