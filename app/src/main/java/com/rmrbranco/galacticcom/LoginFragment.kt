package com.rmrbranco.galacticcom

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MotionEvent
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
                Toast.makeText(context, "Please enter your email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.reload()?.addOnCompleteListener { reloadTask ->
                            progressBar.visibility = View.GONE
                            if (reloadTask.isSuccessful) {
                                val refreshedUser = auth.currentUser
                                if (refreshedUser != null && refreshedUser.isEmailVerified) {
                                    updateIpAddressAndCountry()
                                    getAndStoreFcmToken()
                                } else {
                                    Toast.makeText(context, "Please verify your email address.", Toast.LENGTH_LONG).show()
                                    resendVerificationTextView.visibility = View.VISIBLE
                                    auth.signOut()
                                }
                            } else {
                                Toast.makeText(context, "Failed to check email verification status. Please try again.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        resendVerificationTextView.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification()
                    .addOnCompleteListener { sendTask ->
                        if (sendTask.isSuccessful) {
                            Toast.makeText(context, "Verification email sent to ${user.email}.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
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
                if (task.isSuccessful) {
                    Toast.makeText(context, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to send password reset email.", Toast.LENGTH_SHORT).show()
                }
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
                        findNavController().navigate(R.id.action_login_to_home)
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