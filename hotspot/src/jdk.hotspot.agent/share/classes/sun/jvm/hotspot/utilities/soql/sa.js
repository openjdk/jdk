/*
 * Copyright (c) 2004, 2016, Oracle and/or its affiliates. All rights reserved.
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

// shorter names for SA packages


// SA package name abbreviations are kept in 'sapkg' object
// to avoid global namespace pollution
var sapkg = new Object();

sapkg.hotspot = Packages.sun.jvm.hotspot;
sapkg.asm = sapkg.hotspot.asm;
sapkg.c1 = sapkg.hotspot.c1;
sapkg.code = sapkg.hotspot.code;
sapkg.compiler = sapkg.hotspot.compiler;

// 'debugger' is a JavaScript keyword, but ES5 relaxes the
// restriction of using keywords as property name
sapkg.debugger = sapkg.hotspot.debugger;

sapkg.interpreter = sapkg.hotspot.interpreter;
sapkg.jdi = sapkg.hotspot.jdi;
sapkg.memory = sapkg.hotspot.memory;
sapkg.oops = sapkg.hotspot.oops;
sapkg.runtime = sapkg.hotspot.runtime;
sapkg.tools = sapkg.hotspot.tools;
sapkg.types = sapkg.hotspot.types;
sapkg.ui = sapkg.hotspot.ui;
sapkg.utilities = sapkg.hotspot.utilities;

// SA singletons are kept in 'sa' object
var sa = new Object();
sa.vm = sapkg.runtime.VM.getVM();
sa.dbg = sa.vm.getDebugger();
sa.cdbg = sa.dbg.CDebugger;
sa.heap = sa.vm.universe.heap();
sa.systemDictionary = sa.vm.systemDictionary;
sa.sysDict = sa.systemDictionary;
sa.symbolTable = sa.vm.symbolTable;
sa.symTbl = sa.symbolTable;
sa.threads = sa.vm.threads;
sa.interpreter = sa.vm.interpreter;
sa.typedb = sa.vm.typeDataBase;
sa.codeCache = sa.vm.codeCache;
// 'objHeap' is different from 'heap'!. 
// This is SA's Oop factory and heap-walker
sa.objHeap = sa.vm.objectHeap;

// few useful global variables
var OS = sa.vm.OS;
var CPU = sa.vm.CPU;
var LP64 = sa.vm.LP64;
var isClient = sa.vm.clientCompiler;
var isServer = sa.vm.serverCompiler;
var isCore = sa.vm.isCore();
var addressSize = sa.vm.addressSize;
var oopSize = sa.vm.oopSize;

// this "main" function is called immediately
// after loading this script file
function main(globals, jvmarg) {
  // wrap a sun.jvm.hotspot.utilities.soql.ScriptObject
  // object so that the properties of it can be accessed
  // in natural object.field syntax.
  function wrapScriptObject(so) {
    function unwrapScriptObject(wso) {
      var objType = typeof(wso);
      if ((objType == 'object' ||
           objType == 'function')
          && "__wrapped__" in wso) {
        return wso.__wrapped__;
      } else {
        return wso;
      }
    }

    function prepareArgsArray(array) {
      var args = new Array(array.length);
      for (var a = 0; a < array.length; a++) {
        var elem = array[a];
        elem = unwrapScriptObject(elem);
        if (typeof(elem) == 'function') {
          args[a] = new sapkg.utilities.soql.Callable() {
            call: function(myargs) {
              var tmp = new Array(myargs.length);
              for (var i = 0; i < myargs.length; i++) {
                tmp[i] = wrapScriptObject(myargs[i]);
              }
              return elem.apply(this, tmp);
            }
          }
        } else {
          args[a] = elem;
        }
      }
      return args;
    }

    // Handle __has__ specially to avoid metacircularity problems
    // when called from __get__.
    // Calling
    //   this.__has__(name)
    // will in turn call
    //   this.__call__('__has__', name)
    // which is not handled below
    function __has__(name) {
      if (typeof(name) == 'number') {
        return so["has(int)"](name);
      } else {
        if (name == '__wrapped__') {
          return true;
        } else if (so["has(java.lang.String)"](name)) {
          return true;
        } else if (name.equals('toString')) {
          return true;
        } else {
          return false;
        }
      }
    }

    if (so instanceof sapkg.utilities.soql.ScriptObject) {
      return new JSAdapter() {
        __getIds__: function() {
          return so.getIds();
        },
  
        __has__ : __has__,
  
        __delete__ : function(name) {
          if (typeof(name) == 'number') {
            return so["delete(int)"](name);
          } else {
            return so["delete(java.lang.String)"](name);
          }
        },
  
        __get__ : function(name) {
	      // don't call this.__has__(name); see comments above function __has__
          if (! __has__.call(this, name)) {
            return undefined;
          }
          if (typeof(name) == 'number') {
            return wrapScriptObject(so["get(int)"](name));
          } else {
            if (name == '__wrapped__') {
              return so;
            } else {
              var value = so["get(java.lang.String)"](name);
              if (value instanceof sapkg.utilities.soql.Callable) {
                return function() {
                  var args = prepareArgsArray(arguments);
                  var r;
                  try {
                    r = value.call(Java.to(args, 'java.lang.Object[]'));
                  } catch (e) {
                    println("call to " + name + " failed!");
                    throw e;
                  }
                  return wrapScriptObject(r);
                }
              } else if (name == 'toString') {
                return function() { 
                  return so.toString();
                }
              } else {
                return wrapScriptObject(value);
              }
            }
          }
        }
      };
    } else {
      return so;
    }
  }

  // set "jvm" global variable that wraps a 
  // sun.jvm.hotspot.utilities.soql.JSJavaVM instance
  if (jvmarg != null) {
    jvm = wrapScriptObject(jvmarg);
    // expose "heap" global variable
    heap = jvm.heap;
  }

  // expose all "function" type properties of
  // sun.jvm.hotspot.utilitites.soql.JSJavaScriptEngine
  // as global functions here.
  globals = wrapScriptObject(globals);
  for (var prop in globals) {    
    if (typeof(globals[prop]) == 'function') {
      this[prop] = globals[prop];
    }    
  }

  // define "writeln" and "write" if not defined
  if (typeof(println) == 'undefined') {
    println = function (str) {
      java.lang.System.out.println(String(str));
    }
  }

  if (typeof(print) == 'undefined') {
    print = function (str) {
      java.lang.System.out.print(String(str));
    }
  }

  if (typeof(writeln) == 'undefined') {
    writeln = println;
  }

  if (typeof(write) == 'undefined') {
    write = print;
  }

  // "registerCommand" function is defined if we
  // are running as part of "CLHSDB" tool. CLHSDB
  // tool exposes Unix-style commands. 

  // if "registerCommand" function is defined
  // then register few global functions as "commands".
  if (typeof(registerCommand) == 'function') {
    this.jclass = function(name) {
      if (typeof(name) == "string") {
         var clazz = sapkg.utilities.SystemDictionaryHelper.findInstanceKlass(name);
         if (clazz) {
             writeln(clazz.getName().asString() + " @" + clazz.getAddress().toString());
         } else {
             writeln("class not found: " + name);
         } 
      } else {
         writeln("Usage: class name");
      }
    }
    registerCommand("class", "class name", "jclass");

    this.jclasses = function() {
      forEachKlass(function (clazz) {
        writeln(clazz.getName().asString() + " @" + clazz.getAddress().toString()); 
      });
    }
    registerCommand("classes", "classes", "jclasses");

    this.dclass = function(clazz, dir) {
      if (!clazz) {
         writeln("Usage: dumpclass { address | name } [ directory ]");
      } else {
         if (!dir) { dir = "."; }
         dumpClass(clazz, dir);
      }
    }
    registerCommand("dumpclass", "dumpclass { address | name } [ directory ]", "dclass");
    registerCommand("dumpheap", "dumpheap [ file ]", "dumpHeap");

    this.jseval = function(str) {
      if (!str) {
         writeln("Usage: jseval script");
      } else {
         var res = eval(str);
         if (res) { writeln(res); }
      }
    }
    registerCommand("jseval", "jseval script", "jseval");

    this.jsload = function(file) {
      if (!file) {
         writeln("Usage: jsload file");
      } else {
         load(file);
      }
    }
    registerCommand("jsload", "jsload file", "jsload");

    this.printMem = function(addr, len) {
      if (!addr) {
         writeln("Usage: mem [ length ]");
      } else {
         mem(addr, len);
      }
    }
    registerCommand("mem", "mem address [ length ]", "printMem");

    this.sysProps = function() {
      for (var i in jvm.sysProps) {
         writeln(i + ' = ' + jvm.sysProps[i]);
      }
    }
    registerCommand("sysprops", "sysprops", "sysProps");

    this.printWhatis = function(addr) {
      if (!addr) {
         writeln("Usage: whatis address");
      } else {
         writeln(whatis(addr));
      }
    }
    registerCommand("whatis", "whatis address", "printWhatis");
  }  
}

// debugger functionality

// string-to-Address
function str2addr(str) {
   return sa.dbg.parseAddress(str);
}

// number-to-Address
if (addressSize == 4) {
   eval("function num2addr(num) { \
            return str2addr('0x' + java.lang.Integer.toHexString(0xffffffff & num)); \
         }");
} else {
   eval("function num2addr(num) { \
            return str2addr('0x' + java.lang.Long.toHexString(num));  \
         }");
}

// generic any-type-to-Address
// use this convenience function to accept address in any
// format -- number, string or an Address instance.
function any2addr(addr) {
   var type = typeof(addr);
   if (type == 'number') {
      return num2addr(addr);
   } else if (type == 'string') {         
      return str2addr(addr);
   } else {
      return addr;
   }
}

// Address-to-string
function addr2str(addr) {
   if (addr == null) {
      return (addressSize == 4)? '0x00000000' : '0x0000000000000000';
   } else {
      return addr + '';
   }
}

// Address-to-number
function addr2num(addr) {
   return sa.dbg.getAddressValue(addr);
}

// symbol-to-Address
function sym2addr(dso, sym) {
   return sa.dbg.lookup(dso, sym);
}

function loadObjectContainingPC(addr) {
    if (sa.cdbg == null) {
      // no CDebugger support, return null
      return null;
    }

    return  sa.cdbg.loadObjectContainingPC(addr);
}

// returns the ClosestSymbol or null
function closestSymbolFor(addr) {
    var dso = loadObjectContainingPC(addr);
    if (dso != null) {
      return dso.closestSymbolToPC(addr);
    }

    return null;
}

// Address-to-symbol
// returns nearest symbol as string if found
// else returns address as string
function addr2sym(addr) {
    var sym = closestSymbolFor(addr);
    if (sym != null)  {
       return sym.name + '+' + sym.offset;
    } else {
       return addr2str(addr);
    }
}

// read 'num' words at 'addr' and return an array as result.
// returns Java long[] type result and not a JavaScript array.
function readWordsAt(addr, num) {
   addr = any2addr(addr);
   var res = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, num);
   var i;
   for (i = 0; i < num; i++) {
      res[i] = addr2num(addr.getAddressAt(i * addressSize));
   }
   return res;
}

// read the 'C' string at 'addr'
function readCStrAt(addr) {
   addr = any2addr(addr);
   return sapkg.utilities.CStringUtilities.getString(addr);
}

// read the length of the 'C' string at 'addr'
function readCStrLen(addr) {
   addr = any2addr(addr);
   return sapkg.utilities.CStringUtilities.getStringLength(addr);
}

// iterate through ThreadList of CDebugger
function forEachThread(callback) {
   if (sa.cdbg == null) {
      // no CDebugger support
      return;
   } else {
      var itr = sa.cdbg.threadList.iterator();
      while (itr.hasNext()) {
         if (callback(itr.next()) == false) return;
      }
   }
}

// read register set of a ThreadProxy as name-value pairs
function readRegs(threadProxy) {
   var ctx = threadProxy.context;
   var num = ctx.numRegisters;
   var res = new Object();
   var i;
   for (i = 0; i < num; i++) {
      res[ctx.getRegisterName(i)]= addr2str(ctx.getRegisterAsAddress(i));
   }
   return res;
}

// print register set for a given ThreaProxy
function regs(threadProxy) {
   var res = readRegs(threadProxy);
   for (i in res) {
      writeln(i, '=', res[i]);
   }
}

// iterate through each CFrame of a given ThreadProxy
function forEachCFrame(threadProxy, callback) {   
   if (sa.cdbg == null) {
      // no CDebugger support
      return;
   } else {
      var cframe = sa.cdbg.topFrameForThread(threadProxy);
      while (cframe != null) {
         if (callback(cframe) == false) return;
         cframe = cframe.sender();
      }
   }
}

// iterate through list of load objects (DLLs, DSOs)
function forEachLoadObject(callback) {
   if (sa.cdbg == null) {
      // no CDebugger support
      return;
   } else {
      var itr = sa.cdbg.loadObjectList.iterator();
      while (itr.hasNext()) {
         if (callback(itr.next()) == false) return;
      }
   }
}

// print 'num' words at 'addr'
function mem(addr, num) {
   if (num == undefined) {
      num = 1;
   }
   addr = any2addr(addr);   
   var i;
   for (i = 0; i < num; i++) {
      var value = addr.getAddressAt(0);      
      writeln(addr2sym(addr) + ':', addr2str(value)); 
      addr = addr.addOffsetTo(addressSize);      
   }
   writeln();
}

// System dictionary functions

// find InstanceKlass by name
function findInstanceKlass(name) {
   return sapkg.utilities.SystemDictionaryHelper.findInstanceKlass(name);
}

// get Java system loader (i.e., application launcher loader)
function systemLoader() {
   return sa.sysDict.javaSystemLoader();
}

// iterate system dictionary for each 'Klass' 
function forEachKlass(callback) {
   var VisitorClass = sapkg.memory.SystemDictionary.ClassVisitor;
   var visitor = new VisitorClass() { visit: callback };
   sa.sysDict["classesDo(sun.jvm.hotspot.memory.SystemDictionary.ClassVisitor)"](visitor);
}

// iterate system dictionary for each 'Klass' and initiating loader
function forEachKlassAndLoader(callback) {
   var VisitorClass = sapkg.memory.SystemDictionary.ClassAndLoaderVisitor;
   var visitor = new VisitorClass() { visit: callback };
   sa.sysDict["classesDo(sun.jvm.hotspot.memory.SystemDictionary.ClassAndLoaderVisitor)"](visitor);
}

// iterate system dictionary for each primitive array klass
function forEachPrimArrayKlass(callback) {
   var VisitorClass = sapkg.memory.SystemDictionary.ClassAndLoaderVisitor;
   sa.sysDict.primArrayClassesDo(new VisitorClass() { visit: callback });
}

// 'oop' to higher-level java object wrapper in which for(i in o) 
// works by iterating java level fields and javaobject.javafield
// syntax works.
function oop2obj(oop) {
   return object(addr2str(oop.handle));
}

// higher level java object wrapper to oop
function obj2oop(obj) {
   return addr2oop(str2addr(address(obj)));
}

// Java heap iteration

// iterates Java heap for each Oop
function forEachOop(callback) {
   function empty() { }
   sa.objHeap.iterate(new sapkg.oops.HeapVisitor() {
       prologue: empty,
       doObj: callback,
       epilogue: empty
   });
}

// iterates Java heap for each Oop of given 'klass'.
// 'includeSubtypes' tells whether to include objects 
// of subtypes of 'klass' or not
function forEachOopOfKlass(callback, klass, includeSubtypes) {
   if (klass == undefined) {
       klass = findInstanceKlass("java.lang.Object");
   }

   if (includeSubtypes == undefined) {
      includeSubtypes = true;
   }

   function empty() { }
   sa.objHeap.iterateObjectsOfKlass(
        new sapkg.oops.HeapVisitor() {
            prologue: empty,
            doObj: callback,
            epilogue: empty
        },
        klass, includeSubtypes);
}

// Java thread

// iterates each Thread
function forEachJavaThread(callback) {
   var threads = sa.threads;
   var thread = threads.first();
   while (thread != null) {
      if (callback(thread) == false) return;
      thread = thread.next();
   }  
}

// iterate Frames of a given thread
function forEachFrame(javaThread, callback) {
   var fr = javaThread.getLastFrameDbg();
   while (fr != null) { 
     if (callback(fr) == false) return;
     fr = fr.sender();
   }
}

// iterate JavaVFrames of a given JavaThread
function forEachVFrame(javaThread, callback) {
   var vfr = javaThread.getLastJavaVFrameDbg();
   while (vfr != null) {
      if (callback(vfr) == false) return;
      vfr = vfr.javaSender();
   }
}

function printStackTrace(javaThread) {
   write("Thread ");
   javaThread.printThreadIDOn(java.lang.System.out);
   writeln();
   forEachVFrame(javaThread, function (vf) {
      var method = vf.method;
      write(' - ', method.externalNameAndSignature(), '@bci =', vf.getBCI());
      var line = method.getLineNumberFromBCI(vf.getBCI());
      if (line != -1) { write(', line=', line); }
      if (vf.isCompiledFrame()) { write(" (Compiled Frame)"); }
      if (vf.isInterpretedFrame()) { write(" (Interpreted Frame)"); }
      writeln();
   });
   writeln();
   writeln();
}

// print Java stack trace for all threads
function where(javaThread) {
   if (javaThread == undefined) {
      forEachJavaThread(function (jt) { printStackTrace(jt); });
   } else {
      printStackTrace(javaThread);
   }
}

// vmStructs access -- type database functions

// find a VM type
function findVMType(typeName) {
   return sa.typedb.lookupType(typeName);
}

// iterate VM types
function forEachVMType(callback) {
   var itr = sa.typedb.types;
   while (itr.hasNext()) {
      if (callback(itr.next()) == false) return;
   }
}

// find VM int constant
function findVMIntConst(name) {
   return sa.typedb.lookupIntConstant(name);
}

// find VM long constant
function findVMLongConst(name) {
   return sa.typedb.lookupLongConstant(name);
}

// iterate VM int constants
function forEachVMIntConst(callback) {
   var itr = sa.typedb.intConstants;
   while (itr.hasNext()) {
      if (callback(itr.next()) == false) return;
   } 
}

// iterate VM long constants
function forEachVMLongConst(callback) {
   var itr = sa.typedb.longConstants;
   while (itr.hasNext()) {
      if (callback(itr.next()) == false) return;
   } 
}

// returns VM Type at address
function vmTypeof(addr) {
   addr = any2addr(addr);
   return sa.typedb.guessTypeForAddress(addr);
}

// does the given 'addr' points to an object of given 'type'?
// OR any valid Type at all (if type is undefined)
function isOfVMType(addr, type) {
   addr = any2addr(addr);
   if (type == undefined) {
      return vmTypeof(addr) != null;
   } else {
      if (typeof(type) == 'string') {
         type = findVMType(type);
      } 
      return sa.typedb.addressTypeIsEqualToType(addr, type);
   }
}

// reads static field value
function readVMStaticField(field) {
   var type = field.type;
   if (type.isCIntegerType() || type.isJavaPrimitiveType()) {
      return field.value;
   } else if (type.isPointerType()) {
      return field.address;
   } else if (type.isOopType()) {
      return field.oopHandle;      
   } else {
      return field.staticFieldAddress;
   }
}

// reads given instance field of VM object at 'addr'
function readVMInstanceField(field, addr) {
   var type = field.type;
   if (type.isCIntegerType() || type.isJavaPrimitiveType()) {
      return field.getValue(addr);
   } else if (type.isPointerType()) {
      return field.getAddress(addr);
   } else if (type.isOopType()) {
      return field.getOopHandle(addr);
   } else {
      return addr.addOffsetTo(field.offset);
   }
}

// returns name-value of pairs of VM type at given address.
// If address is unspecified, reads static fields as name-value pairs.
function readVMType(type, addr) {
   if (typeof(type) == 'string') {
      type = findVMType(type);
   }
   if (addr != undefined) {
      addr = any2addr(addr);
   }

   var result = new Object();
   var staticOnly = (addr == undefined);
   while (type != null) {
      var itr = type.fields;
      while (itr.hasNext()) {
         var field = itr.next();
         var isStatic = field.isStatic();
         if (staticOnly && isStatic) {
            result[field.name] = readVMStaticField(field);
         } else if (!staticOnly && !isStatic) {
            result[field.name] = readVMInstanceField(field, addr);
         }
      }
      type = type.superclass;
   } 
   return result;
}

function printVMType(type, addr) {
   if (typeof(type) == 'string') {
      type = findVMType(type);
   }
   var obj = readVMType(type, addr);
   while (type != null) {
      var itr = type.fields;
      while (itr.hasNext()) {
         var field = itr.next();
         var name = field.name;
         var value = obj[name];
         if (value != undefined) {
            writeln(field.type.name, type.name + '::' + name, '=', value);
         }
      }
      type = type.superclass;  
   }
}

// define readXXX and printXXX functions for each VM struct/class Type
tmp = new Object();
tmp.itr = sa.typedb.types;
while (tmp.itr.hasNext()) {
   tmp.type = tmp.itr.next();
   tmp.name = tmp.type.name;
   if (tmp.type.isPointerType() || tmp.type.isOopType() ||
      tmp.type.isCIntegerType() || tmp.type.isJavaPrimitiveType() ||
      tmp.name.equals('address') ||
      tmp.name.equals("<opaque>")) {
         // ignore;
         continue;
   } else {
      // some type names have ':', '<', '>', '*', ' '. replace to make it as a
      // JavaScript identifier
      tmp.name = ("" + tmp.name).replace(/[:<>* ]/g, '_');
      eval("function read" + tmp.name + "(addr) {" +
           "   return readVMType('" + tmp.name + "', addr);}"); 
      eval("function print" + tmp.name + "(addr) {" + 
           "   printVMType('" + tmp.name + "', addr); }");

      /* FIXME: do we need this?
      if (typeof(registerCommand) != 'undefined') {
          var name = "print" + tmp.name;
          registerCommand(name, name + " [address]", name);
      }
      */
   }
}
//clean-up the temporary
delete tmp;

