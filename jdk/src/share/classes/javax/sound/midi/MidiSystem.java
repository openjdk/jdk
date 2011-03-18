/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.midi;

import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.net.URL;

import javax.sound.midi.spi.MidiFileWriter;
import javax.sound.midi.spi.MidiFileReader;
import javax.sound.midi.spi.SoundbankReader;
import javax.sound.midi.spi.MidiDeviceProvider;

import com.sun.media.sound.JDK13Services;
import com.sun.media.sound.ReferenceCountingDevice;
import com.sun.media.sound.AutoConnectSequencer;
import com.sun.media.sound.MidiDeviceReceiverEnvelope;
import com.sun.media.sound.MidiDeviceTransmitterEnvelope;


/**
 * The <code>MidiSystem</code> class provides access to the installed MIDI
 * system resources, including devices such as synthesizers, sequencers, and
 * MIDI input and output ports.  A typical simple MIDI application might
 * begin by invoking one or more <code>MidiSystem</code> methods to learn
 * what devices are installed and to obtain the ones needed in that
 * application.
 * <p>
 * The class also has methods for reading files, streams, and  URLs that
 * contain standard MIDI file data or soundbanks.  You can query the
 * <code>MidiSystem</code> for the format of a specified MIDI file.
 * <p>
 * You cannot instantiate a <code>MidiSystem</code>; all the methods are
 * static.
 *
 * <p>Properties can be used to specify default MIDI devices.
 * Both system properties and a properties file are considered.
 * The properties file is &quot;lib/sound.properties&quot; in the JRE
 * directory. If a property exists both as a system property and in the
 * properties file, the system property takes precedence. If none is
 * specified, a suitable default is chosen among the available devices.
 * The syntax of the properties file is specified in
 * {@link java.util.Properties#load(InputStream) Properties.load}. The
 * following table lists the available property keys and which methods
 * consider them:
 *
 * <table border=0>
 *  <tr>
 *   <th>Property Key</th>
 *   <th>Interface</th>
 *   <th>Affected Method</th>
 *  </tr>
 *  <tr>
 *   <td><code>javax.sound.midi.Receiver</code></td>
 *   <td>{@link Receiver}</td>
 *   <td>{@link #getReceiver}</td>
 *  </tr>
 *  <tr>
 *   <td><code>javax.sound.midi.Sequencer</code></td>
 *   <td>{@link Sequencer}</td>
 *   <td>{@link #getSequencer}</td>
 *  </tr>
 *  <tr>
 *   <td><code>javax.sound.midi.Synthesizer</code></td>
 *   <td>{@link Synthesizer}</td>
 *   <td>{@link #getSynthesizer}</td>
 *  </tr>
 *  <tr>
 *   <td><code>javax.sound.midi.Transmitter</code></td>
 *   <td>{@link Transmitter}</td>
 *   <td>{@link #getTransmitter}</td>
 *  </tr>
 * </table>
 *
 * The property value consists of the provider class name
 * and the device name, separated by the hash mark (&quot;#&quot;).
 * The provider class name is the fully-qualified
 * name of a concrete {@link javax.sound.midi.spi.MidiDeviceProvider
 * MIDI device provider} class. The device name is matched against
 * the <code>String</code> returned by the <code>getName</code>
 * method of <code>MidiDevice.Info</code>.
 * Either the class name, or the device name may be omitted.
 * If only the class name is specified, the trailing hash mark
 * is optional.
 *
 * <p>If the provider class is specified, and it can be
 * successully retrieved from the installed providers,
 * the list of
 * <code>MidiDevice.Info</code> objects is retrieved
 * from the provider. Otherwise, or when these devices
 * do not provide a subsequent match, the list is retrieved
 * from {@link #getMidiDeviceInfo} to contain
 * all available <code>MidiDevice.Info</code> objects.
 *
 * <p>If a device name is specified, the resulting list of
 * <code>MidiDevice.Info</code> objects is searched:
 * the first one with a matching name, and whose
 * <code>MidiDevice</code> implements the
 * respective interface, will be returned.
 * If no matching <code>MidiDevice.Info</code> object
 * is found, or the device name is not specified,
 * the first suitable device from the resulting
 * list will be returned. For Sequencer and Synthesizer,
 * a device is suitable if it implements the respective
 * interface; whereas for Receiver and Transmitter, a device is
 * suitable if it
 * implements neither Sequencer nor Synthesizer and provides
 * at least one Receiver or Transmitter, respectively.
 *
 * For example, the property <code>javax.sound.midi.Receiver</code>
 * with a value
 * <code>&quot;com.sun.media.sound.MidiProvider#SunMIDI1&quot;</code>
 * will have the following consequences when
 * <code>getReceiver</code> is called:
 * if the class <code>com.sun.media.sound.MidiProvider</code> exists
 * in the list of installed MIDI device providers,
 * the first <code>Receiver</code> device with name
 * <code>&quot;SunMIDI1&quot;</code> will be returned. If it cannot
 * be found, the first <code>Receiver</code> from that provider
 * will be returned, regardless of name.
 * If there is none, the first <code>Receiver</code> with name
 * <code>&quot;SunMIDI1&quot;</code> in the list of all devices
 * (as returned by <code>getMidiDeviceInfo</code>) will be returned,
 * or, if not found, the first <code>Receiver</code> that can
 * be found in the list of all devices is returned.
 * If that fails, too, a <code>MidiUnavailableException</code>
 * is thrown.
 *
 * @author Kara Kytle
 * @author Florian Bomers
 * @author Matthias Pfisterer
 */
