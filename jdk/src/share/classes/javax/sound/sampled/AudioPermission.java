/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled;

import java.security.BasicPermission;


/**
 * The <code>AudioPermission</code> class represents access rights to the audio
 * system resources.  An <code>AudioPermission</code> contains a target name
 * but no actions list; you either have the named permission or you don't.
 * <p>
 * The target name is the name of the audio permission (see the table below).
 * The names follow the hierarchical property-naming convention. Also, an asterisk
 * can be used to represent all the audio permissions.
 * <p>
 * The following table lists the possible <code>AudioPermission</code> target names.
 * For each name, the table provides a description of exactly what that permission
 * allows, as well as a discussion of the risks of granting code the permission.
 * <p>
 *
 * <table border=1 cellpadding=5 summary="permission target name, what the permission allows, and associated risks">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 *
 * <tr>
 * <td>play</td>
 * <td>Audio playback through the audio device or devices on the system.
 * Allows the application to obtain and manipulate lines and mixers for
 * audio playback (rendering).</td>
 * <td>In some cases use of this permission may affect other
 * applications because the audio from one line may be mixed with other audio
 * being played on the system, or because manipulation of a mixer affects the
 * audio for all lines using that mixer.</td>
 *</tr>
 *
 * <tr>
 * <td>record</td>
 * <td>Audio recording through the audio device or devices on the system.
 * Allows the application to obtain and manipulate lines and mixers for
 * audio recording (capture).</td>
 * <td>In some cases use of this permission may affect other
 * applications because manipulation of a mixer affects the audio for all lines
 * using that mixer.
 * This permission can enable an applet or application to eavesdrop on a user.</td>
 *</tr>
 *</table>
 *<p>
 *
 * @author Kara Kytle
 * @since 1.3
 */
/*
 * (OLD PERMISSIONS TAKEN OUT FOR 1.2 BETA)
 *
 * <tr>
 * <td>playback device access</td>
 * <td>Direct access to the audio playback device(s), including configuration of the
 * playback format, volume, and balance, explicit opening and closing of the device,
 * etc.</td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>playback device override</td>
 * <td>Manipulation of the audio playback device(s) in a way that directly conflicts
 * with use by other applications.  This includes closing the device while it is in
 * use by another application, changing the device format while another application
 * is using it, etc. </td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>record device access</td>
 * <td>Direct access to the audio recording device(s), including configuration of the
 * the record format, volume, and balance, explicit opening and closing of the device,
 * etc.</td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>record device override</td>
 * <td>Manipulation of the audio recording device(s) in a way that directly conflicts
 * with use by other applications.  This includes closing the device while it is in
 * use by another application, changing the device format while another application
 * is using it, etc. </td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * </table>
 *<p>
 *
 * @author Kara Kytle
 * @since 1.3
 */

/*
 * The <code>AudioPermission</code> class represents access rights to the audio
 * system resources.  An <code>AudioPermission</code> contains a target name
 * but no actions list; you either have the named permission or you don't.
 * <p>
 * The target name is the name of the audio permission (see the table below).
 * The names follow the hierarchical property-naming convention. Also, an asterisk
 * can be used to represent all the audio permissions.
 * <p>
 * The following table lists all the possible AudioPermission target names.
 * For each name, the table provides a description of exactly what that permission
 * allows, as well as a discussion of the risks of granting code the permission.
 * <p>
 *
 * <table border=1 cellpadding=5>
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 *
 * <tr>
 * <td>play</td>
 * <td>Audio playback through the audio device or devices on the system.</td>
 * <td>Allows the application to use a system device.  Can affect other applications,
 * because the result will be mixed with other audio being played on the system.</td>
 *</tr>
 *
 * <tr>
 * <td>record</td>
 * <td>Recording audio from the audio device or devices on the system,
 * commonly through a microphone.</td>
 * <td>Can enable an applet or application to eavesdrop on a user.</td>
 * </tr>
 *
 * <tr>
 * <td>playback device access</td>
 * <td>Direct access to the audio playback device(s), including configuration of the
 * playback format, volume, and balance, explicit opening and closing of the device,
 * etc.</td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>playback device override</td>
 * <td>Manipulation of the audio playback device(s) in a way that directly conflicts
 * with use by other applications.  This includes closing the device while it is in
 * use by another application, changing the device format while another application
 * is using it, etc. </td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>record device access</td>
 * <td>Direct access to the audio recording device(s), including configuration of the
 * the record format, volume, and balance, explicit opening and closing of the device,
 * etc.</td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * <tr>
 * <td>record device override</td>
 * <td>Manipulation of the audio recording device(s) in a way that directly conflicts
 * with use by other applications.  This includes closing the device while it is in
 * use by another application, changing the device format while another application
 * is using it, etc. </td>
 * <td>Changes the properties of a shared system device and therefore
 * can affect other applications.</td>
 * </tr>
 *
 * </table>
 *<p>
 *
 * @author Kara Kytle
 */

public class AudioPermission extends BasicPermission {

    /**
     * Creates a new <code>AudioPermission</code> object that has the specified
     * symbolic name, such as "play" or "record". An asterisk can be used to indicate
     * all audio permissions.
     * @param name the name of the new <code>AudioPermission</code>
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */
    public AudioPermission(String name) {

        super(name);
    }

    /**
     * Creates a new <code>AudioPermission</code> object that has the specified
     * symbolic name, such as "play" or "record".  The <code>actions</code>
     * parameter is currently unused and should be <code>null</code>.
     * @param name the name of the new <code>AudioPermission</code>
     * @param actions (unused; should be <code>null</code>)
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */
    public AudioPermission(String name, String actions) {

        super(name, actions);
    }
}
