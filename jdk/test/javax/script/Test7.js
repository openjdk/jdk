//this is the first line of Test7.js
var filename;
importPackage(java.io);
importPackage(java);
var f = new File(filename);
var r = new BufferedReader(new InputStreamReader(new FileInputStream(f)));

var firstLine = r.readLine() + '';
print(firstLine);
