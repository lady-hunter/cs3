package com.example.date_chat2.ui.main.matches

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.date_chat2.R
import com.example.date_chat2.data.model.MatchItem
import com.example.date_chat2.data.repository.ProfileRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.chat.ChatActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MatchesFragment : Fragment() {

    private val profileRepository = ProfileRepository()
    private val supabase = SupabaseManager.client
    private val adapter = MatchAdapter { match -> openChat(match.otherUserId) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingView: ProgressBar

    private var matches: List<MatchItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_matches, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_matches)
        emptyView = view.findViewById(R.id.tv_no_matches)
        loadingView = view.findViewById(R.id.pb_matches)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        if (this::recyclerView.isInitialized) {
            loadMatches()
        }
    }

    private fun loadMatches() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
        if (userId == null) {
            Log.e(TAG, "MATCH TAB load aborted because current user id is null")
            showEmptyState(getString(R.string.session_expired))
            return
        }

        Log.d(TAG, "MATCH TAB currentUserId=$userId")
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            profileRepository.getMatchesForUser(userId)
                .onSuccess { items ->
                    matches = items
                    adapter.submitList(items)
                    Log.d(TAG, "MATCH TAB loaded count=${items.size}")
                    if (items.isEmpty()) {
                        showEmptyState(getString(R.string.no_matches))
                    } else {
                        showList()
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "MATCH TAB load failed currentUserId=$userId message=${error.message}", error)
                    showEmptyState(getString(R.string.matches_load_failed))
                }
        }
    }

    private fun showList() {
        loadingView.visibility = View.GONE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun showEmptyState(message: String) {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.GONE
        }
    }

    private fun openChat(matchedUserId: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_MATCHED_USER_ID, matchedUserId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        recyclerView.adapter = null
        matches = emptyList()
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "MatchesFragment"
    }
}
