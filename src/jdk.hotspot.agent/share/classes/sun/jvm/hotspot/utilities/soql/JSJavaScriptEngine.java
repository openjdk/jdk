/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.io.*;
import java.util.*;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.tools.*;
import sun.jvm.hotspot.tools.jcore.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Simple wrapper around jsr-223 JavaScript script engine.
 * In addition to wrapping useful functionality of jsr-223 engine,
 * this class exposed certain "global" functions to the script.
 */
public abstract class JSJavaScriptEngine extends MapScriptObject {
    /**
     * Start a read-eval-print loop with this engine.
     */
    public void startConsole() {
      start(true);
    }

    /**
     * Initialize the engine so that we can "eval" strings
     * and files later.
     */
    public void start() {
      start(false);
    }

    /**
     * Define a global function that invokes given Method.
     */
    public void defineFunction(Object target, Method method) {
      putFunction(target, method, false);
    }

    /**
     * Call the script function of given name passing the
     * given arguments.
     */
    public Object call(String name, Object[] args) {
      Invocable invocable = (Invocable)engine;
      try {
        return invocable.invokeFunction(name, args);
      } catch (RuntimeException re) {
        throw re;
      } catch (Exception exp) {
        throw new RuntimeException(exp);
      }
    }

    /**
       address function returns address of JSJavaObject as String. For other
       type of objects, the result is undefined.
    */
    public Object address(Object[] args) {
        if (args.length != 1) return UNDEFINED;
        Object o = args[0];
        if (o != null && o instanceof JSJavaObject) {
            return ((JSJavaObject)o).getOop().getHandle().toString();
        } else {
            return UNDEFINED;
        }
    }


    /**
       classof function gets type of given JSJavaInstance or JSJavaArray. Or
       given a string class name, this function gets the class object. For
       other type of objects, the result is undefined.
    */
    public Object classof(Object[] args) {
        if (args.length != 1) {
            return UNDEFINED;
        }
        Object o = args[0];
        if (o != null) {
            if (o instanceof JSJavaObject) {
                if (o instanceof JSJavaInstance) {
                    return ((JSJavaInstance)o).getJSJavaClass();
                } else if (o instanceof JSJavaArray) {
                    return ((JSJavaArray)o).getJSJavaClass();
                } else {
                    return UNDEFINED;
                }
            } else if (o instanceof String) {
                InstanceKlass ik = SystemDictionaryHelper.findInstanceKlass((String) o);
                return getJSJavaFactory().newJSJavaKlass(ik).getJSJavaClass();
            } else {
                return UNDEFINED;
            }
        } else {
            return UNDEFINED;
        }
    }

