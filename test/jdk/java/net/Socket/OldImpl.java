/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;


/**
 * @test
 * @bug 5089488
 * @summary  java.net.Socket checks for old-style impls
 */

public class OldImpl {

    /**
     * A no-op SocketImpl descendant.
     */
    static class FunkySocketImpl extends SocketImpl {
        protected void accept(SocketImpl impl) throws IOException {
        }

        protected int available(){
            return 0;
        }

        protected void bind(InetAddress host, int port){
        }

        protected void close(){
        }

        protected void connect(InetAddress address, int port){
        }

        protected void connect(String host, int port){
        }

        protected void connect(SocketAddress a,int b){
        }

        protected void create(boolean stream){
        }

        protected InputStream getInputStream(){
            return null;
        }

        protected OutputStream getOutputStream(){
            return null;
        }

        protected void listen(int backlog){
        }

        public Object getOption(int optID){
            return null;
        }

        public void setOption(int optID, Object value){
        }

        protected void sendUrgentData(int i){
        }
    }

    static class FunkyWunkySocketImpl extends FunkySocketImpl {}

    /**
     * A no-op Socket descendant.
     */
    static class FunkySocket extends Socket {
        public FunkySocket(SocketImpl impl) throws IOException {
            super(impl);
        }
    }

    public static void main(String args[]) throws Exception {
        FunkyWunkySocketImpl socketImpl = new FunkyWunkySocketImpl();
        FunkySocket socko = new FunkySocket(socketImpl);
        if (socko.isBound()) {
            throw new RuntimeException ("socket is not really bound");
        }
    }
}
