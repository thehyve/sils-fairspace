// since webpack and babel update problems with jest and node modules. Hard problem, this is a work around to get all tests running
// https://github.com/remarkjs/react-markdown/issues/635
module.exports = {
    moduleNameMapper: {
        'react-markdown': './__mocks__/react-markdown.js',
    },
};
