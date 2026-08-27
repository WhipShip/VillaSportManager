package com.example.villasportmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.villasportmanager.ui.theme.VillaSportManagerTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

//--------------------------------------------------------------------------------------------------
val supabase: SupabaseClient = createSupabaseClient(
    supabaseUrl = "https://nwolxadgjtsjnyyvnwos.supabase.co",
    supabaseKey = "sb_publishable_RAqpRGcMX_3s8Od56w8dvA_1dOFjQmk"
)
{
    install(Postgrest)
}

@Serializable
data class Instrument(
    val id: Int,
    val name: String,
)
//--------------------------------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                InstrumentsList()
            }
        }
    }
}
@Composable
fun InstrumentsList() {
    var instruments by remember { mutableStateOf<List<Instrument>>(listOf()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                instruments = supabase.from("instruments")
                    .select().decodeList<Instrument>()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    if (error != null) {
        Text(
            "Error loading instruments: $error",
            modifier = Modifier.padding(8.dp),
        )
        return
    }
    LazyColumn {
        items(
            instruments,
            key = { instrument -> instrument.id },
        ) { instrument ->
            Text(
                instrument.name,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
