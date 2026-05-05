package com.example.myapplication

import android.content.Context

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton

import androidx.fragment.app.Fragment




class InitialFragment : Fragment(R.layout.fragment_initial) {

    private lateinit var button: ImageButton

    interface OnButtonClickListener{
        fun checkOverlayPermission()


    }
    private var callback: OnButtonClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? OnButtonClickListener

    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_initial, container, false)
        button = view.findViewById(R.id.button1)
        button.setOnClickListener {
            Log.d("APP", "Button clicked")
            callback?.checkOverlayPermission()
        }
        return view
    }



}