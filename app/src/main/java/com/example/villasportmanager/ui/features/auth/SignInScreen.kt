package com.example.villasportmanager.ui.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.villasportmanager.R
import com.example.villasportmanager.ui.components.KayanCrest
import com.example.villasportmanager.ui.components.KayanIcons
import com.example.villasportmanager.ui.components.LayoutTier
import com.example.villasportmanager.ui.components.PhotoHero
import com.example.villasportmanager.ui.components.isShort
import com.example.villasportmanager.ui.components.layoutTier
import com.example.villasportmanager.ui.theme.BricolageGrotesque
import com.example.villasportmanager.ui.theme.ErrorRed
import com.example.villasportmanager.ui.theme.Ink
import com.example.villasportmanager.ui.theme.InkMuted
import com.example.villasportmanager.ui.theme.KayanGreen
import com.example.villasportmanager.ui.theme.KayanLime
import com.example.villasportmanager.ui.theme.Mist

@Composable
fun SignInScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory()),
    onSignInSuccess: () -> Unit
) {
    val split = windowSizeClass.layoutTier != LayoutTier.Compact && !windowSizeClass.isShort
    val submit = { viewModel.signIn(onSuccess = onSignInSuccess) }

    if (split) {
        Row(Modifier.fillMaxSize().background(Mist)) {
            PhotoHero(
                imageRes = R.drawable.main_hero_background,
                shape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(48.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    KayanCrest(size = 112.dp)
                    Spacer(Modifier.height(28.dp))
                    Text("Kayan Club", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Color.White)
                    Text("Welcome home", fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 60.sp, color = KayanLime)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                SignInForm(viewModel, submit, Modifier.widthIn(max = 420.dp).fillMaxWidth())
            }
        }
    } else {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .background(Mist)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            PhotoHero(
                imageRes = R.drawable.main_hero_background,
                modifier = Modifier.fillMaxWidth().height(280.dp + topInset)
            ) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
                    KayanCrest(size = 64.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Kayan Club", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
                    Text("Welcome home", fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, color = KayanLime)
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                    SignInForm(viewModel, submit, Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SignInForm(viewModel: AuthViewModel, onSubmit: () -> Unit, modifier: Modifier) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = KayanGreen,
        focusedLabelColor = KayanGreen,
        focusedLeadingIconColor = KayanGreen,
        cursorColor = KayanGreen,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sign in", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text("Use your villa's account to book courts.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        }
        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text("Username") },
            leadingIcon = { Icon(KayanIcons.User, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(KayanIcons.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!viewModel.isLoading) onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
        viewModel.errorMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ErrorRed)
        }
        Button(
            onClick = onSubmit,
            enabled = !viewModel.isLoading,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KayanGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Text("Log in", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
