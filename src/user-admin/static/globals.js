/**
 * @file manages all globals variables
 * @author Zhoutuo Yang <zhoutuoy@gmail.com>
 */

/**
 * @global
 * @type {String}
 * @constant
 * @desc A variable containing current project's name, once initialized, it is constant.
 */
var project_name = "";
/**
 * @global
 * @type {Number}
 * @desc the integer seed for generating numbers of current template ID
 */
var libraryBlockId = 0;
/**
 * @global
 * @type {Boolean}
 * @desc a boolean value showing whether to load a project or not, which is used for loading a existing project
 */
var needsLoading = true;
/**
 * @global
 * @type {Boolean}
 * @desc a boolean value showing whether it is during loading a project phase or not. It will become true if {@link needsLoading}
 * is true. After loading, it will become false again. During loading phase, the front end will not send out AJAX request to backend
 * since front end got loaded info in the first place.
 */
var loadingPhase = false;
/**
 * @global
 * @type {Object}
 * @desc a hash table for loading a project. key is the name of a template, value is the unique ID of that template,
 * which is used to search for a correspong template for existing blocks.
 */
var templateTable = {};
/**
 * @global
 * @type {Array}
 * @desc a array that will hold all information of a project that is going to be loaded
 * The info mainly consists blocks, their postions and their connections
 */
var loadingBuffer = [];
/**
 * @global
 * @type {Integer}
 * @desc A variable holds the current height of the library blocks, which helps to position the next block, which starts with 0
 */
var libTemplatesHeight = 0;