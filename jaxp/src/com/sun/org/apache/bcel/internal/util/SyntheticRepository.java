/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.util;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.io.*;

import java.util.Map;
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
  private static final String DEFAULT_PATH = ClassPath.getClassPath();

  private static HashMap _instances = new HashMap(); // CLASSPATH X REPOSITORY

  private ClassPath _path = null;
  private HashMap   _loadedClasses = new HashMap(); // CLASSNAME X JAVACLASS

  private SyntheticRepository(ClassPath path) {
    _path = path;
  }

  public static SyntheticRepository getInstance() {
    return getInstance(ClassPath.SYSTEM_CLASS_PATH);
  }

  public static SyntheticRepository getInstance(ClassPath classPath) {
    SyntheticRepository rep = (SyntheticRepository)_instances.get(classPath);

    if(rep == null) {
      rep = new SyntheticRepository(classPath);
      _instances.put(classPath, rep);
    }

    return rep;
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

    try {
      return loadClass(_path.getInputStream(className), className);
    } catch(IOException e) {
      throw new ClassNotFoundException("Exception while looking for class " +
                                       className + ": " + e.toString());
    }
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
