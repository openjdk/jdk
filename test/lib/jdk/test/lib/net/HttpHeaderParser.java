/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpHeaderParser {
    private Map <String, List<String>>  headerMap = new LinkedHashMap<>();
    private InputStream is;
    private List <String> keyList = new ArrayList<>();
    private String requestDetails;

    public HttpHeaderParser(InputStream is) throws IOException {
        this.is = is;
        String headerString = "";
            BufferedReader br = new BufferedReader(new InputStreamReader(this.is));
            while(true) {
                headerString = br.readLine();
                if(headerString == null || headerString.isBlank()) {
                    break;
                }
                if(headerString.contains(": ")) {
                    String key = headerString.substring(0, headerString.indexOf(": "));
                    List <String> values = new ArrayList<>();
                    String headerValue = headerString.substring(headerString.indexOf(": ") + 2);
                    String [] valueArray = headerValue.split(",");
                    for(String value : valueArray) {
                        values.add(value.trim());
                    }
                    headerMap.put(key.trim(), values);
                    keyList.add(key.trim());
                } else {
                    requestDetails = headerString.trim();
                }
            }
    }
    public List<String> getHeaderValue(String key) {
        if(headerMap.containsKey(key)) {
            return headerMap.get(key);
        }
        return null;
    }
    public List<String> getValue(int id) {
        String key = keyList.get(id);
        return headerMap.get(key);
    }

    public String getRequestDetails() {
        return requestDetails;
    }
}