public class MidiSystem {

    /**
     * Private no-args constructor for ensuring against instantiation.
     */
    private MidiSystem() {
    }


    /**
     * Obtains an array of information objects representing
     * the set of all MIDI devices available on the system.
     * A returned information object can then be used to obtain the
     * corresponding device object, by invoking
     * {@link #getMidiDevice(MidiDevice.Info) getMidiDevice}.
     *
     * @return an array of <code>MidiDevice.Info</code> objects, one
     * for each installed MIDI device.  If no such devices are installed,
     * an array of length 0 is returned.
     */
    public static MidiDevice.Info[] getMidiDeviceInfo() {
        List allInfos = new ArrayList();
        List providers = getMidiDeviceProviders();

        for(int i = 0; i < providers.size(); i++) {
            MidiDeviceProvider provider = (MidiDeviceProvider) providers.get(i);
            MidiDevice.Info[] tmpinfo = provider.getDeviceInfo();
            for (int j = 0; j < tmpinfo.length; j++) {
                allInfos.add( tmpinfo[j] );
            }
        }
        MidiDevice.Info[] infosArray = (MidiDevice.Info[]) allInfos.toArray(new MidiDevice.Info[0]);
        return infosArray;
    }


    /**
     * Obtains the requested MIDI device.
     *
     * @param info a device information object representing the desired device.
     * @return the requested device
     * @throws MidiUnavailableException if the requested device is not available
     * due to resource restrictions
     * @throws IllegalArgumentException if the info object does not represent
     * a MIDI device installed on the system
     * @see #getMidiDeviceInfo
     */
    public static MidiDevice getMidiDevice(MidiDevice.Info info) throws MidiUnavailableException {
        List providers = getMidiDeviceProviders();

        for(int i = 0; i < providers.size(); i++) {
            MidiDeviceProvider provider = (MidiDeviceProvider) providers.get(i);
            if (provider.isDeviceSupported(info)) {
                MidiDevice device = provider.getDevice(info);
                return device;
            }
        }
        throw new IllegalArgumentException("Requested device not installed: " + info);
    }


    /**
     * Obtains a MIDI receiver from an external MIDI port
     * or other default device.
     * The returned receiver always implements
     * the {@code MidiDeviceReceiver} interface.
     *
     * <p>If the system property
     * <code>javax.sound.midi.Receiver</code>
     * is defined or it is defined in the file &quot;sound.properties&quot;,
     * it is used to identify the device that provides the default receiver.
     * For details, refer to the {@link MidiSystem class description}.
     *
     * If a suitable MIDI port is not available, the Receiver is
     * retrieved from an installed synthesizer.
     *
     * <p>If this method returns successfully, the {@link
     * javax.sound.midi.MidiDevice MidiDevice} the
     * <code>Receiver</code> belongs to is opened implicitly, if it is
     * not already open. It is possible to close an implicitly opened
     * device by calling {@link javax.sound.midi.Receiver#close close}
     * on the returned <code>Receiver</code>. All open <code>Receiver</code>
     * instances have to be closed in order to release system resources
     * hold by the <code>MidiDevice</code>. For a
     * detailed description of open/close behaviour see the class
     * description of {@link javax.sound.midi.MidiDevice MidiDevice}.
     *
     *
     * @return the default MIDI receiver
     * @throws MidiUnavailableException if the default receiver is not
     *         available due to resource restrictions,
     *         or no device providing receivers is installed in the system
     */
    public static Receiver getReceiver() throws MidiUnavailableException {
        // may throw MidiUnavailableException
        MidiDevice device = getDefaultDeviceWrapper(Receiver.class);
        Receiver receiver;
        if (device instanceof ReferenceCountingDevice) {
            receiver = ((ReferenceCountingDevice) device).getReceiverReferenceCounting();
        } else {
            receiver = device.getReceiver();
        }
        if (!(receiver instanceof MidiDeviceReceiver)) {
            receiver = new MidiDeviceReceiverEnvelope(device, receiver);
        }
        return receiver;
    }


    /**
     * Obtains a MIDI transmitter from an external MIDI port
     * or other default source.
     * The returned transmitter always implements
     * the {@code MidiDeviceTransmitter} interface.
     *
     * <p>If the system property
     * <code>javax.sound.midi.Transmitter</code>
     * is defined or it is defined in the file &quot;sound.properties&quot;,
     * it is used to identify the device that provides the default transmitter.
     * For details, refer to the {@link MidiSystem class description}.
     *
     * If this method returns successfully, the {@link
     * javax.sound.midi.MidiDevice MidiDevice} the
     * <code>Transmitter</code> belongs to is opened implicitly, if it
     * is not already open. It is possible to close an implicitly
     * opened device by calling {@link
     * javax.sound.midi.Transmitter#close close} on the returned
     * <code>Transmitter</code>. All open <code>Transmitter</code>
     * instances have to be closed in order to release system resources
     * hold by the <code>MidiDevice</code>. For a detailed description
     * of open/close behaviour see the class description of {@link
     * javax.sound.midi.MidiDevice MidiDevice}.
     *
     * @return the default MIDI transmitter
     * @throws MidiUnavailableException if the default transmitter is not
     *         available due to resource restrictions,
     *         or no device providing transmitters is installed in the system
     */
    public static Transmitter getTransmitter() throws MidiUnavailableException {
        // may throw MidiUnavailableException
        MidiDevice device = getDefaultDeviceWrapper(Transmitter.class);
        Transmitter transmitter;
        if (device instanceof ReferenceCountingDevice) {
            transmitter = ((ReferenceCountingDevice) device).getTransmitterReferenceCounting();
        } else {
            transmitter = device.getTransmitter();
        }
        if (!(transmitter instanceof MidiDeviceTransmitter)) {
            transmitter = new MidiDeviceTransmitterEnvelope(device, transmitter);
        }
        return transmitter;
    }


