/**
 * @file contains logic of project designing
 * @author Zhoutuo Yang <zhoutuoy@gmail.com>
 */

/**
 * @namespace design
 */


/**
 * This callback function will be called when document is ready
 * It tries to load the templates from backend using JSON
 * if succeed, it initializes the save and generate buttons
 * else it keeps loading and meanwhile user cannot operate
 * @callback DOMReadyCallback
 * @memberof design
 */
$(document).ready(function () {
	$("<div/>").text("Loading Library...").appendTo($("#library"));

	$.ajaxSetup({
		timeout: 3000
	});
	//get project_name
	project_name = $("#project_name").html();
	/**
	 * This callback recevies a JSON object from backend containing all template information.
	 * it will be only invoked there is a valid JSON received.
	 * It will load templates from JSON first.
	 * Then try to load a project if there is one.
	 * @callback initTemplatesCallback
	 * @param  {Object} data a JSON array of templates
	 * @memberof design
	 */
	$.getJSON("/ajax/templates", function (data) {
		$("#library").html("");
		loadLib(data);
		if (needsLoading) {
			loadingPhase = true;
			loadProject();
			loadingPhase = false;
		};
		/**
		 * This callback is binded to click event of "Save" button
		 * Once uses click on the button, this callback will be called
		 * It is used to call backend to save the current project using AJAX
		 * @callback saveClickCallback
		 * @param  {Event} eventHandler A click event from users
		 * @memberof design
		 */
		$('#project_save').click(function (eventHandler) {
			$.getJSON('/ajax/project/save', {
				project_name: project_name
			},
			/**
			 * This callback will be called once front end receives save result from backend and display the result using {@link design.notifyMessage}
			 * @callback saveJSONCallback
			 * @param  {Object} data a JSON object containing the result in the format: {'result': "", 'content': ""}
			 * @memberof design
			 */
			function (data) {
				if (data['result'] == 'error') {
					notifyMessage(data['content']);
				} else {
					notifyMessage('Saved successfully');
				};
			});
		});

		/**
		 * This callback is binded to click event of "Generate" button
		 * It will disply the "Generating" using {@link design.notifyMessage}
		 * And then try to fetch code from backend and finally display it using {@link design.notifyMessage} again.
		 * @callback generateClickCallback
		 * @param  {Event} eventHandler A click event from users
		 * @memberof design
		 */
		$('#project_generate').click(function (eventHandler) {
			//show the generating message before getting the code from backdend
			$('#verilog_code').modal('show').children('.modal-body').empty().append("<pre class='brush: verilog; toolbar: false;'>Generating!</pre>");

			$.getJSON("/ajax/code_generate", {
				project_name: project_name
			},
			/**
			 * Ajax callback from verilog code generation
			 * Once got the code, it uses SyntaxHighlighter to highlight and is displayed by {@link design.notifyMessage}
			 * @callback generateJSONCallback
			 * @param  {Object} data a JSON object containing the result in the format: {'result': "", 'content': ""}
			 * @memberof design
			 */
			function (data) {
				//show the code
				$('#verilog_code').find('pre').text(data['content']);
				//highlight the code
				SyntaxHighlighter.highlight();
			})
			.error(function (jqXHR, textStatus, errorThrown) {
				//show the error information
				$('#verilog_code').find('pre').text(textStatus);
			});
			
		});


	}).error(function (jqXHR, textStatus, errorThrown) {
		notifyMessage(textStatus + ": " + errorThrown);
	});

});

/**
 * The function is used to parse the templates raw JSON data from backend
 * @param  {Array} jsonBlockArr A array of template objects
 * @memberof design
 */
