/* config-overrides.js */
const webpack = require('webpack');
module.exports = function override(config, env) {
    config.resolve.fallback = {
        stream: false,
        crypto: false,
        util: false
    };
    return config;
}
