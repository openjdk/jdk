/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


import  java.security.*;

/*
 * Copyright 2003 Wily Technology, Inc.
 */

public class BufferClassLoader extends SecureClassLoader
{
    private final NamedBuffer[] fBuffers;

    public
    BufferClassLoader(  ClassLoader     parent,
                        NamedBuffer[]   buffers)
        {
        super(parent);
        fBuffers = buffers;     // maybe should copy
        }


    protected Class
    findClass(String name)
        throws ClassNotFoundException
        {
        for ( int x = 0; x < fBuffers.length; x++ )
            {
            if ( fBuffers[x].getName().equals(name) )
                {
                byte[] buffer = fBuffers[x].getBuffer();
                return defineClass( name,
                                    buffer,
                                    0,
                                    buffer.length,
                                    (CodeSource) null);
                }
            }

        throw new ClassNotFoundException(name);
        }


}
