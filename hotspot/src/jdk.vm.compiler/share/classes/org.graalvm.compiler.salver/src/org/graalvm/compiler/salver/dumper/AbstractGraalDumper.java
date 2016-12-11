/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.salver.dumper;

import java.io.IOException;

import org.graalvm.compiler.salver.Salver;
import org.graalvm.compiler.salver.data.DataDict;

public class AbstractGraalDumper extends AbstractSerializerDumper {

    public static final String EVENT_NAMESPACE = "graal";

    private int eventCounter;

    public void beginDump() throws IOException {
        beginDump(EVENT_NAMESPACE);
    }

    protected void beginDump(String namespace) throws IOException {
        beginDump(namespace, getBeginDumpDataDict());
    }

    protected void beginDump(String namespace, DataDict dataDict) throws IOException {
        DataDict eventDict = createEventDict(":begin");
        eventDict.put("@time", System.currentTimeMillis());
        eventDict.put("@ecid", Salver.ECID);
        if (namespace != null) {
            eventDict.put("@namespace", namespace);
        }
        if (dataDict != null) {
            eventDict.put("@data", dataDict);
        }
        serializeAndFlush(eventDict);
    }

    protected DataDict getBeginDumpDataDict() {
        DataDict dataDict = new DataDict();
        dataDict.put("dumper", getClass().getSimpleName());
        dataDict.put("thread", Thread.currentThread().getName());
        return dataDict;
    }

    public void endDump() throws IOException {
        DataDict eventDict = createEventDict(":end");
        eventDict.put("@time", System.currentTimeMillis());
        serializeAndFlush(eventDict);
    }

    @Override
    public void close() throws IOException {
        endDump();
    }

    protected DataDict createEventDict(String name) {
        DataDict eventDict = new DataDict();
        eventDict.put("@event", name);
        eventDict.put("@n", eventCounter++);
        return eventDict;
    }

    protected DataDict createEventDict(String name, DataDict data) {
        DataDict eventDict = createEventDict(name);
        eventDict.put("@data", data);
        return eventDict;
    }
}
