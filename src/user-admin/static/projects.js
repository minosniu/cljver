/**
 * @file manages logic of project page
 * @author Zhoutuo Yang <zhoutuoy@gmail.com>
 */

/** 
 * @namespace project
 */

/**
 * This callback will be called when DOM is ready and register all button events of project page
 * @callback DOMReadyCallback
 * @memberof project
 */
$(document).ready(function () {
	/**
	 * A click event of selecting old projects
	 * It will try to de-select selected project first, then select the project just got clicked
	 * @callback selectClickCallback
	 * @memberof project	 
	 */
	$("select").on("click", "option", function () {
		if($(this).siblings(":selected").length > 0) {
			$(this).removeAttr("selected");
		};
	});

	$("#open_project").popover({
		title: "Error",
		content: "Choose one project to open",
		trigger: "manual"
	});

	$('#create_project').popover({
		title: "Error",
		content: "New project's name cannot be empty",
		trigger: 'manual'
	});
	/**
	 * A click event of open project button, which is used for loading old projects
	 * If there is no loading selected, it will pop up a notification for 1 sec
	 * else it will modify {@link needsLoading} to true and project page action to "open"
	 * @callback projectOpenClickCallback
	 * @param  {Event} eventHandler A mouse click event information
	 * @memberof project
	 */
	$("#open_project").click(function (eventHandler) {
		if ($(this).siblings("select").children(":selected").length == 0) {
			eventHandler.preventDefault();
			$(this).popover("show");
			window.setTimeout(function () {
				$("#open_project").popover("hide")
			}, 1000);
		} else {
			needsLoading = true;
			$('#project_action').val("open");
		}
	});

	/**
	 * A click event of open project button, which is used for creating new projects
	 * If there is no new name specified, it will pop up a notification for 1 sec
	 * else it will modify {@link needsLoading} to false and project page action to "create"
	 * @callback projectCreateClickCallback
	 * @param  {Event} eventHandler A mouse click event information
	 * @memberof project
	 */
	$('#create_project').click(function (eventHandler) {
		if($('#project_new_name').val() === "") {
			eventHandler.preventDefault();
			$(this).popover("show");
			window.setTimeout(function () {
				$("#create_project").popover("hide")
			}, 1000);
		} else {
			needsLoading = false;
			$('#project_action').val("create");
		}
	});

});