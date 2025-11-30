package com.example.evokeraa

import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val importer = ZipImporter(db)
        val settings = SettingsManager(this)

        setContent {
            val isDark by settings.isDarkMode.collectAsState()
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                EvokerApp(db, importer, settings)
            }
        }
    }
}

@Composable
fun EvokerApp(db: AppDatabase, importer: ZipImporter, settings: SettingsManager) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("dashboard") }
    var activeChatId by remember { mutableStateOf("") }
    var jumpToMsgId by remember { mutableStateOf<Long?>(null) }

    // If drawer is open, back button closes it
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Evoker Archive", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = currentScreen == "dashboard",
                    onClick = { currentScreen = "dashboard"; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Home, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Statistics") },
                    selected = currentScreen == "stats",
                    onClick = { currentScreen = "stats"; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.AccountCircle, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = currentScreen == "settings",
                    onClick = { currentScreen = "settings"; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Settings, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        when (currentScreen) {
            "dashboard" -> DashboardScreen(db, importer, settings,
                onNavigateToChat = { chatId, msgId ->
                    activeChatId = chatId
                    jumpToMsgId = msgId
                    currentScreen = "chat"
                },
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
            "settings" -> SettingsScreen(settings) { scope.launch { drawerState.open() } }
            "stats" -> StatisticsScreen(db) { scope.launch { drawerState.open() } }
            "chat" -> ChatScreen(db, activeChatId, jumpToMsgId) { currentScreen = "dashboard" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(db: AppDatabase, onOpenDrawer: () -> Unit) {
    val totalMsgs by db.dao().getTotalMessageCount().collectAsState(initial = 0)
    val totalContacts by db.dao().getTotalContactCount().collectAsState(initial = 0)
    val topContacts by db.dao().getTop5Contacts().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Cards Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Total Messages", "$totalMsgs", Icons.Default.MailOutline, Modifier.weight(1f))
                StatCard("Total Contacts", "$totalContacts", Icons.Default.Person, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Top Friends", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                items(topContacts) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { Text("${contact.messageCount} messages") },
                        leadingContent = {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(contact.displayName.take(1))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase, importer: ZipImporter, settings: SettingsManager, onNavigateToChat: (String, Long?) -> Unit, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by db.dao().getAllContacts().collectAsState(initial = emptyList())
    val myAliases by settings.aliases.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Search Sections State
    var showContacts by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }
    var showMessages by remember { mutableStateOf(true) }
    var messageResults by remember { mutableStateOf<List<Message>>(emptyList()) }

    // Dynamic Search for Messages
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            messageResults = db.dao().searchMessages(searchQuery)
        } else {
            messageResults = emptyList()
        }
    }

    var showManualDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf("") }

    // Smart Import Launcher (Uses Saved Aliases)
    val smartLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                importer.importData(context, uri, myAliases.toList(), "auto") { importStatus = it }
                isImporting = false
                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Manual Import Launcher (Uses Typed Name)
    var pendingUsername by remember { mutableStateOf("") }
    val manualLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                importer.importData(context, uri, listOf(pendingUsername), "auto") { importStatus = it }
                isImporting = false
                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Evoker v10") },
                    navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") } }
                )
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // SMART LOGIC: If aliases exist, skip dialog.
                if (myAliases.isNotEmpty()) {
                    smartLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                } else {
                    showManualDialog = true
                }
            }) { Icon(Icons.Default.Add, "Import") }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (searchQuery.isEmpty()) {
                // STANDARD LIST
                LazyColumn {
                    items(contacts.filter { !it.isHidden }) { contact ->
                        ContactRow(contact) { onNavigateToChat(contact.id, null) }
                    }
                }
            } else {
                // COLLAPSIBLE SEARCH RESULTS
                LazyColumn {
                    // 1. Contacts
                    val matchedContacts = contacts.filter {
                        it.displayName.contains(searchQuery, true) || it.nickname?.contains(searchQuery, true) == true
                    }
                    if (matchedContacts.isNotEmpty()) {
                        item { SectionHeader("Contacts (${matchedContacts.size})", showContacts) { showContacts = !showContacts } }
                        if (showContacts) {
                            items(matchedContacts) { c -> ContactRow(c) { onNavigateToChat(c.id, null) } }
                        }
                    }

                    // 2. Tags
                    val matchedTags = contacts.filter { it.tags.contains(searchQuery, true) }
                    if (matchedTags.isNotEmpty()) {
                        item { SectionHeader("Tags (${matchedTags.size})", showTags) { showTags = !showTags } }
                        if (showTags) {
                            items(matchedTags) { c -> ContactRow(c) { onNavigateToChat(c.id, null) } }
                        }
                    }

                    // 3. Messages
                    if (messageResults.isNotEmpty()) {
                        item { SectionHeader("Messages (${messageResults.size})", showMessages) { showMessages = !showMessages } }
                        if (showMessages) {
                            items(messageResults) { msg ->
                                MessageResultRow(msg) { onNavigateToChat(msg.chatId, msg.id) }
                            }
                        }
                    }
                }
            }
            // THE NEW BLOCKER
            if (isImporting) {
                ProcessingOverlay(status = importStatus)
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Who are you?") },
            text = {
                Column {
                    Text("We don't know your username yet. Enter it here to identify your messages.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = pendingUsername, onValueChange = { pendingUsername = it }, label = { Text("Username") })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tip: Add usernames in Settings to skip this.", fontSize = 10.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pendingUsername.isNotBlank()) {
                        showManualDialog = false
                        manualLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                    }
                }) { Text("Select File") }
            },
            dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SectionHeader(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.3f)), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(if(isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle")
    }
}

@Composable
fun MessageResultRow(msg: Message, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp, 4.dp)) {
        Text(msg.chatId, fontSize = 10.sp, color = Color.Gray)
        Text(msg.content, maxLines = 1)
        HorizontalDivider(modifier = Modifier.padding(top=4.dp))
    }
}