// VMObject factory

// VM type to SA class map
var  vmType2Class = new Object();

// C2 only classes
try{
  vmType2Class["ExceptionBlob"] = sapkg.code.ExceptionBlob;
  vmType2Class["UncommonTrapBlob"] = sapkg.code.UncommonTrapBlob;
} catch(e) {
  // Ignore exception. C2 specific objects might be not 
  // available in client VM
}


// This is *not* exhaustive. Add more if needed.
// code blobs
vmType2Class["BufferBlob"] = sapkg.code.BufferBlob;
vmType2Class["nmethod"] = sapkg.code.NMethod;
vmType2Class["RuntimeStub"] = sapkg.code.RuntimeStub;
vmType2Class["SafepointBlob"] = sapkg.code.SafepointBlob;
vmType2Class["C2IAdapter"] = sapkg.code.C2IAdapter;
vmType2Class["DeoptimizationBlob"] = sapkg.code.DeoptimizationBlob;
vmType2Class["I2CAdapter"] = sapkg.code.I2CAdapter;
vmType2Class["OSRAdapter"] = sapkg.code.OSRAdapter;
vmType2Class["PCDesc"] = sapkg.code.PCDesc;

// interpreter
vmType2Class["InterpreterCodelet"] = sapkg.interpreter.InterpreterCodelet;

