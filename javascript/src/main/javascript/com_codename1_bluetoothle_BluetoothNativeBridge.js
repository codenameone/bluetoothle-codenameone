(function(exports){

var o = {};

function unsupported(callback) {
    if (callback && callback.complete) {
        callback.complete(false);
        return;
    }
    if (callback && callback.error) {
        callback.error(new Error("Not implemented yet"));
    }
}

o.initialize__boolean_boolean_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.enable_ = function(callback) { unsupported(callback); };
o.disable_ = function(callback) { unsupported(callback); };
o.startScan__java_lang_String_boolean_int_int_int_int = function(a, b, c, d, e, f, callback) { unsupported(callback); };
o.stopScan_ = function(callback) { unsupported(callback); };
o.retrieveConnected__java_lang_String = function(a, callback) { unsupported(callback); };
o.connect__java_lang_String = function(a, callback) { unsupported(callback); };
o.reconnect__java_lang_String = function(a, callback) { unsupported(callback); };
o.disconnect__java_lang_String = function(a, callback) { unsupported(callback); };
o.close__java_lang_String = function(a, callback) { unsupported(callback); };
o.discover__java_lang_String = function(a, callback) { unsupported(callback); };
o.services__java_lang_String_java_lang_String = function(a, b, callback) { unsupported(callback); };
o.characteristics__java_lang_String_java_lang_String_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.descriptors__java_lang_String_java_lang_String_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.read__java_lang_String_java_lang_String_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.subscribe__java_lang_String_java_lang_String_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.unsubscribe__java_lang_String_java_lang_String_java_lang_String = function(a, b, c, callback) { unsupported(callback); };
o.write__java_lang_String_java_lang_String_java_lang_String_java_lang_String_boolean = function(a, b, c, d, e, callback) { unsupported(callback); };
o.writeQ__java_lang_String_java_lang_String_java_lang_String_java_lang_String_boolean = function(a, b, c, d, e, callback) { unsupported(callback); };
o.readDescriptor__java_lang_String_java_lang_String_java_lang_String_java_lang_String = function(a, b, c, d, callback) { unsupported(callback); };
o.writeDescriptor__java_lang_String_java_lang_String_java_lang_String_java_lang_String_java_lang_String = function(a, b, c, d, e, callback) { unsupported(callback); };
o.rssi__java_lang_String = function(a, callback) { unsupported(callback); };
o.mtu__java_lang_String_int = function(a, b, callback) { unsupported(callback); };
o.requestConnectionPriority__java_lang_String_java_lang_String = function(a, b, callback) { unsupported(callback); };
o.isInitialized_ = function(callback) { unsupported(callback); };
o.isEnabled_ = function(callback) { unsupported(callback); };
o.isScanning_ = function(callback) { unsupported(callback); };
o.wasConnected__java_lang_String = function(a, callback) { unsupported(callback); };
o.isConnected__java_lang_String = function(a, callback) { unsupported(callback); };
o.isDiscovered__java_lang_String = function(a, callback) { unsupported(callback); };
o.hasPermission_ = function(callback) { unsupported(callback); };
o.requestPermission_ = function(callback) { unsupported(callback); };
o.isLocationEnabled_ = function(callback) { unsupported(callback); };
o.requestLocation_ = function(callback) { unsupported(callback); };
o.isSupported_ = function(callback) { callback.complete(false); };

exports.com_codename1_bluetoothle_BluetoothNativeBridge = o;

})(cn1_get_native_interfaces());
