package com.example.boxfan3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.boxfan3.ui.screens.BoxFanScreen
import com.example.boxfan3.ui.theme.BoxFan3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoxFan3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    BoxFanScreen()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BoxFanScreenPreview() {
    BoxFan3Theme {
        BoxFanScreen()
    }
}