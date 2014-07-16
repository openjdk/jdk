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

import java.lang.reflect.*;

/**
 * Java interpreter replacement, i.e., wrapper that uses its own ClassLoader
 * to modify/generate classes as they're requested. You can take this as a template
 * for your own applications.<br>
 * Call this wrapper with
 * <pre>java com.sun.org.apache.bcel.internal.util.JavaWrapper &lt;real.class.name&gt; [arguments]</pre>
 * <p>
 * To use your own class loader you can set the "bcel.classloader" system property
 * which defaults to "com.sun.org.apache.bcel.internal.util.ClassLoader", e.g., with
 * <pre>java com.sun.org.apache.bcel.internal.util.JavaWrapper -Dbcel.classloader=foo.MyLoader &lt;real.class.name&gt; [arguments]</pre>
 * </p>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see ClassLoader
 */
public class JavaWrapper {
  private java.lang.ClassLoader loader;

  private static java.lang.ClassLoader getClassLoader() {
    String s = SecuritySupport.getSystemProperty("bcel.classloader");

    if((s == null) || "".equals(s))
      s = "com.sun.org.apache.bcel.internal.util.ClassLoader";

    try {
      return (java.lang.ClassLoader)Class.forName(s).newInstance();
    } catch(Exception e) {
      throw new RuntimeException(e.toString());
    }
  }

  public JavaWrapper(java.lang.ClassLoader loader) {
    this.loader = loader;
  }

  public JavaWrapper() {
    this(getClassLoader());
  }

  /** Runs the _main method of the given class with the arguments passed in argv
   *
   * @param class_name the fully qualified class name
   * @param argv the arguments just as you would pass them directly
   */
  public void runMain(String class_name, String[] argv) throws ClassNotFoundException
  {
    Class   cl    = loader.loadClass(class_name);
    Method method = null;

    try {
      method = cl.getMethod("_main",  new Class[] { argv.getClass() });

      /* Method _main is sane ?
       */
      int   m = method.getModifiers();
      Class r = method.getReturnType();

      if(!(Modifier.isPublic(m) && Modifier.isStatic(m)) ||
         Modifier.isAbstract(m) || (r != Void.TYPE))
        throw new NoSuchMethodException();
    } catch(NoSuchMethodException no) {
      System.out.println("In class " + class_name +
                         ": public static void _main(String[] argv) is not defined");
      return;
    }

    try {
      method.invoke(null, new Object[] { argv });
    } catch(Exception ex) {
      ex.printStackTrace();
    }
  }

  /** Default _main method used as wrapper, expects the fully qualified class name
   * of the real class as the first argument.
   */
  public static void _main(String[] argv) throws Exception {
    /* Expects class name as first argument, other arguments are by-passed.
     */
    if(argv.length == 0) {
      System.out.println("Missing class name.");
      return;
    }

    String class_name = argv[0];
    String[] new_argv = new String[argv.length - 1];
    System.arraycopy(argv, 1, new_argv, 0, new_argv.length);

    JavaWrapper wrapper = new JavaWrapper();
    wrapper.runMain(class_name, new_argv);
  }
}
