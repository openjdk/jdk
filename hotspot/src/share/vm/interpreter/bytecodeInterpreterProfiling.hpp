/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2014 SAP SE. All rights reserved.
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

// This file defines a set of macros which are used by the c++-interpreter
// for updating a method's methodData object.


#ifndef SHARE_VM_INTERPRETER_BYTECODEINTERPRETERPROFILING_HPP
#define SHARE_VM_INTERPRETER_BYTECODEINTERPRETERPROFILING_HPP


// Global settings /////////////////////////////////////////////////////////////


// Enables profiling support.
#if defined(COMPILER2)
#define CC_INTERP_PROFILE
#endif

// Enables assertions for profiling code (also works in product-builds).
// #define CC_INTERP_PROFILE_WITH_ASSERTIONS


#ifdef CC_INTERP

// Empty dummy implementations if profiling code is switched off. //////////////

#ifndef CC_INTERP_PROFILE

#define SET_MDX(mdx)

#define BI_PROFILE_GET_OR_CREATE_METHOD_DATA(exception_handler)                \
  if (ProfileInterpreter) {                                                    \
    ShouldNotReachHere();                                                      \
  }

#define BI_PROFILE_ALIGN_TO_CURRENT_BCI()

#define BI_PROFILE_UPDATE_JUMP()
#define BI_PROFILE_UPDATE_BRANCH(is_taken)
#define BI_PROFILE_UPDATE_RET(bci)
#define BI_PROFILE_SUBTYPECHECK_FAILED(receiver)
#define BI_PROFILE_UPDATE_CHECKCAST(null_seen, receiver)
#define BI_PROFILE_UPDATE_INSTANCEOF(null_seen, receiver)
#define BI_PROFILE_UPDATE_CALL()
#define BI_PROFILE_UPDATE_FINALCALL()
#define BI_PROFILE_UPDATE_VIRTUALCALL(receiver)
#define BI_PROFILE_UPDATE_SWITCH(switch_index)


#else


// Non-dummy implementations ///////////////////////////////////////////////////

// Accessors for the current method data pointer 'mdx'.
#define MDX()        (istate->mdx())
#define SET_MDX(mdx)                                                           \
  if (TraceProfileInterpreter) {                                               \
    /* Let it look like TraceBytecodes' format. */                             \
    tty->print_cr("[%d]           %4d  "                                       \
                  "mdx " PTR_FORMAT "(%d)"                                     \
                  "  "                                                         \
                  " \t-> " PTR_FORMAT "(%d)",                                  \
                (int) THREAD->osthread()->thread_id(),                         \
                BCI(),                                                         \
                p2i(MDX()),                                                    \
                (MDX() == NULL                                                 \
                 ? 0                                                           \
                 : istate->method()->method_data()->dp_to_di((address)MDX())), \
                p2i(mdx),                                                      \
                istate->method()->method_data()->dp_to_di((address)mdx)        \
                );                                                             \
  };                                                                           \
  istate->set_mdx(mdx);


// Dumps the profiling method data for the current method.
#ifdef PRODUCT
#define BI_PROFILE_PRINT_METHOD_DATA()
#else  // PRODUCT
#define BI_PROFILE_PRINT_METHOD_DATA()                                         \
  {                                                                            \
    ttyLocker ttyl;                                                            \
    MethodData *md = istate->method()->method_data();                          \
    tty->cr();                                                                 \
    tty->print("method data at mdx " PTR_FORMAT "(0) for",                     \
               p2i(md->data_layout_at(md->bci_to_di(0))));                     \
    istate->method()->print_short_name(tty);                                   \
    tty->cr();                                                                 \
    if (md != NULL) {                                                          \
      md->print_data_on(tty);                                                  \
      address mdx = (address) MDX();                                           \
      if (mdx != NULL) {                                                       \
        tty->print_cr("current mdx " PTR_FORMAT "(%d)",                        \
                      p2i(mdx),                                                \
                      istate->method()->method_data()->dp_to_di(mdx));         \
      }                                                                        \
    } else {                                                                   \
      tty->print_cr("no method data");                                         \
    }                                                                          \
  }
#endif // PRODUCT


// Gets or creates the profiling method data and initializes mdx.
#define BI_PROFILE_GET_OR_CREATE_METHOD_DATA(exception_handler)                \
  if (ProfileInterpreter && MDX() == NULL) {                                   \
    /* Mdx is not yet initialized for this activation. */                      \
    MethodData *md = istate->method()->method_data();                          \
    if (md == NULL) {                                                          \
      MethodCounters* mcs;                                                     \
      GET_METHOD_COUNTERS(mcs);                                                \
      /* The profiling method data doesn't exist for this method, */           \
      /* create it if the counters have overflowed. */                         \
      if (mcs->invocation_counter()                                            \
                         ->reached_ProfileLimit(mcs->backedge_counter())) {    \
        /* Must use CALL_VM, because an async exception may be pending. */     \
        CALL_VM((InterpreterRuntime::profile_method(THREAD)),                  \
                exception_handler);                                            \
        md = istate->method()->method_data();                                  \
        if (md != NULL) {                                                      \
          if (TraceProfileInterpreter) {                                       \
            BI_PROFILE_PRINT_METHOD_DATA();                                    \
          }                                                                    \
          Method *m = istate->method();                                        \
          int bci = m->bci_from(pc);                                           \
          jint di = md->bci_to_di(bci);                                        \
          SET_MDX(md->data_layout_at(di));                                     \
        }                                                                      \
      }                                                                        \
    } else {                                                                   \
      /* The profiling method data exists, align the method data pointer */    \
      /* mdx to the current bytecode index. */                                 \
      if (TraceProfileInterpreter) {                                           \
        BI_PROFILE_PRINT_METHOD_DATA();                                        \
      }                                                                        \
      SET_MDX(md->data_layout_at(md->bci_to_di(BCI())));                       \
    }                                                                          \
  }


