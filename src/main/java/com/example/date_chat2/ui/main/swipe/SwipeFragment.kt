package com.example.date_chat2.ui.main.swipe

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.date_chat2.R
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.model.SwipeFilterPreferences
import com.example.date_chat2.data.repository.DuplicateSwipeException
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.chat.ChatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.CardStackView
import com.yuyakaido.android.cardstackview.Direction
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting
import com.yuyakaido.android.cardstackview.SwipeableMethod
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class SwipeFragment : Fragment(), CardStackListener {

    private val profileRepository = ProfileRepository()
    private val profiles = mutableListOf<Profile>()

    private var cardStackView: CardStackView? = null
    private var layoutManager: CardStackLayoutManager? = null
    private var loadingView: ProgressBar? = null
    private var emptyView: TextView? = null
    private var actionsView: View? = null
    private var likeButton: Button? = null
    private var skipButton: Button? = null
    private var filterButton: Button? = null
    private var currentUserId: String? = null
    private var currentPreferences = SwipeFilterPreferences()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_swipe, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardStackView = view.findViewById(R.id.card_stack_view)
        loadingView = view.findViewById(R.id.pb_loading_profiles)
        emptyView = view.findViewById(R.id.tv_no_profiles)
        actionsView = view.findViewById(R.id.swipe_actions)
        likeButton = view.findViewById(R.id.btn_like)
        skipButton = view.findViewById(R.id.btn_skip)
        filterButton = view.findViewById(R.id.btn_swipe_filters)

        configureCardStack()

        likeButton?.setOnClickListener { performSwipe(Direction.Right) }
        skipButton?.setOnClickListener { performSwipe(Direction.Left) }
        filterButton?.setOnClickListener { showFilterDialog() }

        loadProfiles()
    }

    private fun configureCardStack() {
        layoutManager = CardStackLayoutManager(requireContext(), this).apply {
            setDirections(listOf(Direction.Left, Direction.Right))
            setCanScrollVertical(false)
            setSwipeableMethod(SwipeableMethod.AutomaticAndManual)
        }
        cardStackView?.layoutManager = layoutManager
    }

    private fun loadProfiles() {
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            Log.e(TAG, "SWIPE LOAD aborted because current user id is null")
            showMessage(getString(R.string.session_expired))
            return
        }
        Log.d(TAG, "SWIPE LOAD currentUserId=$userId")
        currentUserId = userId
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.getSwipeFilterPreferences(userId)
                .onSuccess { preferences ->
                    currentPreferences = preferences
                    loadCandidates(userId, preferences)
                }
                .onFailure {
                    Log.e(TAG, "SWIPE FILTER LOAD failed currentUserId=$userId", it)
                    setLoading(false)
                    showMessage(getString(R.string.profiles_load_failed))
                }
        }
    }

    private suspend fun loadCandidates(userId: String, preferences: SwipeFilterPreferences) {
        profileRepository.getProfilesForSwiping(userId, preferences)
                .onSuccess { loadedProfiles ->
                    Log.d(TAG, "SWIPE LOAD profilesLoaded=${loadedProfiles.size}")
                    loadedProfiles.forEach { profile ->
                        Log.d(
                            TAG,
                            "SWIPE PROFILE id=${profile.id} full_name=${profile.full_name} " +
                                "avatar_url=${profile.avatar_url} bio=${profile.bio}"
                        )
                    }
                    profiles.clear()
                    profiles.addAll(loadedProfiles)
                    configureCardStack()
                    cardStackView?.adapter = ProfileCardAdapter(profiles)
                    setLoading(false)
                    updateEmptyState()
                }
                .onFailure {
                    Log.e(TAG, "SWIPE LOAD failed currentUserId=$userId", it)
                    setLoading(false)
                    showMessage(getString(R.string.profiles_load_failed))
                }
    }

    private fun showFilterDialog() {
        val userId = currentUserId ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_swipe_filters, null)
        val genderGroup = dialogView.findViewById<RadioGroup>(R.id.rg_preferred_gender)
        val minAgeLayout = dialogView.findViewById<TextInputLayout>(R.id.til_min_age)
        val maxAgeLayout = dialogView.findViewById<TextInputLayout>(R.id.til_max_age)
        val minAgeInput = dialogView.findViewById<TextInputEditText>(R.id.et_min_age)
        val maxAgeInput = dialogView.findViewById<TextInputEditText>(R.id.et_max_age)

        genderGroup.check(
            when (currentPreferences.preferredGender) {
                "male" -> R.id.rb_gender_male
                "female" -> R.id.rb_gender_female
                else -> R.id.rb_gender_any
            }
        )
        minAgeInput.setText(currentPreferences.minAge.toString())
        maxAgeInput.setText(currentPreferences.maxAge.toString())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.swipe_filters)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    minAgeLayout.error = null
                    maxAgeLayout.error = null
                    val minAge = minAgeInput.text?.toString()?.toIntOrNull()
                    val maxAge = maxAgeInput.text?.toString()?.toIntOrNull()
                    when {
                        minAge == null || maxAge == null -> {
                            minAgeLayout.error = getString(R.string.filter_age_required)
                        }
                        minAge < MIN_AGE -> {
                            minAgeLayout.error = getString(R.string.filter_min_age_invalid)
                        }
                        maxAge < minAge || maxAge > MAX_AGE -> {
                            maxAgeLayout.error = getString(R.string.filter_max_age_invalid)
                        }
                        else -> {
                            val preferences = SwipeFilterPreferences(
                                preferredGender = when (genderGroup.checkedRadioButtonId) {
                                    R.id.rb_gender_male -> "male"
                                    R.id.rb_gender_female -> "female"
                                    else -> "any"
                                },
                                minAge = minAge,
                                maxAge = maxAge
                            )
                            saveFilters(dialog, userId, preferences)
                        }
                    }
                }
        }
        dialog.show()
    }

    private fun saveFilters(
        dialog: androidx.appcompat.app.AlertDialog,
        userId: String,
        preferences: SwipeFilterPreferences
    ) {
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.saveSwipeFilterPreferences(userId, preferences)
                .onSuccess {
                    currentPreferences = preferences
                    dialog.dismiss()
                    loadProfiles()
                }
                .onFailure {
                    Log.e(TAG, "SWIPE FILTER SAVE failed currentUserId=$userId", it)
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .isEnabled = true
                    showMessageToast(getString(R.string.filters_save_failed))
                }
        }
    }

    private fun showMessageToast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    private fun performSwipe(direction: Direction) {
        val manager = layoutManager ?: return
        if (manager.topPosition >= profiles.size) return

        setActionButtonsEnabled(false)
        manager.setSwipeAnimationSetting(
            SwipeAnimationSetting.Builder()
                .setDirection(direction)
                .build()
        )
        cardStackView?.swipe()
    }

    override fun onCardSwiped(direction: Direction) {
        val swipedPosition = (layoutManager?.topPosition ?: return) - 1
        val profile = profiles.getOrNull(swipedPosition) ?: return
        val userId = currentUserId ?: return
        val action = when (direction) {
            Direction.Right -> "like"
            Direction.Left -> "skip"
            else -> return
        }

        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(
                TAG,
                "SWIPE SAVE start currentUserId=$userId targetUserId=${profile.id} action=$action"
            )
            profileRepository.saveSwipeAction(userId, profile.id, action)
                .onSuccess { matchCreatedNew ->
                    Log.d(
                        TAG,
                        "SWIPE SAVE success currentUserId=$userId targetUserId=${profile.id} action=$action"
                    )
                    Log.d(
                        TAG,
                        "MATCH RESULT targetUserId=${profile.id} matchCreatedNew=$matchCreatedNew"
                    )
                    setActionButtonsEnabled(true)
                    updateEmptyState()
                    if (matchCreatedNew && isAdded && view != null) {
                        showMatchPopup(profile)
                    }
                }
                .onFailure { error ->
                    Log.e(
                        TAG,
                        "SWIPE SAVE failed currentUserId=$userId targetUserId=${profile.id} action=$action message=${error.message}",
                        error
                    )
                    setActionButtonsEnabled(true)
                    updateEmptyState()
                    context?.let { context ->
                        Toast.makeText(
                            context,
                            if (error is DuplicateSwipeException) {
                                "You already swiped this profile."
                            } else {
                                getString(R.string.swipe_save_failed)
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun showMatchPopup(profile: Profile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_match, null)
        val avatarView = dialogView.findViewById<ImageView>(R.id.iv_match_popup_avatar)
        val subtitleView = dialogView.findViewById<TextView>(R.id.tv_match_popup_subtitle)
        val sendMessageButton = dialogView.findViewById<Button>(R.id.btn_match_send_message)
        val keepSwipingButton = dialogView.findViewById<Button>(R.id.btn_match_keep_swiping)
        val matchedName = profile.full_name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown_name)

        subtitleView.text = "You and $matchedName liked each other."
        Glide.with(this)
            .load(profile.avatar_url)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .circleCrop()
            .into(avatarView)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        sendMessageButton.setOnClickListener {
            Log.d(TAG, "MATCH POPUP sendMessageClicked targetUserId=${profile.id}")
            dialog.dismiss()
            startActivity(
                Intent(requireContext(), ChatActivity::class.java)
                    .putExtra(EXTRA_MATCHED_USER_ID, profile.id)
                    .putExtra(EXTRA_MATCHED_USER_NAME, matchedName)
            )
        }
        keepSwipingButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
        Log.d(TAG, "MATCH POPUP popupShown targetUserId=${profile.id}")
    }

    private fun setLoading(isLoading: Boolean) {
        loadingView?.visibility = if (isLoading) View.VISIBLE else View.GONE
        cardStackView?.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        actionsView?.visibility = if (isLoading) View.GONE else View.VISIBLE
        emptyView?.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        loadingView?.visibility = View.GONE
        cardStackView?.visibility = View.INVISIBLE
        actionsView?.visibility = View.GONE
        emptyView?.apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun updateEmptyState() {
        val hasProfile = (layoutManager?.topPosition ?: 0) < profiles.size
        cardStackView?.visibility = if (hasProfile) View.VISIBLE else View.INVISIBLE
        actionsView?.visibility = if (hasProfile) View.VISIBLE else View.GONE
        emptyView?.apply {
            text = getString(R.string.no_profiles)
            visibility = if (hasProfile) View.GONE else View.VISIBLE
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        likeButton?.isEnabled = enabled
        skipButton?.isEnabled = enabled
    }

    override fun onCardDragging(direction: Direction, ratio: Float) = Unit

    override fun onCardRewound() = Unit

    override fun onCardCanceled() {
        setActionButtonsEnabled(true)
    }

    override fun onCardAppeared(view: View, position: Int) = Unit

    override fun onCardDisappeared(view: View, position: Int) = Unit

    override fun onDestroyView() {
        profiles.clear()
        cardStackView = null
        layoutManager = null
        loadingView = null
        emptyView = null
        actionsView = null
        likeButton = null
        skipButton = null
        filterButton = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "SwipeFragment"
        const val EXTRA_MATCHED_USER_ID = "matched_user_id"
        const val EXTRA_MATCHED_USER_NAME = "matched_user_name"
        const val MIN_AGE = 18
        const val MAX_AGE = 99
    }
}
