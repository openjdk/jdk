package com.sun.tools.sjavac.server;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CompilationResult {

    // Return code constants
    public final static int ERROR_BUT_TRY_AGAIN = -4712;
    public final static int ERROR_FATAL = -1;

    public int returnCode;
    public Map<String, Set<URI>> packageArtifacts = new HashMap<>();
    public Map<String, Set<String>> packageDependencies = new HashMap<>();
    public Map<String, String> packagePubapis = new HashMap<>();
    public SysInfo sysinfo;
    public String stdout;
    public String stderr;

    public CompilationResult(int returnCode) {
        this.returnCode = returnCode;
        this.sysinfo = new SysInfo(-1, -1);
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }
}
