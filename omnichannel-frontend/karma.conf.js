// Karma configuration — referenced by angular.json's "test" architect
// target via "karmaConfig": "karma.conf.js". Angular CLI's karma builder
// works without this file (it generates an equivalent config internally),
// but a custom coverage reporter set — specifically lcovonly, which
// SonarQube's JS/TS analyzer reads — isn't reachable through angular.json
// alone, hence this file.
// https://karma-runner.github.io/6.4/config/configuration-file.html

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, 'coverage/omnichannel-frontend'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        // SonarQube's javascript/typescript analyzer reads this file
        // directly — see sonar.javascript.lcov.reportPaths in
        // sonar-project.properties, which points at this exact path.
        { type: 'lcovonly', file: 'lcov.info' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['Chrome'],
    // Headless, sandbox-free launcher for CI / non-interactive runs — see
    // the `ng test --browsers=ChromeHeadlessCI` command below. Kept
    // separate from the default 'Chrome' entry above so `ng test` alone
    // (interactive/watch mode, local development) is unaffected.
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu']
      }
    },
    singleRun: false,
    restartOnFileChange: true
  });
};
