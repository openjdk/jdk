var Class = {
  create: function() {
    return function() { //vararg
        this.initialize.apply(this, arguments);
    }
  }
};

Color = Class.create();
Color.prototype = {
    red: 0, green: 0, blue: 0,
    initialize: function(r,g,b) {
    this.red = r;
    this.green = g;
    this.blue = b;
    }
}

function bench(x) {
    var d = new Date;
    var colors = new Array(16);
    for (var i=0;i<1e8;i++) {
    colors[i&0xf] = (new Color(1,2,3));
    }
    print(new Date - d);
    return colors;
}
bench(17);

print("Swapping out call");
Function.prototype.call = function() {
    throw "This should not happen, apply should be called instead";
};

bench(17);

print("All done!");
