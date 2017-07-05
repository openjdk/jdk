/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.regex.*;

/**
 * An encapsulation of the request received.
 * <P>
 * The static method parse() is responsible for creating this
 * object.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class Request {

    /**
     * A helper class for parsing HTTP command actions.
     */
    static class Action {

        private String name;
        private Action(String name) { this.name = name; }
        public String toString() { return name; }

        static Action GET = new Action("GET");
        static Action PUT = new Action("PUT");
        static Action POST = new Action("POST");
        static Action HEAD = new Action("HEAD");

        static Action parse(String s) {
            if (s.equals("GET"))
                return GET;
            if (s.equals("PUT"))
                return PUT;
            if (s.equals("POST"))
                return POST;
            if (s.equals("HEAD"))
                return HEAD;
            throw new IllegalArgumentException(s);
        }
    }

    private Action action;
    private String version;
    private URI uri;

    Action action() { return action; }
    String version() { return version; }
    URI uri() { return uri; }

    private Request(Action a, String v, URI u) {
        action = a;
        version = v;
        uri = u;
    }

    public String toString() {
        return (action + " " + version + " " + uri);
    }

    static boolean isComplete(ByteBuffer bb) {
        int p = bb.position() - 4;
        if (p < 0)
            return false;
        return (((bb.get(p + 0) == '\r') &&
                 (bb.get(p + 1) == '\n') &&
                 (bb.get(p + 2) == '\r') &&
                 (bb.get(p + 3) == '\n')));
    }

    private static Charset ascii = Charset.forName("US-ASCII");

    /*
     * The expected message format is first compiled into a pattern,
     * and is then compared against the inbound character buffer to
     * determine if there is a match.  This convienently tokenizes
     * our request into usable pieces.
     *
     * This uses Matcher "expression capture groups" to tokenize
     * requests like:
     *
     *     GET /dir/file HTTP/1.1
     *     Host: hostname
     *
     * into:
     *
     *     group[1] = "GET"
     *     group[2] = "/dir/file"
     *     group[3] = "1.1"
     *     group[4] = "hostname"
     *
     * The text in between the parens are used to captured the regexp text.
     */
    private static Pattern requestPattern
        = Pattern.compile("\\A([A-Z]+) +([^ ]+) +HTTP/([0-9\\.]+)$"
                          + ".*^Host: ([^ ]+)$.*\r\n\r\n\\z",
                          Pattern.MULTILINE | Pattern.DOTALL);

    static Request parse(ByteBuffer bb) throws MalformedRequestException {

        CharBuffer cb = ascii.decode(bb);
        Matcher m = requestPattern.matcher(cb);
        if (!m.matches())
            throw new MalformedRequestException();
        Action a;
        try {
            a = Action.parse(m.group(1));
        } catch (IllegalArgumentException x) {
            throw new MalformedRequestException();
        }
        URI u;
        try {
            u = new URI("http://"
                        + m.group(4)
                        + m.group(2));
        } catch (URISyntaxException x) {
            throw new MalformedRequestException();
        }
        return new Request(a, m.group(3), u);
    }
}
