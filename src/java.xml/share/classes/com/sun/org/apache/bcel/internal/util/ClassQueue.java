/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.util;

import java.util.LinkedList;

import com.sun.org.apache.bcel.internal.classfile.JavaClass;

/**
 * Utility class implementing a (typesafe) queue of JavaClass objects.
 */
public class ClassQueue {

    /**
     * @deprecated (since 6.0) will be made private; do not access
     */
    @Deprecated
    protected LinkedList<JavaClass> vec = new LinkedList<>(); // TODO not used externally

    /**
     * Constructs a new ClassQueue.
     */
    public ClassQueue() {
    }

    /**
     * Removes and returns the first JavaClass from the queue.
     *
     * @return The first JavaClass from the queue.
     */
    public JavaClass dequeue() {
        return vec.removeFirst();
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if the queue is empty.
     */
    public boolean empty() {
        return vec.isEmpty();
    }

    /**
     * Adds a JavaClass to the end of the queue.
     *
     * @param clazz The JavaClass to add.
     */
    public void enqueue(final JavaClass clazz) {
        vec.addLast(clazz);
    }

    @Override
    public String toString() {
        return vec.toString();
    }
}
