package com.rmrbranco.galacticcom

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar
    private lateinit var resendVerificationTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEditText = view.findViewById<EditText>(R.id.et_email_login)
        val passwordEditText = view.findViewById<EditText>(R.id.et_password_login)
        val loginButton = view.findViewById<Button>(R.id.btn_login)
        val goToRegisterTextView = view.findViewById<TextView>(R.id.tv_go_to_register)
        val forgotPasswordTextView = view.findViewById<TextView>(R.id.tv_forgot_password)
        progressBar = view.findViewById(R.id.progress_bar_login)
        resendVerificationTextView = view.findViewById(R.id.tv_resend_verification)

        setupUI(view)

        val emailFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val filtered = source.subSequence(start, end).filter {
                Character.isLetterOrDigit(it) || "@._+-".contains(it)
            }
            if (filtered.length == end - start) {
                null // Accept original
            } else {
                filtered // Return filtered
            }
        }

        emailEditText.filters = emailEditText.filters + emailFilter

        loginButton.setOnClickListener {
            hideKeyboard()
            emailEditText.clearFocus()
            passwordEditText.clearFocus()
            resendVerificationTextView.visibility = View.GONE

            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                safeToast("Please enter your email and password.")
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                safeToast("Please enter a valid email address.")
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (!isAdded) return@addOnCompleteListener

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.reload()?.addOnCompleteListener { reloadTask ->
                            if (!isAdded) return@addOnCompleteListener
                            
                            progressBar.visibility = View.GONE
                            loginButton.isEnabled = true
                            
                            if (reloadTask.isSuccessful) {
                                val refreshedUser = auth.currentUser
                                if (refreshedUser != null && refreshedUser.isEmailVerified) {
                                    updateIpAddressAndCountry()
                                    getAndStoreFcmToken()
                                } else {
                                    safeToast("Please verify your email address.", Toast.LENGTH_LONG)
                                    resendVerificationTextView.visibility = View.VISIBLE
                                    auth.signOut()
                                }
                            } else {
                                safeToast("Failed to check email verification status. Please try again.", Toast.LENGTH_LONG)
                                auth.signOut()
                            }
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        loginButton.isEnabled = true
                        
                        val exception = task.exception
                        val errorMessage = when (exception) {
                            is FirebaseAuthInvalidUserException -> "Account not found or disabled."
                            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
                            else -> "Login failed: ${exception?.localizedMessage}"
                        }
                        safeToast(errorMessage, Toast.LENGTH_LONG)
                        Log.e("LoginFragment", "Login error", exception)
                    }
                }
        }

        resendVerificationTextView.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification()
                    .addOnCompleteListener { sendTask ->
                        if (!isAdded) return@addOnCompleteListener
                        if (sendTask.isSuccessful) {
                            safeToast("Verification email sent to ${user.email}.")
                        } else {
                            safeToast("Failed to send verification email.")
                        }
                    }
            }
        }

        forgotPasswordTextView.setOnClickListener {
            showForgotPasswordDialog()
        }

        goToRegisterTextView.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    private fun showForgotPasswordDialog() {
        if (!isAdded) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)

        val emailEditText = dialogView.findViewById<EditText>(R.id.et_email_forgot_password)
        val positiveButton = dialogView.findViewById<Button>(R.id.dialog_positive_button)
        val negativeButton = dialogView.findViewById<Button>(R.id.dialog_negative_button)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        positiveButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sendPasswordResetEmail(email)
                dialog.dismiss()
            } else {
                safeToast("Please enter a valid email.")
            }
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                if (task.isSuccessful) {
                    safeToast("Password reset email sent.")
                } else {
                    safeToast("Failed to send password reset email.")
                }
            }
    }
    
    // Helper to prevent crashes when fragment is detached
    private fun safeToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        context?.let {
            Toast.makeText(it, message, length).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) {
        // Set up touch listener for non-text box views to hide keyboard.
        if (view !is EditText) {
            view.setOnTouchListener { v, event ->
                hideKeyboard()
                false
            }
        }

        //If a layout container, iterate over children and seed recursion.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val innerView = view.getChildAt(i)
                setupUI(innerView)
            }
        }
    }

    private fun getAndStoreFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!isAdded) return@addOnCompleteListener
            
            if (!task.isSuccessful) {
                Log.w("LoginFragment", "Fetching FCM registration token failed", task.exception)
                findNavController().navigate(R.id.action_login_to_home)
                return@addOnCompleteListener
            }

            val token = task.result
            val userId = auth.currentUser?.uid

            if (userId != null && token != null) {
                database.reference.child("users").child(userId).child("fcmToken").setValue(token)
                    .addOnCompleteListener {
                        if (isAdded) {
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                    }
            } else {
                findNavController().navigate(R.id.action_login_to_home)
            }
        }
    }

    private fun updateIpAddressAndCountry() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ipInfo = IpApiService.getIpInfo()
            if (ipInfo != null) {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val updates = mapOf(
                        "ipAddress" to ipInfo.ipAddress,
                        "country" to ipInfo.country
                    )
                    database.reference.child("users").child(userId).updateChildren(updates)
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}