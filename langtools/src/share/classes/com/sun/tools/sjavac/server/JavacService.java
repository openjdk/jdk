package com.sun.tools.sjavac.server;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Set;

public interface JavacService {

    SysInfo getSysInfo();

    CompilationResult compile(String protocolId,
                              String invocationId,
                              String[] args,
                              List<File> explicitSources,
                              Set<URI> sourcesToCompile,
                              Set<URI> visibleSources);
}
