package com.pulse.app.ui

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel

@Composable fun GarminSetup(vm: PulseViewModel) {
    val operationError by vm.keyError.collectAsStateWithLifecycle()
    val connection by vm.snapshot.collectAsStateWithLifecycle()
    val configured by vm.garminConfigured.collectAsStateWithLifecycle()
    val busy by vm.keyBusy.collectAsStateWithLifecycle()
    var entry by remember{mutableStateOf(false)}
    var raw by remember{mutableStateOf("")}
    var remove by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::importGarminKey)}
    val window=LocalActivity.current?.window
    DisposableEffect(entry){
        val alreadySecure=(window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        if(entry)window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose{if(entry && !alreadySecure)window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Garmin Personal API",style=MaterialTheme.typography.titleMedium)
        Label(if(configured)"Encrypted key stored · access follows sync status"else"No personal key configured")
        Text("Connect your own Garmin account. The key stays on this device. A replacement is verified through Garmin before the cache is cleared, so accounts cannot mix.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PulseButton(if(configured)"Import replacement key"else"Import key file",enabled=!busy){picker.launch(arrayOf("text/*","application/octet-stream"))}
        Row {SmallAction("Enter key",!busy){entry=true};if(configured)SmallAction("Remove key",!busy){remove=true}}
        operationError?.let{Text(it,color=Red,style=MaterialTheme.typography.bodySmall)}
        if(connection.mode==com.pulse.app.core.DataMode.GARMIN)Text(connection.error ?: connection.status,style=MaterialTheme.typography.bodySmall)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Red)
        Label("Read only · requests go directly to Garmin")
    }
    if(entry)AlertDialog(onDismissRequest={raw="";entry=false},title={Text("Personal API key")},text={
        OutlinedTextField(raw,{raw=it.take(4096)},label={Text("Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false))
    },confirmButton={TextButton({vm.saveGarminKey(raw);raw="";entry=false},enabled=raw.trim().length>=16){Text("Save securely")}},dismissButton={TextButton({raw="";entry=false}){Text("Cancel")}})
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("Remove Garmin key?")},text={Text("The encrypted key and local Garmin cache will be removed. Original Garmin records are unchanged.")},confirmButton={TextButton({remove=false;vm.removeGarminKey()}){Text("Remove key")}},dismissButton={TextButton({remove=false}){Text("Cancel")}})
}
