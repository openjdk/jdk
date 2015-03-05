#// Usage: jjs -fx showenv.js

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

if (!$OPTIONS._fx) {
    print("Usage: jjs -fx showenv.js");
    exit(1);
}

// This script displays environment entries as a HTML table.
// Demonstrates heredoc to generate HTML content and display
// using JavaFX WebView.

// JavaFX classes used
var Scene     = Java.type("javafx.scene.Scene");
var WebView   = Java.type("javafx.scene.web.WebView");

// JavaFX start method
function start(stage) {
    start.title = "Your Environment";
    var wv = new WebView();
    var envrows = "";
    for (var i in $ENV) {
        envrows += <<TBL
<tr>
<td>
${i}
</td>
<td>
${$ENV[i]}
</td>
</tr>
TBL
    }

    wv.engine.loadContent(<<EOF
<html>
<head>
<title>
Your Environment
</title>
</head>
<body>
<h1>Your Environment</h1>
<table border="1">
${envrows}
</table>
</body>
</html>
EOF, "text/html");
    stage.scene = new Scene(wv, 750, 500);
    stage.show();
}
