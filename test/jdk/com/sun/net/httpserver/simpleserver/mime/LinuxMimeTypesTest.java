/*
* Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * @test
 * @summary Basic tests for MIME types for Linux operating system
 * @requires os.family == "linux"
 * @run testng/othervm LinuxMimeTypesTest
 */
public class LinuxMimeTypesTest extends AbstractMimeTypesTest {

    static final String contents = """
            #sun.net.www MIME content-types table
            #
            # Property fields:
            #
            #   <description> ::= 'description' '=' <descriptive string>
            #    <extensions> ::= 'file_extensions' '=' <comma-delimited list, include '.'>
            #         <image> ::= 'icon' '=' <filename of icon image>
            #        <action> ::= 'browser' | 'application' | 'save' | 'unknown'
            #   <application> ::= 'application' '=' <command line template>
            #
                        
            #
            # The "we don't know anything about this data" type(s).
            # Used internally to mark unrecognized types.
            #
            content/unknown: description=Unknown Content
            unknown/unknown: description=Unknown Data Type
                        
            #
            # The template we should use for temporary files when launching an application
            # to view a document of given type.
            #
            temp.file.template: /tmp/%s
                        
            #
            # The "real" types.
            #
            application/octet-stream: \\
            	description=Generic Binary Stream;\\
            	file_extensions=.saveme,.dump,.hqx,.arc,.o,.a,.bin,.exe,.z,.gz
                        
            application/oda: \\
            	description=ODA Document;\\
            	file_extensions=.oda
                        
            application/pdf: \\
            	description=Adobe PDF Format;\\
            	file_extensions=.pdf
                        
            application/postscript: \\
            	description=Postscript File;\\
            	file_extensions=.eps,.ai,.ps;\\
            	icon=ps;\\
            	action=application;\\
            	application=imagetool %s
                        
            application/x-dvi: \\
            	description=TeX DVI File;\\
            	file_extensions=.dvi;\\
            	action=application;\\
            	application=xdvi %s
                        
            application/x-hdf: \\
            	description=Hierarchical Data Format;\\
            	file_extensions=.hdf;\\
            	action=save
                        
            application/x-latex: \\
            	description=LaTeX Source;\\
            	file_extensions=.latex
                        
            application/x-netcdf: \\
            	description=Unidata netCDF Data Format;\\
            	file_extensions=.nc,.cdf;\\
            	action=save
                        
            application/x-tex: \\
            	description=TeX Source;\\
            	file_extensions=.tex
                        
            application/x-texinfo: \\
            	description=Gnu Texinfo;\\
            	file_extensions=.texinfo,.texi
                        
            application/x-troff: \\
            	description=Troff Source;\\
            	file_extensions=.t,.tr,.roff;\\
            	action=application;\\
            	application=xterm -title troff -e sh -c \\"nroff %s | col | more -w\\"
                        
            application/x-troff-man: \\
            	description=Troff Manpage Source;\\
            	file_extensions=.man;\\
            	action=application;\\
            	application=xterm -title troff -e sh -c \\"nroff -man %s | col | more -w\\"
                        
            application/x-troff-me: \\
            	description=Troff ME Macros;\\
            	file_extensions=.me;\\
            	action=application;\\
            	application=xterm -title troff -e sh -c \\"nroff -me %s | col | more -w\\"
                        
            application/x-troff-ms: \\
            	description=Troff MS Macros;\\
            	file_extensions=.ms;\\
            	action=application;\\
            	application=xterm -title troff -e sh -c \\"nroff -ms %s | col | more -w\\"
                        
            application/x-wais-source: \\
            	description=Wais Source;\\
            	file_extensions=.src,.wsrc
                        
            application/zip: \\
            	description=Zip File;\\
            	file_extensions=.zip;\\
            	icon=zip;\\
            	action=save
                        
            application/x-bcpio: \\
            	description=Old Binary CPIO Archive;\\
            	file_extensions=.bcpio; action=save
                        
            application/x-cpio: \\
            	description=Unix CPIO Archive;\\
            	file_extensions=.cpio; action=save
                        
            application/x-gtar: \\
            	description=Gnu Tar Archive;\\
            	file_extensions=.gtar;\\
            	icon=tar;\\
            	action=save
                        
            application/x-shar: \\
            	description=Shell Archive;\\
            	file_extensions=.sh,.shar;\\
            	action=save
                        
            application/x-sv4cpio: \\
            	description=SVR4 CPIO Archive;\\
            	file_extensions=.sv4cpio; action=save
                        
            application/x-sv4crc: \\
            	description=SVR4 CPIO with CRC;\\
            	file_extensions=.sv4crc; action=save
                        
            application/x-tar: \\
            	description=Tar Archive;\\
            	file_extensions=.tar;\\
            	icon=tar;\\
            	action=save
                        
            application/x-ustar: \\
            	description=US Tar Archive;\\
            	file_extensions=.ustar;\\
            	action=save
                        
            audio/aac: \\
            	description=Advanced Audio Coding Audio;\\
            	file_extensions=.aac
                        
            audio/basic: \\
            	description=Basic Audio;\\
            	file_extensions=.snd,.au;\\
            	icon=audio;\\
            	action=application;\\
            	application=audiotool %s
                        
            audio/flac: \\
            	description=Free Lossless Audio Codec Audio;\\
            	file_extensions=.flac
                        
            audio/mp4: \\
            	description=MPEG-4 Audio;\\
            	file_extensions=.m4a
                        
            audio/mpeg: \\
            	description=MPEG Audio;\\
            	file_extensions=.mp2,.mp3
                        
            audio/ogg: \\
            	description=Ogg Audio;\\
            	file_extensions=.oga,.ogg,.opus,.spx
                        
            audio/x-aiff: \\
            	description=Audio Interchange Format File;\\
            	file_extensions=.aifc,.aif,.aiff;\\
            	icon=aiff
                        
            audio/x-wav: \\
            	description=Wav Audio;\\
            	file_extensions=.wav;\\
            	icon=wav
                        
            image/gif: \\
            	description=GIF Image;\\
            	file_extensions=.gif;\\
            	icon=gif;\\
            	action=browser
                        
            image/ief: \\
            	description=Image Exchange Format;\\
            	file_extensions=.ief
                        
            image/jpeg: \\
            	description=JPEG Image;\\
            	file_extensions=.jfif,.jfif-tbnl,.jpe,.jpg,.jpeg;\\
            	icon=jpeg;\\
            	action=browser;\\
            	application=imagetool %s
                        
            image/svg+xml: \\
            	description=Scalable Vector Graphics;\\
            	file_extensions=.svg,.svgz
                        
            image/tiff: \\
            	description=TIFF Image;\\
            	file_extensions=.tif,.tiff;\\
            	icon=tiff
                        
            image/vnd.fpx: \\
            	description=FlashPix Image;\\
            	file_extensions=.fpx,.fpix
                        
            image/x-cmu-rast: \\
            	description=CMU Raster Image;\\
            	file_extensions=.ras
                        
            image/x-portable-anymap: \\
            	description=PBM Anymap Format;\\
            	file_extensions=.pnm
                        
            image/x-portable-bitmap: \\
            	description=PBM Bitmap Format;\\
            	file_extensions=.pbm
                        
            image/x-portable-graymap: \\
            	description=PBM Graymap Format;\\
            	file_extensions=.pgm
                        
            image/x-portable-pixmap: \\
            	description=PBM Pixmap Format;\\
            	file_extensions=.ppm
                        
            image/x-rgb: \\
            	description=RGB Image;\\
            	file_extensions=.rgb
                        
            image/x-xbitmap: \\
            	description=X Bitmap Image;\\
            	file_extensions=.xbm,.xpm
                        
            image/x-xwindowdump: \\
            	description=X Window Dump Image;\\
            	file_extensions=.xwd
                        
            image/png: \\
            	description=PNG Image;\\
            	file_extensions=.png;\\
            	icon=png;\\
            	action=browser
                        
            image/bmp: \\
            	description=Bitmap Image;\\
            	file_extensions=.bmp;
                        
            text/css: \\
            	description=CSS File;\\
            	file_extensions=.css;
                        
            text/html: \\
            	description=HTML Document;\\
            	file_extensions=.htm,.html;\\
            	icon=html
                        
            text/javascript: \\
            	description=Javascript File;\\
            	file_extensions=.js;
                        
            text/plain: \\
            	description=Plain Text;\\
            	file_extensions=.text,.c,.cc,.c++,.h,.pl,.txt,.java,.el;\\
            	icon=text;\\
            	action=browser
                        
            text/tab-separated-values: \\
            	description=Tab Separated Values Text;\\
            	file_extensions=.tsv
                        
            text/x-setext: \\
            	description=Structure Enhanced Text;\\
            	file_extensions=.etx
                        
            video/mp4: \\
            	description=MPEG-4 Video;\\
            	file_extensions=.m4v,.mp4
                        
            video/mpeg: \\
            	description=MPEG Video Clip;\\
            	file_extensions=.mpg,.mpe,.mpeg;\\
            	icon=mpeg;\\
            	action=application;\\
            	application=mpeg_play %s
                        
            video/ogg: \\
            	description=Ogg Video;\\
            	file_extensions=.ogv
                        
            video/quicktime: \\
            	description=QuickTime Video Clip;\\
            	file_extensions=.mov,.qt
                        
            video/webm: \\
            	description=WebM Video;\\
            	file_extensions=.webm
                        
            application/x-troff-msvideo: \\
            	description=AVI Video;\\
            	file_extensions=.avi;\\
            	icon=avi
                        
            video/x-sgi-movie: \\
            	description=SGI Movie;\\
            	file_extensions=.movie,.mv
                        
            message/rfc822: \\
            	description=Internet Email Message;\\
            	file_extensions=.mime
                        
            application/xml: \\
            	description=XML document;\\
            	file_extensions=.xml
            """;

    /*
    src/java.base/unix/classes/sun/net/www/content-types.properties
    src/java.base/windows/classes/sun/net/www/content-types.properties
 */

    protected Properties getActualOperatingSystemSpecificMimeTypes(Properties properties) throws Exception {
        Properties actual = deserialize(contents);
        for (Object key : actual.keySet()) {
            properties.put(key,actual.get(key));
        }
        return properties;
    }

    protected Properties getExpectedOperatingSystemSpecificMimeTypes() throws Exception {
       return load("expected-unix-content-types.properties");
    }

}
