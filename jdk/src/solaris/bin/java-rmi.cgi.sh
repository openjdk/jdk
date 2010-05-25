#!/bin/sh

#
# Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

#
#

#
# This script executes the Java interpreter, defines properties
# that correspond to the CGI 1.0 environment variables, and executes
# the class "sun.rmi.transport.proxy.CGIHandler".  It should be
# installed in the directory to which the HTTP server maps the
# URL path "/cgi-bin".
#
# (Configuration is necessary as noted below.)
#
# This class will support a QUERY_STRING of the form "forward=<port>"
# with a REQUEST_METHOD "POST".  The body of the request will be
# forwarded (as another POST request) to the server listening on the
# specified port (must be >= 1024).  The response from this forwarded
# request will be the response to the original request.
#
# CONFIGURATION:
#
# Fill in correct absolute path to Java interpreter below.  For example,
# the "PATH=" line might be changed to the follow if the JDK is installed
# at the path "/home/peter/java":
#
# PATH=/home/peter/java/bin:$PATH
#
PATH=/usr/local/java/bin:$PATH
exec java \
	-DAUTH_TYPE="$AUTH_TYPE" \
	-DCONTENT_LENGTH="$CONTENT_LENGTH" \
	-DCONTENT_TYPE="$CONTENT_TYPE" \
	-DGATEWAY_INTERFACE="$GATEWAY_INTERFACE" \
	-DHTTP_ACCEPT="$HTTP_ACCEPT" \
	-DPATH_INFO="$PATH_INFO" \
	-DPATH_TRANSLATED="$PATH_TRANSLATED" \
	-DQUERY_STRING="$QUERY_STRING" \
	-DREMOTE_ADDR="$REMOTE_ADDR" \
	-DREMOTE_HOST="$REMOTE_HOST" \
	-DREMOTE_IDENT="$REMOTE_IDENT" \
	-DREMOTE_USER="$REMOTE_USER" \
	-DREQUEST_METHOD="$REQUEST_METHOD" \
	-DSCRIPT_NAME="$SCRIPT_NAME" \
	-DSERVER_NAME="$SERVER_NAME" \
	-DSERVER_PORT="$SERVER_PORT" \
	-DSERVER_PROTOCOL="$SERVER_PROTOCOL" \
	-DSERVER_SOFTWARE="$SERVER_SOFTWARE" \
	sun.rmi.transport.proxy.CGIHandler