    /**
     * Obtains the default synthesizer.
     *
     * <p>If the system property
     * <code>javax.sound.midi.Synthesizer</code>
     * is defined or it is defined in the file &quot;sound.properties&quot;,
     * it is used to identify the default synthesizer.
     * For details, refer to the {@link MidiSystem class description}.
     *
     * @return the default synthesizer
     * @throws MidiUnavailableException if the synthesizer is not
     *         available due to resource restrictions,
     *         or no synthesizer is installed in the system
     */
    public static Synthesizer getSynthesizer() throws MidiUnavailableException {
        // may throw MidiUnavailableException
        return (Synthesizer) getDefaultDeviceWrapper(Synthesizer.class);
    }


    /**
     * Obtains the default <code>Sequencer</code>, connected to
     * a default device.
     * The returned <code>Sequencer</code> instance is
     * connected to the default <code>Synthesizer</code>,
     * as returned by {@link #getSynthesizer}.
     * If there is no <code>Synthesizer</code>
     * available, or the default <code>Synthesizer</code>
     * cannot be opened, the <code>sequencer</code> is connected
     * to the default <code>Receiver</code>, as returned
     * by {@link #getReceiver}.
     * The connection is made by retrieving a <code>Transmitter</code>
     * instance from the <code>Sequencer</code> and setting its
     * <code>Receiver</code>.
     * Closing and re-opening the sequencer will restore the
     * connection to the default device.
     *
     * <p>This method is equivalent to calling
     * <code>getSequencer(true)</code>.
     *
     * <p>If the system property
     * <code>javax.sound.midi.Sequencer</code>
     * is defined or it is defined in the file &quot;sound.properties&quot;,
     * it is used to identify the default sequencer.
     * For details, refer to the {@link MidiSystem class description}.
     *
     * @return the default sequencer, connected to a default Receiver
     * @throws MidiUnavailableException if the sequencer is not
     *         available due to resource restrictions,
     *         or there is no <code>Receiver</code> available by any
     *         installed <code>MidiDevice</code>,
     *         or no sequencer is installed in the system.
     * @see #getSequencer(boolean)
     * @see #getSynthesizer
     * @see #getReceiver
     */
    public static Sequencer getSequencer() throws MidiUnavailableException {
        return getSequencer(true);
    }



    /**
     * Obtains the default <code>Sequencer</code>, optionally
     * connected to a default device.
     *
     * <p>If <code>connected</code> is true, the returned
     * <code>Sequencer</code> instance is
     * connected to the default <code>Synthesizer</code>,
     * as returned by {@link #getSynthesizer}.
     * If there is no <code>Synthesizer</code>
     * available, or the default <code>Synthesizer</code>
     * cannot be opened, the <code>sequencer</code> is connected
     * to the default <code>Receiver</code>, as returned
     * by {@link #getReceiver}.
     * The connection is made by retrieving a <code>Transmitter</code>
     * instance from the <code>Sequencer</code> and setting its
     * <code>Receiver</code>.
     * Closing and re-opening the sequencer will restore the
     * connection to the default device.
     *
     * <p>If <code>connected</code> is false, the returned
     * <code>Sequencer</code> instance is not connected, it
     * has no open <code>Transmitters</code>. In order to
     * play the sequencer on a MIDI device, or a <code>Synthesizer</code>,
     * it is necessary to get a <code>Transmitter</code> and set its
     * <code>Receiver</code>.
     *
     * <p>If the system property
     * <code>javax.sound.midi.Sequencer</code>
     * is defined or it is defined in the file "sound.properties",
     * it is used to identify the default sequencer.
     * For details, refer to the {@link MidiSystem class description}.
     *
     * @return the default sequencer
     * @throws MidiUnavailableException if the sequencer is not
     *         available due to resource restrictions,
     *         or no sequencer is installed in the system,
     *         or if <code>connected</code> is true, and there is
     *         no <code>Receiver</code> available by any installed
     *         <code>MidiDevice</code>
     * @see #getSynthesizer
     * @see #getReceiver
     * @since 1.5
     */
    public static Sequencer getSequencer(boolean connected)
        throws MidiUnavailableException {
        Sequencer seq = (Sequencer) getDefaultDeviceWrapper(Sequencer.class);

        if (connected) {
            // IMPORTANT: this code needs to be synch'ed with
            //            all AutoConnectSequencer instances,
            //            (e.g. RealTimeSequencer) because the
            //            same algorithm for synth retrieval
            //            needs to be used!

            Receiver rec = null;
            MidiUnavailableException mue = null;

            // first try to connect to the default synthesizer
            try {
                Synthesizer synth = getSynthesizer();
                if (synth instanceof ReferenceCountingDevice) {
                    rec = ((ReferenceCountingDevice) synth).getReceiverReferenceCounting();
                } else {
                    synth.open();
                    try {
                        rec = synth.getReceiver();
                    } finally {
                        // make sure that the synth is properly closed
                        if (rec == null) {
                            synth.close();
                        }
                    }
                }
            } catch (MidiUnavailableException e) {
                // something went wrong with synth
                if (e instanceof MidiUnavailableException) {
                    mue = (MidiUnavailableException) e;
                }
            }
            if (rec == null) {
                // then try to connect to the default Receiver
                try {
                    rec = MidiSystem.getReceiver();
                } catch (Exception e) {
                    // something went wrong. Nothing to do then!
                    if (e instanceof MidiUnavailableException) {
                        mue = (MidiUnavailableException) e;
                    }
                }
            }
            if (rec != null) {
                seq.getTransmitter().setReceiver(rec);
                if (seq instanceof AutoConnectSequencer) {
                    ((AutoConnectSequencer) seq).setAutoConnect(rec);
                }
            } else {
                if (mue != null) {
                    throw mue;
                }
                throw new MidiUnavailableException("no receiver available");
            }
        }
        return seq;
    }




