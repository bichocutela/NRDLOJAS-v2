from pathlib import Path

screen_path = Path("app/src/main/java/com/example/ui/PromotionsScreen.kt")
text = screen_path.read_text(encoding="utf-8")

old = "import com.example.data.NossaGenteApi\n"
new = "import com.example.data.NossaGenteApi\nimport com.example.data.NossaGenteCredentialStore\n"
if "import com.example.data.NossaGenteCredentialStore\n" not in text:
    assert old in text
    text = text.replace(old, new, 1)

old = '''    var cpf by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api) {
        if (api.hasSession()) {
            onLoginSuccess()
        }
    }
'''
new = '''    var cpf by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saveCredentials by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialStore = remember(context) { NossaGenteCredentialStore(context.applicationContext) }

    LaunchedEffect(credentialStore) {
        val saved = withContext(Dispatchers.IO) { credentialStore.load() }
        if (saved != null) {
            cpf = saved.cpf
            password = saved.password
            saveCredentials = true
        }
    }

    LaunchedEffect(api) {
        if (api.hasSession()) {
            onLoginSuccess()
        }
    }
'''
if "var saveCredentials by remember" not in text:
    assert old in text
    text = text.replace(old, new, 1)

old = '''            Text(
                "Use seu CPF e sua senha do Nossa Gente. A senha é usada somente nesta autenticação e não é salva no aparelho.",
                style = MaterialTheme.typography.bodyMedium
            )
'''
new = '''            Text(
                "Use seu CPF e sua senha do Nossa Gente. Se quiser, você pode salvar o acesso neste aparelho para não precisar digitar toda vez.",
                style = MaterialTheme.typography.bodyMedium
            )
'''
if old in text:
    text = text.replace(old, new, 1)

old = '''            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
'''
new = '''            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = saveCredentials,
                    onCheckedChange = { checked ->
                        saveCredentials = checked
                        if (!checked) {
                            scope.launch(Dispatchers.IO) { credentialStore.clear() }
                        }
                    }
                )
                Text(
                    text = "Salvar CPF e senha neste aparelho",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "O acesso salvo fica criptografado localmente neste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
'''
if "Salvar CPF e senha neste aparelho" not in text:
    assert old in text
    text = text.replace(old, new, 1)

old = '''                            NossaGenteLoginResult.Success -> {
                                password = ""
                                if (api.hasSession()) {
                                    onLoginSuccess()
                                } else {
                                    error = "A sessão não ficou disponível. Tente novamente."
                                }
                            }
'''
new = '''                            NossaGenteLoginResult.Success -> {
                                if (api.hasSession()) {
                                    if (saveCredentials) {
                                        withContext(Dispatchers.IO) {
                                            credentialStore.save(cpf = cpf, password = password)
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) { credentialStore.clear() }
                                        password = ""
                                    }
                                    onLoginSuccess()
                                } else {
                                    error = "A sessão não ficou disponível. Tente novamente."
                                }
                            }
'''
if "credentialStore.save(cpf = cpf, password = password)" not in text:
    assert old in text
    text = text.replace(old, new, 1)

screen_path.write_text(text, encoding="utf-8")
print("Patch aplicado com sucesso.")
