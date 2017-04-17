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

import java.util.LinkedList;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;

/**
 * Utility class implementing a (typesafe) queue of JavaClass
 * objects.
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see ClassVector
*/
public class ClassQueue implements java.io.Serializable {
  protected LinkedList vec  = new LinkedList();

  public void enqueue(JavaClass clazz) { vec.addLast(clazz); }

  public JavaClass dequeue()                {
    return (JavaClass)vec.removeFirst();
  }

  public boolean empty() { return vec.isEmpty(); }

  public String toString() {
    return vec.toString();
  }
}
