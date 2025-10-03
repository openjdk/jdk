/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

/**
 * Used for BCEL comparison strategy.
 *
 * @param <T> What type we are comparing.
 * @since 5.2
 */
public interface BCELComparator<T> {

    /**
     * Compares two objects and return what a.equals(b) should return.
     *
     * @param a an object.
     * @param b an object to be compared with {@code a} for equality.
     * @return {@code true} if the arguments are equal to each other and {@code false} otherwise.
     */
    boolean equals(T a, T b);

    /**
     * Gets the hash code for o.hashCode()
     *
     * @param o
     * @return hash code for o.hashCode()
     */
    int hashCode(T o);
}