@Composable
fun SettingsScreen(settings: SettingsManager, onOpenDrawer: () -> Unit) {
    var apiKey by remember { mutableStateOf(settings.getApiKey()) }
    val isDark by settings.isDarkMode.collectAsState()
    val aliases by settings.aliases.collectAsState()
    var newAlias by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("My Personas (Usernames)", style = MaterialTheme.typography.titleMedium)
        Text("Add all your usernames (Insta, Snap, etc.) here. The app will use these to identify 'You' during import.", fontSize = 12.sp, color = Color.Gray)

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(value = newAlias, onValueChange = { newAlias = it }, modifier = Modifier.weight(1f), placeholder = { Text("e.g. bcasefire") }, singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if(newAlias.isNotBlank()) { settings.addAlias(newAlias); newAlias = "" }
            }) { Text("Add") }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(aliases.toList()) { alias ->
                InputChip(
                    selected = true,
                    onClick = { settings.removeAlias(alias) },
                    label = { Text(alias) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text("Gemini API Key")
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(onClick = { settings.saveApiKey(apiKey) }, modifier = Modifier.align(Alignment.End)) { Text("Save Key") }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Dark Mode", modifier = Modifier.weight(1f))
            Switch(checked = isDark, onCheckedChange = { settings.setDarkMode(it) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(db: AppDatabase, chatId: String, jumpToMsgId: Long?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val contacts by db.dao().getAllContacts().collectAsState(initial = emptyList())
    val contact = contacts.find { it.id == chatId }

    val allChatIds = remember(contact) {
        val list = mutableListOf(chatId)
        if (!contact?.mergedWith.isNullOrEmpty()) list.addAll(contact!!.mergedWith.split(","))
        list
    }

    val messages by db.dao().getMessagesForIds(allChatIds).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    // FILTER LOGIC
    var isSearchMode by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var smartFilterEnabled by remember { mutableStateOf(false) }

    // 1. First Pass: Apply Smart Filter (Actually Hide Items)
    val filteredMessages = remember(messages, smartFilterEnabled) {
        messages.filter { msg ->
            if (smartFilterEnabled) {
                val txt = msg.content.lowercase()
                // SMART FILTER RULESET:
                if (txt.contains("reacted") || txt.contains("liked a message") || txt.contains("unsent") || txt.contains("theme to") || txt.isEmpty()) return@filter false
            }
            true
        }
    }

    // 2. Second Pass: Search Matching (Find indices, don't hide)
    val searchMatches = remember(filteredMessages, inChatQuery) {
        if (inChatQuery.isEmpty()) emptyList()
        else filteredMessages.mapIndexedNotNull { index, msg ->
            if (msg.content.contains(inChatQuery, true)) index else null
        }
    }

    var currentMatchIndex by remember { mutableStateOf(-1) }

    // JUMP LOGIC (External & Internal)
    LaunchedEffect(filteredMessages, jumpToMsgId) {
        if (jumpToMsgId != null && filteredMessages.isNotEmpty()) {
            val index = filteredMessages.indexOfFirst { it.id == jumpToMsgId }
            if (index != -1) listState.scrollToItem(index)
        } else if (filteredMessages.isNotEmpty() && jumpToMsgId == null && !isSearchMode) {
            listState.scrollToItem(filteredMessages.size - 1)
        }
    }

    // SEARCH NAV LOGIC
    fun jumpToMatch(direction: Int) {
        if (searchMatches.isEmpty()) return
        var newIndex = currentMatchIndex + direction
        if (newIndex >= searchMatches.size) newIndex = 0
        if (newIndex < 0) newIndex = searchMatches.size - 1
        currentMatchIndex = newIndex
        scope.launch { listState.scrollToItem(searchMatches[newIndex]) }
    }

    var showActionMenu by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showContactInfo by remember { mutableStateOf(false) }

    var smartTags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val rawTags = db.dao().getAllTags()
        val unique = mutableSetOf<String>()
        rawTags.forEach { t -> t.split(",").forEach { if(it.isNotBlank()) unique.add(it.trim()) } }
        smartTags = unique.toList().sorted()
    }

    Scaffold(
        topBar = {
            if (isSearchMode) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = inChatQuery,
                                onValueChange = { inChatQuery = it; currentMatchIndex = -1 },
                                placeholder = { Text("Find in chat...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            // NAV ARROWS
                            IconButton(onClick = { jumpToMatch(-1) }) { Icon(Icons.Default.KeyboardArrowUp, "Prev") }
                            IconButton(onClick = { jumpToMatch(1) }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
                            Text("${if(currentMatchIndex>=0) currentMatchIndex+1 else 0}/${searchMatches.size}", fontSize = 12.sp, modifier = Modifier.padding(horizontal=4.dp))
                        }
                    },
                    navigationIcon = { IconButton(onClick = { isSearchMode = false; inChatQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(contact?.nickname ?: contact?.displayName ?: chatId)
                            if (allChatIds.size > 1) Text("Merged: ${allChatIds.size} sources", fontSize = 10.sp, color = Color.Magenta)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { smartFilterEnabled = !smartFilterEnabled }) {
                            Icon(
                                imageVector = if(smartFilterEnabled) Icons.Default.Close else Icons.Default.List,
                                contentDescription = "Filter",
                                tint = if(smartFilterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isSearchMode = true }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { showActionMenu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                        DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { showRenameDialog = true; showActionMenu = false })
                            DropdownMenuItem(text = { Text("Edit Tags") }, onClick = { showTagDialog = true; showActionMenu = false })
                            DropdownMenuItem(text = { Text("Link Account") }, onClick = { showMergePicker = true; showActionMenu = false })
                            DropdownMenuItem(text = { Text("Info") }, onClick = { showContactInfo = true; showActionMenu = false })
                            DropdownMenuItem(
                                text = { Text(if (contact?.isPinned == true) "Unpin" else "Pin") },
                                onClick = { scope.launch { db.dao().setPinned(chatId, !(contact?.isPinned ?: false)) }; showActionMenu = false }
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(filteredMessages) { index, msg ->
                val isMatch = inChatQuery.isNotEmpty() && msg.content.contains(inChatQuery, true)
                val isCurrentMatch = isMatch && (searchMatches.getOrNull(currentMatchIndex) == index)

                MessageBubble(msg, highlight = isMatch, activeHighlight = isCurrentMatch)
            }
        }
    }

    if (showMergePicker) {
        var mergeSearch by remember { mutableStateOf("") }
        val candidates = contacts.filter {
            it.id != chatId && !allChatIds.contains(it.id) && (it.displayName.contains(mergeSearch, true) || it.id.contains(mergeSearch, true))
        }
        AlertDialog(
            onDismissRequest = { showMergePicker = false },
            title = { Text("Link Account") },
            text = {
                Column(modifier = Modifier.height(400.dp)) {
                    OutlinedTextField(value = mergeSearch, onValueChange = { mergeSearch = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn {
                        items(candidates) { candidate ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                val currentMerged = contact?.mergedWith ?: ""
                                val newMerged = if (currentMerged.isEmpty()) candidate.id else "$currentMerged,${candidate.id}"
                                scope.launch { db.dao().linkContacts(chatId, newMerged); db.dao().setHidden(candidate.id, true) }
                                showMergePicker = false
                            }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(candidate.displayName) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMergePicker = false }) { Text("Close") } }
        )
    }
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(contact?.nickname ?: "") }
        AlertDialog(onDismissRequest={showRenameDialog=false}, title={Text("Rename")}, text={OutlinedTextField(value=newName, onValueChange={newName=it}, label={Text("Nickname")})}, confirmButton={Button(onClick={scope.launch{db.dao().updateNickname(chatId,newName)};showRenameDialog=false}){Text("Save")}})
    }
    if (showTagDialog) {
        var tags by remember { mutableStateOf(contact?.tags ?: "") }
        AlertDialog(onDismissRequest={showTagDialog=false}, title={Text("Tags")}, text={Column{OutlinedTextField(value=tags, onValueChange={tags=it}, label={Text("Tags")}); if(smartTags.isNotEmpty()){Text("Quick Add:",fontSize=10.sp);LazyRow{items(smartTags){tag->FilterChip(selected=false,onClick={tags=if(tags.isEmpty())tag else "$tags, $tag"},label={Text(tag)})}}}}}, confirmButton={Button(onClick={scope.launch{db.dao().updateTags(chatId,tags)};showTagDialog=false}){Text("Save")}})
    }
    if (showContactInfo) {
        AlertDialog(onDismissRequest={showContactInfo=false}, title={Text("Info")}, text={Column{Text("Main ID: $chatId"); Text("Linked: ${contact?.mergedWith?:"None"}")}}, confirmButton={TextButton(onClick={showContactInfo=false}){Text("Close")}})
    }
}

@Composable
fun ContactRow(contact: Contact, onClick: () -> Unit) {
    val isMergedParent = contact.mergedWith.isNotEmpty()
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp).background(if(contact.isHidden) Color.LightGray.copy(alpha=0.1f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if(isMergedParent) Color(0xFFE0B0FF) else MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(contact.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.nickname ?: contact.displayName, fontWeight = FontWeight.SemiBold)
                if (contact.isPinned) Icon(Icons.Default.Star, "Pinned", modifier = Modifier.size(14.dp), tint = Color(0xFFFFD700))
                if (isMergedParent) Icon(Icons.Default.Share, "Merged", modifier = Modifier.size(14.dp), tint = Color.Magenta)
            }
            if (contact.nickname != null) Text("(${contact.displayName})", fontSize = 10.sp, color = Color.Gray)
            if (contact.tags.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    contact.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(end = 4.dp)) {
                            Text(tag, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, highlight: Boolean = false, activeHighlight: Boolean = false) {
    val isMe = message.isFromMe
    val bgColor = when {
        activeHighlight -> Color(0xFFFFD700)
        highlight -> Color(0xFFFFFACD)
        isMe -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        highlight || activeHighlight -> Color.Black
        isMe -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        if (message.platform.isNotEmpty()) {
            Text(text = message.platform.uppercase(), fontSize = 8.sp, color = Color.LightGray, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMe) 16.dp else 4.dp, bottomEnd = if (isMe) 4.dp else 16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.content, color = textColor)
                val date = Date(message.timestamp)
                val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                Text(text = format.format(date), fontSize = 10.sp, color = textColor.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
fun ProcessingOverlay(status: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp)
            Spacer(modifier = Modifier.height(32.dp))
            Text("Processing Archive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Please do not close the app", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}