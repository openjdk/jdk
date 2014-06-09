/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8015267: have a List/Deque adapter for JS array-like objects
 *
 * @test
 * @run
 */

var a = ['a', 'b', 'c', 'd']

var l = Java.to(a, java.util.List)
print(l instanceof java.util.List)
print(l instanceof java.util.Deque)

print(l[0])
print(l[1])
print(l[2])
print(l[3])

print(l.size())

l.push('x')
print(a)

l.addLast('y')
print(a)

print(l.pop())
print(l.removeLast())
print(a)

l.add('e')
l.add(5, 'f')
print(a)

l.add(0, 'z')
print(a)

l.add(2, 'x')
print(a)

l[7] = 'g'
print(a)

try { l.add(15, '') } catch(e) { print(e.class) }
try { l.remove(15) } catch(e) { print(e.class) }
try { l.add(-1, '') } catch(e) { print(e.class) }
try { l.remove(-1) } catch(e) { print(e.class) }

l.remove(7)
l.remove(2)
l.remove(0)
print(a)

print(l.peek())
print(l.peekFirst())
print(l.peekLast())

print(l.element())
print(l.getFirst())
print(l.getLast())

l.offer('1')
l.offerFirst('2')
l.offerLast('3')
print(a)

a = ['1', '2', 'x', '3', '4', 'x', '5', '6', 'x', '7', '8']
print(a)
var l = Java.to(a, java.util.List)
l.removeFirstOccurrence('x')
print(a)
l.removeLastOccurrence('x')
print(a)

var empty = Java.to([], java.util.List)
try { empty.pop() } catch(e) { print(e.class) }
try { empty.removeFirst() } catch(e) { print(e.class) }
try { empty.removeLast() } catch(e) { print(e.class) }

try { empty.element() } catch(e) { print(e.class) }
try { empty.getFirst() } catch(e) { print(e.class) }
try { empty.getLast() } catch(e) { print(e.class) }

print(empty.peek())
print(empty.peekFirst())
print(empty.peekLast())
