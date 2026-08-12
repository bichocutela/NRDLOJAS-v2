import re
content = open("app/src/main/java/com/example/ui/MainViewModel.kt").read()

# Fields to extract:
# private var isSyncingTabs = false
# private val _syncMessage = MutableSharedFlow<String>()
# val syncMessage = _syncMessage.asSharedFlow()
# private val _isSyncing = MutableStateFlow(false)
# val isSyncing = _isSyncing.asStateFlow()

syncing_tabs = r"\s*private var isSyncingTabs = false\n"
content = re.sub(syncing_tabs, "\n", content)

sync_msg = r"\s*private val _syncMessage = MutableSharedFlow<String>\(\)\n\s*val syncMessage = _syncMessage\.asSharedFlow\(\)\n"
content = re.sub(sync_msg, "\n", content)

is_syncing = r"\s*private val _isSyncing = MutableStateFlow\(false\)\n\s*val isSyncing = _isSyncing\.asStateFlow\(\)\n"
content = re.sub(is_syncing, "\n", content)

# insert them before init
insert_pos = content.find("init {")
if insert_pos != -1:
    to_insert = """
    private var isSyncingTabs = false
    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage = _syncMessage.asSharedFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    """
    content = content[:insert_pos] + to_insert + content[insert_pos:]
    
open("app/src/main/java/com/example/ui/MainViewModel.kt", "w").write(content)