function loadLib(jsonBlockArr) {
	//generate all blocks for library
	for (var i = jsonBlockArr.length - 1; i >= 0; i--) {
		generateLibarayBlock(jsonBlockArr[i]);
	};
	//update library box's height according to all blocks' height
	$('#library').height(libTemplatesHeight);

	//register jsplumb events here

	/**
	 * notification that a Connection is about to be dropped.
	 * In the callback, it skip if the connection is during loading phase or it is a reverting connection
	 * (A connection with Parameter "state", which means it falls back since backend did not respond)
	 * It will detach existing connection if there is on dest port and AJAX request the new connection to get approval
	 * Returning false from this method cancels the drop.
	 * @callback beforeDropCallback
	 * @param  {Event} info A object containing { sourceId, targetId, scope, connection, dropEndpoint }
	 * @memberof design
	 */
	jsPlumb.bind("beforeDrop", function (info) {
		//if it is a loading phase or revert operation, do not send out the message
		if (info.connection.getParameter('state') || loadingPhase) {
			return true;
		};

		var targetEP = info.dropEndpoint;
		//check whether the current drop endpoint has already a connection
		//if so, detach the original one to support the new one
		if (targetEP.getAttachedElements().length != 0) {
			targetEP.detachAll();
		};

		$.getJSON('/ajax/block/connect', {
			project_name: project_name,
			src_block_id: info.sourceId,
			src_port: info.connection.endpoints[0].getParameter('src_port'),
			dest_block_id: info.targetId,
			dest_port: targetEP.getParameter('dest_port')
		},
		/**
		 * AJAX callback to handle new connection result
		 * if there is an error, display it using {@link design.notifyMessage}
		 * Set this new connection as a reverting connection (this will not invoke the {@link design.beforeDetachCallback})
		 * detach the connection, namely revert back to original state
		 * @callback connectJSONCallback
		 * @param  {Object} data a JSON object containing the result in the format: {'result': "", 'content': ""}
		 * @memberof design
		 */
		function (data) {
			if (data['result'] == 'error') {
				// $('#notification').modal('show').children('.modal-body').text(data['content']);
				notifyMessage(data['content']);
				info.connection.setParameter('state', 'canceled');
				//detach the connection
				jsPlumb.detach(info);
				info.connection.setParameter('state', null);
			};
		});

		//return value shows whether the new connection should be established or not
		return true;
	});
	/**
	 * notification that a Connection is about to be detached.  Returning false from this method cancels the detach.
	 * It does the same skip as {@link design.beforeDropCallback}
	 * Also send out AJAX request to backend to get confirmation
	 * jsPlumb passes the Connection to your callback.
	 * @callback beforeDetachCallback
	 * @param  {Connection} info The connecting line between two elements.  A Connection consists of two Endpoints and a Connector.
	 * @memberof design
	 */
	jsPlumb.bind("beforeDetach", function (info) {
		//if it is a loading phase or revert operation, do not send out the message
		if (info.getParameter('state') || loadingPhase) {
			return true;
		};

		$.getJSON('/ajax/block/disconnect', {
			project_name: project_name,
			src_block_id: info.sourceId,
			src_port: info.endpoints[0].getParameter('src_port'),
			dest_block_id: info.targetId,
			dest_port: info.endpoints[1].getParameter('dest_port')
		}, 
		/**
		 * AJAX callback to handle new detachment result
		 * if there is an error, display it using {@link design.notifyMessage}
		 * Set this new detachment as a reverting connection (this will not invoke the {@link design.beforeDropCallback})
		 * reconnect the connection, namely revert back to original state
		 * @callback disconnectJSONCallback
		 * @param  {Object} data a JSON object containing the result in the format: {'result': "", 'content': ""}
		 * @memberof design
		 */
		function (data) {
			if (data['result'] == 'error') {
				notifyMessage(data['content']);
				info.setParameter('state', 'canceled');
				jsPlumb.connect({
					source: info.endpoints[0],
					target: info.endpoints[1]
				});
				info.setParameter('state', null);
			};			
		});
		return true;
	});
	
	/**
	 * mouse click event of a connection
	 * This callback will try to detach the clicked connection, namely delete it, which will invoke {@link design.beforeDetachCallback}
	 * @callback connectionClickCallback
	 * @param  {Connection} connection The connecting line between two elements.  A Connection consists of two Endpoints and a Connector.
	 * @param  {Event} orignalMouseEvent The event containing mouse information
	 * @memberof design
	 */
	jsPlumb.bind('click', function (connection, orignalMouseEvent) {
		jsPlumb.detach(connection);
		//do not let click goes up to upper events
		orignalMouseEvent.stopPropagation();
	});

};

/**
 * This function will load the project if there exists one
 * draw blocks and draw connections
 * @memberof design
 */
