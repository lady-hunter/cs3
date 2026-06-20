package com.example.date_chat2.ui.chat

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.date_chat2.R

class ImagePreviewDialogFragment : DialogFragment() {

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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_image_preview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUrl = requireArguments().getString(ARG_IMAGE_URL).orEmpty()
        val imageView = view.findViewById<ImageView>(R.id.iv_preview_image)
        view.findViewById<ImageButton>(R.id.btn_close_preview).setOnClickListener {
            dismiss()
        }
        view.findViewById<ImageButton>(R.id.btn_download_image).setOnClickListener {
            downloadImage(imageUrl)
        }

        Glide.with(this)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .fitCenter()
            .into(imageView)
    }

    private fun downloadImage(imageUrl: String) {
        if (imageUrl.isBlank()) return

        try {
            val fileName = "date_chat_${System.currentTimeMillis()}.jpg"
            val request = DownloadManager.Request(Uri.parse(imageUrl))
                .setTitle(fileName)
                .setMimeType("image/jpeg")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    requireContext(),
                    Environment.DIRECTORY_PICTURES,
                    fileName
                )
            val downloadManager = requireContext()
                .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(requireContext(), R.string.image_download_started, Toast.LENGTH_SHORT)
                .show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.image_download_failed, Toast.LENGTH_SHORT)
                .show()
        }
    }

    companion object {
        const val TAG = "ImagePreviewDialog"
        private const val ARG_IMAGE_URL = "image_url"

        fun newInstance(imageUrl: String) = ImagePreviewDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_IMAGE_URL, imageUrl)
            }
        }
    }
}
