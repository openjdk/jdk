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
 * Generate HTML documentation for shell tool. Re-run this tool to regenerate
 * html doc when you change options.
 *
 * Usage:
 *
 *     jjs -scripting genshelldoc.js > shell.html
 */

var Options = Packages.jdk.nashorn.internal.runtime.options.Options;
var title = "Nashorn command line shell tool";

print(<<PREFIX
<html>
<head>
<title>
${title}
</title>
</head>
<body>
<h1>Usage</h1>
<p>
<code>
<b>jjs &lt;options&gt; &lt;script-files&gt; [ -- &lt;script-arguments&gt; ]</b>
</code>
</p>

<h1>${title} options</h1>

<table border="0">
<tr>
<th>name</th>
<th>type</th>
<th>default</th>
<th>description</th>
</tr>
PREFIX);

for each (opt in Options.validOptions) {

var isTimezone = (opt.type == "timezone");
var defValue = opt.defaultValue;
if (defValue == null) {
    defValue = "&lt;none&gt;";
}

if (isTimezone) {
    // don't output current user's timezone
    defValue = "&lt;default-timezone&gt;"
}

print(<<ROW
  <tr>
  <td><b>${opt.name} ${opt.shortName == null? "" : opt.shortName}</b></td>
  <td>${opt.type}</td>
  <td>${defValue}</td>
  <td>${opt.description}</td>
  </tr>
ROW);

}

print(<<SUFFIX
</table>
</body>
</html>
SUFFIX);
