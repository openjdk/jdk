README:
  This file should be located at the top of the OpenJDK Mercurial root
  repository. A full OpenJDK repository set (forest) should also include
  the following 6 nested repositories:
    "jdk", "hotspot", "langtools", "corba", "jaxws"  and "jaxp".

  The root repository can be obtained with something like:
    hg clone http://hg.openjdk.java.net/jdk8/jdk8 openjdk8
  
  You can run the get_source.sh script located in the root repository to get
  the other needed repositories:
    cd openjdk8 && sh ./get_source.sh

  People unfamiliar with Mercurial should read the first few chapters of
  the Mercurial book: http://hgbook.red-bean.com/read/

  See http://openjdk.java.net/ for more information about OpenJDK.

Simple Build Instructions:
  
  0. Get the necessary system software/packages installed on your system, see
     http://hg.openjdk.java.net/jdk8/jdk8/raw-file/tip/README-builds.html

  1. If you don't have a jdk7u7 or newer jdk, download and install it from
     http://java.sun.com/javase/downloads/index.jsp
     Add the /bin directory of this installation to your PATH environment
     variable.

  2. Configure the build:
       bash ./configure
  
  3. Build the OpenJDK:
       make all
     The resulting JDK image should be found in build/*/images/j2sdk-image

where make is GNU make 3.81 or newer, /usr/bin/make on Linux usually
is 3.81 or newer. Note that on Solaris, GNU make is called "gmake".

Complete details are available in the file:
     http://hg.openjdk.java.net/jdk8/jdk8/raw-file/tip/README-builds.html
