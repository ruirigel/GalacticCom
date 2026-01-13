package com.rmrbranco.galacticcom.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.rmrbranco.galacticcom.R

class ImageViewerDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_IMAGE_URL = "image_url"

        fun newInstance(imageUrl: String): ImageViewerDialogFragment {
            val fragment = ImageViewerDialogFragment()
            val args = Bundle()
            args.putString(ARG_IMAGE_URL, imageUrl)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_image_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUrl = arguments?.getString(ARG_IMAGE_URL)
        val zoomableImageView = view.findViewById<ZoomableImageView>(R.id.zoomable_image_view)
        val closeButton = view.findViewById<ImageButton>(R.id.btn_close)

        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .into(zoomableImageView)
        }

        closeButton.setOnClickListener {
            dismiss()
        }
    }
}