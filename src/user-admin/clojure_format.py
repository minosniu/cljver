import urllib
import json
from flask import abort

_clojure_engine_base_url = "http://localhost:3000/"

def fetch_from_clojure(url, data):
	# print data
	params = urllib.urlencode(data)
	# print params
	try:
		response = urllib.urlopen(url, params)
		tmp = response.read()
		# print tmp
		return tmp
	except IOError:
		abort(404)

def get_project_url():
	return _clojure_engine_base_url + 'project'


def get_design_url():
	return _clojure_engine_base_url + 'design'


def _get_base_format(user, project):
	base_format = {
		'user': user,
		'project': project
	}
	return base_format


def get_new_project_format(user, project):
	new_project = {
		'action': 'new'
	}
	new_project.update(_get_base_format(user, project))
	return new_project


def get_load_project_format(user, project):
	load_project = {
		'action': 'load'
	}
	load_project.update(_get_base_format(user, project))
	return load_project


def get_save_project_format(user, project):
	save_project = get_new_project_format(user, project)
	save_project['action'] = 'save'
	return save_project


def get_new_block_format(user, project_name, template_name, position):
	new_block = {
		'action': 'new',
		'data': json.dumps({
			'template': template_name,
			'position': position
		})
	}
	new_block.update(_get_base_format(user, project_name))
	return new_block


def get_move_block_format(user, project_name, block_id, position):
	move_block = {
		'action': 'move',
		'data': json.dumps({
			'block': block_id,
			'position': position
		})	
	}
	move_block.update(_get_base_format(user, project_name))
	return move_block


def get_delete_block_format(user, project_name, block_id):
	delete_block = {
		'action': 'delete',
		'data': json.dumps({
			'block': block_id,
		})
	}
	delete_block.update(_get_base_format(user, project_name))
	return delete_block


def get_connect_block_format(user, project_name, src_block_id, src_port, dest_block_id, dest_port):
	connect_block = {
		'action': 'connect',
		'data': json.dumps({
			'src': {
				'block': src_block_id,
				'port': src_port
			},
			'dest': {
				'block': dest_block_id,
				'port': dest_port
			}
		})
	}
	connect_block.update(_get_base_format(user, project_name))
	return connect_block


def get_disconnect_block_format(user, project_name, src_block_id, src_port, dest_block_id, dest_port):
	disconnect_block = get_connect_block_format(user, project_name, src_block_id, src_port, dest_block_id, dest_port)
	disconnect_block['action'] = 'disconnect'
	return disconnect_block


def get_verilog_generation_format(user, project):
	verilog_generation = {
		'action': 'generate',
		'data': json.dumps({})
	}
	verilog_generation.update(_get_base_format(user, project))
	return verilog_generation