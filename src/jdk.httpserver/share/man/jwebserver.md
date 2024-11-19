---
# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

title: 'JWEBSERVER(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jwebserver - launch the Java Simple Web Server

## Synopsis

`jwebserver` \[*options*\]

*options*
:   Command-line options. For a detailed description of the options, see [Options].

## Description
The `jwebserver` tool provides a minimal HTTP server, designed to be used
for prototyping, testing, and debugging. It serves a single directory hierarchy,
and only serves static files. Only HTTP/1.1 is supported;
HTTP/2 and HTTPS are not supported.

Only idempotent HEAD and GET requests are served. Any other requests receive
a `501 - Not Implemented` or a `405 - Not Allowed` response. GET requests are
mapped to the directory being served, as follows:

* If the requested resource is a file, its content is served.
* If the requested resource is a directory that contains an index file,
the content of the index file is served.
* Otherwise, the names of all files and subdirectories of the directory are
listed. Symbolic links and hidden files are not listed or served.

MIME types are configured automatically, using the built-in table. For example,
`.html` files are served as `text/html` and `.java` files are served as
`text/plain`.

`jwebserver` is located in the jdk.httpserver module, and can alternatively
be started with `java -m jdk.httpserver`. It is based on the web server
implementation in the `com.sun.net.httpserver` package.
The `com.sun.net.httpserver.SimpleFileServer` class provides a programmatic
way to retrieve the server and its components for reuse and extension.

## Usage
```
jwebserver [-b bind address] [-p port] [-d directory]
           [-o none|info|verbose] [-h to show options]
           [-version to show version information]
```

## Options

`-h` or `-?` or `--help`
:   Prints the help message and exits.

`-b` *addr* or `--bind-address` *addr*
:   Specifies the address to bind to.
    Default: 127.0.0.1 or ::1 (loopback).
    For all interfaces use `-b 0.0.0.0` or `-b ::`.

`-d` *dir* or `--directory` *dir*
:   Specifies the directory to serve.
    Default: current directory.

`-o` *level* or `--output` *level*
:   Specifies the output format. `none` | `info` | `verbose`.
    Default: `info`.

`-p` *port* or `--port` *port*
:   Specifies the port to listen on.
    Default: 8000.

`-version` or `--version`
:   Prints the version information and exits.

To stop the server, press `Ctrl + C`.

## Starting the Server
The following command starts the Simple Web Server:
```
$ jwebserver
```
If startup is successful, the server prints a message to `System.out`
listing the local address and the absolute path of the directory being
served. For example:
```
$ jwebserver
Binding to loopback by default. For all interfaces use "-b 0.0.0.0" or "-b ::".
Serving /cwd and subdirectories on 127.0.0.1 port 8000
URL http://127.0.0.1:8000/
```

## Configuration
By default, the server runs in the foreground and binds to the loopback
address and port 8000. This can be changed with the `-b` and `-p` options.
For example, to bind the Simple Web Server to all interfaces, use:
```
$ jwebserver -b 0.0.0.0
Serving /cwd and subdirectories on 0.0.0.0 (all interfaces) port 8000
URL http://123.456.7.891:8000/
```
Note that this makes the web server accessible to all hosts on the network.
*Do not do this unless you are sure the server cannot leak any sensitive
information.*

As another example, use the following command to run on port 9000:
```
$ jwebserver -p 9000
```

By default, the files of the current directory are served. A different
directory can be specified with the `-d` option.

By default, every request is logged on the console. The output looks like
this:
```
127.0.0.1 - - [10/Feb/2021:14:34:11 +0000] "GET /some/subdirectory/ HTTP/1.1" 200 -
```
Logging output can be changed with the `-o` option. The default setting is
`info`. The `verbose` setting additionally includes the request and response
headers as well as the absolute path of the requested resource.

## Stopping the Server
Once started successfully, the server runs until it is stopped. On Unix
platforms, the server can be stopped by sending it a `SIGINT` signal
(`Ctrl+C` in a terminal window).

## Help Option
The `-h` option displays a help message describing the usage and the options
of the `jwebserver`.
