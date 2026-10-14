package com.arjun.capture

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.arjun.capture.ui.theme.CaptureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// --- DATA MODELS ---
data class Subject(val id: String, val name: String, val columns: Map<Int, List<String>>)

// --- STORAGE MANAGER ---
object StorageManager {
    private const val PREFS_NAME = "CapturePrefs"
    private const val KEY_STORAGE_URI = "storage_uri"
    private const val FILE_NAME = "capture_boards.json"

    fun saveStorageUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_STORAGE_URI, uri.toString()).apply()
    }

    fun getStorageUri(context: Context): Uri? {
        val uriStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STORAGE_URI, null)
        return uriStr?.let { Uri.parse(it) }
    }

    fun saveBoardsState(context: Context, subjects: List<Subject>) {
        val uri = getStorageUri(context) ?: return
        val jsonArray = JSONArray()
        for (sub in subjects) {
            val subObj = JSONObject().apply {
                put("id", sub.id)
                put("name", sub.name)
                val colsObj = JSONObject()
                for ((colIndex, uris) in sub.columns) {
                    val uriArray = JSONArray()
                    uris.forEach { uriArray.put(it) }
                    colsObj.put(colIndex.toString(), uriArray)
                }
                put("columns", colsObj)
            }
            jsonArray.put(subObj)
        }

        try {
            val rootDoc = DocumentFile.fromTreeUri(context, uri)
            var fileDoc = rootDoc?.findFile(FILE_NAME)
            if (fileDoc == null) {
                fileDoc = rootDoc?.createFile("application/json", FILE_NAME)
            }
            fileDoc?.uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri, "wt")?.use {
                    it.write(jsonArray.toString().toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadBoardsState(context: Context): List<Subject> {
        val uri = getStorageUri(context) ?: return emptyList()
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, uri)
            val fileDoc = rootDoc?.findFile(FILE_NAME) ?: return emptyList()

            val jsonStr = context.contentResolver.openInputStream(fileDoc.uri)?.use {
                it.reader().readText()
            } ?: return emptyList()

            val jsonArray = JSONArray(jsonStr)
            val subjects = mutableListOf<Subject>()
            for (i in 0 until jsonArray.length()) {
                val subObj = jsonArray.getJSONObject(i)
                val id = subObj.getString("id")
                val name = subObj.getString("name")
                val colsObj = subObj.getJSONObject("columns")
                val columns = mutableMapOf<Int, List<String>>()
                val keys = colsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val uriArray = colsObj.getJSONArray(key)
                    val uris = mutableListOf<String>()
                    for (j in 0 until uriArray.length()) {
                        uris.add(uriArray.getString(j))
                    }
                    columns[key.toInt()] = uris
                }
                subjects.add(Subject(id, name, columns))
            }
            subjects
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun copyMediaToWorkspace(context: Context, sourceUri: Uri, rootTreeUri: Uri): String? {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
            var mediaDir = rootDoc.findFile("Media")
            if (mediaDir == null) {
                mediaDir = rootDoc.createDirectory("Media")
            }

            // Fallback for file:// URIs generated by our custom camera/audio
            var mimeType = context.contentResolver.getType(sourceUri)
            if (mimeType == null) {
                val path = sourceUri.path ?: ""
                mimeType = when {
                    path.endsWith(".m4a") -> "audio/mp4"
                    path.endsWith(".mp4") -> "video/mp4"
                    path.endsWith(".png") -> "image/png"
                    path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                    else -> "application/octet-stream"
                }
            }

            val extension = when {
                mimeType.startsWith("video/") -> "mp4"
                mimeType.startsWith("audio/") -> "m4a"
                mimeType == "image/jpeg" -> "jpg"
                mimeType == "image/png" -> "png"
                else -> "bin"
            }

            val fileName = "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}.$extension"
            val newMediaFile = mediaDir?.createFile(mimeType, fileName) ?: return null

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newMediaFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            newMediaFile.uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// --- UI COMPONENTS ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaptureTheme {
                CanvasBoardApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasBoardApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storageUri by remember { mutableStateOf(StorageManager.getStorageUri(context)) }
    var subjects by remember { mutableStateOf(listOf<Subject>()) }
    var currentSubjectId by remember { mutableStateOf<String?>(null) }

    var isCaptureButtonEnabled by remember { mutableStateOf(false) }
    var isOverlayActive by remember { mutableStateOf(false) }
    var showNewSubjectDialog by remember { mutableStateOf(false) }

    var isEditMode by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var insertTargetIndex by remember { mutableStateOf(-1) }

    // Custom Camera Trigger
    var showCustomCamera by remember { mutableStateOf(false) }

    var isShareMode by remember { mutableStateOf(false) }
    var selectedImagesForShare by remember { mutableStateOf(emptySet<String>()) }

    fun shareSelectedImages() {
        if (selectedImagesForShare.isEmpty()) return
        val uris = ArrayList(selectedImagesForShare.map { Uri.parse(it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*" // Updated to accept media wildcard
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val pagerState = rememberPagerState(pageCount = { 10 })

    LaunchedEffect(storageUri) {
        if (storageUri != null) {
            withContext(Dispatchers.IO) {
                val loadedSubjects = StorageManager.loadBoardsState(context)
                withContext(Dispatchers.Main) {
                    if (loadedSubjects.isEmpty()) {
                        val default = Subject(UUID.randomUUID().toString(), "Default Board", emptyMap())
                        subjects = listOf(default)
                        currentSubjectId = default.id
                        StorageManager.saveBoardsState(context, subjects)
                    } else {
                        subjects = loadedSubjects
                        if (currentSubjectId == null || !subjects.any { it.id == currentSubjectId }) {
                            currentSubjectId = subjects.first().id
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(currentSubjectId) {
        pagerState.scrollToPage(0)
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            StorageManager.saveStorageUri(context, it)
            storageUri = it
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            showCustomPicker = true
        } else {
            Toast.makeText(context, "Storage permission required to pick media.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPickerAccess(index: Int) {
        insertTargetIndex = index
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionsLauncher.launch(perms)
    }

    fun handleMediaAdded(uris: List<Uri>, insertIndex: Int) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val savedUriStrings = uris.mapNotNull { uri ->
                StorageManager.copyMediaToWorkspace(context, uri, storageUri!!)
            }
            withContext(Dispatchers.Main) {
                val currentPage = pagerState.currentPage
                subjects = subjects.map { sub ->
                    if (sub.id == currentSubjectId) {
                        val currentList = sub.columns[currentPage]?.toMutableList() ?: mutableListOf()
                        if (insertIndex == -1 || insertIndex >= currentList.size) {
                            currentList.addAll(savedUriStrings)
                        } else {
                            currentList.addAll(insertIndex, savedUriStrings)
                        }
                        val newCols = sub.columns.toMutableMap()
                        newCols[currentPage] = currentList
                        sub.copy(columns = newCols)
                    } else sub
                }
                StorageManager.saveBoardsState(context, subjects)
                showCustomPicker = false
            }
        }
    }

    fun moveImage(page: Int, fromIndex: Int, offset: Int) {
        subjects = subjects.map { sub ->
            if (sub.id == currentSubjectId) {
                val list = sub.columns[page]?.toMutableList() ?: return@map sub
                val toIndex = fromIndex + offset
                if (fromIndex in list.indices && toIndex in list.indices) {
                    val item = list.removeAt(fromIndex)
                    list.add(toIndex, item)
                    val newCols = sub.columns.toMutableMap()
                    newCols[page] = list
                    sub.copy(columns = newCols)
                } else sub
            } else sub
        }
        StorageManager.saveBoardsState(context, subjects)
    }

    fun deleteImage(page: Int, targetIndex: Int) {
        subjects = subjects.map { sub ->
            if (sub.id == currentSubjectId) {
                val list = sub.columns[page]?.toMutableList() ?: return@map sub
                if (targetIndex in list.indices) {
                    list.removeAt(targetIndex)
                    val newCols = sub.columns.toMutableMap()
                    newCols[page] = list
                    sub.copy(columns = newCols)
                } else sub
            } else sub
        }
        StorageManager.saveBoardsState(context, subjects)
    }

    if (storageUri == null) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Welcome to Capture Board", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please select an external Workspace Folder.\nYour boards and images will be securely saved here.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { directoryPickerLauncher.launch(null) }) {
                    Text("Select Workspace Folder")
                }
            }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(32.dp))
                Text("Subjects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(subjects) { subject ->
                        val isSelected = subject.id == currentSubjectId
                        Text(
                            text = subject.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSubjectId = subject.id
                                    scope.launch { drawerState.close() }
                                }
                                .padding(16.dp)
                        )
                    }
                    item {
                        TextButton(
                            onClick = { showNewSubjectDialog = true },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Subject")
                        }
                    }
                }
                HorizontalDivider()
                Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Global Capture")
                    Switch(checked = isCaptureButtonEnabled, onCheckedChange = { isCaptureButtonEnabled = it })
                }
                TextButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Accessibility Settings")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        val currentSubject = subjects.find { it.id == currentSubjectId }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (isShareMode) {
                        TopAppBar(
                            title = { Text("${selectedImagesForShare.size} Selected") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    isShareMode = false
                                    selectedImagesForShare = emptySet()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Share")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    shareSelectedImages()
                                    isShareMode = false
                                    selectedImagesForShare = emptySet()
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Confirm Share")
                                }
                            }
                        )
                    } else {
                        TopAppBar(
                            title = { Text(currentSubject?.name ?: "Capture Board") },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    isShareMode = true
                                    isEditMode = false
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Media")
                                }
                                IconButton(onClick = {
                                    isEditMode = !isEditMode
                                    isShareMode = false
                                }) {
                                    Icon(
                                        if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Toggle Edit Mode",
                                        tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { page ->
                    val columnImages = currentSubject?.columns?.get(page) ?: emptyList()

                    ZoomablePage(isEditMode = isEditMode || isShareMode) { isZoomed ->
                        if (columnImages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (isEditMode) {
                                    IconButton(onClick = { requestPickerAccess(0) }, modifier = Modifier.size(64.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Media", modifier = Modifier.fillMaxSize(), tint = Color.Gray)
                                    }
                                } else {
                                    Text(
                                        text = "Column $page\nTap Edit (top right) to add media",
                                        textAlign = TextAlign.Center,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                userScrollEnabled = isEditMode || isShareMode || !isZoomed,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                if (isEditMode) {
                                    item {
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            IconButton(onClick = { requestPickerAccess(0) }) {
                                                Icon(Icons.Default.Add, contentDescription = "Add top")
                                            }
                                        }
                                    }
                                }

                                itemsIndexed(columnImages) { index, uriString ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isEditMode) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(
                                                    onClick = { moveImage(page, index, -1) },
                                                    enabled = index > 0
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                                }
                                                IconButton(
                                                    onClick = { moveImage(page, index, 1) },
                                                    enabled = index < columnImages.size - 1
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                                }
                                                IconButton(
                                                    onClick = { deleteImage(page, index) }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }

                                        val isSelectedForShare = selectedImagesForShare.contains(uriString)

                                        // Try reading dynamically, use extension as fallback
                                        val mimeType by remember(uriString) {
                                            val resolverType = context.contentResolver.getType(Uri.parse(uriString))
                                            val fallbackType = when {
                                                uriString.endsWith(".mp4") -> "video/mp4"
                                                uriString.endsWith(".m4a") || uriString.endsWith(".mp3") -> "audio/mpeg"
                                                else -> "image/*"
                                            }
                                            mutableStateOf(resolverType ?: fallbackType)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp)
                                                .clickable(enabled = isShareMode) {
                                                    selectedImagesForShare = if (isSelectedForShare) {
                                                        selectedImagesForShare - uriString
                                                    } else {
                                                        selectedImagesForShare + uriString
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Conditional Rendering for Audio/Video vs Images
                                            if (mimeType.startsWith("video/")) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(16f / 9f)
                                                        .background(Color.Black, shape = MaterialTheme.shapes.medium),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.PlayCircle, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(64.dp))
                                                }
                                            } else if (mimeType.startsWith("audio/")) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(100.dp)
                                                        .background(Color.DarkGray, shape = MaterialTheme.shapes.medium),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(48.dp))
                                                }
                                            } else {
                                                AsyncImage(
                                                    model = Uri.parse(uriString),
                                                    contentDescription = "Canvas Media",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentScale = ContentScale.FillWidth
                                                )
                                            }

                                            // Selection Overlay
                                            if (isShareMode && isSelectedForShare) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(Color.Black.copy(alpha = 0.4f))
                                                )
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(48.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isEditMode) {
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            IconButton(onClick = { requestPickerAccess(index + 1) }) {
                                                Icon(Icons.Default.Add, contentDescription = "Add here")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isCaptureButtonEnabled && !isOverlayActive) {
                SmallFloatingActionButton(
                    onClick = { isOverlayActive = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Start Capture")
                }
            }

            if (isOverlayActive) {
                CaptureOverlay(
                    onCaptureComplete = { startY, endY ->
                        isOverlayActive = false
                        Toast.makeText(context, "Captured from Y:$startY to Y:$endY", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { isOverlayActive = false }
                )
            }
        }

        AnimatedVisibility(
            visible = showCustomPicker,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            CustomImagePickerScreen(
                onImagesSelected = { uris -> handleMediaAdded(uris, insertTargetIndex) },
                onLaunchCamera = { showCustomCamera = true },
                onDismiss = { showCustomPicker = false }
            )
        }
    }

    if (showCustomCamera) {
        CustomCameraScreen(
            onMediaCaptured = { imageUri, audioUri ->
                val uris = listOfNotNull(imageUri, audioUri)
                handleMediaAdded(uris, insertTargetIndex)
                showCustomCamera = false
            },
            onCancel = { showCustomCamera = false }
        )
    }

    if (showNewSubjectDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSubjectDialog = false },
            title = { Text("New Subject") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Subject Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        val newSub = Subject(UUID.randomUUID().toString(), newName, emptyMap())
                        subjects = subjects + newSub
                        currentSubjectId = newSub.id
                        StorageManager.saveBoardsState(context, subjects)
                    }
                    showNewSubjectDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewSubjectDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// --- ZOOM & PAN ENGINE ---
@Composable
fun ZoomablePage(
    isEditMode: Boolean,
    content: @Composable (isZoomed: Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                var zoom = 1f
                                var pan = Offset.Zero
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop

                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            zoom *= zoomChange
                                            pan += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1 - zoom) * centroidSize
                                            val panMotion = pan.getDistance()

                                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val isMultiTouch = event.changes.size > 1

                                            if (isMultiTouch || scale > 1f) {
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                                scale = (scale * zoomChange).coerceIn(1f, 10f)
                                                if (scale > 1f) {
                                                    offset += panChange
                                                } else {
                                                    offset = Offset.Zero
                                                }
                                            }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            content(scale > 1f)
        }
    }
}

// --- CUSTOM IMAGE PICKER OVERLAY ---
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomImagePickerScreen(
    onImagesSelected: (List<Uri>) -> Unit,
    onLaunchCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedImages by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var multiSelectMode by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            onImagesSelected(listOf(it))
            onDismiss()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            onImagesSelected(listOf(it))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fetchedUris = mutableListOf<Uri>()
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    fetchedUris.add(contentUri)
                }
            }
            images = fetchedUris
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (multiSelectMode) "${selectedImages.size} Selected" else "Select Media") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    },
                    actions = {
                        if (multiSelectMode) {
                            TextButton(onClick = {
                                onImagesSelected(selectedImages.toList())
                                onDismiss()
                            }) {
                                Text("Add", style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            IconButton(onClick = { onLaunchCamera(); onDismiss() }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                            }
                            IconButton(onClick = { videoPicker.launch("video/*") }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video")
                            }
                            IconButton(onClick = { audioPicker.launch("audio/*") }) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(images) { uri ->
                    val isSelected = selectedImages.contains(uri)

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .combinedClickable(
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedImages = if (isSelected) selectedImages - uri else selectedImages + uri
                                        if (selectedImages.isEmpty()) multiSelectMode = false
                                    } else {
                                        onImagesSelected(listOf(uri))
                                        onDismiss()
                                    }
                                },
                                onLongClick = {
                                    multiSelectMode = true
                                    selectedImages = selectedImages + uri
                                }
                            )
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        if (multiSelectMode && isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                            )
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =======================================================================
// --- NEW CUSTOM CAMERA ENGINE ---
// =======================================================================

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File? {
        outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }
        return outputFile
    }

    fun stop() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
        }
        recorder = null
    }
}

@Composable
fun CustomCameraScreen(onMediaCaptured: (Uri, Uri?) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermissions = it.values.all { granted -> granted }
    }

    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    if (!hasPermissions) return

    var phase by remember { mutableStateOf(0) } // 0: Preview, 1: Crop Dim Layer, 2: Audio Capture
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    when (phase) {
        0 -> CameraPreviewPhase(
            onCapture = { bmp ->
                capturedBitmap = bmp
                phase = 1
            },
            onCancel = onCancel
        )
        1 -> CropPhase(
            initialBitmap = capturedBitmap!!,
            onCrop = { cropped ->
                croppedBitmap = cropped
                phase = 2
            },
            onCancel = { phase = 0 }
        )
        2 -> AudioPhase(
            bitmap = croppedBitmap!!,
            onDone = { audioUri ->
                val file = File(context.cacheDir, "img_${System.currentTimeMillis()}.png")
                file.outputStream().use { croppedBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                onMediaCaptured(Uri.fromFile(file), audioUri)
            }
        )
    }
}

@Composable
fun CameraPreviewPhase(onCapture: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Allows toggling between front/back
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            IconButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) {
                Icon(Icons.Default.FlipCameraAndroid, "Flip Camera", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val matrix = Matrix().apply {
                                postRotate(image.imageInfo.rotationDegrees.toFloat())
                                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    postScale(-1f, 1f, image.width / 2f, image.height / 2f) // Mirror matching the preview for front camera
                                }
                            }
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            image.close()
                            onCapture(rotated)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(48.dp)
        ) {
            Icon(Icons.Default.CameraAlt, "Capture")
        }
    }
}

@Composable
fun CropPhase(initialBitmap: Bitmap, onCrop: (Bitmap) -> Unit, onCancel: () -> Unit) {
    var bitmap by remember { mutableStateOf(initialBitmap) }

    var startY by remember { mutableStateOf<Float?>(null) }
    var currentY by remember { mutableStateOf<Float?>(null) }

    var topOffsetState by remember { mutableFloatStateOf(0f) }
    var drawHeightState by remember { mutableFloatStateOf(0f) }
    var imageScaleState by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) { // Restarts gesture detection if bitmap rotated
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Multi-finger check -> Cancel crop UI and go back to fully dim
                            if (event.changes.size > 1) {
                                startY = null
                                currentY = null
                                // Wait to flush multi-finger lift
                                do {
                                    val flushEvent = awaitPointerEvent()
                                    flushEvent.changes.forEach { it.consume() }
                                } while (flushEvent.changes.any { it.pressed })
                                continue
                            }

                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed && !change.previousPressed) {
                                startY = change.position.y
                                currentY = change.position.y
                            } else if (change.pressed && change.previousPressed) {
                                if (startY != null) {
                                    currentY = change.position.y
                                }
                            } else if (!change.pressed && change.previousPressed) {
                                if (startY != null && currentY != null) {
                                    val top = min(startY!!, currentY!!)
                                    val bottom = max(startY!!, currentY!!)

                                    if (bottom - top > 50) {
                                        // Calculates relative coordinate mapping from exact Canvas placement
                                        val cropTop = ((top - topOffsetState) / imageScaleState).toInt().coerceIn(0, bitmap.height - 1)
                                        val cropBottom = ((bottom - topOffsetState) / imageScaleState).toInt().coerceIn(1, bitmap.height)
                                        val cropHeight = cropBottom - cropTop

                                        if (cropHeight > 10) {
                                            val cropped = Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, cropHeight)
                                            onCrop(cropped)
                                        } else {
                                            startY = null
                                            currentY = null
                                        }
                                    } else {
                                        startY = null
                                        currentY = null
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val canvasRatio = canvasWidth / canvasHeight

                val drawWidth: Float
                val drawHeight: Float
                val leftOffset: Float
                val topOffset: Float

                // Maintain natural dimensions on screen perfectly without arbitrary zooming
                if (imgRatio > canvasRatio) {
                    drawWidth = canvasWidth
                    drawHeight = canvasWidth / imgRatio
                    leftOffset = 0f
                    topOffset = (canvasHeight - drawHeight) / 2f
                } else {
                    drawHeight = canvasHeight
                    drawWidth = canvasHeight * imgRatio
                    leftOffset = (canvasWidth - drawWidth) / 2f
                    topOffset = 0f
                }

                topOffsetState = topOffset
                drawHeightState = drawHeight
                imageScaleState = drawWidth / bitmap.width

                // 1. Draw natural sized image
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(leftOffset.toInt(), topOffset.toInt()),
                    dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                )

                // 2. Draw dim bounds
                if (startY == null || currentY == null) {
                    drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(leftOffset, topOffset), size = Size(drawWidth, drawHeight))
                } else {
                    val topY = min(startY!!, currentY!!).coerceIn(topOffset, topOffset + drawHeight)
                    val bottomY = max(startY!!, currentY!!).coerceIn(topOffset, topOffset + drawHeight)

                    // Draw dark dim over the un-selected top and bottom portions
                    drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(leftOffset, topOffset), size = Size(drawWidth, topY - topOffset))
                    drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(leftOffset, bottomY), size = Size(drawWidth, topOffset + drawHeight - bottomY))

                    // Borders framing vertical crop
                    drawLine(Color.White, start = Offset(leftOffset, topY), end = Offset(leftOffset + drawWidth, topY), strokeWidth = 4f)
                    drawLine(Color.White, start = Offset(leftOffset, bottomY), end = Offset(leftOffset + drawWidth, bottomY), strokeWidth = 4f)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            // Image Rotate Button
            IconButton(onClick = {
                val matrix = Matrix().apply { postRotate(90f) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                // Restarts crop dim logic whenever you rotate
                startY = null
                currentY = null
            }) {
                Icon(Icons.Default.RotateRight, "Rotate Image", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        Text(
            "Drag vertically to crop full width",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}

@Composable
fun AudioPhase(bitmap: Bitmap, onDone: (Uri?) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val recorder = remember { AudioRecorder(context) }

    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.weight(1f).padding(16.dp), contentScale = ContentScale.Fit)

        Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (isRecording) {
                    recorder.stop()
                    isRecording = false
                } else {
                    audioFile = recorder.start()
                    isRecording = true
                }
            }) {
                Icon(if (isRecording) Icons.Default.Close else Icons.Default.Audiotrack, "")
                Spacer(Modifier.width(8.dp))
                Text(if (isRecording) "Stop Recording" else "Add Audio Note")
            }

            Button(onClick = {
                if (isRecording) recorder.stop()
                onDone(audioFile?.let { Uri.fromFile(it) })
            }) {
                Icon(Icons.Default.Check, "")
                Spacer(Modifier.width(8.dp))
                Text("Confirm & Add")
            }
        }
    }
}

