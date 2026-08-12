sed -i '/Button(/,/Spacer(modifier = Modifier.height(16.dp))/d' app/src/main/java/com/example/ui/MestreScreen.kt
sed -i '/val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()/d' app/src/main/java/com/example/ui/MestreScreen.kt
sed -i '/val snackbarHostState = remember { SnackbarHostState() }/,/}/d' app/src/main/java/com/example/ui/MestreScreen.kt
sed -i '/snackbarHost = { SnackbarHost(snackbarHostState) },/d' app/src/main/java/com/example/ui/MestreScreen.kt
