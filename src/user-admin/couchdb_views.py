from couchdb.design import ViewDefinition

#template view
templates_docs = ViewDefinition('templates', "cur_templates", """
		function (doc) {
			if(doc.type == "template") {
				emit(doc.name, {"name": doc.name, "in": doc.in, "out": doc.out, "height": doc.height});
			};
		}
	""")
#user view
users_docs = ViewDefinition('users', "cur_users", """
		function (doc) {
			if(doc.type == "user") {
				emit(doc.name, {"username": doc.name, "password": doc.password});
			};
		}
	""")

projects_docs = ViewDefinition('design-view', "design-temp", """
		function (doc) {
			if(doc.type == "design-hash") {
				emit(doc.user, {"name": doc.project});
			};
		}
	""")


project_docs = ViewDefinition('design-view', "design-hash2", """
		function (doc) {
			if(doc.type == "design-hash") {
				emit([doc.user, doc.project], doc);
			};
		}
	""")


project_content_docs = ViewDefinition('design-view', 'design-content2', """
		function(doc) {
			if(doc.type == "design-content") {
				emit(doc.uuid, doc);
			};
		}
	""")