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
 * The repository maintains information about which classes have
 * been loaded.
 *
 * It loads its data from the ClassLoader implementation
 * passed into its constructor.
 *
 * @see com.sun.org.apache.bcel.internal.Repository
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @author David Dixon-Peugh
 */
public class ClassLoaderRepository
  implements Repository
{
  private java.lang.ClassLoader loader;
  private HashMap loadedClasses =
    new HashMap(); // CLASSNAME X JAVACLASS

  public ClassLoaderRepository( java.lang.ClassLoader loader ) {
    this.loader = loader;
  }

  /**
   * Store a new JavaClass into this Repository.
   */
  public void storeClass( JavaClass clazz ) {
    loadedClasses.put( clazz.getClassName(),
                       clazz );
    clazz.setRepository( this );
  }

  /**
   * Remove class from repository
   */
  public void removeClass(JavaClass clazz) {
    loadedClasses.remove(clazz.getClassName());
  }

  /**
   * Find an already defined JavaClass.
   */
  public JavaClass findClass( String className ) {
    if ( loadedClasses.containsKey( className )) {
      return (JavaClass) loadedClasses.get( className );
    } else {
      return null;
    }
  }

  /**
   * Lookup a JavaClass object from the Class Name provided.
   */
  public JavaClass loadClass( String className )
    throws ClassNotFoundException
  {
    String classFile = className.replace('.', '/');

    JavaClass RC = findClass( className );
    if (RC != null) { return RC; }

    try {
      InputStream is =
        loader.getResourceAsStream( classFile + ".class" );

      if(is == null) {
        throw new ClassNotFoundException(className + " not found.");
      }

      ClassParser parser = new ClassParser( is, className );
      RC = parser.parse();

      storeClass( RC );

      return RC;
    } catch (IOException e) {
      throw new ClassNotFoundException( e.toString() );
    }
  }

  public JavaClass loadClass(Class clazz) throws ClassNotFoundException {
    return loadClass(clazz.getName());
  }

  /** Clear all entries from cache.
   */
  public void clear() {
    loadedClasses.clear();
  }
}
