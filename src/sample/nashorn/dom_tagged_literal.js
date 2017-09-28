/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This sample demonstrates the use of es6 tagged template literal to
 * create a java object. A XML DOM Document object is created from
 * String using es6 tagged template literal.
 *
 * Usage:
 *    jjs --language=es6 dom_tagged_literal.js
 */

// Java types used
const BAIS = Java.type("java.io.ByteArrayInputStream")
const DocBuilderFac = Java.type("javax.xml.parsers.DocumentBuilderFactory")
const DOMSource = Java.type("javax.xml.transform.dom.DOMSource")
const StreamResult = Java.type("javax.xml.transform.stream.StreamResult")
const StringWriter = Java.type("java.io.StringWriter")
const TransformerFactory = Java.type("javax.xml.transform.TransformerFactory")

function DOM(str) {
    var docBuilder = DocBuilderFac.newInstance().newDocumentBuilder()
    docBuilder.validating = false
    return docBuilder["parse(java.io.InputStream)"](new BAIS(String(str).bytes))
}

// es6 tagged template literal to create DOM from
// multi-line XML string

const dom = DOM`
<foo>
  <bar title="hello">world</bar>
</foo>`

// access DOM elements
const foo = dom.documentElement
print(foo.tagName)
const bar = foo.getElementsByTagName("bar").item(0)
print(bar.tagName)
print(bar.getAttribute("title"))

// modify DOM
foo.setAttribute("name", "nashorn")
foo.appendChild(dom.createElement("test"))

// serialize DOM to XML string
function domToXML(d) {
    const transformer = TransformerFactory.newInstance().newTransformer()
    const res = new StreamResult(new StringWriter())
    transformer.transform(new DOMSource(d), res)
    return res.writer.toString()
}

// serialize DOM to a String & print
print(domToXML(dom))