// Java Threads
vmType2Class["JavaThread"] = sapkg.runtime.JavaThread;
vmType2Class["CompilerThread"] = sapkg.runtime.CompilerThread;
vmType2Class["CodeCacheSweeperThread"] = sapkg.runtime.CodeCacheSweeperThread;
vmType2Class["ReferencePendingListLockerThread"] = sapkg.runtime.JavaThread;
vmType2Class["DebuggerThread"] = sapkg.runtime.DebuggerThread;

// gc
vmType2Class["GenCollectedHeap"] = sapkg.memory.GenCollectedHeap;
vmType2Class["DefNewGeneration"] = sapkg.memory.DefNewGeneration;
vmType2Class["TenuredGeneration"] = sapkg.memory.TenuredGeneration;

// generic VMObject factory for a given address
// This is equivalent to VirtualConstructor.
function newVMObject(addr) {
   addr = any2addr(addr);
   var result = null;
   forEachVMType(function (type) {
                    if (isOfVMType(addr, type)) {
                       var clazz = vmType2Class[type.name];
                       if (clazz != undefined) {
                          result = new clazz(addr);
                       }
                       return false;
                    } else {
                       return true;
                    }
                 });
   return result;
}

function vmobj2addr(vmobj) {
   return vmobj.address;
}

