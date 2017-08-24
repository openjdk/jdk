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


import java.io.*;

import java.util.HashMap;

import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * This repository is used in situations where a Class is created
 * outside the realm of a ClassLoader. Classes are loaded from
 * the file systems using the paths specified in the given
 * class path. By default, this is the value returned by
 * ClassPath.getClassPath().
 * <br>
 * It is designed to be used as a singleton, however it
 * can also be used with custom classpaths.
 *
/**
 * Abstract definition of a class repository. Instances may be used
 * to load classes from different sources and may be used in the
 * Repository.setRepository method.
 *
 * @see com.sun.org.apache.bcel.internal.Repository
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @author David Dixon-Peugh
 */
public class SyntheticRepository implements Repository {

  private static HashMap _instances = new HashMap(); // CLASSPATH X REPOSITORY

  private HashMap   _loadedClasses = new HashMap(); // CLASSNAME X JAVACLASS

    private SyntheticRepository() {
    }

  public static SyntheticRepository getInstance() {
      return new SyntheticRepository();
  }

  /**
   * Store a new JavaClass instance into this Repository.
   */
  public void storeClass(JavaClass clazz) {
    _loadedClasses.put(clazz.getClassName(), clazz);
    clazz.setRepository(this);
 }

  /**
   * Remove class from repository
   */
  public void removeClass(JavaClass clazz) {
    _loadedClasses.remove(clazz.getClassName());
  }

  /**
   * Find an already defined (cached) JavaClass object by name.
   */
  public JavaClass findClass(String className) {
    return (JavaClass)_loadedClasses.get(className);
  }

  /**
   * Load a JavaClass object for the given class name using
   * the CLASSPATH environment variable.
   */
  public JavaClass loadClass(String className)
    throws ClassNotFoundException
  {
    if(className == null || className.equals("")) {
      throw new IllegalArgumentException("Invalid class name " + className);
    }

    className = className.replace('/', '.'); // Just in case, canonical form

    IOException e = new IOException("Couldn't find: " + className + ".class");
    throw new ClassNotFoundException("Exception while looking for class " +
                                       className + ": " + e.toString());
  }

  /**
   * Try to find class source via getResourceAsStream().
   * @see Class
   * @return JavaClass object for given runtime class
   */
  public JavaClass loadClass(Class clazz) throws ClassNotFoundException {
    String className = clazz.getName();
    String name      = className;
    int    i         = name.lastIndexOf('.');

    if(i > 0) {
      name = name.substring(i + 1);
    }

    return loadClass(clazz.getResourceAsStream(name + ".class"), className);
  }

  private JavaClass loadClass(InputStream is, String className)
    throws ClassNotFoundException
  {
    JavaClass clazz = findClass(className);

    if(clazz != null) {
      return clazz;
    }

    try {
      if(is != null) {
        ClassParser parser = new ClassParser(is, className);
        clazz = parser.parse();

        storeClass(clazz);

        return clazz;
      }
    } catch(IOException e) {
      throw new ClassNotFoundException("Exception while looking for class " +
                                       className + ": " + e.toString());
    }

    throw new ClassNotFoundException("SyntheticRepository could not load " +
                                     className);
  }

  /** Clear all entries from cache.
   */
  public void clear() {
    _loadedClasses.clear();
  }
}
