package com.example.date_chat2.ui.main.swipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.data.model.Profile
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
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
    private var currentUserId: String? = null

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

        layoutManager = CardStackLayoutManager(requireContext(), this).apply {
            setDirections(listOf(Direction.Left, Direction.Right))
            setCanScrollVertical(false)
            setSwipeableMethod(SwipeableMethod.AutomaticAndManual)
        }
        cardStackView?.layoutManager = layoutManager

        likeButton?.setOnClickListener { performSwipe(Direction.Right) }
        skipButton?.setOnClickListener { performSwipe(Direction.Left) }

        loadProfiles()
    }

    private fun loadProfiles() {
        val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            showMessage(getString(R.string.session_expired))
            return
        }
        currentUserId = userId
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.getProfilesForSwiping(userId)
                .onSuccess { loadedProfiles ->
                    profiles.clear()
                    profiles.addAll(loadedProfiles)
                    cardStackView?.adapter = ProfileCardAdapter(profiles)
                    setLoading(false)
                    updateEmptyState()
                }
                .onFailure {
                    setLoading(false)
                    showMessage(getString(R.string.profiles_load_failed))
                }
        }
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
        val action = if (direction == Direction.Right) "like" else "skip"

        setActionButtonsEnabled(true)
        updateEmptyState()

        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.saveSwipeAction(userId, profile.id, action)
                .onFailure {
                    context?.let { context ->
                        Toast.makeText(
                            context,
                            R.string.swipe_save_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
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
        cardStackView?.adapter = null
        cardStackView = null
        layoutManager = null
        loadingView = null
        emptyView = null
        actionsView = null
        likeButton = null
        skipButton = null
        super.onDestroyView()
    }
}
