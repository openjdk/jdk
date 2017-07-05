/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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


package sun.security.ssl;

import java.security.SecureRandom;

/**
 * Encapsulates an SSL session ID.  SSL Session IDs are not reused by
 * servers during the lifetime of any sessions it created.  Sessions may
 * be used by many connections, either concurrently (for example, two
 * connections to a web server at the same time) or sequentially (over as
 * long a time period as is allowed by a given server).
 *
 * @author Satish Dharmaraj
 * @author David Brownell
 */
final
class SessionId
{
    private byte sessionId [];          // max 32 bytes

    /** Constructs a new session ID ... perhaps for a rejoinable session */
    SessionId (boolean isRejoinable, SecureRandom generator)
    {
        if (isRejoinable)
            // this will be unique, it's a timestamp plus much randomness
            sessionId = new RandomCookie (generator).random_bytes;
        else
            sessionId = new byte [0];
    }

    /** Constructs a session ID from a byte array (max size 32 bytes) */
    SessionId (byte sessionId [])
        { this.sessionId = sessionId; }

    /** Returns the length of the ID, in bytes */
    int length ()
        { return sessionId.length; }

    /** Returns the bytes in the ID.  May be an empty array.  */
    byte [] getId ()
    {
        return sessionId.clone ();
    }

    /** Returns the ID as a string */
    @Override
    public String toString ()
    {
        int             len = sessionId.length;
        StringBuilder    sb = new StringBuilder (10 + 2 * len);

        sb.append("{");
        for (int i = 0; i < len; i++) {
            sb.append(0x0ff & sessionId[i]);
            if (i != (len - 1))
                sb.append (", ");
        }
        sb.append("}");
        return sb.toString ();
    }


    /** Returns a value which is the same for session IDs which are equal */
    @Override
    public int hashCode ()
    {
        int     retval = 0;

        for (int i = 0; i < sessionId.length; i++)
            retval += sessionId [i];
        return retval;
    }

    /** Returns true if the parameter is the same session ID */
    @Override
    public boolean equals (Object obj)
    {
        if (!(obj instanceof SessionId))
            return false;

        SessionId s = (SessionId) obj;
        byte b [] = s.getId ();

        if (b.length != sessionId.length)
            return false;
        for (int i = 0; i < sessionId.length; i++) {
            if (b [i] != sessionId [i])
                return false;
        }
        return true;
    }
}
