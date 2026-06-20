package com.example.date_chat2.ui.main

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.date_chat2.R
import com.example.date_chat2.data.repository.NotificationRepository
import com.example.date_chat2.network.SupabaseManager
import com.example.date_chat2.ui.main.matches.MatchesFragment
import com.example.date_chat2.ui.main.swipe.SwipeFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val supabase = SupabaseManager.client
    private val notificationRepository = NotificationRepository()
    private lateinit var bottomNavigation: BottomNavigationView
    private var currentMatchIds = emptySet<Long>()
    private var matchesTabSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bottomNavigation = findViewById(R.id.bottom_navigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_swipe -> {
                    matchesTabSelected = false
                    replaceFragment(SwipeFragment())
                    true
                }
                R.id.nav_matches -> {
                    matchesTabSelected = true
                    markCurrentMatchesSeen()
                    replaceFragment(MatchesFragment())
                    true
                }
                R.id.nav_profile -> {
                    matchesTabSelected = false
                    replaceFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_swipe
        } else {
            matchesTabSelected = bottomNavigation.selectedItemId == R.id.nav_matches
        }

        observeNotificationChanges()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationState()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun observeNotificationChanges() {
        val channel = supabase.realtime.channel("home-notifications")
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.onEach {
            refreshNotificationState()
        }.launchIn(lifecycleScope)
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "matches"
        }.onEach {
            refreshNotificationState()
        }.launchIn(lifecycleScope)

        lifecycleScope.launch { channel.subscribe() }
    }

    private fun refreshNotificationState() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        lifecycleScope.launch {
            try {
                val unreadCount = notificationRepository
                    .getUnreadMessageCounts(userId)
                    .values
                    .sum()
                currentMatchIds = notificationRepository.getMatchIds(userId)
                val hasNewMatches = updateMatchSeenState(userId)
                updateMatchesBadge(unreadCount, hasNewMatches)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to refresh in-app notifications", error)
            }
        }
    }

    private fun updateMatchSeenState(userId: String): Boolean {
        val preferences = getSharedPreferences(PREFS_NOTIFICATIONS, MODE_PRIVATE)
        val initializedKey = "matches_initialized_$userId"
        val seenKey = "seen_matches_$userId"
        val currentIds = currentMatchIds.map(Long::toString).toSet()

        if (!preferences.getBoolean(initializedKey, false) || matchesTabSelected) {
            preferences.edit()
                .putBoolean(initializedKey, true)
                .putStringSet(seenKey, currentIds)
                .apply()
            return false
        }

        val seenIds = preferences.getStringSet(seenKey, emptySet()).orEmpty()
        return currentIds.any { it !in seenIds }
    }

    private fun markCurrentMatchesSeen() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        val currentIds = currentMatchIds.map(Long::toString).toSet()
        getSharedPreferences(PREFS_NOTIFICATIONS, MODE_PRIVATE)
            .edit()
            .putBoolean("matches_initialized_$userId", true)
            .putStringSet("seen_matches_$userId", currentIds)
            .apply()
        refreshNotificationState()
    }

    private fun updateMatchesBadge(unreadCount: Int, hasNewMatches: Boolean) {
        if (unreadCount == 0 && !hasNewMatches) {
            bottomNavigation.removeBadge(R.id.nav_matches)
            return
        }

        bottomNavigation.getOrCreateBadge(R.id.nav_matches).apply {
            isVisible = true
            backgroundColor = getColor(R.color.tinder_pink)
            badgeTextColor = getColor(R.color.white)
            if (unreadCount > 0) {
                number = unreadCount
            } else {
                clearNumber()
            }
        }
    }

    private companion object {
        const val TAG = "HomeActivity"
        const val PREFS_NOTIFICATIONS = "in_app_notifications"
    }
}
