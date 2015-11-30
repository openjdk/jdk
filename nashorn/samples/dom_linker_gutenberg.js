#// Usage: jjs -scripting dom_linker_gutenberg.js

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

// Simple example that demonstrates reading XML Rss feed
// to generate a HTML file from script and show it by browser.
// Uses XML DOM parser along with DOM element pluggable dynalink linker
// for ease of navigation of DOM (child) elements by name.

// Java classes used
var DocBuilderFac = Java.type("javax.xml.parsers.DocumentBuilderFactory");
var Node = Java.type("org.w3c.dom.Node");
var File = Java.type("java.io.File");
var FileWriter = Java.type("java.io.FileWriter");
var PrintWriter = Java.type("java.io.PrintWriter");

// parse XML from uri and return Document
function parseXML(uri) {
    var docBuilder = DocBuilderFac.newInstance().newDocumentBuilder();
    return docBuilder["parse(java.lang.String)"](uri);
}

// generate HTML using here-doc and string interpolation
function getBooksHtml() {
    var doc = parseXML("http://www.gutenberg.org/cache/epub/feeds/today.rss");
    // wrap document root Element as script convenient object
    var rss = doc.documentElement;

    var str = <<HEAD

<html>
<title>${rss._channel._title._}</title>
<body>
<h1>${rss._channel._description._}</h1>
<p>
Published on ${rss._channel._pubDate._}
</p>

HEAD

    var items = rss._channel._item;
    for each (var i in items) {
        str += <<LIST

<dl>
<dt><a href="${i._link._}">${i._title._}</a></dt>
<dd>${i._description._}</dd>
</dl>

LIST
    }
    str += <<END

</body>
</html>

END
    return str;
}

// write the string to the given file
function writeTo(file, str) {
    var w = new PrintWriter(new FileWriter(file));
    try {
        w.print(str);
    } finally {
        w.close();
    }
}

// generate books HTML
var str = getBooksHtml();

// write to file. __DIR__ is directory where
// this script is stored.
var file = new File(__DIR__ + "books.html");
writeTo(file, str);

// show it by desktop browser
try {
    var Desktop = Java.type("java.awt.Desktop");
    Desktop.desktop.browse(file.toURI());
} catch (e) {
    print(e);
}