function loadProject() {
	// console.log(location.search);

	$.getJSON('/ajax/project/load', {
		project_name: project_name
	}, function (data) {
		if (data['result'] == 'success') {
			loadingBuffer = data['content'];
			for (var i = loadingBuffer.length - 1; i >= 0; i--) {
				var template = loadingBuffer[i].template;
				var block_id = loadingBuffer[i].uuid;
				var pos = loadingBuffer[i].position;
				var $template = $('#' + templateTable[template]);
				loadExistingBlock($template, block_id, pos);
			};
			loadProjectConnections(loadingBuffer);	
		};

	}).error(function (jqXHR, textStatus, errorThrown) {
		console.log(errorThrown);
	});
};

/**
 * generate one template from elements of JSON back from server
 * @param  {object} blockDef including name/dest ports/src ports
 * @memberof design
 */
function generateLibarayBlock(blockDef) {
	var name = blockDef.name;
	//create a whole block
	var newBlock = $("<div/>").addClass("libBlock").attr("id", libraryBlockIdGenerator());
	//get the template height for this template if there is
	if(blockDef.height != undefined) {
		newBlock.height(blockDef.height);
	}
	//add new template into template table
	templateTable[name] = newBlock.attr('id');
	//create the txt title block
	$("<div/>").addClass("title").text(name).appendTo(newBlock);
	//add to library container
	newBlock.appendTo($("#library"));
	//set position in the lib
	newBlock.position({
		my: "center top+" + libraryBlockId * 20, //the position of new block
		at: "center top+" + libTemplatesHeight, //at the some pos of lib
		of: $("#library"), //relative position according to library
		collision: "none"
	});
	//update library templates height
	libTemplatesHeight += (newBlock.height());
	//remember the initial pos to revert back after dragging to canvas
	newBlock.data("pos", newBlock.position());
	//genereate input endpoints (target)
	var inputNames = blockNameExtracter(blockDef.in);
	var inputEPs = generateInputEps(inputNames);
	//generate output endpoints (source)
	var outputNames = blockNameExtracter(blockDef.out);
	var outputEPs = generateOutputEps(outputNames, false);
	//store the input and output names to instantiate blocks
	newBlock.data("endPoints", {
		inputNames: inputNames,
		outputNames: outputNames
	});	
	//add initialize endpoints to block
	jsPlumb.addEndpoints(newBlock, inputEPs.concat(outputEPs));
	//make lib block draggable
	jsPlumb.draggable(newBlock, {
		//stop function will trigger when stop dragging
		/**
		 * The stop event from jQuery UI, it is invoked when a draggable is released somewhere in the canvas.
		 * This callback will check whether the release position of template is outside the library area or not.
		 * If so, iinstantiate a block in the same place as the release point.
		 * Whatever the position is, the template block will always revert to the orignal place in the library area.
		 * @callback templateStopCallback
		 * @param  {Event} event An stop event of template block
		 * @param  {Object} ui An object contains helper, position and offset information.
		 * @memberof design
		 */
		stop: function (event, ui) {
			var posD = $(this).position(); //get dragging position
			var posC = $("#library").position(); //get container position (leftupper)
			//if dragging position is inside the container
			//here does not check whether it overflow or not
			if (posD.left > posC.left + $("#library").width()) {
				instantiateBlock($(this));
			};
			//get inital pos of this lib block
			var pos = $(this).data("pos");
			//revert back to pos using animation
			jsPlumb.animate(newBlock, {
				left: pos.left,
				top: pos.top
			}, {
				duration: 100
			});
		}

	});


}

/**
 * generate id for template, which is used for jsPlumb to unique identify block, since template is a special type of block
 * @return {String} template ID which starts with "lib_block" followed by increasing number
 * @memberof design
 */
function libraryBlockIdGenerator() {
	return "lib_block" + libraryBlockId++;
}

/**
 * extract an array of names from ports of elements from JSON template Array
 * @param  {object} ports An object of dest ports or src ports
 * @return {Array} An array of port name strings
 * @memberof design
 */
function blockNameExtracter(ports) {
	var names = [];
	Object.keys(ports).forEach(function (value) {
		names.push(value);
	});
	return names;
}