    /**
     * Constructs a MIDI sound bank by reading it from the specified stream.
     * The stream must point to
     * a valid MIDI soundbank file.  In general, MIDI soundbank providers may
     * need to read some data from the stream before determining whether they
     * support it.  These parsers must
     * be able to mark the stream, read enough data to determine whether they
     * support the stream, and, if not, reset the stream's read pointer to
     * its original position.  If the input stream does not support this,
     * this method may fail with an IOException.
     * @param stream the source of the sound bank data.
     * @return the sound bank
     * @throws InvalidMidiDataException if the stream does not point to
     * valid MIDI soundbank data recognized by the system
     * @throws IOException if an I/O error occurred when loading the soundbank
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    public static Soundbank getSoundbank(InputStream stream)
        throws InvalidMidiDataException, IOException {

        SoundbankReader sp = null;
        Soundbank s = null;

        List providers = getSoundbankReaders();

        for(int i = 0; i < providers.size(); i++) {
            sp = (SoundbankReader)providers.get(i);
            s = sp.getSoundbank(stream);

            if( s!= null) {
                return s;
            }
        }
        throw new InvalidMidiDataException("cannot get soundbank from stream");

    }


    /**
     * Constructs a <code>Soundbank</code> by reading it from the specified URL.
     * The URL must point to a valid MIDI soundbank file.
     *
     * @param url the source of the sound bank data
     * @return the sound bank
     * @throws InvalidMidiDataException if the URL does not point to valid MIDI
     * soundbank data recognized by the system
     * @throws IOException if an I/O error occurred when loading the soundbank
     */
    public static Soundbank getSoundbank(URL url)
        throws InvalidMidiDataException, IOException {

        SoundbankReader sp = null;
        Soundbank s = null;

        List providers = getSoundbankReaders();

        for(int i = 0; i < providers.size(); i++) {
            sp = (SoundbankReader)providers.get(i);
            s = sp.getSoundbank(url);

            if( s!= null) {
                return s;
            }
        }
        throw new InvalidMidiDataException("cannot get soundbank from stream");

    }


    /**
     * Constructs a <code>Soundbank</code> by reading it from the specified
     * <code>File</code>.
     * The <code>File</code> must point to a valid MIDI soundbank file.
     *
     * @param file the source of the sound bank data
     * @return the sound bank
     * @throws InvalidMidiDataException if the <code>File</code> does not
     * point to valid MIDI soundbank data recognized by the system
     * @throws IOException if an I/O error occurred when loading the soundbank
     */
    public static Soundbank getSoundbank(File file)
        throws InvalidMidiDataException, IOException {

        SoundbankReader sp = null;
        Soundbank s = null;

        List providers = getSoundbankReaders();

        for(int i = 0; i < providers.size(); i++) {
            sp = (SoundbankReader)providers.get(i);
            s = sp.getSoundbank(file);

            if( s!= null) {
                return s;
            }
        }
        throw new InvalidMidiDataException("cannot get soundbank from stream");
    }



    /**
     * Obtains the MIDI file format of the data in the specified input stream.
     * The stream must point to valid MIDI file data for a file type recognized
     * by the system.
     * <p>
     * This method and/or the code it invokes may need to read some data from
     * the stream to determine whether its data format is supported.  The
     * implementation may therefore
     * need to mark the stream, read enough data to determine whether it is in
     * a supported format, and reset the stream's read pointer to its original
     * position.  If the input stream does not permit this set of operations,
     * this method may fail with an <code>IOException</code>.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while determining the file format.
     *
     * @param stream the input stream from which file format information
     * should be extracted
     * @return an <code>MidiFileFormat</code> object describing the MIDI file
     * format
     * @throws InvalidMidiDataException if the stream does not point to valid
     * MIDI file data recognized by the system
     * @throws IOException if an I/O exception occurs while accessing the
     * stream
     * @see #getMidiFileFormat(URL)
     * @see #getMidiFileFormat(File)
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    public static MidiFileFormat getMidiFileFormat(InputStream stream)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        MidiFileFormat format = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                format = reader.getMidiFileFormat( stream ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( format==null ) {
            throw new InvalidMidiDataException("input stream is not a supported file type");
        } else {
            return format;
        }
    }


    /**
     * Obtains the MIDI file format of the data in the specified URL.  The URL
     * must point to valid MIDI file data for a file type recognized
     * by the system.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while determining the file format.
     *
     * @param url the URL from which file format information should be
     * extracted
     * @return a <code>MidiFileFormat</code> object describing the MIDI file
     * format
     * @throws InvalidMidiDataException if the URL does not point to valid MIDI
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs while accessing the URL
     *
     * @see #getMidiFileFormat(InputStream)
     * @see #getMidiFileFormat(File)
     */
    public static MidiFileFormat getMidiFileFormat(URL url)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        MidiFileFormat format = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                format = reader.getMidiFileFormat( url ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( format==null ) {
            throw new InvalidMidiDataException("url is not a supported file type");
        } else {
            return format;
        }
    }


