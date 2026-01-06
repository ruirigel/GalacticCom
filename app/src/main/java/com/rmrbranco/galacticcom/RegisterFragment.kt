package com.rmrbranco.galacticcom

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var galaxySpinner: Spinner
    private lateinit var starSpinner: Spinner
    private lateinit var planetSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var avatarPreviewImageView: ImageView
    private lateinit var avatarSeed: String

    private lateinit var galaxies: List<GalaxyInfo>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEditText = view.findViewById<EditText>(R.id.et_email)
        val passwordEditText = view.findViewById<EditText>(R.id.et_password)
        val nicknameEditText = view.findViewById<EditText>(R.id.et_nickname)
        galaxySpinner = view.findViewById(R.id.spinner_galaxy)
        starSpinner = view.findViewById(R.id.spinner_star)
        planetSpinner = view.findViewById(R.id.spinner_planet)
        progressBar = view.findViewById(R.id.progress_bar_register)
        avatarPreviewImageView = view.findViewById(R.id.iv_avatar_preview)
        val registerButton = view.findViewById<Button>(R.id.btn_register)
        val goToLoginTextView = view.findViewById<TextView>(R.id.tv_go_to_login)

        setupUI(view)

        // Filter for allowed characters in nickname (letters, digits, hyphen, underscore)
        val nicknameFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val filtered = source.subSequence(start, end).filter {
                Character.isLetterOrDigit(it) || it == '_' || it == '-'
            }
            if (filtered.length == end - start) {
                null // Accept original
            } else {
                filtered // Return filtered version
            }
        }

        // Add nickname length and character filters
        nicknameEditText.filters = arrayOf(InputFilter.LengthFilter(24), nicknameFilter)

        // Generate a random seed and display the preview
        avatarSeed = System.currentTimeMillis().toString()
        viewLifecycleOwner.lifecycleScope.launch {
            val avatar = AlienAvatarGenerator.generate(avatarSeed, 256, 256)
            avatarPreviewImageView.setImageBitmap(avatar)
        }

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

        setupCosmicSpinners()

        registerButton.setOnClickListener {
            hideKeyboard()
            emailEditText.clearFocus()
            passwordEditText.clearFocus()
            nicknameEditText.clearFocus()

            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
                Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (nickname.length < 3) {
                Toast.makeText(context, "Nickname must be at least 3 characters long.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            registerButton.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val ipInfo = IpApiService.getIpInfo()

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser!!
                            val userId = user.uid

                            val selectedGalaxy = galaxySpinner.selectedItem as String
                            val orbit = (100..1000).random() // Generate a random orbit time

                            val userMap = hashMapOf(
                                "nickname" to nickname,
                                "galaxy" to selectedGalaxy,
                                "star" to starSpinner.selectedItem.toString(),
                                "planet" to planetSpinner.selectedItem.toString(),
                                "orbit" to orbit,
                                "notificationsEnabled" to true,
                                "avatarSeed" to avatarSeed,
                                "ipAddress" to (ipInfo?.ipAddress ?: "Unknown"),
                                "country" to (ipInfo?.country ?: "Unknown"),
                                "experience" to 0, // Initial experience

                                // Action logs for tracking limits
                                "actionLogs" to mapOf(
                                    "lastPlanetaryTravelTimestamp" to 0L,
                                    "planetaryTravelCountToday" to 0,
                                    "lastIntergalacticTravelTimestamp" to 0L,
                                    "intergalacticTravelCountThisWeek" to 0,
                                    "lastPublicMessageTimestamp" to 0L,
                                    "publicMessageCountToday" to 0,
                                    "lastPrivateMessageTimestamp" to 0L,
                                    "privateMessageCountToday" to 0,
                                    "lastAvatarChangeTimestamp" to 0L,
                                    "avatarChangeCountThisMonth" to 0
                                )
                            )

                            database.reference.child("users").child(userId).setValue(userMap)
                                .addOnSuccessListener {
                                    user.sendEmailVerification()
                                        .addOnCompleteListener { verificationTask ->
                                            progressBar.visibility = View.GONE
                                            registerButton.isEnabled = true
                                            if (verificationTask.isSuccessful) {
                                                Toast.makeText(context, "Registration successful! A verification email has been sent to ${user.email}.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Registration successful, but failed to send verification email.", Toast.LENGTH_LONG).show()
                                            }
                                            auth.signOut()
                                            findNavController().navigate(R.id.action_register_to_login)
                                        }
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    registerButton.isEnabled = true
                                    Toast.makeText(context, "Failed to save data: ${e.message}", Toast.LENGTH_LONG).show()
                                    user.delete() // Rollback user creation
                                }
                        } else {
                            progressBar.visibility = View.GONE
                            registerButton.isEnabled = true
                            if (task.exception is FirebaseAuthUserCollisionException) {
                                Toast.makeText(context, "An account with this email already exists. Please log in.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                Log.w("RegisterFragment", "createUserWithEmail:failure", task.exception)
                            }
                        }
                    }
            }
        }

        goToLoginTextView.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) {
        // Set up touch listener for non-text box views to hide keyboard.
        if (view !is EditText) {
            view.setOnTouchListener { v, event ->
                hideKeyboard()
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
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

    private fun setupCosmicSpinners() {
        galaxies = CosmicNameGenerator.generateGalaxies()
        val galaxyNames = galaxies.map { it.name }
        val galaxyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, galaxyNames)
        galaxyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        galaxySpinner.adapter = galaxyAdapter

        galaxySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedGalaxy = galaxies[position]
                val stars = CosmicNameGenerator.generateStars(selectedGalaxy.name)
                val starAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, stars)
                starAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                starSpinner.adapter = starAdapter
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        starSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStar = parent?.getItemAtPosition(position).toString()
                val planets = CosmicNameGenerator.generatePlanets(selectedStar)
                val planetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, planets)
                planetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                planetSpinner.adapter = planetAdapter
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}