/**
 * generate the inital parameters for target endpoints which is used by jsPlumb to display dest/src ports
 * @param  {Array} names an array of name strings
 * @return {Array} an array of objects containing parameters for jsPlumb to generate endpoints for coresponding names
 * @memberof design
 */
function generateInputEps(names) {
	var interval = 1.0 / names.length; //used to calculate the position along the Y-coordinate in left side of the block
	var inputEPs = [];
	for (var i = 0; i < names.length; i++) {
		inputEPs.push({
			isTarget: true, //make it a input
			//endpoint style
			endpoint: ["Dot", {
				radius: 4
			}],
			//position based on interval
			anchor: [0, 1 - interval * i, -1, 0],
			//label based on the name
			overlays: [
				["Label", {
					label: names[i],
					location: [0, -0.5], // location of the label
					cssClass: "blockOverlay"
				}]
			],
			maxConnections: 2, //must be larger than default 1, cause jsPlumb doesnot allow drop when the endpoint is full
			//target endpoints style
			paintStyle: {
				fillStyle: "green",
				outlineColor: "blue",
				outlineWidth: 2
			},
			parameters: {
				"dest_port": names[i]
			}
		});
	}
	return inputEPs;
}

/**
 * generate the inital parameters for source endpoints which is used by jsPlumb to display dest/src ports
 * @param  {Array} names an array of name strings
 * @param  {Boolean} flag a boolean value indicating whether these endpoints are src ports or not, true for blocks and false for templates so that templates can not be linked
 * @return {Array} an array of objects containing parameters for jsPlumb to generate endpoints for coresponding names
 * @memberof design
 */
function generateOutputEps(names, flag) {
	var interval = 1.0 / names.length;
	var outputEPs = [];
	for (var i = 0; i < names.length; i++) {
		outputEPs.push({
			isSource: flag,
			//infinite number of connections is allowed
			maxConnections: -1,
			endpoint: ["Dot", {
				radius: 4
			}],
			anchor: [1, 1 - interval * i, 1, 0],
			overlays: [
				["Label", {
					label: names[i],
					location: [0, -1],
					cssClass: "blockOverlay"
				}]
			],
			//style for source end points
			paintStyle: {
				fillStyle: "blue"
			},
			//conection style out from source end points
			connectorStyle: {
				strokeStyle: "Chocolate",
				lineWidth: 5
			},
			parameters: {
				"src_port": names[i]
			}
		});
	}
	return outputEPs;
}
/**
 * instantiate a block out of a designated template with current template position and all its properties except the unique ID, which
 * is fetched from backend using AJAX. a DOM object will be newly created, which could be dragged or linked in the canvas
 * @param  {object} $template a DOM object which resides inside Library div
 * @memberof design
 */
function instantiateBlock($template) {

	//set pos of new block according to the lib block
	var pos = $template.offset();
	//get template's name from $template
	var title = $template.children('.title').text();
	//only after get the unique id from server, that we can instantiate it
	//because we cannot change the id after that
	$.ajax({
		url: "/ajax/block/new",
		type: 'GET',
		dataType: 'json',
		data: {
			project_name: project_name,
			template_name: title,
			position: JSON.stringify({
				left: pos.left,
				top: pos.top
			})
		},
		timeout: 1000,
		/**
		 * The function will run once the AJAX request is exec successfully.
		 * It is used to request an unique ID for a new block dragged from library.
		 * An ID must be fetched before generating a block, which is required by jsPlumb
		 * @callback blockNewCallback
		 * @param  {Object} data a JSON object containing the result in the format: {'result': "", 'content': "", 'block': "ID here"} 
		 * @memberof design
		 */
		success: function (data) {
			if(data['result'] == 'error') {
				notifyMessage(data['content']);
			} else {
				var id = data['block'];
				//load the new block with new ID
				loadExistingBlock($template, id, pos);
			}
		},
		error: function (jqXHR, textStatus, errorThrown) {
			console.log(textStatus);
			console.log(errorThrown);
		}
	});
}

/**
 * This function loads exisiting block into the canvas
 * @param  {object} $template a object outlook of the block
 * @param  {String} block_id the unique ID that identify the block
 * @param  {Position} block_pos the position of newly loaded block
 * @memberof design
 */