function addr2vmobj(addr) {
   return newVMObject(addr);
}     

// Miscellaneous utilities

// returns PointerLocation that describes the given pointer
function findPtr(addr) {
   addr = any2addr(addr);
   return sapkg.utilities.PointerFinder.find(addr);
}

// is given address a valid Oop?
function isOop(addr) {
   addr = any2addr(addr);
   var oopHandle = addr.addOffsetToAsOopHandle(0);
   return sapkg.utilities.RobustOopDeterminator.oopLooksValid(oopHandle);
}

// returns description of given pointer as a String
function whatis(addr) {
  addr = any2addr(addr);
  var ptrLoc = findPtr(addr);
  if (!ptrLoc.isUnknown()) {
    return ptrLoc.toString();
  }

  var vmType = vmTypeof(addr);
  if (vmType != null) {
    return "pointer to " + vmType.name;
  }

  var dso = loadObjectContainingPC(addr);
  if (dso == null) {
    return ptrLoc.toString();
  }

  var sym = dso.closestSymbolToPC(addr);
  if (sym != null) {
    return sym.name + '+' + sym.offset;
  }

  var s = dso.getName();
  var p = s.lastIndexOf("/");
  var base = dso.getBase();
  return s.substring(p+1, s.length) + '+' + addr.minus(base);
}
