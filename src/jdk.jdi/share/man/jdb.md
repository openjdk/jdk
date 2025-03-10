---
# Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JDB(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jdb - find and fix bugs in Java platform programs

## Synopsis

`jdb` \[*options*\] \[*classname*\] \[*arguments*\]

*options*
:   This represents the `jdb` command-line options. See [Options for the jdb
    command].

*classname*
:   This represents the name of the main class to debug.

*arguments*
:   This represents the arguments that are passed to the `main()` method of the
    class.

## Description

The Java Debugger (JDB) is a simple command-line debugger for Java classes. The
`jdb` command and its options call the JDB. The `jdb` command demonstrates the
Java Platform Debugger Architecture and provides inspection and debugging of a
local or remote JVM.

## Start a JDB Session

There are many ways to start a JDB session. The most frequently used way is to
have the JDB launch a new JVM with the main class of the application to be
debugged. Do this by substituting the `jdb` command for the `java` command in
the command line. For example, if your application's main class is `MyClass`,
then use the following command to debug it under the JDB:

>   `jdb MyClass`

When started this way, the `jdb` command calls a second JVM with the specified
parameters, loads the specified class, and stops the JVM before executing that
class's first instruction.

Another way to use the `jdb` command is by attaching it to a JVM that's already
running. Syntax for starting a JVM to which the `jdb` command attaches when the
JVM is running is as follows. This loads in-process debugging libraries and
specifies the kind of connection to be made.

>   `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n MyClass`

You can then attach the `jdb` command to the JVM with the following command:

>   `jdb -attach 8000`

8000 is the address of the running JVM.

The `MyClass` argument isn't specified in the `jdb` command line in this case
because the `jdb` command is connecting to an existing JVM instead of launching
a new JVM.

There are many other ways to connect the debugger to a JVM, and all of them are
supported by the `jdb` command. The Java Platform Debugger Architecture has
additional documentation on these connection options.

## Breakpoints

Breakpoints can be set in the JDB at line numbers or at the first instruction
of a method, for example:

-   The command `stop at MyClass:22` sets a breakpoint at the first instruction
    for line 22 of the source file containing `MyClass`.

-   The command `stop in java.lang.String.length` sets a breakpoint at the
    beginning of the method `java.lang.String.length`.

-   The command `stop in MyClass.<clinit>` uses `<clinit>` to identify the
    static initialization code for `MyClass`.

When a method is overloaded, you must also specify its argument types so that
the proper method can be selected for a breakpoint. For example,
`MyClass.myMethod(int,java.lang.String)` or `MyClass.myMethod()`.

The `clear` command removes breakpoints using the following syntax:
`clear MyClass:45`. Using the `clear` or `stop` command with no argument
displays a list of all breakpoints currently set. The `cont` command continues
execution.

## Stepping

The `step` command advances execution to the next line whether it's in the
current stack frame or a called method. The `next` command advances execution
to the next line in the current stack frame.

## Exceptions

When an exception occurs for which there isn't a `catch` statement anywhere in
the throwing thread's call stack, the JVM typically prints an exception trace
and exits. When running under the JDB, however, control returns to the JDB at
the offending throw. You can then use the `jdb` command to diagnose the cause
of the exception.

Use the `catch` command to cause the debugged application to stop at other
thrown exceptions, for example: `catch java.io.FileNotFoundException` or
`catch` `mypackage.BigTroubleException`. Any exception that's an instance of
the specified class or subclass stops the application at the point where the
exception is thrown.

The `ignore` command negates the effect of an earlier `catch` command. The
`ignore` command doesn't cause the debugged JVM to ignore specific exceptions,
but only to ignore the debugger.

## Options for the jdb command

When you use the `jdb` command instead of the `java` command on the command
line, the `jdb` command accepts many of the same options as the `java`
command.

The following options are accepted by the `jdb` command:

`-help`
:   Displays a help message.

`-sourcepath` *dir1*`:`*dir2*`:`...
:   Uses the specified path to search for source files in the specified path.
    If this option is not specified, then use the default path of dot (`.`).

`-attach` *address*
:   Attaches the debugger to a running JVM with the default connection
    mechanism.

`-listen` *address*
:   Waits for a running JVM to connect to the specified address with a standard
    connector.

`-listenany`
:   Waits for a running JVM to connect at any available address using a
    standard connector.

`-launch`
:   Starts the debugged application immediately upon startup of the `jdb`
    command. The `-launch` option removes the need for the `run` command. The
    debugged application is launched and then stopped just before the initial
    application class is loaded. At that point, you can set any necessary
    breakpoints and use the `cont` command to continue execution.

`-listconnectors`
:   Lists the connectors available in this JVM.

`-connect` *connector-name*`:`*name1*`=`*value1*....
:   Connects to the target JVM with the named connector and listed argument
    values.

`-dbgtrace` \[*flags*\]
:   Prints information for debugging the `jdb` command.

`-tclient`
:   Runs the application in the Java HotSpot VM client.

`-trackallthreads`
:   Track all threads as they are created, including virtual threads.
    See [Working With Virtual Threads](#working-with-virtual-threads) below.

`-tserver`
:   Runs the application in the Java HotSpot VM server.

`-J`*option*
:   Passes *option* to the JDB JVM, where option is one of the options described on
    the reference page for the Java application launcher. For example,
    `-J-Xms48m` sets the startup memory to 48 MB. See *Overview of Java
    Options* in [java](java.html).

The following options are forwarded to the debuggee process:

`-R`*option*
:   Passes *option* to the debuggee JVM, where option is one of the options described on
    the reference page for the Java application launcher. For example,
    `-R-Xms48m` sets the startup memory to 48 MB. See *Overview of Java
    Options* in [java](java.html).

`-v` or `-verbose`\[`:`*class*\|`gc`\|`jni`\]
:   Turns on the verbose mode.

`-D`*name*`=`*value*
:   Sets a system property.

`-classpath` *dir*
:   Lists directories separated by colons in which to look for classes.

`-X` *option*
:   A nonstandard target JVM option.

Other options are supported to provide alternate mechanisms for connecting the
debugger to the JVM that it's to debug.

## Working With Virtual Threads

Often virtual theads are created in such large numbers and frequency that they
can overwhelm a debugger. For this reason by default JDB does not keep track
of virtual threads as they are created. It will only keep track of virtual
threads that an event has arrived on, such as a breakpoint event. The
`-trackallthreads` option can be used to make JDB track all virtual threads
as they are created.

When JDB first connects, it requests a list of all known threads from the Debug Agent.
By default the debug agent does not return any virtual threads in this list, once
again because the list could be so large that it overwhelms the debugger. The
Debug Agent has an `includevirtualthreads` option that can be enabled to change
this behavior so all known virtual threads will be included in the list. The
JDB `-trackallthreads` option will cause JDB to automatically enable the Debug Agent's
`includevirtualthreads` option when JDB launches an application to debug. However, keep in mind
that the Debug Agent may not know about any virtual threads that were created
before JDB attached to the debugged application.
