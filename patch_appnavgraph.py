import re

with open('app/src/main/java/com/example/ui/AppNavGraph.kt', 'r') as f:
    content = f.read()

content = content.replace('kotlinx.coroutines.tasks.await(auth.signInWithEmailAndPassword(email, password))', 'auth.signInWithEmailAndPassword(email, password).await()')

with open('app/src/main/java/com/example/ui/AppNavGraph.kt', 'w') as f:
    f.write(content)
