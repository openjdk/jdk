/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JDK-8046905: apply on apply is broken
 *
 * @test
 * @run
 */

var apply = Function.prototype.apply;
var call = Function.prototype.call;
var sort = Array.prototype.sort;
var join = Array.prototype.join;

// Running three times so that we test an already linked call site too:
// i==0: linking initially with assumed optimistic returned type int.
// i==1: linking after deoptimization with returned type Object.
// i==2: re-running code linked in previous iteration. This will
//       properly exercise the guards too.
print("1 level of apply")
for(i = 0; i < 3; ++i) {
    print(sort.apply([4,3,2,1]))
}
print("2 levels of apply")
for(i = 0; i < 3; ++i) {
    print(apply.apply(sort,[[4,3,2,1]]))
}
print("3 levels of apply")
for(i = 0; i < 3; ++i) {
    print(apply.apply(apply,[sort,[[4,3,2,1]]]))
}
print("4 levels of apply")
for(i = 0; i < 3; ++i) {
    print(apply.apply(apply,[apply,[sort,[[4,3,2,1]]]]))
}
print("5 levels of apply")
for(i = 0; i < 3; ++i) {
    print(apply.apply(apply,[apply,[apply,[sort,[[4,3,2,1]]]]]))
}
print("Many levels of apply!")
for(i = 0; i < 3; ++i) {
    print(apply.apply(apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[apply,[sort,[[4,3,2,1]]]]]]]]]]]]]]]]]]]]]]))
}

print("different invocations that'll trigger relinking")
var invocation = [sort,[[4,3,2,1]]];
for(i = 0; i < 4; ++i) {
    print(apply.apply(apply,[apply,invocation]))
    // First change after i==1, so it relinks an otherwise stable linkage
    if(i == 1) {
    invocation = [sort,[[8,7,6,5]]];
    } else if(i == 2) {
        invocation = [join,[[8,7,6,5],["-"]]];
    }
}

print("Many levels of call!")
for(i = 0; i < 3; ++i) {
    print(call.call(call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,call,sort,[4,3,2,1]))
}

print("call apply call apply call... a lot");
for(i = 0; i < 3; ++i) {
    print(apply.call(call, apply, [call, apply, [call, apply, [call, apply, [call, apply, [call, apply, [sort, [4,3,2,1]]]]]]]))
}

print("apply call apply call apply... a lot");
for(i = 0; i < 3; ++i) {
    print(call.apply(apply, [call, apply, [call, apply, [call, apply, [call, apply, [call, apply, [call, apply, [call, sort, [[4,3,2,1]]]]]]]]]))
}
