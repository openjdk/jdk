#// Usage: jjs -scripting gutenberg.js

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

// Simple example that demonstrates reading XML Rss feed
// to generate a HTML file from script and show it by browser

// Java classes used
var Characters = Java.type("javax.xml.stream.events.Characters");
var Factory = Java.type("javax.xml.stream.XMLInputFactory");
var File = Java.type("java.io.File");
var FileWriter = Java.type("java.io.FileWriter");
var PrintWriter = Java.type("java.io.PrintWriter");
var URL = Java.type("java.net.URL");

// read Rss feed from a URL. Returns an array
// of objects having only title and link properties
function readRssFeed(url) {
    var fac = Factory.newInstance();
    var reader = fac.createXMLEventReader(url.openStream());

    // get text content from next event
    function getChars() {
        var result = "";
        var e = reader.nextEvent();
        if (e instanceof Characters) {
            result = e.getData();
        }
        return result;
    }

    var items = [];
    var title, link;
    var inItem = false;
    while (reader.hasNext()) {
        var evt = reader.nextEvent();
        if (evt.isStartElement()) {
            var local = evt.name.localPart;
            if (local == "item") {
               // capture title, description now
               inItem = true;
            }

            if (inItem) {
                switch (local) {
                    case 'title':
                        title = getChars();
                        break;
                    case 'link':
                        link = getChars();
                        break;
                }
            }
        } else if (evt.isEndElement()) {
            var local = evt.name.localPart;
            if (local == "item") {
                // one item done, save it in result array
                items.push({ title: title, link: link });
                inItem = false;
            }
        }
    }

    return items;
}

// generate simple HTML for an RSS feed
function getBooksHtml() {
    var url = new URL("http://www.gutenberg.org/cache/epub/feeds/today.rss");
    var items = readRssFeed(url);

    var str = "<ul>";

    // Nashorn's string interpolation and heredoc
    // support is very handy in generating text content
    // that is filled with elements from runtime objects.
    // We insert title and link in <li> elements here.
    for each (i in items) {
        str += <<EOF
<li>
    <a href="${i.link}">${i.title}</a>
</li>
EOF
    }
    str += "</ul>";
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
