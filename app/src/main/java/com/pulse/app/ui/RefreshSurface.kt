package com.pulse.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RefreshSurface(loading:Boolean,refresh:()->Unit,enabled:Boolean=true,showIndicator:Boolean=true,content:@Composable ()->Unit){
 if(enabled)PullToRefreshBox(isRefreshing=loading,onRefresh=refresh,modifier=Modifier.fillMaxSize(),indicator={if(loading&&showIndicator)Surface(Modifier.align(Alignment.TopCenter),color=MaterialTheme.colorScheme.background){EcgSync(Modifier.width(180.dp))}}){content()} else content()
}
