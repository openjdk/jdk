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
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;

import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.salver.data.DataDict;
import org.graalvm.compiler.salver.data.DataList;
import org.graalvm.compiler.salver.util.MethodContext;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class AbstractMethodScopeDumper extends AbstractGraalDumper {

    protected MethodContext previousMethodContext;

    protected final Deque<Integer> pathStack = new ArrayDeque<>();
    protected int pathCounter;
    protected final Deque<Integer> itemIdStack = new ArrayDeque<>();
    protected int itemIdCounter;

    protected void resolveMethodContext() throws IOException {
        // Get all current JavaMethod instances in the context.
        MethodContext methodContext = new MethodContext();
        // Reverse list such that inner method comes after outer method.
        Collections.reverse(methodContext);

        int size = methodContext.size();
        int previousSize = previousMethodContext != null ? previousMethodContext.size() : 0;
        // Check for method scopes that must be closed since the previous dump.
        for (int i = 0; i < previousSize; ++i) {
            if (i >= size || !methodContext.itemEquals(i, previousMethodContext)) {
                for (int inlineDepth = previousSize - 1; inlineDepth >= i; --inlineDepth) {
                    closeScope();
                }
                break;
            }
        }
        // Check for method scopes that must be opened since the previous dump.
        for (int i = 0; i < size; ++i) {
            if (i >= previousSize || !methodContext.itemEquals(i, previousMethodContext)) {
                for (int inlineDepth = i; inlineDepth < size; ++inlineDepth) {
                    openScope(methodContext.get(inlineDepth));
                }
                break;
            }
        }
        // Save inline context for next dump.
        previousMethodContext = methodContext;
    }

    protected void openScope(MethodContext.Item item) throws IOException {
        int debugId = item.getDebugId();
        int id = debugId != -1 ? debugId : pathCounter;

        pathStack.push(id);
        itemIdStack.push(itemIdCounter);
        pathCounter = 0;
        itemIdCounter = 0;

        processMethod(item.getMethod(), id, item.getName());
    }

    @SuppressWarnings("unused")
    protected void closeScope() throws IOException {
        if (!pathStack.isEmpty()) {
            pathCounter = pathStack.pop();
            pathCounter++;
        }
        if (!itemIdStack.isEmpty()) {
            itemIdCounter = itemIdStack.pop();
        }
    }

    protected void processMethod(JavaMethod method, int id, String name) throws IOException {
        DataDict dataDict = new DataDict();
        dataDict.put("id", id);
        dataDict.put("name", name);

        if (method instanceof ResolvedJavaMethod) {
            DataDict methodDict = new DataDict();
            dataDict.put("method", methodDict);

            ResolvedJavaMethod resolvedMethod = (ResolvedJavaMethod) method;

            methodDict.put("modifiers", Modifier.toString(resolvedMethod.getModifiers()));
            methodDict.put("code", new BytecodeDisassembler(false).disassemble(resolvedMethod));
        }
        serializeAndFlush(createEventDictWithId("method", dataDict, false));
    }

    protected int nextItemId() {
        return itemIdCounter++;
    }

    protected DataDict createEventDictWithId(String name, boolean isItem) {
        DataDict eventDict = createEventDict(name);

        DataDict idDict = new DataDict();
        eventDict.put("@id", idDict);

        DataList pathList = new DataList();
        idDict.put("path", pathList);

        Iterator<Integer> i = pathStack.descendingIterator();
        while (i.hasNext()) {
            pathList.add(i.next());
        }
        if (isItem) {
            pathList.add(pathCounter++);
        }
        return eventDict;
    }

    protected DataDict createEventDictWithId(String name, DataDict dataDict, boolean isItem) {
        DataDict eventDict = createEventDictWithId(name, isItem);
        eventDict.put("@data", dataDict);
        return eventDict;
    }

    protected DataDict createEventDictWithId(String name) {
        return createEventDictWithId(name, true);
    }

    protected DataDict createEventDictWithId(String name, DataDict dataDict) {
        return createEventDictWithId(name, dataDict, true);
    }
}
