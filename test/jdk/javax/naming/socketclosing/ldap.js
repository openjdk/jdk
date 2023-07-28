/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

// The node.js LDAP server used to generate data in Data.java. Run
// "java Main prep2" against this server to collect actual LDAP network
// data and save them into Data.java, so that in test running time there
// is no need for real network communications.
//
// Read test.sh for details.

var ldap = require('ldapjs');
var server = ldap.createServer();

server.search('o=example', function(req, res, next) {
  var obj = {
    dn: req.dn.toString(),
    attributes: {
      objectclass: ['organization', 'top'],
      javaClassName: 'EvilClassLoader',
      javaSerializedData: new Buffer(
// The following line should be replaced by output of "java Main prep1" if
// you want to recreate data materials in this test.
[-84, -19, 0, 5, 115, 114, 0, 20, 77, 97, 105, 110, 36, 69, 118, 105, 108, 67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 0, 0, 0, 0, 0, 0, 0, 1, 2, 0, 0, 120, 112]
            ),
      o: 'example'
    }
  };

  if (req.filter.matches(obj.attributes))
    res.send(obj);

  res.end();
});

server.listen(1389, function() {
  console.log('LDAP server listening at %s', server.url);
});
