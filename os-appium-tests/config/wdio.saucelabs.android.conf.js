const { config } = require('./wdio.shared.conf');
// ============
// Capabilities
// ============
// For all capabilities please check
// http://appium.io/docs/en/writing-running-appium/caps/#general-capabilities
config.capabilities = [
    {
        // The reference to the app
        testobject_app_id: '1', //find it at SauceLabs App Dashboard
        // The api key that has a reference to the app-project in the TO cloud
        testobject_api_key: '7031A38E6F8741BAA0AA526BB7D78DCE',
        // You can find more info in the Appium Basic Setup section
        platformName: 'Android',
        platformVersion: '9', //e.g. 8.1
        idleTimeout: 180,
        maxInstances: 2,
        orientation: 'PORTRAIT',
        newCommandTimeout: 180,
        privateDevicesOnly: false, //use Public or Private Cloud
        enableAnimations: false,
        autoAcceptAlerts: true,
        locationServicesEnabled: true,
        locationServicesAuthorized: true        
        // testobject_test_name: '..', // The name of the test for in the cloud
        // deviceName: '...', // e.g. Samsung
        // testobject_cache_device: true,
        // noReset: true,
        // phoneOnly: false,
        // tabletOnly: false
    },
];

// =========================
// Sauce RDC specific config
// =========================
// The new version of WebdriverIO will:
// - automatically update the job status in the RDC cloud
// - automatically default to the US RDC cloud
config.services = [ 'sauce' ];
// If you need to connect to the US RDC cloud comment the below line of code
config.region = 'eu';
// and uncomment the below line of code
// config.region = 'us';
config.protocol = 'https';
config.host = 'appium.testobject.com';
config.port = 443;
config.path = '/wd/hub';

// This port was defined in the `wdio.shared.conf.js` for appium
// delete config.port;

exports.config = config;