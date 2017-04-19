#!/bin/bash -e
# Create a bundle in the current directory, containing what's needed to run
# the 'dot' program from the graphviz suite by the OpenJDK build.

TMPDIR=`mktemp -d -t graphvizbundle-XXXX`
trap "rm -rf \"$TMPDIR\"" EXIT

ORIG_DIR=`pwd`
cd "$TMPDIR"
GRAPHVIZ_VERSION=2.38.0-1
PACKAGE_VERSION=1.1
TARGET_PLATFORM=linux_x64
BUNDLE_NAME=graphviz-$TARGET_PLATFORM-$GRAPHVIZ_VERSION+$PACKAGE_VERSION.tar.gz
wget http://www.graphviz.org/pub/graphviz/stable/redhat/el6/x86_64/os/graphviz-$GRAPHVIZ_VERSION.el6.x86_64.rpm
wget http://www.graphviz.org/pub/graphviz/stable/redhat/el6/x86_64/os/graphviz-libs-$GRAPHVIZ_VERSION.el6.x86_64.rpm
wget http://www.graphviz.org/pub/graphviz/stable/redhat/el6/x86_64/os/graphviz-plugins-core-$GRAPHVIZ_VERSION.el6.x86_64.rpm
wget http://www.graphviz.org/pub/graphviz/stable/redhat/el6/x86_64/os/graphviz-plugins-x-$GRAPHVIZ_VERSION.el6.x86_64.rpm

mkdir graphviz
cd graphviz
for rpm in ../*.rpm; do
  rpm2cpio $rpm | cpio --extract --make-directories
done

cat > dot << EOF
#!/bin/bash
# Get an absolute path to this script
this_script_dir=\`dirname \$0\`
this_script_dir=\`cd \$this_script_dir > /dev/null && pwd\`
export LD_LIBRARY_PATH="\$this_script_dir/usr/lib64:\$LD_LIBRARY_PATH"
exec \$this_script_dir/usr/bin/dot "\$@"
EOF
chmod +x dot
export LD_LIBRARY_PATH="$TMPDIR/graphviz/usr/lib64:$LD_LIBRARY_PATH"
# create config file
./dot -c
tar -cvzf ../$BUNDLE_NAME *
cp ../$BUNDLE_NAME "$ORIG_DIR"
