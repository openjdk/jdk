suite = {
  "mxversion" : "5.23.1",
  "name" : "jvmci",
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "http://openjdk.java.net/projects/graal",
  },
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL"]
    },
  },

  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },

  "defaultLicense" : "GPLv2-CPE",

  "outputRoot" : "../../build/mx/hotspot",

    # ------------- Libraries -------------

  "libraries" : {

    "TESTNG" : {
      "urls" : ["http://central.maven.org/maven2/org/testng/testng/6.9.10/testng-6.9.10.jar"],
      "sha1" : "6feb3e964aeb7097aff30c372aac3ec0f8d87ede",
    },
  },

  "projects" : {

    # ------------- JVMCI:Service -------------

    "jdk.vm.ci.services" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    # ------------- JVMCI:API -------------

    "jdk.vm.ci.common" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.meta" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.code" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.meta"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.code.test" : {
      "subDir" : "../../test/hotspot/jtreg/compiler/jvmci",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.sparc",
        "jdk.vm.ci.code",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.runtime" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.code",
        "jdk.vm.ci.services",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.runtime.test" : {
      "subDir" : "../../test/hotspot/jtreg/compiler/jvmci",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "jdk.vm.ci.common",
        "jdk.vm.ci.runtime",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    # ------------- JVMCI:HotSpot -------------

    "jdk.vm.ci.aarch64" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,AArch64",
    },

    "jdk.vm.ci.amd64" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,AMD64",
    },

    "jdk.vm.ci.sparc" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,SPARC",
    },

    "jdk.vm.ci.hotspot" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.common",
        "jdk.vm.ci.runtime",
        "jdk.vm.ci.services",
      ],
      "imports" : [
        "jdk.internal.misc",
        "jdk.internal.org.objectweb.asm",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI",
    },

    "jdk.vm.ci.hotspot.test" : {
      "subDir" : "../../test/hotspot/jtreg/compiler/jvmci",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "TESTNG",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.hotspot.aarch64" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.aarch64",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,HotSpot,AArch64",
    },

    "jdk.vm.ci.hotspot.amd64" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,HotSpot,AMD64",
    },

    "jdk.vm.ci.hotspot.sparc" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.sparc",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.services",
      "javaCompliance" : "9",
      "workingSets" : "JVMCI,HotSpot,SPARC",
    },

    "hotspot" : {
      "native" : True,
      "class" : "HotSpotProject",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "JVMCI_SERVICES" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "dependencies" : ["jdk.vm.ci.services"],
    },

    "JVMCI_API" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "dependencies" : [
        "jdk.vm.ci.runtime",
        "jdk.vm.ci.common",
        "jdk.vm.ci.aarch64",
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.sparc",
      ],
      "distDependencies" : [
        "JVMCI_SERVICES",
      ],
    },

    "JVMCI_HOTSPOT" : {
      "subDir" : "../jdk.internal.vm.ci/share/classes",
      "dependencies" : [
        "jdk.vm.ci.hotspot.aarch64",
        "jdk.vm.ci.hotspot.amd64",
        "jdk.vm.ci.hotspot.sparc",
      ],
      "distDependencies" : [
        "JVMCI_SERVICES",
        "JVMCI_API",
      ],
    },

    "JVMCI_TEST" : {
      "subDir" : "../../test/hotspot/jtreg/compiler/jvmci",
      "dependencies" : [
        "jdk.vm.ci.runtime.test",
      ],
      "distDependencies" : [
        "JVMCI_API",
      ],
      "exclude" : ["mx:JUNIT"],
    },
  },
}
