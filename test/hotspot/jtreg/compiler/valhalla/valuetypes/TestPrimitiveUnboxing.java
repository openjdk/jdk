/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8392758
 * @summary C2 must handle boxing and unboxing call projections consistently
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:CompileCommand=dontinline,java.lang.Integer::intValue
 *                   -XX:CompileCommand=delayinline,java.lang.Long::longValue ${test.main.class}
 * @run main ${test.main.class}
 */

import jdk.test.lib.Asserts;

public class TestPrimitiveUnboxing {
    static final Integer THE_ANSWER = 42;

/*
// C2 incorrectly treated projections of eliminable unboxing calls as dead-loop-safe, allowing IGVN to create a self-referential boxing/unboxing call cycle that later crashes or hangs during macro elimination. The fix mirrors existing
//  boxing handling by making unboxing-call projections unsafe, allowing dead-loop analysis to detect and break the cycle before eliminating the calls.

  When the normal entry dies, the faulty dead-loop check collapses the Phi to the unboxing I/O projection, creating a self-referential unboxing call. Macro elimination then triggers the failure.

  I/O Phi -> intValue call -> I/O projection -> I/O Phi

  Because the return value is unused, macro expansion eliminates intValue() and attempts to bypass its I/O projection:

  replace_node(unbox_io_proj, call->in(TypeFunc::I_O));

  But call->in(TypeFunc::I_O) is now that same projection, producing:

  replace_node(unbox_io_proj, unbox_io_proj);

  That self-replacement triggers the fastdebug def-use assertion; product builds can fail to converge. No boxing macro is involved in this minimized testDeadLoop().
  
  
  # A fatal error has been detected by the Java Runtime Environment:
#
#  Internal Error (/oracle/jdk/open/src/hotspot/share/opto/node.cpp:205), pid=2745988, tid=2746003
#  assert(cnt == _outcnt) failed: no insertions allowed
#
# JRE version: Java(TM) SE Runtime Environment (28.0) (fastdebug build 28-internal-tohartma.open)
# Java VM: Java HotSpot(TM) 64-Bit Server VM (fastdebug 28-internal-tohartma.open, compiled mode, tiered, compressed oops, compact obj headers, g1 gc, linux-amd64)
# Problematic frame:
# V  [libjvm.so+0x17f5c68]  DUIterator_Fast::verify(Node const*, bool) [clone .part.0]+0x28

  C2:311   45    b  4       TestPrimitiveUnboxing::testDeadLoop (40 bytes)

  Stack: [0x00007f9c39e58000,0x00007f9c39f58000],  sp=0x00007f9c39f52ae0,  free space=1002k
  Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
  V  [libjvm.so+0x17f5c68]  DUIterator_Fast::verify(Node const*, bool) [clone .part.0]+0x28  (node.cpp:205)
  V  [libjvm.so+0x17f7017]  DUIterator_Last::verify_step(unsigned int)+0x167  (node.cpp:273)
  V  [libjvm.so+0x191991a]  PhaseIterGVN::subsume_node(Node*, Node*)+0x3aa  (node.hpp:1747)
  V  [libjvm.so+0x164e7ba]  PhaseMacroExpand::process_users_of_allocation(CallNode*, bool) [clone .constprop.0]+0x5ba  (macro.cpp:1504)
  V  [libjvm.so+0x16570dd]  PhaseMacroExpand::eliminate_boxing_node(CallStaticJavaNode*)+0x9d  (macro.cpp:1603)
  V  [libjvm.so+0x16677d1]  PhaseMacroExpand::eliminate_macro_nodes(bool)+0x681  (macro.cpp:3296)
  V  [libjvm.so+0xc4f160]  Compile::Optimize()+0x2180  (compile.cpp:3154)
  V  [libjvm.so+0xc51389]  Compile::Compile(ciEnv*, ciMethod*, int, Options, DirectiveSet*)+0x2059  (compile.cpp:891)
  V  [libjvm.so+0xa2913c]  C2Compiler::compile_method(ciEnv*, ciMethod*, int, bool, DirectiveSet*)+0x4cc  (c2compiler.cpp:149)
  V  [libjvm.so+0xc60cb4]  CompileBroker::invoke_compiler_on_method(CompileTask*)+0x844  (compileBroker.cpp:2041)
  V  [libjvm.so+0xc62098]  CompileBroker::compiler_thread_loop()+0x5e8  (compileBroker.cpp:1748)
  V  [libjvm.so+0x124f21b]  JavaThread::thread_main_inner()+0x13b  (javaThread.cpp:661)
  V  [libjvm.so+0x1c97196]  Thread::call_run()+0xb6  (thread.cpp:243)
  V  [libjvm.so+0x18782c8]  thread_native_entry(Thread*)+0x118  (os_linux.cpp:932)

*/
    // Test that unboxing methods are marked as dead loop safe
    static void testDeadLoop() {
        int j = 0;
        // Nested loops keep the dead parts of the graph alive for long enough
        do {
            for (int i = 0; i < 1; i++) {
                // This always throws
                int res = 42/0;
            }
        } while (j < 0);

        // Unreachable
        while (j < 1) {
            // We assert here when intValue is removed by macro expansion because
            // the dead loop entry created an I/O phi -> call -> proj data loop.
            THE_ANSWER.intValue();
        }
    }

/*

Unboxing result projections, like boxing projections, are already classified as dead-loop-unsafe by Node::is_dead_loop_safe(). Without excluding them from generic late-inline marking, mark_not_dead_loop_safe() violates its precondition
  and triggers a fastdebug assertion; the guard avoids this redundant marking.
  
# A fatal error has been detected by the Java Runtime Environment:
#
#  Internal Error (/oracle/jdk/open/src/hotspot/share/opto/node.hpp:1084), pid=2749500, tid=2749515
#  assert(is_dead_loop_safe()) failed: shouldn't be cleared yet
#
# JRE version: Java(TM) SE Runtime Environment (28.0) (fastdebug build 28-internal-tohartma.open)
# Java VM: Java HotSpot(TM) 64-Bit Server VM (fastdebug 28-internal-tohartma.open, compiled mode, tiered, compressed oops, compact obj headers, g1 gc, linux-amd64)
# Problematic frame:
# V  [libjvm.so+0xa362b5]  Node::mark_not_dead_loop_safe()+0x95
#

Current CompileTask:
C2:320   44    b  4       TestPrimitiveUnboxing::testLateInline (5 bytes)

Stack: [0x00007f94ec452000,0x00007f94ec552000],  sp=0x00007f94ec54e0f0,  free space=1008k
Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
V  [libjvm.so+0xa362b5]  Node::mark_not_dead_loop_safe()+0x95  (node.hpp:1084)
V  [libjvm.so+0xa3467b]  DirectCallGenerator::generate(JVMState*)+0x45b  (callGenerator.cpp:217)
V  [libjvm.so+0xe333ad]  Parse::do_call()+0x34d  (doCall.cpp:736)
V  [libjvm.so+0x18e9468]  Parse::do_one_bytecode()+0x458  (parse2.cpp:3815)
V  [libjvm.so+0x18cfe27]  Parse::do_one_block()+0x357  (parse1.cpp:1783)
V  [libjvm.so+0x18d0fc0]  Parse::do_all_blocks()+0x130  (parse1.cpp:775)
V  [libjvm.so+0x18d4a21]  Parse::Parse(JVMState*, ciMethod*, float)+0xaf1  (parse1.cpp:674)
V  [libjvm.so+0xa2bbe5]  ParseGenerator::generate(JVMState*)+0x135  (callGenerator.cpp:128)
V  [libjvm.so+0xc50b9c]  Compile::Compile(ciEnv*, ciMethod*, int, Options, DirectiveSet*)+0x186c  (compile.cpp:836)
V  [libjvm.so+0xa2913c]  C2Compiler::compile_method(ciEnv*, ciMethod*, int, bool, DirectiveSet*)+0x4cc  (c2compiler.cpp:149)
V  [libjvm.so+0xc60cb4]  CompileBroker::invoke_compiler_on_method(CompileTask*)+0x844  (compileBroker.cpp:2041)
V  [libjvm.so+0xc62098]  CompileBroker::compiler_thread_loop()+0x5e8  (compileBroker.cpp:1748)
V  [libjvm.so+0x124f21b]  JavaThread::thread_main_inner()+0x13b  (javaThread.cpp:661)
V  [libjvm.so+0x1c97196]  Thread::call_run()+0xb6  (thread.cpp:243)
V  [libjvm.so+0x18782c8]  thread_native_entry(Thread*)+0x118  (os_linux.cpp:932)
*/

    // Test that unboxing methods are not re-marked as not dead loop safe
    static long testLateInline(Long value) {
        return value.longValue();
    }

    public static void main(String[] args) {
        Asserts.assertThrows(ArithmeticException.class, TestPrimitiveUnboxing::testDeadLoop);
        Asserts.assertEQ(testLateInline(42L), 42L);
    }
}

