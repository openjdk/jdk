#!/bin/bash

# Get an absolute path to this script, since that determines the top-level
# directory.
this_script_dir=`dirname $0`
TOPDIR=`cd $this_script_dir/../.. > /dev/null && pwd`

GREP=grep
MD_FILE=$TOPDIR/README-builds.md
HTML_FILE=$TOPDIR/README-builds.html

# Locate the markdown processor tool and check that it is the correct version.
locate_markdown_processor() {
  if [ -z "$MARKDOWN" ]; then
    MARKDOWN=`which markdown 2> /dev/null`
    if [ -z "$MARKDOWN" ]; then
      echo "Error: Cannot locate markdown processor" 1>&2
      exit 1
    fi
  fi

  # Test version
  MARKDOWN_VERSION=`$MARKDOWN -version | $GREP version`
  if [ "x$MARKDOWN_VERSION" != "xThis is Markdown, version 1.0.1." ]; then
    echo "Error: Expected markdown version 1.0.1." 1>&2
    echo "Actual version found: $MARKDOWN_VERSION" 1>&2
    echo "Download markdown here: https://daringfireball.net/projects/markdown/"  1>&2
    exit 1
  fi

}

# Verify that the source markdown file looks sound.
verify_source_code() {
  TOO_LONG_LINES=`$GREP -E -e '^.{80}.+$' $MD_FILE`
  if [ "x$TOO_LONG_LINES" != x ]; then
    echo "Warning: The following lines are longer than 80 characters:"
    $GREP -E -e '^.{80}.+$' $MD_FILE
  fi
}

# Convert the markdown file to html format.
process_source() {
  echo "Generating html file from markdown"
  cat > $HTML_FILE << END
<html>
  <head>
    <title>OpenJDK Build README</title>
  </head>
  <body>
END
  markdown $MD_FILE >> $HTML_FILE
  cat >> $HTML_FILE <<END
  </body>
</html>
END
  echo "Done"
}

locate_markdown_processor
verify_source_code
process_source
