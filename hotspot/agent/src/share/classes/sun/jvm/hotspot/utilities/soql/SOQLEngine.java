/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

/**
 * This is SOQL (Simple Object Query Language) engine. This
 * uses JavaScript engine for the "select" and "where" expression
 * parts.
 */
public class SOQLEngine extends JSJavaScriptEngine {
   public static synchronized SOQLEngine getEngine() {
      if (soleInstance == null) {
         soleInstance = new SOQLEngine();
      }
      return soleInstance;
   }

   /**
      Query is of the form

         select &lt;java script code to select&gt;
         [ from [instanceof] &lt;class name&gt; [&lt;identifier&gt;]
           [ where &lt;java script boolean expression&gt; ]
         ]
   */
   public synchronized void executeQuery(String query, ObjectVisitor visitor)
                                                 throws SOQLException {
      debugPrint("query : " + query);
      StringTokenizer st = new StringTokenizer(query);
      if (st.hasMoreTokens()) {
         String first = st.nextToken();
         if (! first.equals("select") ) {
            throw new SOQLException("query syntax error: no 'select' clause");
         }
      } else {
         throw new SOQLException("query syntax error: no 'select' clause");
      }

      int selectStart = query.indexOf("select");
      int fromStart = query.indexOf("from");

      String selectExpr = null;
      String className = null;
      boolean isInstanceOf = false;
      String whereExpr = null;
      String identifier = null;

      if (fromStart != -1) {
         selectExpr = query.substring(selectStart + "select".length(), fromStart);
         st = new StringTokenizer(query.substring(fromStart + "from".length()));

         if (st.hasMoreTokens()) {
            String tmp = st.nextToken();
            if (tmp.equals("instanceof")) {
               isInstanceOf = true;
               if (! st.hasMoreTokens()) {
                  throw new SOQLException("no class name after 'instanceof'");
               }
               className = st.nextToken();
            } else {
               className = tmp;
            }
         } else {
            throw new SOQLException("query syntax error: class name must follow 'from'");
         }

         if (st.hasMoreTokens()) {
            identifier = st.nextToken();
            if (identifier.equals("where")) {
               throw new SOQLException("query syntax error: identifier should follow class name");
            }
            if (st.hasMoreTokens()) {
               String tmp = st.nextToken();
               if (! tmp.equals("where")) {
                  throw new SOQLException("query syntax error: 'where' clause expected after 'from' clause");
               }
               int whereEnd = query.lastIndexOf("where") + 5; // "where".length
               whereExpr = query.substring(whereEnd);
            }
         } else {
            throw new SOQLException("query syntax error: identifier should follow class name");
         }
      } else { // no from clause
         selectExpr = query.substring(selectStart + "select".length(), query.length());
      }

      executeQuery(new SOQLQuery(selectExpr, isInstanceOf, className, identifier, whereExpr), visitor);
   }

   private void executeQuery(SOQLQuery q, ObjectVisitor visitor) throws SOQLException {
      InstanceKlass kls = null;
      if (q.className != null) {
         kls = SystemDictionaryHelper.findInstanceKlass(q.className);
         if (kls == null) {
            throw new SOQLException(q.className + " is not found!");
         }
      }


      StringBuffer buf = new StringBuffer();
      buf.append("function result(");
      if (q.identifier != null) {
         buf.append(q.identifier);
      }
      buf.append(") { return ");
      buf.append(q.selectExpr.replace('\n', ' '));
      buf.append("; }");

      String selectCode = buf.toString();
      debugPrint(selectCode);
      String whereCode = null;
      if (q.whereExpr != null) {
         buf = new StringBuffer();
         buf.append("function filter(");
         buf.append(q.identifier);
         buf.append(") { return ");
         buf.append(q.whereExpr.replace('\n', ' '));
         buf.append("; }");
         whereCode = buf.toString();
         debugPrint(whereCode);
      } else {
         whereCode = "filter = null;";
      }

      beginQuery();
      // compile select expression and where condition
      evalString(selectCode, "", 1);
      evalString(whereCode,  "", 1);

      // iterate thru heap, if needed
      if (q.className != null) {
         try {
            iterateOops(kls, visitor, q.isInstanceOf);
         } finally {
            endQuery();
         }
      } else {
         // simple "select <expr>" query
         try {
            Object select = call("result", new Object[] {});
            visitor.visit(select);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   private void dispatchObject(Oop oop, ObjectVisitor visitor, boolean filterExists) {
      JSJavaObject jsObj = factory.newJSJavaObject(oop);
      Object[] args = new Object[] { jsObj };
      boolean b = true;

      try {
         if (filterExists) {
            Object res = call("filter", args);
            if (res instanceof Boolean) {
               b = ((Boolean)res).booleanValue();
            } else if (res instanceof Number) {
               b = ((Number)res).intValue() != 0;
            } else {
               b = (res != null);
            }
         }

         if (b) {
            Object select = call("result", args);
            visitor.visit(select);
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void iterateOops(final InstanceKlass ik, final ObjectVisitor visitor,
                            boolean includeSubtypes) {
      ObjectHeap oh = VM.getVM().getObjectHeap();
      oh.iterateObjectsOfKlass(new HeapVisitor() {
                    boolean filterExists;
                    public void prologue(long usedSize) {
                        filterExists = getScriptEngine().get("filter") != null;
                    }
                    public boolean doObj(Oop obj) {
                       dispatchObject(obj, visitor, filterExists);
                       return false;
                    }
                    public void epilogue() {}
                 }, ik, includeSubtypes);
   }

   // we create fresh ObjectReader and factory to avoid
   // excessive cache across queries.
   private void beginQuery() {
      objReader = new ObjectReader();
      factory = new JSJavaFactoryImpl();
   }

   // at the end of query we clear object reader cache
   // and factory cache
   private void endQuery() {
      objReader = null;
      factory = null;
   }

   protected ObjectReader getObjectReader() {
      return objReader;
   }

   protected JSJavaFactory getJSJavaFactory() {
      return factory;
   }

   protected boolean isQuitting() {
      return false;
   }

   protected void quit() {
      // do nothing
   }

   private static void debugPrint(String msg) {
      if (debug) System.out.println(msg);
   }

   private static final boolean debug;
   static {
      debug = System.getProperty("sun.jvm.hotspot.utilities.soql.SOQLEngine.debug") != null;
   }

   protected SOQLEngine() {
       super(debug);
       start();
   }

   private ObjectReader objReader;
   private JSJavaFactory factory;
   private static SOQLEngine soleInstance;
}