    /**
     * Obtains the MIDI file format of the specified <code>File</code>.  The
     * <code>File</code> must point to valid MIDI file data for a file type
     * recognized by the system.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while determining the file format.
     *
     * @param file the <code>File</code> from which file format information
     * should be extracted
     * @return a <code>MidiFileFormat</code> object describing the MIDI file
     * format
     * @throws InvalidMidiDataException if the <code>File</code> does not point
     *  to valid MIDI file data recognized by the system
     * @throws IOException if an I/O exception occurs while accessing the file
     *
     * @see #getMidiFileFormat(InputStream)
     * @see #getMidiFileFormat(URL)
     */
    public static MidiFileFormat getMidiFileFormat(File file)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        MidiFileFormat format = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                format = reader.getMidiFileFormat( file ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( format==null ) {
            throw new InvalidMidiDataException("file is not a supported file type");
        } else {
            return format;
        }
    }


    /**
     * Obtains a MIDI sequence from the specified input stream.  The stream must
     * point to valid MIDI file data for a file type recognized
     * by the system.
     * <p>
     * This method and/or the code it invokes may need to read some data
     * from the stream to determine whether
     * its data format is supported.  The implementation may therefore
     * need to mark the stream, read enough data to determine whether it is in
     * a supported format, and reset the stream's read pointer to its original
     * position.  If the input stream does not permit this set of operations,
     * this method may fail with an <code>IOException</code>.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while constructing the <code>Sequence</code>
     * object from the file data.
     *
     * @param stream the input stream from which the <code>Sequence</code>
     * should be constructed
     * @return a <code>Sequence</code> object based on the MIDI file data
     * contained in the input stream
     * @throws InvalidMidiDataException if the stream does not point to
     * valid MIDI file data recognized by the system
     * @throws IOException if an I/O exception occurs while accessing the
     * stream
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    public static Sequence getSequence(InputStream stream)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        Sequence sequence = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                sequence = reader.getSequence( stream ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( sequence==null ) {
            throw new InvalidMidiDataException("could not get sequence from input stream");
        } else {
            return sequence;
        }
    }


    /**
     * Obtains a MIDI sequence from the specified URL.  The URL must
     * point to valid MIDI file data for a file type recognized
     * by the system.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while constructing the <code>Sequence</code>
     * object from the file data.
     *
     * @param url the URL from which the <code>Sequence</code> should be
     * constructed
     * @return a <code>Sequence</code> object based on the MIDI file data
     * pointed to by the URL
     * @throws InvalidMidiDataException if the URL does not point to valid MIDI
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs while accessing the URL
     */
    public static Sequence getSequence(URL url)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        Sequence sequence = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                sequence = reader.getSequence( url ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( sequence==null ) {
            throw new InvalidMidiDataException("could not get sequence from URL");
        } else {
            return sequence;
        }
    }