    /**
     * dumpClass function creates a .class file for a given Class object.
     * On success, returns true. Else, returns false. Second optional argument
     * specifies the directory in which .class content is dumped. This defaults
     * to '.'
    */
    public Object dumpClass(Object[] args) {
        if (args.length == 0) {
            return Boolean.FALSE;
        }
        Object clazz = args[0];
      if (clazz == null) {
          return Boolean.FALSE;
      }
        InstanceKlass ik = null;
        if (clazz instanceof String) {
            String name = (String) clazz;
            if (name.startsWith("0x")) {
                // treat it as address
                VM vm = VM.getVM();
                Address addr = vm.getDebugger().parseAddress(name);
                Metadata metadata = Metadata.instantiateWrapperFor(addr.addOffsetTo(0));
                if (metadata instanceof InstanceKlass) {
                    ik = (InstanceKlass) metadata;
                } else {
                    return Boolean.FALSE;
                }
            } else {
                ik = SystemDictionaryHelper.findInstanceKlass((String) clazz);
            }
        } else if (clazz instanceof JSJavaClass) {
            JSJavaKlass jk = ((JSJavaClass)clazz).getJSJavaKlass();
            if (jk != null && jk instanceof JSJavaInstanceKlass) {
                ik = ((JSJavaInstanceKlass)jk).getInstanceKlass();
            }
        } else {
            return Boolean.FALSE;
        }

        if (ik == null) return Boolean.FALSE;
        StringBuffer buf = new StringBuffer();
        if (args.length > 1) {
            buf.append(args[1].toString());
        } else {
            buf.append('.');
        }

        buf.append(File.separatorChar);
        buf.append(ik.getName().asString().replace('/', File.separatorChar));
        buf.append(".class");
        String fileName = buf.toString();
        File file = new File(fileName);

        try {
            int index = fileName.lastIndexOf(File.separatorChar);
            File dir = new File(fileName.substring(0, index));
            dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            ClassWriter cw = new ClassWriter(ik, fos);
            cw.write();
            fos.close();
        } catch (IOException exp) {
            printError(exp.toString(), exp);
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }

    /**
     * dumpHeap function creates a heap dump file.
     * On success, returns true. Else, returns false.
    */
    public Object dumpHeap(Object[] args) {
        String fileName = "heap.bin";
        if (args.length > 0) {
            fileName = args[0].toString();
        }
        return new JMap().writeHeapHprofBin(fileName)? Boolean.TRUE: Boolean.FALSE;
    }

    /**
        help function prints help message for global functions and variables.
    */
    public void help(Object[] args) {
        println("Function/Variable        Description");
        println("=================        ===========");
        println("address(jobject)         returns the address of the Java object");
        println("classof(jobject)         returns the class object of the Java object");
        println("dumpClass(jclass,[dir])  writes .class for the given Java Class");
        println("dumpHeap([file])         writes heap in hprof binary format");
        println("help()                   prints this help message");
        println("identityHash(jobject)    returns the hashCode of the Java object");
        println("mirror(jobject)          returns a local mirror of the Java object");
        println("load([file1, file2,...]) loads JavaScript file(s). With no files, reads <stdin>");
        println("object(string)           converts a string address into Java object");
        println("owner(jobject)           returns the owner thread of this monitor or null");
        println("sizeof(jobject)          returns the size of Java object in bytes");
        println("staticof(jclass, field)  returns a static field of the given Java class");
        println("read([prompt])           reads a single line from standard input");
        println("quit()                   quits the interactive load call");
        println("jvm                      the target jvm that is being debugged");
    }

    /**
       identityHash function gets identity hash code value of given
       JSJavaObject. For other type of objects, the result is undefined.
    */
    public Object identityHash(Object[] args) {
        if (args.length != 1) return UNDEFINED;
        Object o = args[0];
        if (o != null && o instanceof JSJavaObject) {
            return new Long(((JSJavaObject)o).getOop().identityHash());
        } else {
            return UNDEFINED;
        }
    }


    /**
     * Load and execute a set of JavaScript source files.
     * This method is defined as a JavaScript function.
     */
    public void load(Object[] args) {
       for (int i = 0; i < args.length; i++) {
         processSource(args[i].toString());
       }
    }

    /**
       mirror function creats local copy of the Oop wrapper supplied.
       if mirror can not be created, return undefined. For other types,
       mirror is undefined.
    */
    public Object mirror(Object[] args) {
        Object o = args[0];
        Object res = UNDEFINED;
        if (o != null) {
            if (o instanceof JSJavaObject) {
            Oop oop = ((JSJavaObject)o).getOop();
            try {
                    res = getObjectReader().readObject(oop);
                } catch (Exception e) {
                    if (debug) e.printStackTrace(getErrorStream());
                }
            } else if (o instanceof JSMetadata) {
                Metadata metadata = ((JSMetadata)o).getMetadata();
                try {
                    if (metadata instanceof InstanceKlass) {
                        res = getObjectReader().readClass((InstanceKlass) metadata);
                }
            } catch (Exception e) {
                if (debug) e.printStackTrace(getErrorStream());
            }
        }
    }
        return res;
    }

    /**
       owner function gets owning thread of given JSJavaObjec, if any, else
       returns null. For other type of objects, the result is undefined.
    */
    public Object owner(Object[] args) {
        Object o = args[0];
        if (o != null && o instanceof JSJavaObject) {
            return getOwningThread((JSJavaObject)o);
        } else {
            return UNDEFINED;
        }
    }

    /**
       object function takes a string address and returns a JSJavaObject.
       For other type of objects, the result is undefined.
    */
    public Object object(Object[] args) {
        Object o = args[0];
        if (o != null && o instanceof String) {
            VM vm = VM.getVM();
            Address addr = vm.getDebugger().parseAddress((String)o);
            Oop oop = vm.getObjectHeap().newOop(addr.addOffsetToAsOopHandle(0));
            return getJSJavaFactory().newJSJavaObject(oop);
        } else {
            return UNDEFINED;
        }
    }

    /**
       sizeof function returns size of a Java object in bytes. For other type
       of objects, the result is undefined.
    */
    public Object sizeof(Object[] args) {
        if (args.length != 1) return UNDEFINED;
        Object o = args[0];
        if (o != null && o instanceof JSJavaObject) {
            return new Long(((JSJavaObject)o).getOop().getObjectSize());
        } else {
            return UNDEFINED;
        }
    }

    /**
       staticof function gets static field of given class. Both class and
       field name are specified as strings. undefined is returned if there is
       no such named field.
    */
    public Object staticof(Object[] args) {
        Object classname = args[0];
        Object fieldname = args[1];
        if (fieldname == null || classname == null ||
            !(fieldname instanceof String)) {
            return UNDEFINED;
        }

        InstanceKlass ik = null;
        if (classname instanceof JSJavaClass) {
            JSJavaClass jclass = (JSJavaClass) classname;
            JSJavaKlass jk = jclass.getJSJavaKlass();
            if (jk != null && jk instanceof JSJavaInstanceKlass) {
                ik = ((JSJavaInstanceKlass)jk).getInstanceKlass();
            }
        } else if (classname instanceof String) {
            ik = SystemDictionaryHelper.findInstanceKlass((String)classname);
        } else {
            return UNDEFINED;
        }

        if (ik == null) {
            return UNDEFINED;
        }
        JSJavaFactory factory = getJSJavaFactory();
        try {
            return ((JSJavaInstanceKlass) factory.newJSJavaKlass(ik)).getStaticFieldValue((String)fieldname);
        } catch (NoSuchFieldException e) {
            return UNDEFINED;
        }
    }

    /**
     * read function reads a single line of input from standard input
    */
    public Object read(Object[] args) {
        BufferedReader in = getInputReader();
      if (in == null) {
        return null;
      }
        if (args.length > 0) {
          print(args[0].toString());
          print(":");
        }
        try {
          return in.readLine();
        } catch (IOException exp) {
        exp.printStackTrace();
          throw new RuntimeException(exp);
        }
    }

    /**
     * Quit the shell.
     * This only affects the interactive mode.
     */
    public void quit(Object[] args) {
        quit();
    }

    public void writeln(Object[] args) {
      for (int i = 0; i < args.length; i++) {
        print(args[i].toString());
        print(" ");
      }
      println("");
    }

    public void write(Object[] args) {
      for (int i = 0; i < args.length; i++) {
        print(args[i].toString());
        print(" ");
      }
    }

    //-- Internals only below this point
    protected void start(boolean console) {
      ScriptContext context = engine.getContext();
      OutputStream out = getOutputStream();
      if (out != null) {
        context.setWriter(new PrintWriter(out));
      }
      OutputStream err = getErrorStream();
      if (err != null) {
        context.setErrorWriter(new PrintWriter(err));
      }
      // load "sa.js" initialization file
      loadInitFile();
      // load "~/jsdb.js" (if found) to perform user specific
      // initialization steps, if any.
      loadUserInitFile();

      JSJavaFactory fac = getJSJavaFactory();
      JSJavaVM jvm = (fac != null)? fac.newJSJavaVM() : null;
      // call "main" function from "sa.js" -- main expects
      // 'this' object and jvm object
      call("main", new Object[] { this, jvm });

      // if asked, start read-eval-print console
      if (console) {
        processSource(null);
      }
    }

    protected JSJavaScriptEngine(boolean debug) {
        this.debug = debug;
      ScriptEngineManager manager = new ScriptEngineManager();
      engine = manager.getEngineByName("javascript");
      if (engine == null) {
        throw new RuntimeException("can't load JavaScript engine");
      }
      Method[] methods = getClass().getMethods();
      for (int i = 0; i < methods.length; i++) {
        Method m = methods[i];
        if (! Modifier.isPublic(m.getModifiers())) {
          continue;
        }
        Class[] argTypes = m.getParameterTypes();
        if (argTypes.length == 1 &&
            argTypes[0] == Object[].class) {
          putFunction(this, m);
        }
      }
    }

    protected JSJavaScriptEngine() {
        this(false);
    }

    protected abstract ObjectReader getObjectReader();
    protected abstract JSJavaFactory getJSJavaFactory();
    protected void printPrompt(String str) {
        System.err.print(str);
        System.err.flush();
    }

    protected void loadInitFile() {
      InputStream is = JSJavaScriptEngine.class.getResourceAsStream("sa.js");
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      evalReader(reader, "sa.js");
    }

    protected void loadUserInitFile() {
        File initFile = new File(getUserInitFileDir(), getUserInitFileName());
        if (initFile.exists() && initFile.isFile()) {
        // load the init script
          processSource(initFile.getAbsolutePath());
        }
    }

    protected String getUserInitFileDir() {
        return System.getProperty("user.home");
    }

    protected String getUserInitFileName() {
        return "jsdb.js";
    }

    protected BufferedReader getInputReader() {
      if (inReader == null) {
        inReader = new BufferedReader(new InputStreamReader(System.in));
      }
      return inReader;
    }

    protected PrintStream getOutputStream() {
      return System.out;
    }

    protected PrintStream getErrorStream() {
      return System.err;
    }

    protected void print(String name) {
      getOutputStream().print(name);
    }

    protected void println(String name) {
      getOutputStream().println(name);
    }

    protected void printError(String message) {
        printError(message, null);
    }

    protected void printError(String message, Exception exp) {
        getErrorStream().println(message);
        if (exp != null && debug) {
          exp.printStackTrace(getErrorStream());
        }
    }

    protected boolean isQuitting() {
        return quitting;
    }

    protected void quit() {
        quitting = true;
    }

    protected ScriptEngine getScriptEngine() {
      return engine;
    }

    private JSJavaThread getOwningThread(JSJavaObject jo) {
        Oop oop = jo.getOop();
        Mark mark = oop.getMark();
        ObjectMonitor mon = null;
      Address owner = null;
        JSJavaThread owningThread = null;
        // check for heavyweight monitor
        if (! mark.hasMonitor()) {
            // check for lightweight monitor
            if (mark.hasLocker()) {
                owner = mark.locker().getAddress(); // save the address of the Lock word
            }
            // implied else: no owner
        } else {
            // this object has a heavyweight monitor
            mon = mark.monitor();

            // The owner field of a heavyweight monitor may be NULL for no
            // owner, a JavaThread * or it may still be the address of the
            // Lock word in a JavaThread's stack. A monitor can be inflated
            // by a non-owning JavaThread, but only the owning JavaThread
            // can change the owner field from the Lock word to the
            // JavaThread * and it may not have done that yet.
            owner = mon.owner();
        }

        // find the owning thread
        if (owner != null) {
            JSJavaFactory factory = getJSJavaFactory();
            owningThread = (JSJavaThread) factory.newJSJavaThread(VM.getVM().getThreads().owningThreadFromMonitor(owner));
        }
        return owningThread;
    }

    /**
     * Evaluate JavaScript source.
     * @param filename the name of the file to compile, or null
     *                 for interactive mode.
     */
    private void processSource(String filename) {
        if (filename == null) {
            BufferedReader in = getInputReader();
            String sourceName = "<stdin>";
            int lineno = 0;
            boolean hitEOF = false;
            do {
                int startline = lineno;
                printPrompt("jsdb> ");
                Object source = read(EMPTY_ARRAY);
                if (source == null) {
                   hitEOF = true;
                   break;
                }
                lineno++;
                Object result = evalString(source.toString(), sourceName, startline);
                if (result != null) {
                    printError(result.toString());
                }
                if (isQuitting()) {
                    // The user executed the quit() function.
                    break;
                }
            } while (!hitEOF);
        } else {
            Reader in = null;
            try {
                in = new BufferedReader(new FileReader(filename));
                evalReader(in, filename);
            } catch (FileNotFoundException ex) {
                println("File '" + filename + "' not found");
                throw new RuntimeException(ex);
            }
        }
    }

    protected Object evalString(String source, String filename, int lineNum) {
       try {
         engine.put(ScriptEngine.FILENAME, filename);
         return engine.eval(source);
       } catch (ScriptException sexp) {
         printError(sexp.toString(), sexp);
         } catch (Exception exp) {
         printError(exp.toString(), exp);
       }
       return null;
    }

    private Object evalReader(Reader in, String filename) {
       try {
         engine.put(ScriptEngine.FILENAME, filename);
         return engine.eval(in);
       } catch (ScriptException sexp) {
         System.err.println(sexp);
         printError(sexp.toString(), sexp);
         } finally {
         try {
           in.close();
         } catch (IOException ioe) {
           printError(ioe.toString(), ioe);
         }
       }
       return null;
    }

    // lazily initialized input reader
    private BufferedReader inReader;
    // debug mode or not
    protected final boolean debug;
    private boolean quitting;
    // underlying jsr-223 script engine
    private ScriptEngine engine;
}
