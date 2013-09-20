import json

from flask import g
from flask.ext.login import current_user
from flask.ext.restful import Resource, reqparse, abort

import couchdb_views
import clojure_format

class Project(Resource):
	def put(self):
		pass

class Template(Resource):
	def get(self):
		return json.dumps([row.value for row in couchdb_views.templates_docs(g.couch)])


class Block(Resource):
	def post(self):
		parser = reqparse.RequestParser()
		parser.add_argument('project_name')
		parser.add_argument('template_name')
		parser.add_argument('position')
		args = parser.parse_args()
		args['position'] = json.loads(args['position'])
		# return json.dumps(args)
		new_block = clojure_format.get_new_block_format(current_user.get_id(), **args)
		# return json.dumps(new_block)
		try:
			return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), new_block)
		except IOError:
			abort(404)

	def put(self, block_id = None):
		if block_id == None:
			return
		parser = reqparse.RequestParser()
		parser.add_argument('project_name')
		parser.add_argument('position')
		args = parser.parse_args()
		args['position'] = json.loads(args['position'])
		args['block_id'] = block_id
		move_block = clojure_format.get_move_block_format(current_user.get_id(), **args)
		try:
			return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), move_block)
		except IOError:
			abort(404)

	def delete(self, block_id = None):
		if block_id == None:
			return
		parser = reqparse.RequestParser()
		parser.add_argument('project_name')
		args = parser.parse_args()
		args['block_id'] = block_id
		delete_block = clojure_format.get_delete_block_format(current_user.get_id(), **args)
		try:
			return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), delete_block)
		except IOError:
			abort(404)

class Connection(Resource):
	def post(self):
		parser = reqparse.RequestParser()
		parser.add_argument('project_name')
		parser.add_argument('src_block_id')
		parser.add_argument('src_port')
		parser.add_argument('dest_block_id')
		parser.add_argument('dest_port')
		args = parser.parse_args()
		connect_block = clojure_format.get_connect_block_format(current_user.get_id(), **args)
		try:
			return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), connect_block)
		except IOError:
			abort(404)

	def delete(self):
		parser = reqparse.RequestParser()
		parser.add_argument('project_name')
		parser.add_argument('src_block_id')
		parser.add_argument('src_port')
		parser.add_argument('dest_block_id')
		parser.add_argument('dest_port')
		args = parser.parse_args()
		disconnect_block = clojure_format.get_disconnect_block_format(current_user.get_id(), **args)
		try:
			return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), disconnect_block)
		except IOError:
			abort(404)

