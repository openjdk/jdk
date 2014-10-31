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

/*
 * A micro-benchmark for getters and setters with primitive values,
 * alternating between ints and doubles. Introduction of primitive
 * and optimistic user accessors in JDK-8062401 make this faster by
 * 10x or more by allowing inlining and other optimizations to take place.
 */

var x = {
    get m() {
        return this._m;
    },
    set m(v) {
        this._m = v;
    },
    get n() {
        return this._n;
    },
    set n(v) {
        this._n = v;
    }
};


function bench(v1, v2, result) {
    var start = Date.now();
    x.n = v1;
    for (var i = 0; i < 1e8; i++) {
        x.m = v2;
        if (x.m + x.n !== result) {
            throw "wrong result";
        }
    }
    print("done in", Date.now() - start, "millis");
}

for (var i = 0; i < 10; i++) {
    bench(i, 4, 4 + i);
}

for (var i = 0; i < 10; i++) {
    bench(i, 4.5, 4.5 + i);
}

for (var i = 0; i < 10; i++) {
    bench(i, 5, 5 + i);
}

for (var i = 0; i < 10; i++) {
    bench(i, 5.5, 5.5 + i);
}
