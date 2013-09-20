import json

from flask import Flask, render_template, g, request, redirect, url_for, flash
from flask.ext.login import LoginManager, login_required, current_user, login_user, logout_user
from flask.ext.couchdb import CouchDBManager
from flask.ext import restful

import forms
import clojure_format
import couchdb_views
# import rest
from models import User
app = Flask(__name__)
app.secret_key = 'A0Zr98j/3yX R~XHH!jmN]LWX/,?RT'
# fetch the database from the default url and port
couch = CouchDBManager()
loginManager = LoginManager()
api = restful.Api(app)

# api.add_resource(rest.Template, '/ajax/templates')
# api.add_resource(rest.Block, '/ajax/block', '/ajax/block/<string:block_id>')
# api.add_resource(rest.Connection, '/ajax/connection')

@app.route('/ajax/templates')
def templates():
	return json.dumps([row.value for row in couchdb_views.templates_docs(g.couch)])


@app.route('/ajax/block/new')
def block_new():
	username, project_name = get_user_project()
	template_name = request.args['template_name']
	position = request.args['position']
	position = json.loads(position)
	new_block = clojure_format.get_new_block_format(username, project_name, template_name, position)
	# print new_block
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url() ,new_block)


@app.route('/ajax/block/move')
def block_move():
	username, project_name = get_user_project()
	# print username, project_name
	block_id = request.args['block_id']
	# print block_id
	position = request.args['position']
	position = json.loads(position)
	# print position
	move_block = clojure_format.get_move_block_format(username, project_name, block_id, position)
	print move_block
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url() ,move_block)


@app.route('/ajax/block/delete')
def block_delete():
	username, project_name = get_user_project()
	block_id = request.args['block_id']
	delete_block = clojure_format.get_delete_block_format(username, project_name, block_id)
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), delete_block)


@app.route('/ajax/block/connect')
def block_connect():
	username, project_name = get_user_project()
	src_block = request.args['src_block_id']
	src_port = request.args['src_port']
	dest_block = request.args['dest_block_id']
	dest_port = request.args['dest_port']
	connect_block = clojure_format.get_connect_block_format(username, project_name, src_block, src_port, dest_block, dest_port)
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), connect_block)


@app.route('/ajax/block/disconnect')
def block_disconnect():
	username, project_name = get_user_project()
	src_block = request.args['src_block_id']
	src_port = request.args['src_port']
	dest_block = request.args['dest_block_id']
	dest_port = request.args['dest_port']
	disconnect_block = clojure_format.get_disconnect_block_format(username, project_name, src_block, src_port, dest_block, dest_port)
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), disconnect_block)


@app.route('/ajax/project/save')
def save_project():
	username, project_name = get_user_project()
	save_project = clojure_format.get_save_project_format(username, project_name)
	return clojure_format.fetch_from_clojure(clojure_format.get_project_url(), save_project)


@app.route('/ajax/project/load')
def load_project():
	username, project_name = get_user_project()
	cur_project = fetch_project_db(username, project_name).value
	# print dir(cur_project)
	# cur_project.value
	blocks = []
	if cur_project:
		for block_id in cur_project['block_uuid']:
			print block_id
			# tmp = fetch_block_db(block_id)
			# tmp.id
			blocks.append(fetch_block_db(block_id).value)
	return json.dumps({'result': 'success', 'content': blocks})


@app.route('/ajax/code_generate')
def code_generate():
	username, project_name = get_user_project()
	generate_code = clojure_format.get_verilog_generation_format(username, project_name)
	print generate_code
	return clojure_format.fetch_from_clojure(clojure_format.get_design_url(), generate_code)


@app.route('/signup', methods=['GET', 'POST'])
def signup():
	form = forms.SignupForm()
	if form.validate_on_submit():
		#check whether the user is already in the database or not
		if fetch_user_db(form.username.data):
			flash('This username is used')
		else:
			#save the new user right here
			create_user_db(form.username.data, form.password.data)
			return redirect(url_for('login'))
	return render_template('signup.html', form = form)


@app.route('/login', methods=['GET', 'POST'])
def login():
	form = forms.LoginForm()
	if form.validate_on_submit():
		username = form.username.data
		password = form.password.data
		if validate_user_db(username, password):
			login_user(User(username))
			return redirect(url_for("projects"))
		else:
			flash("no such username found or username and password does not match")
	return render_template('login.html', form = form)

@app.route('/logout')
def logout():
	#pop out the username from the session
	logout_user()
	return redirect(url_for('login'))


@app.route('/')
@login_required
def index():
	project_id = request.args.get('project_id')
	if project_id:
		project = g.couch.get(project_id)
		if project:
			return render_template('index.html', name=project['project'])
	return redirect(url_for('projects'))


@app.route('/projects', methods=['GET', 'POST'])
@login_required
def projects():
	if request.method == "GET":
		cur_projects = fetch_projects_db(current_user.get_id())
		return render_template('projects.html', username=current_user.get_id(), projects=cur_projects)
	else:
		project_action = request.form['project_action']
		project_name = request.form['project_name']
		# print project_name
		username = current_user.get_id()
		#load a old project
		if project_action == 'open':
			load_project = clojure_format.get_load_project_format(username, project_name)
			response = clojure_format.fetch_from_clojure(clojure_format.get_project_url(), load_project)
		else:
			#new project
			new_project = clojure_format.get_new_project_format(username, project_name)
			response = clojure_format.fetch_from_clojure(clojure_format.get_project_url(), new_project)
		response = json.loads(response)
		print response
		if response['result'] == 'success':
			project_id = response['content']
		else:
			return response['content']
		return redirect(url_for("index", **{"project_id": project_id}))


def get_user_project():
	return current_user.get_id(), request.args['project_name']


@loginManager.user_loader
def load_user(id):
	user = fetch_user_db(id)
	if user:
		return User(id)
	else:
		return None


def create_user_db(username, password):
	g.couch.save({
		'type': 'user',
		'name': username,
		'password': password
	})


def validate_user_db(username, password):
	profile = fetch_user_db(username)
	if profile and profile['password'] == password:
		return True
	else:
		return False


def fetch_user_db(username):
	profile = couchdb_views.users_docs(g.couch)[username]
	if len(profile.rows) == 1:
		profile = profile.rows[0]
		return {"id": profile.id, "username": profile.value['username'], "password": profile.value['password']}
	return None


def fetch_projects_db(username):
	cur_projects = couchdb_views.projects_docs(g.couch)[username]
	if cur_projects:
		return [row.value for row in cur_projects.rows]
	return []


def fetch_project_db(username, project):
	cur_project = couchdb_views.project_docs(g.couch)[username, project]
	if len(cur_project.rows) != 1:
		return None
	else:
		return cur_project.rows[0]


def fetch_block_db(block_uuid):
	cur_block = couchdb_views.project_content_docs(g.couch)[block_uuid]
	# cur_block.id
	if len(cur_block.rows) != 1:
		return None
	else:
		return cur_block.rows[0]


if __name__ == '__main__':
    app.config.update(
        DEBUG = True,
        COUCHDB_SERVER = 'http://localhost:5984/',
        COUCHDB_DATABASE = 'nerf-db'
    )
    couch.setup(app)
    couch.add_viewdef((couchdb_views.templates_docs, couchdb_views.users_docs,
    	couchdb_views.projects_docs, couchdb_views.project_docs, couchdb_views.project_content_docs))
    loginManager.login_view = 'login'
    loginManager.setup_app(app)
    app.run(host='0.0.0.0')