// --- CAPTURE OVERLAY COMPOSABLE (Global Capture) ---
@Composable
fun CaptureOverlay(
    onCaptureComplete: (top: Float, bottom: Float) -> Unit,
    onCancel: () -> Unit
) {
    var startY by remember { mutableStateOf<Float?>(null) }
    var currentY by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startY = offset.y
                        currentY = offset.y
                    },
                    onDrag = { change, _ ->
                        currentY = change.position.y
                    },
                    onDragEnd = {
                        if (startY != null && currentY != null) {
                            val topY = min(startY!!, currentY!!)
                            val bottomY = max(startY!!, currentY!!)
                            onCaptureComplete(topY, bottomY)
                        } else {
                            onCancel()
                        }
                    },
                    onDragCancel = { onCancel() }
                )
            }
    ) {
        if (startY == null || currentY == null) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
        } else {
            val topY = min(startY!!, currentY!!)
            val bottomY = max(startY!!, currentY!!)

            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = Size(size.width, topY))
            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, bottomY), size = Size(size.width, size.height - bottomY))

            drawLine(color = Color.White, start = Offset(0f, topY), end = Offset(size.width, topY), strokeWidth = 4f)
            drawLine(color = Color.White, start = Offset(0f, bottomY), end = Offset(size.width, bottomY), strokeWidth = 4f)
        }
    }
}
