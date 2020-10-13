var exec = require('cordova/exec');
/*
exports.coolMethod = function (arg0, success, error) {
    exec(success, error, 'PhotoEditorPlugin2', 'edit', [arg0]);
};
*/

exports.editFromGallery = function(success, error){
    exec(success, error, 'PhotoEditorPlugin2', 'edit', ["from", "Gallery"]);
};

exports.editFromCamera = function(success, error){
    exec(success, error, 'PhotoEditorPlugin2', 'edit', ["from", "Camera"]);
};

exports.editFromBase64 = function(base64File, success, error){
    exec(success, error, 'PhotoEditorPlugin2', 'edit', ["from", "Base64", base64File]);
};
