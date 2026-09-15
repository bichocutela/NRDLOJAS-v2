from pathlib import Path

# 1) Reinicia o listener de produtos quando o estado de autenticação Firebase mudar.
service_path = Path("app/src/main/java/com/example/data/FirebaseService.kt")
service = service_path.read_text(encoding="utf-8")
start_marker = "    fun observeProductUsage(): Flow<List<GlobalProductUsage>> = callbackFlow {"
end_marker = "\n    fun observeProducts(): Flow<List<com.example.data.Product>> ="
start = service.find(start_marker)
end = service.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("Não foi possível localizar observeProductUsage()")

new_observer = '''    fun observeProductUsage(): Flow<List<GlobalProductUsage>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            close()
            return@callbackFlow
        }

        val firestore = FirebaseFirestore.getInstance()
        var registration: com.google.firebase.firestore.ListenerRegistration? = null

        fun startProductsListener() {
            registration?.remove()
            registration = firestore.collection("products")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseService", "Error in observeProducts", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val products = snapshot.documents.mapNotNull { doc ->
                            val code = doc.getString("code") ?: return@mapNotNull null
                            val name = doc.getString("name") ?: ""
                            val searchName = doc.getString("searchName") ?: ""
                            val category = doc.getString("category") ?: ""
                            val unit = doc.getString("unit") ?: "un"
                            val imageUrl = doc.getString("imageUrl")
                            val searchCount = doc.getLong("searchCount")?.toInt() ?: 0
                            GlobalProductUsage(
                                product = com.example.data.Product(
                                    code = code,
                                    name = name,
                                    searchName = searchName,
                                    category = category,
                                    unit = unit,
                                    imageUrl = imageUrl,
                                    searchCount = searchCount
                                ),
                                lastViewedAt = when (val viewedAt = doc.get("lastViewedAt")) {
                                    is com.google.firebase.Timestamp -> viewedAt.toDate().time
                                    is Number -> viewedAt.toLong()
                                    else -> null
                                },
                                createdAt = when (val addedAt = doc.get("createdAt") ?: doc.get("timestamp")) {
                                    is com.google.firebase.Timestamp -> addedAt.toDate().time
                                    is Number -> addedAt.toLong()
                                    else -> null
                                }
                            )
                        }
                        trySend(products)
                    }
                }
        }

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        var observedUid = auth.currentUser?.uid
        startProductsListener()
        val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { state ->
            val newUid = state.currentUser?.uid
            if (newUid != observedUid) {
                observedUid = newUid
                Log.d("FirebaseService", "Sessão Firebase mudou; reiniciando listener do catálogo")
                startProductsListener()
            }
        }
        auth.addAuthStateListener(authListener)

        awaitClose {
            registration?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }
'''
service = service[:start] + new_observer + service[end:]
service_path.write_text(service, encoding="utf-8")

# 2) Mantém diálogos de produto abertos vinculados à versão mais nova do catálogo local.
search_path = Path("app/src/main/java/com/example/ui/SearchScreen.kt")
search = search_path.read_text(encoding="utf-8")
products_anchor = '    val activeCategoryNames by viewModel.activeCategoryNames.collectAsStateWithLifecycle()\n'
if products_anchor not in search:
    raise SystemExit("Âncora de allProducts não encontrada")
search = search.replace(
    products_anchor,
    products_anchor + '    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()\n',
    1
)

state_anchor = '''    var showNotificationsSheet by remember { mutableStateOf(false) }\n    var selectedNotificationProduct by remember { mutableStateOf<Product?>(null) }\n'''
if state_anchor not in search:
    raise SystemExit("Âncora dos produtos selecionados não encontrada")
state_replacement = state_anchor + '''\n    LaunchedEffect(allProducts) {\n        selectedMostUsedProduct = selectedMostUsedProduct?.let { selected ->\n            allProducts.firstOrNull { it.code == selected.code } ?: selected\n        }\n        selectedNotificationProduct = selectedNotificationProduct?.let { selected ->\n            allProducts.firstOrNull { it.code == selected.code } ?: selected\n        }\n    }\n'''
search = search.replace(state_anchor, state_replacement, 1)
search_path.write_text(search, encoding="utf-8")
