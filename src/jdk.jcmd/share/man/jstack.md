---
# Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

title: 'JSTACK(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jstack - print Java stack traces of Java threads for a specified Java process

## Synopsis

**Note:** This command is experimental and unsupported.

`jstack` \[*options*\] *pid*

*options*
:   This represents the `jstack` command-line options. See [Options for the
    jstack Command].

*pid*
:   The process ID for which the stack trace is printed. The process must be a
    Java process. To get a list of Java processes running on a machine, use
    either the `ps` command or, if the JVM processes are not running in a
    separate docker instance, the [jps](jps.html) command.

## Description

The `jstack` command prints Java stack traces of Java threads for a specified
Java process. For each Java frame, the full class name, method name, byte code
index (BCI), and line number, when available, are printed. C++ mangled names
aren't demangled. To demangle C++ names, the output of this command can be
piped to `c++filt`. When the specified process is running on a 64-bit JVM, you
might need to specify the `-J-d64` option, for example: `jstack -J-d64` *pid*.

**Note:**

This command is unsupported and might not be available in future releases of
the JDK. In Windows Systems where the `dbgeng.dll` file isn't present, the
Debugging Tools for Windows must be installed so that these tools work. The
`PATH` environment variable needs to contain the location of the `jvm.dll` that
is used by the target process, or the location from which the core dump file
was produced.

## Options for the jstack Command

`-l`
:   The long listing option prints additional information about locks.

`-h` or `-help`
:   Prints a help message.
