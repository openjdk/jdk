if (key == undefined || key != 'engine value') {
    throw "unexpected engine scope value";
}

// pre-defined context variable refers to current ScriptContext
if (context.getAttribute('key', context.GLOBAL_SCOPE) != 'global value') {
    throw "unexpected global scope value";
}

// change the engine scope value
key = 'new engine value';

if (context.getAttribute('key', context.GLOBAL_SCOPE) != 'global value') {
    throw "global scope should not change here";
}

// delete engine scope value
delete key;

if (key == undefined && key != 'xglobal value') {
    throw 'global scope should be visible after engine scope removal';
}
