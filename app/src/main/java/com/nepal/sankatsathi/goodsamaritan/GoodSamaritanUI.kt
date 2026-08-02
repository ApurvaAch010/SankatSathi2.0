package com.nepal.sankatsathi.goodsamaritan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SamaritanPointsCard(points: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "🏆 $points Samaritan Points",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "You're making Nepal safer!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AccidentHistoryList(reports: List<AccidentReport>) {
    Column {
        Text(
            "Good Samaritan Impact History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (reports.isEmpty()) {
            Text("No reports submitted yet.", color = Color.Gray)
        } else {
            reports.forEach { report ->
                AccidentReportItem(report)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AccidentReportItem(report: AccidentReport) {
    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(report.timestamp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (report.imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(report.imageUri),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    imageVector = when (report.severity) {
                        Severity.MINOR_INJURY -> Icons.Default.Info
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when (report.severity) {
                        Severity.CRITICAL_RESCUE_NEEDED -> Color.Red
                        Severity.SEVERE_INJURY -> Color(0xFFFFA500)
                        else -> Color.Blue
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(report.severity.name.replace("_", " "), fontWeight = FontWeight.Bold)
                Text(date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (!report.textDescription.isNullOrBlank()) {
                    Text(report.textDescription, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            if (report.voiceNoteUri != null) {
                Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportAccidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (Severity, String, String?, String?) -> Unit
) {
    val context = LocalContext.current
    var severity by remember { mutableStateOf(Severity.MINOR_INJURY) }
    var description by remember { mutableStateOf("") }
    
    var capturedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    var isRecording by remember { mutableStateOf(false) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }
    val audioRecorder = remember { AudioRecorder(context) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri = tempImageUri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Accident") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Severity", fontWeight = FontWeight.Bold)
                Severity.entries.forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = severity == s, onClick = { severity = s })
                        Text(s.name.replace("_", " "))
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Describe the situation") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Text("Media Evidence", fontWeight = FontWeight.Bold)
                
                if (capturedImageUri != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp))) {
                        Image(
                            painter = rememberAsyncImagePainter(capturedImageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { capturedImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                }

                if (recordedAudioFile != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio Recorded", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { recordedAudioFile = null }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                audioRecorder.stopRecording()
                                isRecording = false
                            } else {
                                val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp4")
                                audioRecorder.startRecording(file)
                                recordedAudioFile = file
                                isRecording = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRecording) "Stop" else "Record")
                    }

                    Button(
                        onClick = {
                            try {
                                val file = File(context.cacheDir, "SOS_${System.currentTimeMillis()}.jpg")
                                if (file.exists()) file.delete()
                                file.createNewFile()

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                tempImageUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Camera Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onSubmit(
                        severity, 
                        description, 
                        recordedAudioFile?.absolutePath, 
                        capturedImageUri?.toString()
                    ) 
                },
                enabled = !isRecording
            ) {
                Text("Submit & Earn Points")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

