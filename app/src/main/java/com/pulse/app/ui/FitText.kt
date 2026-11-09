package com.pulse.app.ui
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.Color
@Composable fun FitText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,textAlign:TextAlign?=null,maxLines:Int=1,overflow:TextOverflow=TextOverflow.Clip,style:TextStyle=LocalTextStyle.current) {
 androidx.compose.foundation.text.BasicText(text,modifier,style=style.merge(TextStyle(color=if(color!=Color.Unspecified)color else if(style.color!=Color.Unspecified)style.color else LocalContentColor.current,fontSize=fontSize,fontWeight=fontWeight,textAlign=textAlign ?: TextAlign.Unspecified)),maxLines=maxLines,autoSize=androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize=7.sp,maxFontSize=if(fontSize!=TextUnit.Unspecified)fontSize else if(style.fontSize!=TextUnit.Unspecified)style.fontSize else 14.sp,stepSize=.5.sp))
}
