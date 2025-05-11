package com.example.atlas.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.atlas.ui.viewmodel.UserViewModel

@SuppressLint("UnrememberedMutableState")
@Composable
fun ChangeDataScreen(navController: NavHostController, userViewModel: UserViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Validation for enabling the Update button
    val isUpdateEnabled by derivedStateOf {
        (newUsername.isNotBlank() && newUsername.length <= 50) ||
                (newPassword.isNotBlank() && repeatPassword.isNotBlank() &&
                        newPassword == repeatPassword && newPassword.length >= 6 && newPassword.length <= 21)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Update User Data",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Username TextField
        OutlinedTextField(
            value = newUsername,
            onValueChange = { if (it.length <= 50) newUsername = it },
            label = { Text("New Username") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && newUsername.isBlank(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // New Password TextField
        OutlinedTextField(
            value = newPassword,
            onValueChange = { if (it.length <= 21) newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && newPassword.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Repeat Password TextField
        OutlinedTextField(
            value = repeatPassword,
            onValueChange = { if (it.length <= 21) repeatPassword = it },
            label = { Text("Repeat New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && (repeatPassword.isBlank() || repeatPassword != newPassword),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        // Error message
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back Button
            Button(
                onClick = {
                    Log.d("ChangeDataScreen", "Navigating back")
                    navController.popBackStack()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            // Update Button
            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    userViewModel.updateUserData(
                        newUsername = newUsername.takeIf { it.isNotBlank() },
                        newPassword = newPassword.takeIf { it.isNotBlank() && it == repeatPassword },
                        context = context
                    ) { success, errorMsg ->
                        isLoading = false
                        if (success) {
                            Toast.makeText(context, "Data updated successfully", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } else {
                            errorMessage = errorMsg ?: "Failed to update data"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = isUpdateEnabled && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Update")
                }
            }
        }
    }
}