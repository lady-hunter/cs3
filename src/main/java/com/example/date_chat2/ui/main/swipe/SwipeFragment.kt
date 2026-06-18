package com.example.date_chat2.ui.main.swipe

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.date_chat2.R

class SwipeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_swipe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: Step 2 - Initialize CardStackView and its manager
        // TODO: Step 3 - Fetch profiles from repository
        // TODO: Step 4 - Create and set adapter
        // TODO: Step 5 - Set swipe listener
    }
}

