"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const Context = require("../helpers/Context");
// APPLICATION DEFAULTS
function getBottomLocationLink() {
    return Context.getElemBySelector('#b7-bottomGetLocationLink');
}
exports.getBottomLocationLink = getBottomLocationLink;
function getBottomWatchPositionLink() {
    return Context.getElemBySelector('#b7-bottomGetWatchPositionLink');
}
exports.getBottomWatchPositionLink = getBottomWatchPositionLink;
function getBottomClearWatchLink() {
    return Context.getElemBySelector('#b7-bottomClearWatchLink');
}
exports.getBottomClearWatchLink = getBottomClearWatchLink;
function getBottomPositionLink() {
    return Context.getElemBySelector('#b7-bottomPositionsLink');
}
exports.getBottomPositionLink = getBottomPositionLink;