    /**
     * Obtains a MIDI sequence from the specified <code>File</code>.
     * The <code>File</code> must point to valid MIDI file data
     * for a file type recognized by the system.
     * <p>
     * This operation can only succeed for files of a type which can be parsed
     * by an installed file reader.  It may fail with an InvalidMidiDataException
     * even for valid files if no compatible file reader is installed.  It
     * will also fail with an InvalidMidiDataException if a compatible file reader
     * is installed, but encounters errors while constructing the <code>Sequence</code>
     * object from the file data.
     *
     * @param file the <code>File</code> from which the <code>Sequence</code>
     * should be constructed
     * @return a <code>Sequence</code> object based on the MIDI file data
     * pointed to by the File
     * @throws InvalidMidiDataException if the File does not point to valid MIDI
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    public static Sequence getSequence(File file)
        throws InvalidMidiDataException, IOException {

        List providers = getMidiFileReaders();
        Sequence sequence = null;

        for(int i = 0; i < providers.size(); i++) {
            MidiFileReader reader = (MidiFileReader) providers.get(i);
            try {
                sequence = reader.getSequence( file ); // throws IOException
                break;
            } catch (InvalidMidiDataException e) {
                continue;
            }
        }

        if( sequence==null ) {
            throw new InvalidMidiDataException("could not get sequence from file");
        } else {
            return sequence;
        }
    }


    /**
     * Obtains the set of MIDI file types for which file writing support is
     * provided by the system.
     * @return array of unique file types.  If no file types are supported,
     * an array of length 0 is returned.
     */
    public static int[] getMidiFileTypes() {

        List providers = getMidiFileWriters();
        Set allTypes = new HashSet();

        // gather from all the providers

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            int[] types = writer.getMidiFileTypes();
            for (int j = 0; j < types.length; j++ ) {
                allTypes.add(new Integer(types[j]));
            }
        }
        int resultTypes[] = new int[allTypes.size()];
        int index = 0;
        Iterator iterator = allTypes.iterator();
        while (iterator.hasNext()) {
            Integer integer = (Integer) iterator.next();
            resultTypes[index++] = integer.intValue();
        }
        return resultTypes;
    }


    /**
     * Indicates whether file writing support for the specified MIDI file type
     * is provided by the system.
     * @param fileType the file type for which write capabilities are queried
     * @return <code>true</code> if the file type is supported,
     * otherwise <code>false</code>
     */
    public static boolean isFileTypeSupported(int fileType) {

        List providers = getMidiFileWriters();

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            if( writer.isFileTypeSupported(fileType)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Obtains the set of MIDI file types that the system can write from the
     * sequence specified.
     * @param sequence the sequence for which MIDI file type support
     * is queried
     * @return the set of unique supported file types.  If no file types are supported,
     * returns an array of length 0.
     */
    public static int[] getMidiFileTypes(Sequence sequence) {

        List providers = getMidiFileWriters();
        Set allTypes = new HashSet();

        // gather from all the providers

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            int[] types = writer.getMidiFileTypes(sequence);
            for (int j = 0; j < types.length; j++ ) {
                allTypes.add(new Integer(types[j]));
            }
        }
        int resultTypes[] = new int[allTypes.size()];
        int index = 0;
        Iterator iterator = allTypes.iterator();
        while (iterator.hasNext()) {
            Integer integer = (Integer) iterator.next();
            resultTypes[index++] = integer.intValue();
        }
        return resultTypes;
    }


    /**
     * Indicates whether a MIDI file of the file type specified can be written
     * from the sequence indicated.
     * @param fileType the file type for which write capabilities
     * are queried
     * @param sequence the sequence for which file writing support is queried
     * @return <code>true</code> if the file type is supported for this
     * sequence, otherwise <code>false</code>
     */
    public static boolean isFileTypeSupported(int fileType, Sequence sequence) {

        List providers = getMidiFileWriters();

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            if( writer.isFileTypeSupported(fileType,sequence)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Writes a stream of bytes representing a file of the MIDI file type
     * indicated to the output stream provided.
     * @param in sequence containing MIDI data to be written to the file
     * @param fileType the file type of the file to be written to the output stream
     * @param out stream to which the file data should be written
     * @return the number of bytes written to the output stream
     * @throws IOException if an I/O exception occurs
     * @throws IllegalArgumentException if the file format is not supported by
     * the system
     * @see #isFileTypeSupported(int, Sequence)
     * @see     #getMidiFileTypes(Sequence)
     */
    public static int write(Sequence in, int fileType, OutputStream out) throws IOException {

        List providers = getMidiFileWriters();
        //$$fb 2002-04-17: Fix for 4635287: Standard MidiFileWriter cannot write empty Sequences
        int bytesWritten = -2;

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            if( writer.isFileTypeSupported( fileType, in ) ) {

                bytesWritten = writer.write(in, fileType, out);
                break;
            }
        }
        if (bytesWritten == -2) {
            throw new IllegalArgumentException("MIDI file type is not supported");
        }
        return bytesWritten;
    }


    /**
     * Writes a stream of bytes representing a file of the MIDI file type
     * indicated to the external file provided.
     * @param in sequence containing MIDI data to be written to the file
     * @param type the file type of the file to be written to the output stream
     * @param out external file to which the file data should be written
     * @return the number of bytes written to the file
     * @throws IOException if an I/O exception occurs
     * @throws IllegalArgumentException if the file type is not supported by
     * the system
     * @see #isFileTypeSupported(int, Sequence)
     * @see     #getMidiFileTypes(Sequence)
     */
    public static int write(Sequence in, int type, File out) throws IOException {

        List providers = getMidiFileWriters();
        //$$fb 2002-04-17: Fix for 4635287: Standard MidiFileWriter cannot write empty Sequences
        int bytesWritten = -2;

        for (int i = 0; i < providers.size(); i++ ) {
            MidiFileWriter writer = (MidiFileWriter) providers.get(i);
            if( writer.isFileTypeSupported( type, in ) ) {

                bytesWritten = writer.write(in, type, out);
                break;
            }
        }
        if (bytesWritten == -2) {
            throw new IllegalArgumentException("MIDI file type is not supported");
        }
        return bytesWritten;
    }



    // HELPER METHODS

    private static List getMidiDeviceProviders() {
        return getProviders(MidiDeviceProvider.class);
    }


    private static List getSoundbankReaders() {
        return getProviders(SoundbankReader.class);
    }


    private static List getMidiFileWriters() {
        return getProviders(MidiFileWriter.class);
    }


    private static List getMidiFileReaders() {
        return getProviders(MidiFileReader.class);
    }


    /** Attempts to locate and return a default MidiDevice of the specified
     * type.
     *
     * This method wraps {@link #getDefaultDevice}. It catches the
     * <code>IllegalArgumentException</code> thrown by
     * <code>getDefaultDevice</code> and instead throws a
     * <code>MidiUnavailableException</code>, with the catched
     * exception chained.
     *
     * @param deviceClass The requested device type, one of Synthesizer.class,
     * Sequencer.class, Receiver.class or Transmitter.class.
     * @throws  MidiUnavalableException on failure.
     */
    private static MidiDevice getDefaultDeviceWrapper(Class deviceClass)
        throws MidiUnavailableException{
        try {
            return getDefaultDevice(deviceClass);
        } catch (IllegalArgumentException iae) {
            MidiUnavailableException mae = new MidiUnavailableException();
            mae.initCause(iae);
            throw mae;
        }
    }


    /** Attempts to locate and return a default MidiDevice of the specified
     * type.
     *
     * @param deviceClass The requested device type, one of Synthesizer.class,
     * Sequencer.class, Receiver.class or Transmitter.class.
     * @throws  IllegalArgumentException on failure.
     */
    private static MidiDevice getDefaultDevice(Class deviceClass) {
        List providers = getMidiDeviceProviders();
        String providerClassName = JDK13Services.getDefaultProviderClassName(deviceClass);
        String instanceName = JDK13Services.getDefaultInstanceName(deviceClass);
        MidiDevice device;

        if (providerClassName != null) {
            MidiDeviceProvider defaultProvider = getNamedProvider(providerClassName, providers);
            if (defaultProvider != null) {
                if (instanceName != null) {
                    device = getNamedDevice(instanceName, defaultProvider, deviceClass);
                    if (device != null) {
                        return device;
                    }
                }
                device = getFirstDevice(defaultProvider, deviceClass);
                if (device != null) {
                    return device;
                }
            }
        }

        /* Provider class not specified or cannot be found, or
           provider class specified, and no appropriate device available or
           provider class and instance specified and instance cannot be found or is not appropriate */
        if (instanceName != null) {
            device = getNamedDevice(instanceName, providers, deviceClass);
            if (device != null) {
                return device;
            }
        }

        /* No default are specified, or if something is specified, everything
           failed. */
        device = getFirstDevice(providers, deviceClass);
        if (device != null) {
            return device;
        }
        throw new IllegalArgumentException("Requested device not installed");
    }



    /** Return a MidiDeviceProcider of a given class from the list of
        MidiDeviceProviders.

        @param providerClassName The class name of the provider to be returned.
        @param provider The list of MidiDeviceProviders that is searched.
        @return A MidiDeviceProvider of the requested class, or null if none
        is found.
    */
    private static MidiDeviceProvider getNamedProvider(String providerClassName, List providers) {
        for(int i = 0; i < providers.size(); i++) {
            MidiDeviceProvider provider = (MidiDeviceProvider) providers.get(i);
            if (provider.getClass().getName().equals(providerClassName)) {
                return provider;
            }
        }
        return null;
    }


    /** Return a MidiDevice with a given name from a given MidiDeviceProvider.
        @param deviceName The name of the MidiDevice to be returned.
        @param provider The MidiDeviceProvider to check for MidiDevices.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.

        @return A MidiDevice matching the requirements, or null if none is found.
    */
    private static MidiDevice getNamedDevice(String deviceName,
                                             MidiDeviceProvider provider,
                                             Class deviceClass) {
        MidiDevice device;
        // try to get MIDI port
        device = getNamedDevice(deviceName, provider, deviceClass,
                                 false, false);
        if (device != null) {
            return device;
        }

        if (deviceClass == Receiver.class) {
            // try to get Synthesizer
            device = getNamedDevice(deviceName, provider, deviceClass,
                                     true, false);
            if (device != null) {
                return device;
            }
        }

        return null;
    }


    /** Return a MidiDevice with a given name from a given MidiDeviceProvider.
      @param deviceName The name of the MidiDevice to be returned.
      @param provider The MidiDeviceProvider to check for MidiDevices.
      @param deviceClass The requested device type, one of Synthesizer.class,
      Sequencer.class, Receiver.class or Transmitter.class.

      @return A MidiDevice matching the requirements, or null if none is found.
     */
    private static MidiDevice getNamedDevice(String deviceName,
                                             MidiDeviceProvider provider,
                                             Class deviceClass,
                                             boolean allowSynthesizer,
                                             boolean allowSequencer) {
        MidiDevice.Info[] infos = provider.getDeviceInfo();
        for (int i = 0; i < infos.length; i++) {
            if (infos[i].getName().equals(deviceName)) {
                MidiDevice device = provider.getDevice(infos[i]);
                if (isAppropriateDevice(device, deviceClass,
                                        allowSynthesizer, allowSequencer)) {
                    return device;
                }
            }
        }
        return null;
    }


    /** Return a MidiDevice with a given name from a list of
        MidiDeviceProviders.
        @param deviceName The name of the MidiDevice to be returned.
        @param providers The List of MidiDeviceProviders to check for
        MidiDevices.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A Mixer matching the requirements, or null if none is found.
    */
    private static MidiDevice getNamedDevice(String deviceName,
                                             List providers,
                                             Class deviceClass) {
        MidiDevice device;
        // try to get MIDI port
        device = getNamedDevice(deviceName, providers, deviceClass,
                                 false, false);
        if (device != null) {
            return device;
        }

        if (deviceClass == Receiver.class) {
            // try to get Synthesizer
            device = getNamedDevice(deviceName, providers, deviceClass,
                                     true, false);
            if (device != null) {
                return device;
            }
        }

        return null;
    }


    /** Return a MidiDevice with a given name from a list of
        MidiDeviceProviders.
        @param deviceName The name of the MidiDevice to be returned.
        @param providers The List of MidiDeviceProviders to check for
        MidiDevices.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A Mixer matching the requirements, or null if none is found.
     */
    private static MidiDevice getNamedDevice(String deviceName,
                                             List providers,
                                             Class deviceClass,
                                             boolean allowSynthesizer,
                                             boolean allowSequencer) {
        for(int i = 0; i < providers.size(); i++) {
            MidiDeviceProvider provider = (MidiDeviceProvider) providers.get(i);
            MidiDevice device = getNamedDevice(deviceName, provider,
                                               deviceClass,
                                               allowSynthesizer,
                                               allowSequencer);
            if (device != null) {
                return device;
            }
        }
        return null;
    }


    /** From a given MidiDeviceProvider, return the first appropriate device.
        @param provider The MidiDeviceProvider to check for MidiDevices.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A MidiDevice is considered appropriate, or null if no
        appropriate device is found.
    */
    private static MidiDevice getFirstDevice(MidiDeviceProvider provider,
                                             Class deviceClass) {
        MidiDevice device;
        // try to get MIDI port
        device = getFirstDevice(provider, deviceClass,
                                false, false);
        if (device != null) {
            return device;
        }

        if (deviceClass == Receiver.class) {
            // try to get Synthesizer
            device = getFirstDevice(provider, deviceClass,
                                    true, false);
            if (device != null) {
                return device;
            }
        }

        return null;
    }


    /** From a given MidiDeviceProvider, return the first appropriate device.
        @param provider The MidiDeviceProvider to check for MidiDevices.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A MidiDevice is considered appropriate, or null if no
        appropriate device is found.
     */
    private static MidiDevice getFirstDevice(MidiDeviceProvider provider,
                                             Class deviceClass,
                                             boolean allowSynthesizer,
                                             boolean allowSequencer) {
        MidiDevice.Info[] infos = provider.getDeviceInfo();
        for (int j = 0; j < infos.length; j++) {
            MidiDevice device = provider.getDevice(infos[j]);
            if (isAppropriateDevice(device, deviceClass,
                                    allowSynthesizer, allowSequencer)) {
                return device;
            }
        }
        return null;
    }


    /** From a List of MidiDeviceProviders, return the first appropriate
        MidiDevice.
        @param providers The List of MidiDeviceProviders to search.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A MidiDevice that is considered appropriate, or null
        if none is found.
    */
    private static MidiDevice getFirstDevice(List providers,
                                             Class deviceClass) {
        MidiDevice device;
        // try to get MIDI port
        device = getFirstDevice(providers, deviceClass,
                                false, false);
        if (device != null) {
            return device;
        }

        if (deviceClass == Receiver.class) {
            // try to get Synthesizer
            device = getFirstDevice(providers, deviceClass,
                                    true, false);
            if (device != null) {
                return device;
            }
        }

        return null;
    }


    /** From a List of MidiDeviceProviders, return the first appropriate
        MidiDevice.
        @param providers The List of MidiDeviceProviders to search.
        @param deviceClass The requested device type, one of Synthesizer.class,
        Sequencer.class, Receiver.class or Transmitter.class.
        @return A MidiDevice that is considered appropriate, or null
        if none is found.
     */
    private static MidiDevice getFirstDevice(List providers,
                                             Class deviceClass,
                                             boolean allowSynthesizer,
                                             boolean allowSequencer) {
        for(int i = 0; i < providers.size(); i++) {
            MidiDeviceProvider provider = (MidiDeviceProvider) providers.get(i);
            MidiDevice device = getFirstDevice(provider, deviceClass,
                                               allowSynthesizer,
                                               allowSequencer);
            if (device != null) {
                return device;
            }
        }
        return null;
    }


    /** Checks if a MidiDevice is appropriate.
        If deviceClass is Synthesizer or Sequencer, a device implementing
        the respective interface is considered appropriate. If deviceClass
        is Receiver or Transmitter, a device is considered appropriate if
        it implements neither Synthesizer nor Transmitter, and if it can
        provide at least one Receiver or Transmitter, respectively.

        @param device the MidiDevice to test
        @param allowSynthesizer if true, Synthesizers are considered
        appropriate. Otherwise only pure MidiDevices are considered
        appropriate (unless allowSequencer is true). This flag only has an
        effect for deviceClass Receiver and Transmitter. For other device
        classes (Sequencer and Synthesizer), this flag has no effect.
        @param allowSequencer if true, Sequencers are considered
        appropriate. Otherwise only pure MidiDevices are considered
        appropriate (unless allowSynthesizer is true). This flag only has an
        effect for deviceClass Receiver and Transmitter. For other device
        classes (Sequencer and Synthesizer), this flag has no effect.
        @return true if the device is considered appropriate according to the
        rules given above, false otherwise.
    */
    private static boolean isAppropriateDevice(MidiDevice device,
                                               Class deviceClass,
                                               boolean allowSynthesizer,
                                               boolean allowSequencer) {
        if (deviceClass.isInstance(device)) {
            // This clause is for deviceClass being either Synthesizer
            // or Sequencer.
            return true;
        } else {
            // Now the case that deviceClass is Transmitter or
            // Receiver. If neither allowSynthesizer nor allowSequencer is
            // true, we require device instances to be
            // neither Synthesizer nor Sequencer, since we only want
            // devices representing MIDI ports.
            // Otherwise, the respective type is accepted, too
            if ( (! (device instanceof Sequencer) &&
                  ! (device instanceof Synthesizer) ) ||
                 ((device instanceof Sequencer) && allowSequencer) ||
                 ((device instanceof Synthesizer) && allowSynthesizer)) {
                // And of cource, the device has to be able to provide
                // Receivers or Transmitters.
                if ((deviceClass == Receiver.class &&
                     device.getMaxReceivers() != 0) ||
                    (deviceClass == Transmitter.class &&
                     device.getMaxTransmitters() != 0)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Obtains the set of services currently installed on the system
     * using sun.misc.Service, the SPI mechanism in 1.3.
     * @return a List of instances of providers for the requested service.
     * If no providers are available, a List of length 0 will be returned.
     */
    private static List getProviders(Class providerClass) {
        return JDK13Services.getProviders(providerClass);
    }
}
