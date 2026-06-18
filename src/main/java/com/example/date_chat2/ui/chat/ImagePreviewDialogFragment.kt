package com.example.date_chat2.ui.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
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

        Glide.with(this)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .fitCenter()
            .into(imageView)
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