function loadExistingBlock($template, block_id, block_pos) {
	var newState = $('<div/>').addClass("libBlock");
	//copy height
	newState.height($template.height());
	//copy the title
	$template.children('.title').clone().appendTo(newState);

	$('#container').append(newState);
	//set position of new state
	newState.offset(block_pos);
	var names = $template.data("endPoints");
	var inputs = generateInputEps(names.inputNames);
	var outputs = generateOutputEps(names.outputNames, true);

	//load the block with existing id property
	newState.attr("id", block_id);
	jsPlumb.addEndpoints(newState, inputs.concat(outputs));
	jsPlumb.draggable(newState, {
		/**
		 * It will invoke when a block gets moved around, which is different from the {@link design.templateStopCallback}
		 * This callback will send an AJAX request to report moving information about a block.
		 * If succeed, it does nothing
		 * if failed, it reverts that block back to the original position and displays the message using {@link design.notifyMessage}
		 * @callback blockStopCallback
		 * @param  {Event} event An stop event of template block
		 * @param  {Object} ui An object contains helper, position and offset information.
		 * @memberof design
		 */
		stop: function (event, ui) {
			var currentPos = $(this).offset();
			$.getJSON("/ajax/block/move", {
				project_name: project_name,
				block_id: block_id,
				position: JSON.stringify({
					left: currentPos.left,
					top: currentPos.top
				})
			}, function (data) {
				if (data['result'] == 'error') {
					notifyMessage(data['content']);
					//go back to original position if backend fails to answer
					newState.offset(ui.originalPosition);
				};
			});
		}
	});
	/**
	 * A double click event of a block
	 * This callback will send an AJAX request to report deleting a block.
	 * If succeed, it detach all its connections, which will invoke {@link design.beforeDetachCallback} and also remove itself.
	 * if failed, it displays the message using {@link design.notifyMessage}
	 * @callback blockDeleteCallback
	 * @param  {Event} event An stop event of template block
	 * @param  {Object} ui An object contains helper, position and offset information.
	 * @memberof design
	 */
	$(newState).dblclick(function (event) {
		$.getJSON('/ajax/block/delete', {
			project_name: project_name,
			block_id: $(this).attr('id'),
		}, function (data) {
			if(data['result'] == 'error') {
				notifyMessage(data['content']);
			} else {
				console.log($(newState).attr('id'));
				jsPlumb.detachAllConnections($(newState).attr('id'));
				jsPlumb.removeAllEndpoints($(newState).attr('id'));
				$(newState).remove();
				
			}
		});
		event.stopPropagation();
	});
}

/**
 * this function is used to reconnect all connections for a existing project, must be called after {@link design.loadProject} function
 * @memberof design
 * @param {Array} An array of blocks from loaded project.
 */
function loadProjectConnections (data) {
	for (var i = data.length - 1; i >= 0; i--) {
		var curBlock = data[i];
		if (curBlock['in']) {
			for(var targetPort in curBlock['in']) {
				var source = curBlock['in'][targetPort].split('@');
				if (source.length != 2) {
					break;
				};
				var sourcePort = source[0];
				var sourceId = source[1];
				var targetId = curBlock['uuid'];

				//find the target port
				var targetEnds = jsPlumb.getEndpoints(targetId);
				for (var j = targetEnds.length - 1; j >= 0; j--) {
					if(targetEnds[j].getParameter('dest_port') == targetPort) {
						var targetEP = targetEnds[j];
						break;
					}
				};

				//find the source port
				var sourceEnds = jsPlumb.getEndpoints(sourceId);
				for (var j = sourceEnds.length - 1; j >= 0; j--) {
					if(sourceEnds[j].getParameter('src_port') == sourcePort) {
						var sourceEp = sourceEnds[j];
						break;
					}
				};

				jsPlumb.connect({
					source: sourceEp,
					target: targetEP
				});

			}
		};
	};
}
/**
 * a notification window which is used to show information/alert to user
 * @param  {String} content the content of notification
 * @memberof design
 */
function notifyMessage(content) {
	$('#notification').modal('show').children('.modal-body').text(content);
	window.setTimeout(function () {
		$('#notification').modal('hide');
	} ,1500);
}