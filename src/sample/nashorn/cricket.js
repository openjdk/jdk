#// Usage: jjs -scripting cricket.js

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

// Example that demonstrates reading XML Rss feed.
// XML DOM Document element is wrapped by script
// "proxy" (JSAdapter constructor)

// Java classes used
var DocBuilderFac = Java.type("javax.xml.parsers.DocumentBuilderFactory");
var Node = Java.type("org.w3c.dom.Node");

// constants from Node class
var ELEMENT_NODE = Node.ELEMENT_NODE;
var TEXT_NODE = Node.TEXT_NODE;

// parse XML from uri and return Document
function parseXML(uri) {
    var docBuilder = DocBuilderFac.newInstance().newDocumentBuilder();
    return docBuilder["parse(java.lang.String)"](uri);
}

// get child Elements of given name of the parent element given
function getChildElements(elem, name) {
    var nodeList = elem.childNodes;
    var childElems = [];
    var len = nodeList.length;
    for (var i = 0; i < len; i++) {
        var node = nodeList.item(i);
        if (node.nodeType == ELEMENT_NODE &&
            node.tagName == name) {
            childElems.push(wrapElement(node));
        }
    }

    return childElems;
}

// get concatenated child text content of an Element
function getElemText(elem) {
    var nodeList = elem.childNodes;
    var len = nodeList.length;
    var text = '';
    for (var i = 0; i < len; i++) {
        var node = nodeList.item(i);
        if (node.nodeType == TEXT_NODE) {
            text += node.nodeValue;
        }
    }

    return text;
}

// Wrap DOM Element object as a convenient script object
// using JSAdapter. JSAdapter is like java.lang.reflect.Proxy
// in that it allows property access, method calls be trapped
// by 'magic' methods like __get__, __call__.
function wrapElement(elem) {
    if (! elem) {
        return elem;
    }
    return new JSAdapter() {
        // getter to expose child elements and attributes by name
        __get__: function(name) {
            if (typeof name == 'string') {
                if (name.startsWith('@')) {
                    var attr = elem.getAttributeNode(name.substring(1));
                    return !attr? undefined : attr.value;
                }

                var arr = getChildElements(elem, name);
                if (arr.length == 1) {
                    // single child element, expose as single element
                    return arr[0];
                } else {
                    // multiple children of given name, expose as array
                    return arr;
                }
            }
            return undefined;
        },

        __call__: function(name) {
            // toString override to get text content of this Element
            if (name == 'toString' || name == 'valueOf') {
                return getElemText(elem);
            }
            return undefined;
        }
    }
}

function printCricketScore() {
    var doc = parseXML("http://static.cricinfo.com/rss/livescores.xml");
    // wrap document root Element as script convenient object
    var rss = wrapElement(doc.documentElement);
    print("rss file version " + rss['@version']);

    print(rss.channel.title);
    print(rss.channel.description);
    print(rss.channel.pubDate);

    print("=====================");

    var items = rss.channel.item;
    for each (var i in items) {
        print(i.description);
    }
}

printCricketScore();
