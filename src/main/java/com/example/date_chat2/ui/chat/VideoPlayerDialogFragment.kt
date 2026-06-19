package com.example.date_chat2.ui.chat

import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import android.widget.VideoView
import androidx.fragment.app.DialogFragment
import com.example.date_chat2.R

class VideoPlayerDialogFragment : DialogFragment() {

    private var videoView: VideoView? = null
    private var playPauseButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)

            videoView = VideoView(context).also { player ->
                addView(
                    player,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            playPauseButton = ImageButton(context).also { button ->
                button.setImageResource(R.drawable.ic_play_circle)
                button.setBackgroundColor(Color.TRANSPARENT)
                button.contentDescription = getString(R.string.play_video)
                addView(
                    button,
                    FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER)
                )
            }

            addView(
                ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setBackgroundColor(Color.TRANSPARENT)
                    setColorFilter(Color.WHITE)
                    contentDescription = getString(R.string.close_video)
                    setOnClickListener { dismiss() }
                },
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(16)
                    marginEnd = dp(16)
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val videoUrl = requireArguments().getString(ARG_VIDEO_URL).orEmpty()
        val player = requireNotNull(videoView)
        val control = requireNotNull(playPauseButton)

        fun updateControl(isPlaying: Boolean) {
            control.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else R.drawable.ic_play_circle
            )
            control.contentDescription = getString(
                if (isPlaying) R.string.pause_video else R.string.play_video
            )
        }

        fun togglePlayback() {
            if (player.isPlaying) {
                player.pause()
                updateControl(false)
            } else {
                player.start()
                updateControl(true)
            }
        }

        control.setOnClickListener { togglePlayback() }
        player.setOnClickListener { togglePlayback() }
        player.setOnPreparedListener {
            player.start()
            updateControl(true)
        }
        player.setOnCompletionListener { updateControl(false) }
        player.setOnErrorListener { _, _, _ ->
            Toast.makeText(requireContext(), R.string.video_playback_failed, Toast.LENGTH_SHORT).show()
            updateControl(false)
            true
        }
        player.setVideoURI(Uri.parse(videoUrl))
    }

    override fun onStop() {
        videoView?.pause()
        super.onStop()
    }

    override fun onDestroyView() {
        videoView?.stopPlayback()
        videoView = null
        playPauseButton = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "VideoPlayerDialog"
        private const val ARG_VIDEO_URL = "video_url"

        fun newInstance(videoUrl: String) = VideoPlayerDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_VIDEO_URL, videoUrl)
            }
        }
    }
}
