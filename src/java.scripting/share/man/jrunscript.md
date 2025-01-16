---
# Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JRUNSCRIPT(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jrunscript - run a command-line script shell that supports interactive and
batch modes

## Synopsis

**Note:**

This tool is **experimental** and unsupported. It is deprecated and will be
removed in a future release.

`jrunscript` \[*options*\] \[*arguments*\]

*options*
:   This represents the `jrunscript` command-line options that can be used. See
    [Options for the jrunscript Command].

*arguments*
:   Arguments, when used, follow immediately after options or the command name.
    See [Arguments].

## Description

The `jrunscript` command is a language-independent command-line script shell.
The `jrunscript` command supports both an interactive (read-eval-print) mode
and a batch (`-f` option) mode of script execution. By default, JavaScript is
the language used, but the `-l` option can be used to specify a different
language. By using Java to scripting language communication, the `jrunscript`
command supports an exploratory programming style.

If JavaScript is used, then before it evaluates a user defined script, the
`jrunscript` command initializes certain built-in functions and objects, which
are documented in the API Specification for `jrunscript` JavaScript built-in
functions.

## Options for the jrunscript Command

`-cp` *path* or `-classpath` *path*
:   Indicates where any class files are that the script needs to access.

`-D`*name*`=`*value*
:   Sets a Java system property.

`-J`*flag*
:   Passes *flag* directly to the Java Virtual Machine where the `jrunscript`
    command is running.

`-l` *language*
:   Uses the specified scripting language. By default, JavaScript is used. To
    use other scripting languages, you must specify the corresponding script
    engine's JAR file with the `-cp` or `-classpath` option.

`-e` *script*
:   Evaluates the specified script. This option can be used to run one-line
    scripts that are specified completely on the command line.

`-encoding` *encoding*
:   Specifies the character encoding used to read script files.

`-f` *script-file*
:   Evaluates the specified script file (batch mode).

`-f -`
:   Enters interactive mode to read and evaluate a script from standard input.

`-help` or `-?`
:   Displays a help message and exits.

`-q`
:   Lists all script engines available and exits.

## Arguments

If arguments are present and if no `-e` or `-f` option is used, then the first
argument is the script file and the rest of the arguments, if any, are passed
as script arguments. If arguments and the `-e` or the `-f` option are used,
then all arguments are passed as script arguments. If arguments `-e` and `-f`
are missing, then the interactive mode is used.

## Example of Executing Inline Scripts

>   `jrunscript -e "print('hello world')"`

>   `jrunscript -e "cat('http://www.example.com')"`

## Example of Using Specified Language and Evaluate the Script File

>   `jrunscript -l js -f test.js`

## Example of Interactive Mode

```
jrunscript
js> print('Hello World\n');
Hello World
js> 34 + 55
89.0
js> t = new java.lang.Thread(function() { print('Hello World\n'); })
Thread[Thread-0,5,main]
js> t.start()
js> Hello World

js>
```

## Run Script File with Script Arguments

In this example, the `test.js` file is the script file. The `arg1`, `arg2`, and
`arg3` arguments are passed to the script. The script can access these
arguments with an arguments array.

>   `jrunscript test.js arg1 arg2 arg3`
