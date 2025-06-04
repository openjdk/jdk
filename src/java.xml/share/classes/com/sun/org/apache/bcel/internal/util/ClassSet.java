/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.bcel.internal.Const;
import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.bcel.internal.classfile.JavaClass;

/**
 * Utility class implementing a (type-safe) set of JavaClass objects. Since JavaClass has no equals() method, the name of the class is used for comparison.
 *
 * @see ClassStack
 * @LastModified: Feb 2023
 */
public class ClassSet {

    private final Map<String, JavaClass> map = new HashMap<>();

    public boolean add(final JavaClass clazz) {
        return map.putIfAbsent(clazz.getClassName(), clazz) != null;
    }

    public boolean empty() {
        return map.isEmpty();
    }

    public String[] getClassNames() {
        return map.keySet().toArray(Const.EMPTY_STRING_ARRAY);
    }

    public void remove(final JavaClass clazz) {
        map.remove(clazz.getClassName());
    }

    public JavaClass[] toArray() {
        return map.values().toArray(JavaClass.EMPTY_ARRAY);
    }
}
