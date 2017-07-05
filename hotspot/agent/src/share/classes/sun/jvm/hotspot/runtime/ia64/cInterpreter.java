/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime.ia64;

import java.util.*;
// import sun.jvm.hotspot.asm.ia64.*;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.compiler.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

/** Specialization of and implementation of abstract methods of the
    Frame class for the ia64 family of CPUs. */

public class cInterpreter extends VMObject {
  private static final boolean DEBUG = true;

  private static AddressField bcpField;
  private static AddressField localsField;
  private static AddressField constantsField;
  private static AddressField methodField;
  private static AddressField stackField;       // i.e. tos
  private static AddressField stackBaseField;   // ultimate bottom of stack
  private static AddressField stackLimitField;  // ultimate top of stack
  private static AddressField monitorBaseField;
  private static CIntegerField messageField;
  private static AddressField prevFieldField;
  private static AddressField wrapperField;
  private static AddressField prevField;

  private static int NO_REQUEST;
  private static int INITIALIZE;
  private static int METHOD_ENTRY;
  private static int METHOD_RESUME;
  private static int GOT_MONITORS;
  private static int RETHROW_EXCEPTION;
  private static int CALL_METHOD;
  private static int RETURN_FROM_METHOD;
  private static int RETRY_METHOD;
  private static int MORE_MONITORS;
  private static int THROWING_EXCEPTION;
  private static int POPPING_FRAME;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {

    Type cInterpreterType = db.lookupType("cInterpreter");
    bcpField = cInterpreterType.getAddressField("_bcp");
    localsField = cInterpreterType.getAddressField("_locals");
    constantsField = cInterpreterType.getAddressField("_constants");
    methodField = cInterpreterType.getAddressField("_method");
    stackField = cInterpreterType.getAddressField("_stack");
    stackBaseField = cInterpreterType.getAddressField("_stack_base");
    stackLimitField = cInterpreterType.getAddressField("_stack_limit");
    monitorBaseField = cInterpreterType.getAddressField("_monitor_base");
    // messageField = cInterpreterType.getCIntegerField("_msg");
    messageField = null;
    wrapperField = cInterpreterType.getAddressField("_wrapper");
    prevField = cInterpreterType.getAddressField("_prev_link");

    /*
    NO_REQUEST = db.lookupIntConstant("no_request").intValue();
    INITIALIZE = db.lookupIntConstant("initialize").intValue();
    METHOD_ENTRY = db.lookupIntConstant("method_entry").intValue();
    METHOD_RESUME = db.lookupIntConstant("method_resume").intValue();
    GOT_MONITORS = db.lookupIntConstant("got_monitors").intValue();
    RETHROW_EXCEPTION = db.lookupIntConstant("rethrow_exception").intValue();
    CALL_METHOD = db.lookupIntConstant("call_method").intValue();
    RETURN_FROM_METHOD = db.lookupIntConstant("return_from_method").intValue();
    RETRY_METHOD = db.lookupIntConstant("retry_method").intValue();
    MORE_MONITORS = db.lookupIntConstant("more_monitors").intValue();
    THROWING_EXCEPTION = db.lookupIntConstant("throwing_exception").intValue();
    POPPING_FRAME = db.lookupIntConstant("popping_frame").intValue();
    */
  }


  public cInterpreter(Address addr) {
    super(addr);
  }

  public Address prev() {
    return prevField.getValue(addr);
  }

  public Address locals() {

    Address val = localsField.getValue(addr);
    return val;
  }

  public Address localsAddr() {

    Address localsAddr = localsField.getValue(addr);
    return localsAddr;
  }

  public Address bcp() {

    Address val = bcpField.getValue(addr);
    return val;
  }

  public Address bcpAddr() {

    Address bcpAddr = addr.addOffsetTo(bcpField.getOffset());
    return bcpAddr;
  }

  public Address constants() {

    Address val = constantsField.getValue(addr);
    return val;
  }

  public Address constantsAddr() {

    Address constantsAddr = constantsField.getValue(addr);
    return constantsAddr;
  }

  public Address method() {

    Address val = methodField.getValue(addr);
    return val;
  }
  public Address methodAddr() {

    Address methodAddr = addr.addOffsetTo(methodField.getOffset());
    return methodAddr;
  }

  public Address stack() {

    Address val = stackField.getValue(addr);
    return val;
  }

  public Address stackBase() {

    Address val = stackBaseField.getValue(addr);
    return val;
  }

  public Address stackLimit() {

    Address val = stackLimitField.getValue(addr);
    return val;
  }

  public Address monitorBase() {

    Address val = monitorBaseField.getValue(addr);
    return val;
  }

  public Address wrapper() {

    return wrapperField.getValue(addr);
  }

  public int message() {
    int val = (int) messageField.getValue(addr);
    return val;
  }

}
