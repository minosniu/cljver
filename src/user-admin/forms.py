from flask.ext.wtf import Form
from wtforms import TextField, PasswordField
from wtforms.validators import DataRequired, Length, EqualTo


class SignupForm(Form):
	username = TextField('Username', [DataRequired(), Length(5)])
	password = PasswordField('Password', [DataRequired(), Length(5), EqualTo('confirm_password', message='Passwords must match')])
	confirm_password = PasswordField('Repeat Password')


class LoginForm(Form):
	username = TextField('Username', [DataRequired()])
	password = PasswordField('Password', [DataRequired()])

