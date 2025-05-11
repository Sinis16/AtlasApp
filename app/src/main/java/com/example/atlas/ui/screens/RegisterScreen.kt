package com.example.atlas.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.atlas.ui.viewmodel.UserViewModel

@Composable
fun RegisterScreen(
    navController: NavController,
    userViewModel: UserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isAuthenticated by userViewModel.isAuthenticated.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Navigate to home if already authenticated
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            Log.d("RegisterScreen", "User already authenticated, navigating to home")
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Register", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Name TextField
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 50) name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && name.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Email TextField
        OutlinedTextField(
            value = email,
            onValueChange = { if (it.length <= 50) email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && email.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Password TextField
        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= 21) password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && password.isBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm Password TextField
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { if (it.length <= 21) confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null && (confirmPassword.isBlank() || confirmPassword != password),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        // Password length warning
        Text(
            text = "Password must be between 8 and 21 characters.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        // Terms and Conditions Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = acceptTerms,
                onCheckedChange = { acceptTerms = it }
            )
            Text("Accept Terms and Conditions")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Register Button
        Button(
            onClick = {
                errorMessage = null
                when {
                    name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                        errorMessage = "Please fill in all fields"
                    }
                    password.length < 8 || confirmPassword.length < 8 -> {
                        errorMessage = "Password must be at least 8 characters"
                    }
                    password != confirmPassword -> {
                        errorMessage = "Passwords do not match"
                    }
                    !acceptTerms -> {
                        errorMessage = "Please accept the terms and conditions"
                    }
                    !email.endsWith("@uniandes.edu.co") -> {
                        errorMessage = "Email must end with @uniandes.edu.co"
                    }
                    else -> {
                        isLoading = true
                        userViewModel.signUpWithEmail(email, password) { success, errorMsg ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Registration successful",
                                    Toast.LENGTH_LONG
                                ).show()
                                navController.navigate("home") {
                                    popUpTo("register") { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                errorMessage = errorMsg ?: "Failed to register"
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log In Link
        Text(
            text = "Already have an account? Log in",
            color = MaterialTheme.colorScheme.primary,
            style = TextStyle(textDecoration = TextDecoration.Underline),
            modifier = Modifier
                .clickable { navController.navigate("logIn") }
        )
    }
}