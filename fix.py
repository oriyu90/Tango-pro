import re

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

# remove everything that looks like duplicate val newGroup ...
text = re.sub(r'(val newGroup = wordDao.getGroupById\([^\)]+\)\s+withContext\(Dispatchers\.Main\) \{\s+isImporting = false\s+if \(newGroup != null\) \{\s+changeSelectedGroup\(newGroup\)\s+onComplete\(true, "[^\"]+"\)\s+\} else \{\s+onComplete\(false, "[^\"]+"\)\s+\}\s+\})\s+val newGroup = wordDao.getGroupById\([^\)]+\)\s+withContext\(Dispatchers\.Main\) \{\s+isImporting = false\s+if \(newGroup != null\) \{\s+changeSelectedGroup\(newGroup\)\s+onComplete\(true, "[^\"]+"\)\s+\} else \{\s+onComplete\(false, "[^\"]+"\)\s+\}\s+\}', r'\1', text, flags=re.MULTILINE)

with open("app/src/main/java/com/example/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)

