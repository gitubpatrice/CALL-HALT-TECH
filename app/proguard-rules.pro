# R8 rules for Call Halt Tech.
#
# Empty on purpose: Hilt, Room and Compose ship their own consumer rules, and nothing in the app is reached
# by reflection yet. A rule is added WITH the code that needs it, saying which class is built by reflection
# and by whom — Agenda Tech published a widget that stayed blank because R8 removed a constructor that
# WorkManager builds by reflection (issue #37), while every check was green.