// Asserts that the current method data pointer mdx corresponds
// to the current bytecode.
#if defined(CC_INTERP_PROFILE_WITH_ASSERTIONS)
#define BI_PROFILE_CHECK_MDX()                                                 \
  {                                                                            \
    MethodData *md = istate->method()->method_data();                          \
    address mdx  = (address) MDX();                                            \
    address mdx2 = (address) md->data_layout_at(md->bci_to_di(BCI()));         \
    guarantee(md   != NULL, "1");                                              \
    guarantee(mdx  != NULL, "2");                                              \
    guarantee(mdx2 != NULL, "3");                                              \
    if (mdx != mdx2) {                                                         \
      BI_PROFILE_PRINT_METHOD_DATA();                                          \
      fatal3("invalid mdx at bci %d:"                                          \
             " was " PTR_FORMAT                                                \
             " but expected " PTR_FORMAT,                                      \
             BCI(),                                                            \
             mdx,                                                              \
             mdx2);                                                            \
    }                                                                          \
  }
#else
#define BI_PROFILE_CHECK_MDX()
#endif


// Aligns the method data pointer mdx to the current bytecode index.
#define BI_PROFILE_ALIGN_TO_CURRENT_BCI()                                      \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    MethodData *md = istate->method()->method_data();                          \
    SET_MDX(md->data_layout_at(md->bci_to_di(BCI())));                         \
  }


// Updates profiling data for a jump.
#define BI_PROFILE_UPDATE_JUMP()                                               \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    JumpData::increment_taken_count_no_overflow(MDX());                        \
    /* Remember last branch taken count. */                                    \
    mdo_last_branch_taken_count = JumpData::taken_count(MDX());                \
    SET_MDX(JumpData::advance_taken(MDX()));                                   \
  }


// Updates profiling data for a taken/not taken branch.
#define BI_PROFILE_UPDATE_BRANCH(is_taken)                                     \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    if (is_taken) {                                                            \
      BranchData::increment_taken_count_no_overflow(MDX());                    \
      /* Remember last branch taken count. */                                  \
      mdo_last_branch_taken_count = BranchData::taken_count(MDX());            \
      SET_MDX(BranchData::advance_taken(MDX()));                               \
    } else {                                                                   \
      BranchData::increment_not_taken_count_no_overflow(MDX());                \
      SET_MDX(BranchData::advance_not_taken(MDX()));                           \
    }                                                                          \
  }


// Updates profiling data for a ret with given bci.
#define BI_PROFILE_UPDATE_RET(bci)                                             \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    MethodData *md = istate->method()->method_data();                          \
/* FIXME: there is more to do here than increment and advance(mdx)! */         \
    CounterData::increment_count_no_overflow(MDX());                           \
    SET_MDX(RetData::advance(md, bci));                                        \
  }

// Decrement counter at checkcast if the subtype check fails (as template
// interpreter does!).
#define BI_PROFILE_SUBTYPECHECK_FAILED(receiver)                               \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    ReceiverTypeData::increment_receiver_count_no_overflow(MDX(), receiver);   \
    ReceiverTypeData::decrement_count(MDX());                                  \
  }

// Updates profiling data for a checkcast (was a null seen? which receiver?).
#define BI_PROFILE_UPDATE_CHECKCAST(null_seen, receiver)                       \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    if (null_seen) {                                                           \
      ReceiverTypeData::set_null_seen(MDX());                                  \
    } else {                                                                   \
      /* Template interpreter doesn't increment count. */                      \
      /* ReceiverTypeData::increment_count_no_overflow(MDX()); */              \
      ReceiverTypeData::increment_receiver_count_no_overflow(MDX(), receiver); \
    }                                                                          \
    SET_MDX(ReceiverTypeData::advance(MDX()));                                 \
  }


// Updates profiling data for an instanceof (was a null seen? which receiver?).
#define BI_PROFILE_UPDATE_INSTANCEOF(null_seen, receiver)                      \
  BI_PROFILE_UPDATE_CHECKCAST(null_seen, receiver)


// Updates profiling data for a call.
#define BI_PROFILE_UPDATE_CALL()                                               \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    CounterData::increment_count_no_overflow(MDX());                           \
    SET_MDX(CounterData::advance(MDX()));                                      \
  }


// Updates profiling data for a final call.
#define BI_PROFILE_UPDATE_FINALCALL()                                          \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    VirtualCallData::increment_count_no_overflow(MDX());                       \
    SET_MDX(VirtualCallData::advance(MDX()));                                  \
  }


// Updates profiling data for a virtual call with given receiver Klass.
#define BI_PROFILE_UPDATE_VIRTUALCALL(receiver)                                \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    VirtualCallData::increment_receiver_count_no_overflow(MDX(), receiver);    \
    SET_MDX(VirtualCallData::advance(MDX()));                                  \
  }


// Updates profiling data for a switch (tabelswitch or lookupswitch) with
// given taken index (-1 means default case was taken).
#define BI_PROFILE_UPDATE_SWITCH(switch_index)                                 \
  if (ProfileInterpreter && MDX() != NULL) {                                   \
    BI_PROFILE_CHECK_MDX();                                                    \
    MultiBranchData::increment_count_no_overflow(MDX(), switch_index);         \
    SET_MDX(MultiBranchData::advance(MDX(), switch_index));                    \
  }


// The end /////////////////////////////////////////////////////////////////////

#endif // CC_INTERP_PROFILE

#endif // CC_INTERP

#endif // SHARE_VM_INTERPRETER_BYTECODECINTERPRETERPROFILING_HPP
