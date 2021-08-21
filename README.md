# Welcome to the JDK!

For build instructions please see the
[online documentation](https://openjdk.java.net/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

See <https://openjdk.java.net/> for more information about
the OpenJDK Community and the JDK.

文档：https://www.zhoujunwen.com/2019/building-openjdk-8-on-mac-osx-catalina-10-15/
mac编译：
 bash ./configure --with-debug-level=slowdebug --with-freetype-include=/usr/local/include/freetype2 --with-freetype-lib=/usr/local/lib --with-boot-jdk=/Library/Java/JavaVirtualMachines/jdk1.8.0_261.jdk/Contents/Home
执行之后的结果：
A new configuration has been successfully created in
/Users/hulishui/CLionProjects/jvmStudy/build/macosx-x86_64-normal-server-slowdebug
using configure arguments '--with-debug-level=slowdebug --with-freetype-include=/usr/local/include/freetype2 --with-freetype-lib=/usr/local/lib --with-boot-jdk=/Library/Java/JavaVirtualMachines/jdk1.8.0_261.jdk/Contents/Home'.

Configuration summary:
* Debug level:    slowdebug
* JDK variant:    normal
* JVM variants:   server
* OpenJDK target: OS: macosx, CPU architecture: x86, address length: 64

Tools summary:
* Boot JDK:       java version "1.8.0_261" Java(TM) SE Runtime Environment (build 1.8.0_261-b12) Java HotSpot(TM) 64-Bit Server VM (build 25.261-b12, mixed mode)  (at /Library/Java/JavaVirtualMachines/jdk1.8.0_261.jdk/Contents/Home)
* C Compiler:      version Configured with: --prefix=/Applications/Xcode.app/Contents/Developer/usr --with-gxx-include-dir=/Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/include/c++/4.2.1 (at /usr/bin/gcc)
* C++ Compiler:    version Configured with: --prefix=/Applications/Xcode.app/Contents/Developer/usr --with-gxx-include-dir=/Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/include/c++/4.2.1 (at /usr/bin/g++)

Build performance summary:
* Cores to use:   5
* Memory limit:   16384 MB
* ccache status:  installed, but disabled (version older than 3.1.4)

WARNING: The result of this configuration has overridden an older
configuration. You *should* run 'make clean' to make sure you get a
proper build. Failure to do so might result in strange build problems.

下载：
git clone git@github.com:imkiwa/xcode-missing-libstdc-.git
配置：
sudo mkdir -p /Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/include/c++
sudo cp -R include/c++/* /Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/include/c++
sudo cp lib/*  /Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/lib    
