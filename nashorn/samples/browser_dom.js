#// Usage: jjs -fx browser_dom.js

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
    print("Usage: jjs -fx browser_dom.js");
    exit(1);
}

// JavaFX classes used
var ChangeListener = Java.type("javafx.beans.value.ChangeListener");
var Scene     = Java.type("javafx.scene.Scene");
var WebView   = Java.type("javafx.scene.web.WebView");

// JavaFX start method
function start(stage) {
    stage.title = "Web View";
    var wv = new WebView();
    wv.engine.loadContent(<<EOF
<html>
<head>
<title>
This is the title
</title>
<script>
// click count for OK button
var okCount = 0;
</script>
</head>
<body>
Button from the input html<br>
<button type="button" onclick="okCount++">OK</button><br>
</body>
</html>
EOF, "text/html");

    // attach onload handler
    wv.engine.loadWorker.stateProperty().addListener(
        new ChangeListener() {
            changed: function() {
               // DOM document element
               var document = wv.engine.document;
               // DOM manipulation
               var btn = document.createElement("button");
               var n = 0;
               // attach a button handler - nashorn function!
               btn.onclick = function() {
                   n++; print("You clicked " + n + " time(s)");
                   print("you clicked OK " + wv.engine.executeScript("okCount"));
               };
               // attach text to button
               var t = document.createTextNode("Click Me!"); 
               btn.appendChild(t);
               // attach button to the document
               document.body.appendChild(btn); 
           }
        }
    );
    stage.scene = new Scene(wv, 750, 500);
    stage.show();
}
