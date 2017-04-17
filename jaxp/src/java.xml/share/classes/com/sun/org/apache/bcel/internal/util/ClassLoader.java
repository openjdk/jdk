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


import java.util.Hashtable;
import java.io.*;
import com.sun.org.apache.bcel.internal.*;
import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * <p>Drop in replacement for the standard class loader of the JVM. You can use it
 * in conjunction with the JavaWrapper to dynamically modify/create classes
 * as they're requested.</p>
 *
 * <p>This class loader recognizes special requests in a distinct
 * format, i.e., when the name of the requested class contains with
 * "$$BCEL$$" it calls the createClass() method with that name
 * (everything bevor the $$BCEL$$ is considered to be the package
 * name. You can subclass the class loader and override that
 * method. "Normal" classes class can be modified by overriding the
 * modifyClass() method which is called just before defineClass().</p>
 *
 * <p>There may be a number of packages where you have to use the default
 * class loader (which may also be faster). You can define the set of packages
 * where to use the system class loader in the constructor. The default value contains
 * "java.", "sun.", "javax."</p>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see JavaWrapper
 * @see ClassPath
 */
public class ClassLoader extends java.lang.ClassLoader {
  private Hashtable classes = new Hashtable(); // Hashtable is synchronized thus thread-safe
  private String[] ignored_packages = {
    "java.", "javax.", "sun."
  };
  private Repository repository = SyntheticRepository.getInstance();
  private java.lang.ClassLoader deferTo = ClassLoader.getSystemClassLoader();

  public ClassLoader() {
  }

  public ClassLoader(java.lang.ClassLoader deferTo) {
    this.deferTo = deferTo;
    this.repository = new ClassLoaderRepository(deferTo);
  }

  /** @param ignored_packages classes contained in these packages will be loaded
   * with the system class loader
   */
  public ClassLoader(String[] ignored_packages) {
    addIgnoredPkgs(ignored_packages);
  }

  public ClassLoader(java.lang.ClassLoader deferTo, String [] ignored_packages) {
    this.deferTo = deferTo;
    this.repository = new ClassLoaderRepository(deferTo);

    addIgnoredPkgs(ignored_packages);
  }

  private void addIgnoredPkgs(String[] ignored_packages) {
    String[] new_p = new String[ignored_packages.length + this.ignored_packages.length];

    System.arraycopy(this.ignored_packages, 0, new_p, 0, this.ignored_packages.length);
    System.arraycopy(ignored_packages, 0, new_p, this.ignored_packages.length,
                     ignored_packages.length);

    this.ignored_packages = new_p;
  }

  protected Class loadClass(String class_name, boolean resolve)
    throws ClassNotFoundException
  {
    Class cl = null;

    /* First try: lookup hash table.
     */
    if((cl=(Class)classes.get(class_name)) == null) {
      /* Second try: Load system class using system class loader. You better
       * don't mess around with them.
       */
      for(int i=0; i < ignored_packages.length; i++) {
        if(class_name.startsWith(ignored_packages[i])) {
          cl = deferTo.loadClass(class_name);
          break;
        }
      }

      if(cl == null) {
        JavaClass clazz = null;

        /* Third try: Special request?
         */
        if(class_name.indexOf("$$BCEL$$") >= 0)
          clazz = createClass(class_name);
        else { // Fourth try: Load classes via repository
          if ((clazz = repository.loadClass(class_name)) != null) {
            clazz = modifyClass(clazz);
          }
          else
            throw new ClassNotFoundException(class_name);
        }

        if(clazz != null) {
          byte[] bytes  = clazz.getBytes();
          cl = defineClass(class_name, bytes, 0, bytes.length);
        } else // Fourth try: Use default class loader
          cl = Class.forName(class_name);
      }

      if(resolve)
        resolveClass(cl);
    }

    classes.put(class_name, cl);

    return cl;
  }

  /** Override this method if you want to alter a class before it gets actually
   * loaded. Does nothing by default.
   */
  protected JavaClass modifyClass(JavaClass clazz) {
    return clazz;
  }

  /**
   * Override this method to create you own classes on the fly. The
   * name contains the special token $$BCEL$$. Everything before that
   * token is consddered to be a package name. You can encode you own
   * arguments into the subsequent string. You must regard however not
   * to use any "illegal" characters, i.e., characters that may not
   * appear in a Java class name too<br>
   *
   * The default implementation interprets the string as a encoded compressed
   * Java class, unpacks and decodes it with the Utility.decode() method, and
   * parses the resulting byte array and returns the resulting JavaClass object.
   *
   * @param class_name compressed byte code with "$$BCEL$$" in it
   */
  protected JavaClass createClass(String class_name) {
    int    index     = class_name.indexOf("$$BCEL$$");
    String real_name = class_name.substring(index + 8);

    JavaClass clazz = null;
    try {
      byte[]      bytes  = Utility.decode(real_name, true);
      ClassParser parser = new ClassParser(new ByteArrayInputStream(bytes), "foo");

      clazz = parser.parse();
    } catch(Throwable e) {
      e.printStackTrace();
      return null;
    }

    // Adapt the class name to the passed value
    ConstantPool cp = clazz.getConstantPool();

    ConstantClass cl = (ConstantClass)cp.getConstant(clazz.getClassNameIndex(),
                                                     Constants.CONSTANT_Class);
    ConstantUtf8 name = (ConstantUtf8)cp.getConstant(cl.getNameIndex(),
                                                     Constants.CONSTANT_Utf8);
    name.setBytes(class_name.replace('.', '/'));

    return clazz;
  }
}
